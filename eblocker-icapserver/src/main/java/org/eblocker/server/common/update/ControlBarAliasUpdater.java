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
package org.eblocker.server.common.update;

import org.eblocker.server.common.network.InetAddressWrapper;
import org.eblocker.server.common.network.unix.NetworkInterfaceAliases;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ControlBarAliasUpdater {

    private static final Logger log = LoggerFactory.getLogger(ControlBarAliasUpdater.class);

    private final String controlBarHostName;
    private final String controlBarHostFallbackIp;
    private final long regularPeriod;
    private final long errorPeriod;
    private final int errorLogThreshold;
    private final long initialDelay;
    private final InetAddressWrapper inetAddress;
    private final NetworkInterfaceAliases networkInterfaceAliases;
    private final ScheduledExecutorService scheduledExecutorService;

    private String alias;
    private String controlBarAddress;
    private ScheduledFuture future;
    private int errors;

    @Inject
    public ControlBarAliasUpdater(@Named("network.control.bar.host.name") String controlBarHostName,
                                  @Named("network.control.bar.host.fallback.ip") String controlBarHostFallbackIp,
                                  @Named("network.control.bar.updater.initial.delay") long initialDelay,
                                  @Named("network.control.bar.updater.regular.period") long regularPeriod,
                                  @Named("network.control.bar.updater.error.period") long errorPeriod,
                                  @Named("network.control.bar.updater.error.log.threshold") int errorLogThreshold,
                                  InetAddressWrapper inetAddress,
                                  NetworkInterfaceAliases networkInterfaceAliases,
                                  @Named("lowPrioScheduledExecutor") ScheduledExecutorService scheduledExecutorService) {
        this.controlBarHostName = controlBarHostName;
        this.controlBarHostFallbackIp = controlBarHostFallbackIp;
        this.initialDelay = initialDelay;
        this.regularPeriod = regularPeriod;
        this.errorPeriod = errorPeriod;
        this.errorLogThreshold = errorLogThreshold;
        this.inetAddress = inetAddress;
        this.networkInterfaceAliases = networkInterfaceAliases;
        this.scheduledExecutorService = scheduledExecutorService;
    }

    public void start() {
        future = scheduledExecutorService.scheduleAtFixedRate(this::regularUpdate, initialDelay, regularPeriod, TimeUnit.SECONDS);
    }

    private void regularUpdate() {
        try {
            update();
        } catch (UnknownHostException e) {
            log.debug("failed to resolve control bar host: {} in regular update.", controlBarHostName, e);
            future.cancel(false);
            log.debug("scheduling error update in {}s.", errorPeriod);
            future = scheduledExecutorService.scheduleAtFixedRate(this::errorUpdate, errorPeriod, errorPeriod, TimeUnit.SECONDS);

            if (alias == null) {
                log.info("no control bar host available at the moment, creating alias with fallback ip {}", controlBarHostFallbackIp);
                alias = networkInterfaceAliases.add(controlBarHostFallbackIp, "255.255.255.255");
                controlBarAddress = controlBarHostFallbackIp;
            }
        }
    }

    private void errorUpdate() {
        try {
            ++errors;
            update();
            log.debug("succeeded to resolve control bar host, scheduling regular updates.");
            future.cancel(false);
            future = scheduledExecutorService.scheduleAtFixedRate(this::regularUpdate, regularPeriod, regularPeriod, TimeUnit.SECONDS);
        } catch (UnknownHostException e) {
            if (errorLogThreshold != 0 && errors % errorLogThreshold == 0) {
                log.warn("failed to resolve control bar host: {} in error update #{}.", controlBarHostName, errors, e);
            } else {
                log.debug("failed to resolve control bar host: {} in error update #{}.", controlBarHostName, errors, e);
            }
        }
    }

    private void update() throws UnknownHostException {
        log.info("checking for control bar host: {} update", controlBarHostName);
        InetAddress newAddress = inetAddress.getByName(controlBarHostName);
        errors = 0;
        if (newAddress.getHostAddress().equals(controlBarAddress)) {
            log.debug("no update needed, address resolves still to {} ", controlBarAddress);
            return;
        }

        log.info("address of {} changed from {} to {}", controlBarHostName, controlBarAddress, newAddress.getHostAddress());

        String newAlias = networkInterfaceAliases.add(newAddress.getHostAddress(), "255.255.255.255");
        log.debug("assigned alias {} to {}", newAlias, newAddress);

        if (alias != null) {
            log.debug("removing old alias {} {}", alias, controlBarAddress);
            networkInterfaceAliases.remove(alias);
        }

        alias = newAlias;
        controlBarAddress = newAddress.getHostAddress();
    }
}
