/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License. You may obtain a copy of the License at:
 *
 *   https://joinup.ec.europa.eu/page/eupl-text-11-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.eblocker.server.common.squid;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.pki.PKI;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.http.service.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SubSystemService(SubSystem.BACKGROUND_TASKS)
public class SquidWarningService {
    private static final Logger log = LoggerFactory.getLogger(SquidWarningService.class);

    private final long backlogMaxAgeInDays;
    private final Set<String> ignoredErrors;
    private final long updateTaskInitialDelay;
    private final long updateTaskFixedRate;
    private final Clock clock;
    private final DataSource dataSource;
    private final DeviceService deviceService;
    private final ScheduledExecutorService executorService;
    private final SquidCacheLogReader cacheLogReader;
    private final SquidConfigController squidConfigController;
    private final List<FailedConnectionsListener> listeners = new ArrayList<>();

    private Future<?> updateTask;

    @Inject
    public SquidWarningService(@Named("squid.ssl.error.backlog.max.age.days") long backlogMaxAgeInDays,
                               @Named("squid.ssl.error.ignored") String ignoredErrors,
                               @Named("executor.squidWarning.startupDelay") long updateTaskInitialDelay,
                               @Named("executor.squidWarning.fixedRate") long updateTaskFixedRate,
                               Clock clock,
                               DataSource dataSource,
                               DeviceService deviceService,
                               @Named("lowPrioScheduledExecutor") ScheduledExecutorService executorService,
                               SquidCacheLogReader cacheLogReader,
                               SquidConfigController squidConfigController) {
        this.backlogMaxAgeInDays = backlogMaxAgeInDays;
        this.ignoredErrors = splitAndTrim(ignoredErrors);
        this.updateTaskInitialDelay = updateTaskInitialDelay;
        this.updateTaskFixedRate = updateTaskFixedRate;
        this.clock = clock;
        this.dataSource = dataSource;
        this.deviceService = deviceService;
        this.executorService = executorService;
        this.cacheLogReader = cacheLogReader;
        this.squidConfigController = squidConfigController;

        deviceService.addListener(new DeviceService.DeviceChangeListener() {
            @Override
            public void onChange(Device device) {
                checkFailedConnectionSettings(device);
            }

            @Override
            public void onDelete(Device device) {
                checkFailedConnectionSettings(device);
            }

            private void checkFailedConnectionSettings(Device device) {
                if (!device.isSslRecordErrorsEnabled()) {
                    clearFailedConnections(device);
                }
            }

            @Override
            public void onReset(Device device) {
                // Nothing to do here
            }
        });
    }

    @SubSystemInit
    public synchronized void init() throws IOException {
        if (dataSource.getSslRecordErrors()) {
            start();
        }
    }

    public void addListener(FailedConnectionsListener listener) {
        listeners.add(listener);
    }

    public boolean isEnabled() {
        return dataSource.getSslRecordErrors();
    }

    public synchronized void clearFailedConnections() {
        log.debug("removing all failed connections");
        cacheLogReader.pollFailedConnections();
        dataSource.delete(FailedConnectionsEntity.class);
        notifyFailedConnectionListenersOnReset();
    }

    public synchronized void clearFailedConnections(Device device) {
        log.debug("removing all failed connections for {}", device.getId());

        List<FailedConnection> connections = load();
        connections = connections.stream()
            .map(c -> {
                List<String> filteredDeviceIds = c.getDeviceIds().stream()
                    .filter(id -> !id.equals(device.getId()))
                    .collect(Collectors.toList());
                return new FailedConnection(filteredDeviceIds, c.getDomains(), c.getErrors(), c.getLastOccurrence());
            })
            .filter(c -> !c.getDeviceIds().isEmpty())
            .collect(Collectors.toList());
        save(connections);
    }

    public synchronized void setRecordingFailedConnectionsEnabled(boolean enabled) throws IOException {
        boolean currentlyEnabled = dataSource.getSslRecordErrors();
        if (currentlyEnabled == enabled) {
            log.debug("recording is already {}", enabled);
            return;
        }

        if (enabled) {
            start();
        } else {
            stop();
        }

        dataSource.setSslRecordErrors(enabled);
        squidConfigController.updateSquidConfig();
    }

    private void start() throws IOException {
        log.info("starting log reader");
        cacheLogReader.start();

        log.info("scheduling update task");
        updateTask = executorService.scheduleAtFixedRate(this::updateFailedConnections, updateTaskInitialDelay, updateTaskFixedRate, TimeUnit.SECONDS);
    }

    private void stop() throws IOException {
        log.info("stopping log reader");
        cacheLogReader.stop();

        log.info("canceling update task");
        updateTask.cancel(false);

        log.info("deleting all logged connections");
        clearFailedConnections();
    }

    private void updateFailedConnections() {
        List<FailedConnectionLogEntry> failedConnectionLogEntries = fetchFailedConnectionLogEntries();
        if (failedConnectionLogEntries.isEmpty()) {
            log.debug("No new failed connections");
            return;
        } else {
            mergeInNewFailedConnections(failedConnectionLogEntries);
        }
    }

    private List<FailedConnectionLogEntry> fetchFailedConnectionLogEntries() {
        if (!dataSource.getSslRecordErrors()) {
            log.debug("recording errors disabled");
        }

        log.debug("running update");

        List<FailedConnectionLogEntry> newFailedConnections = cacheLogReader.pollFailedConnections();
        if (newFailedConnections.isEmpty()) {
            log.debug("No new failed connections");
        }
        return newFailedConnections;
    }

    private synchronized List<FailedConnection> mergeInNewFailedConnections(List<FailedConnectionLogEntry> failedConnectionLogEntries) {
        List<FailedConnection> failedConnections = load();

        boolean modified = insertUpdateNewEntries(failedConnections, failedConnectionLogEntries);
        modified |= removeOldEntries(failedConnections);
        if (modified) {
            log.debug("update finished - writing changes to db");
            save(failedConnections);
            notifyFailedConnectionListenersOnChange(failedConnections);
        } else {
            log.debug("update finished - no changes");
        }

        return failedConnections;
    }

    public List<FailedConnection> updatedFailedConnections() {
        List<FailedConnectionLogEntry> failedConnectionLogEntries = fetchFailedConnectionLogEntries();

        return mergeInNewFailedConnections(failedConnectionLogEntries);
    }

    private void notifyFailedConnectionListenersOnChange(List<FailedConnection> failedConnections) {
        listeners.forEach(listener -> {
            try {
                listener.onChange(failedConnections);
            } catch (Exception e) {
                log.error("Exception while calling FailedConnectionsListener", e);
            }
        });
    }

    private void notifyFailedConnectionListenersOnReset() {
        listeners.forEach(listener -> {
            try {
                listener.onReset();
            } catch (Exception e) {
                log.error("Exception while calling FailedConnectionsListener", e);
            }
        });
    }

    private boolean insertUpdateNewEntries(List<FailedConnection> failedConnections, List<FailedConnectionLogEntry> polledFailedConnections) {
        boolean modified = false;
        for (FailedConnectionLogEntry entry : polledFailedConnections) {
            List<String> domains = getDomains(entry);
            if (domains.isEmpty()) {
                log.warn("could not extract any domain from failed log entry");
                continue;
            }

            if (ignoredErrors.contains(entry.getError())) {
                log.info("error {} is ignored", entry.getError());
                continue;
            }

            Device device = deviceService.getDeviceById(entry.getDeviceId());
            if (!device.isSslRecordErrorsEnabled()) {
                log.info("error recording for {} is disabled", device.getId());
                continue;
            }

            modified = true;
            ListIterator<FailedConnection> i = findFailedConnections(failedConnections, domains);
            if (i != null) {
                FailedConnection failedConnection = i.previous();
                List<String> deviceIds = addIfNotExists(entry.getDeviceId(), failedConnection.getDeviceIds());
                List<String> errors = addIfNotExists(entry.getError(), failedConnection.getErrors());
                FailedConnection newConnection = new FailedConnection(deviceIds, domains, errors, entry.getInstant());
                i.set(newConnection);
            } else {
                List<String> deviceIds = Lists.newArrayList(entry.getDeviceId());
                List<String> errors = Lists.newArrayList(entry.getError());
                FailedConnection newConnection = new FailedConnection(deviceIds, domains, errors, entry.getInstant());
                failedConnections.add(newConnection);
            }
        }

        return modified;
    }

    private List<String> addIfNotExists(String entry, List<String> list) {
        if (!list.contains(entry)) {
            list.add(entry);
        }
        return list;
    }

    private boolean removeOldEntries(List<FailedConnection> failedConnections) {
        Instant maxAge = ZonedDateTime.now(clock).minusDays(backlogMaxAgeInDays).toInstant();
        boolean modified = failedConnections.removeIf(c -> !maxAge.isBefore(c.getLastOccurrence()));
        failedConnections.sort(Comparator.comparing(FailedConnection::getLastOccurrence));
        return modified;
    }

    private List<FailedConnection> load() {
        FailedConnectionsEntity entity = dataSource.get(FailedConnectionsEntity.class);
        if (entity == null) {
            return new ArrayList<>();
        }
        return entity.getFailedConnections();
    }

    private void save(List<FailedConnection> failedConnections) {
        FailedConnectionsEntity entity = new FailedConnectionsEntity(failedConnections);
        dataSource.save(entity);
    }

    private List<String> getDomains(FailedConnectionLogEntry entry) {
        if (entry.getSni() != null) {
            return Collections.singletonList(entry.getSni());
        }

        if (entry.getCertificate() != null) {
            return extractDomainsFromCertificate(entry.getCertificate());
        }

        if (entry.getHost() != null) {
            return Collections.singletonList(entry.getHost());
        }

        return Collections.emptyList();
    }

    private List<String> extractDomainsFromCertificate(String pem) {
        try {
            X509Certificate certificate = PKI.loadCertificate(new ByteArrayInputStream(pem.getBytes()));
            String cn = PKI.getCN(certificate);
            List<String> altNames = certificate.getSubjectAlternativeNames().stream().map(n -> (String) n.get(1)).collect(Collectors.toList());

            LinkedHashSet<String> domains = new LinkedHashSet<>();
            domains.add(cn);
            domains.addAll(altNames);
            return new ArrayList<>(domains);
        } catch (IOException | CryptoException | CertificateParsingException e) {
            log.warn("Failed to decode certificate: {}", pem, e);
            return Collections.emptyList();
        }
    }

    private ListIterator findFailedConnections(List<FailedConnection> connections, List<String> domains) {
        ListIterator<FailedConnection> i = connections.listIterator();
        while (i.hasNext()) {
            FailedConnection connection = i.next();
            if (connection.getDomains().equals(domains)) {
                return i;
            }
        }
        return null;
    }

    private Set<String> splitAndTrim(String input) {
        Set<String> values = new HashSet<>();
        for (String value : input.split(",")) {
            values.add(value.trim());
        }
        return values;
    }

    public interface FailedConnectionsListener {
        void onChange(List<FailedConnection> failedConnections);

        void onReset();
    }
}
