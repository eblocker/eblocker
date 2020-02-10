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
package org.eblocker.server.http.service;

import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.registration.DeviceRegistrationClient;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.eblocker.server.common.registration.RegistrationState;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.common.util.HttpClient;
import org.eblocker.registration.TosContainer;
import org.eblocker.server.common.system.ScriptRunner;
import com.google.common.base.Splitter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
@SubSystemService(SubSystem.SERVICES)
public class RegistrationServiceAvailabilityCheck {

    private static final Logger log = LoggerFactory.getLogger(RegistrationServiceAvailabilityCheck.class);

    private final List<String> pingTargets;
    private final List<String> httpTargets;
    private final String pingCommand;
    private final DeviceRegistrationProperties registrationProperties;
    private final DeviceRegistrationClient registrationClient;
    private final ExecutorService executorService;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduledExecutorService;
    private final ScriptRunner scriptRunner;

    private Boolean registrationAvailable;
    private int checks;

    @Inject
    public RegistrationServiceAvailabilityCheck(@Named("registration.availabilityCheck.ping.targets") String pingTargets,
                                                @Named("registration.availabilityCheck.http.targets") String httpTargets,
                                                @Named("ping.process.command") String pingCommand,
                                                DeviceRegistrationClient registrationClient,
                                                DeviceRegistrationProperties registrationProperties,
                                                @Named("unlimitedCachePoolExecutorService") ExecutorService executorService,
                                                HttpClient httpClient,
                                                @Named("lowPrioScheduledExecutor") ScheduledExecutorService scheduledExecutorService,
                                                ScriptRunner scriptRunner) {
        Splitter splitter = Splitter.on(',').trimResults().omitEmptyStrings();
        this.pingTargets = splitter.splitToList(pingTargets);
        this.httpTargets = splitter.splitToList(httpTargets);
        this.pingCommand = pingCommand;
        this.registrationProperties = registrationProperties;
        this.registrationClient = registrationClient;
        this.executorService = executorService;
        this.httpClient = httpClient;
        this.scheduledExecutorService = scheduledExecutorService;
        this.scriptRunner = scriptRunner;

        checks = 0;
    }

    @SubSystemInit
    public void init() {
        scheduledExecutorService.submit(this::check);
    }

    /**
     * Check if the eBlocker registration service is still operated.
     *
     * Iff internet can be accessed but Terms Of Service can not be retrieved it is assumed it has ceased operation.
     *
     */
    public synchronized boolean isRegistrationAvailable() {
        return registrationAvailable == null || Boolean.TRUE.equals(registrationAvailable);
    }

    private void check() {
        ++checks;

        // No need to check if device has been registered already.
        if (registrationProperties.getRegistrationState() != RegistrationState.NEW) {
            return;
        }

        // If service has been seen once assume it is still available.
        if (Boolean.TRUE.equals(registrationAvailable)) {
            return;
        }

        // Check if registration service is reachable.
        boolean isRegistrationServerOnline = isRegistrationServerOnline();
        if (isRegistrationServerOnline) {
            registrationAvailable = true;
            return;
        }

        // Service or internet is probably not reachable, schedule a recheck to verify it.
        scheduleNextCheck();

        // No need to re-check internet connectivity as it will not change our conclusion if we have already
        // decided it is down.
        if (Boolean.FALSE.equals(registrationAvailable)) {
            return;
        }

        // Only assume service is down if we are connected to the internet.
        if (!isAnyInternetHostPingable()) {
            return;
        }

        if (!isHttpAccessAvailable()) {
            return;
        }

        // We got internet but service is not reachable. Assume it is gone.
        registrationAvailable = false;
    }

    private void scheduleNextCheck() {
        int i = Math.min(checks, 10);
        int delay = (int) Math.pow(2, i);
        log.debug("Scheduling check {} in {} minutes", checks, delay);
        scheduledExecutorService.schedule(this::check, delay, TimeUnit.MINUTES);
    }

    private boolean isAnyInternetHostPingable() {
        return firstMatch(pingTargets, this::pingTask);
    }

    private boolean isHttpAccessAvailable() {
        return firstMatch(httpTargets, this::httpTask);
    }

    private boolean firstMatch(List<String> source, Function<String, Callable<Boolean>> createTaskFn) {
        List<Future<Boolean>> futures = source.stream()
            .map(createTaskFn::apply)
            .map(executorService::submit)
            .collect(Collectors.toList());
        return futures.stream().anyMatch(this::await);
    }

    private boolean isRegistrationServerOnline() {
        try {
            TosContainer container = registrationClient.getTosContainer();
            if (log.isDebugEnabled()) {
                String text = container.getText() != null ? container.getText().toString().substring(0, 10) : "null";
                log.debug("TOS version: {} date: {} text: {}", container.getVersion(), container.getDate(), text);
            }
            return container.getVersion() != null && container.getDate() != null && container.getText() != null && !container.getText().isEmpty();
        } catch (Exception e) {
            log.info("retrieving tos failed", e);
            return false;
        }
    }

    private Callable<Boolean> pingTask(String host) {
        return () -> {
            try {
                int result = scriptRunner.runScript(pingCommand, "-c3", "-w3", host);
                log.debug("pinging {} exit code: {}", host, result);
                return result == 0;
            } catch (IOException e) {
                log.error("failed to ping {}: ", host, e);
                return false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        };
    }

    private Callable<Boolean> httpTask(String url) {
        return () -> {
            try {
                httpClient.download(url);
                return true;
            } catch (Exception e) {
                log.info("retrieving {} failed", url, e);
                return false;
            }
        };
    }

    private boolean await(Future<Boolean> future) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            log.error("exception awaiting result", e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

}
