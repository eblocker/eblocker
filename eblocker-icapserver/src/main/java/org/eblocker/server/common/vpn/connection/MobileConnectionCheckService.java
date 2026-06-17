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
package org.eblocker.server.common.vpn.connection;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.vpn.PortForwardingMode;
import org.eblocker.server.common.exceptions.UpnpPortForwardingException;
import org.eblocker.server.http.service.WireGuardMobileService;
import org.eblocker.server.upnp.UpnpManagementService;
import org.eblocker.server.upnp.UpnpPortForwardingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

@Singleton
public class MobileConnectionCheckService {

    private static final Logger log = LoggerFactory.getLogger(MobileConnectionCheckService.class);

    private final UpnpManagementService upnpService;
    private final ExecutorService executorService;
    private final Provider<MobileConnectionCheckTask> testTaskProvider;
    private final WireGuardMobileService wireGuardMobileService;
    private final DataSource dataSource;
    private final int portForwardingDuration;
    private final int internalPort;
    private final String portForwardingDescription;

    private MobileConnectionCheckStatus status;
    private MobileConnectionCheckTask task;
    private Future<?> future;

    @Inject
    public MobileConnectionCheckService(UpnpManagementService upnpService,
                                        @Named("lowPrioScheduledExecutor") ScheduledExecutorService executorService,
                                        Provider<MobileConnectionCheckTask> testTaskProvider,
                                        WireGuardMobileService wireGuardMobileService,
                                        DataSource dataSource,
                                        @Named("wireguard.mobile.server.portforwarding.duration.connectiontest") int portForwardingDuration,
                                        @Named("wireguard.mobile.server.port") int internalPort,
                                        @Named("wireguard.mobile.server.portforwarding.description") String portForwardingDescription) {
        this.upnpService = upnpService;
        this.executorService = executorService;
        this.testTaskProvider = testTaskProvider;
        this.wireGuardMobileService = wireGuardMobileService;
        this.dataSource = dataSource;
        this.portForwardingDuration = portForwardingDuration;
        this.internalPort = internalPort;
        this.portForwardingDescription = portForwardingDescription;
    }

    public synchronized MobileConnectionCheckStatus getStatus() {
        if (task != null) {
            status = task.getStatus();
            if (future.isDone()) {
                task = null;
            }
        }
        return status;
    }

    public synchronized void start() throws UpnpPortForwardingException {
        if (task != null) {
            stop();
        }

        // If needed, open port
        if (dataSource.getWireGuardMobilePortForwardingMode() == PortForwardingMode.AUTO) {
            openPort();
        }

        task = testTaskProvider.get();
        status = null;
        future = executorService.submit(task);
    }

    private void openPort() throws UpnpPortForwardingException {
        int externalPort = wireGuardMobileService.getWireGuardMappedPort();
        List<UpnpPortForwardingResult> openedPorts = upnpService.addPortForwarding(externalPort, internalPort,
                portForwardingDuration, portForwardingDescription);
        Optional<UpnpPortForwardingResult> potentiallyFailedOpening = openedPorts.stream()
                .filter(res -> !res.isSuccess()).findFirst();
        if (potentiallyFailedOpening.isPresent()) {
            throw new UpnpPortForwardingException(potentiallyFailedOpening.get().getErrorMsg());
        }
    }

    public synchronized void stop() {
        if (task != null) {
            log.info("stopping running test");
            if (!future.cancel(true) && !future.isDone()) {
                log.error("failed to cancel running test");
                throw new IllegalThreadStateException("failed to cancel running test task");
            }

            status = task.getStatus();
            task = null;
        }
    }
}
