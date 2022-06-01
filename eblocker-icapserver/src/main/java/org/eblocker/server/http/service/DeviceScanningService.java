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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.network.ArpSweeper;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.eblocker.server.common.network.NetworkUtils;
import org.eblocker.server.common.util.IpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Singleton
public class DeviceScanningService {
    private static final Logger log = LoggerFactory.getLogger(DeviceScanningService.class);
    private final ArpSweeper arpSweeper;
    private final DataSource dataSource;
    private final long defaultScanningInterval;
    private long currentScanningInterval;
    private final long startupDelay;
    private ScheduledExecutorService highPrioExecutorService;
    private ScheduledFuture<?> scheduledFuture;
    private final List<Integer[]> privateNetworks;
    private final NetworkInterfaceWrapper networkInterface;
    private final boolean serverEnv;

    @Inject
    public DeviceScanningService(ArpSweeper arpSweeper,
                                 DataSource dataSource,
                                 @Named("highPrioScheduledExecutor") ScheduledExecutorService highPrioExecutorService,
                                 @Named("executor.arpSweeper.startupDelay") long startupDelay,
                                 @Named("executor.arpSweeper.default.fixedRate") long defaultScanningInterval,
                                 @Named("environment") String environment,
                                 NetworkInterfaceWrapper networkInterface) {
        this.arpSweeper = arpSweeper;
        this.dataSource = dataSource;
        this.highPrioExecutorService = highPrioExecutorService;
        this.startupDelay = startupDelay;
        this.defaultScanningInterval = defaultScanningInterval;
        this.privateNetworks = createNetworks(NetworkUtils.privateClassA, NetworkUtils.privateClassB, NetworkUtils.privateClassC);
        this.networkInterface = networkInterface;
        serverEnv = environment.equalsIgnoreCase("server");
    }

    public void scan() {
        arpSweeper.fullScan();
    }

    public boolean isScanAvailable() {
        return arpSweeper.isFullScanAvailable();
    }

    /**
     * Set the interval between device scanning runs in seconds
     *
     * @param seconds if set to 0, scanning for devices is disabled
     */
    public void setScanningInterval(long seconds) {
        dataSource.setDeviceScanningInterval(seconds);

        if (currentScanningInterval == seconds) {
            return;
        }

        if (scheduledFuture != null) {
            log.info("Cancelling ARP scanning");
            scheduledFuture.cancel(true);
        }

        schedule(0L, seconds);
    }

    public long getScanningInterval() {
        Long seconds;
        try {
            seconds = dataSource.getDeviceScanningInterval();
        } catch (NumberFormatException e) {
            log.error("Could not get device scanning interval from data source. Returning default value.", e);
            return defaultScanningInterval;
        }

        if (seconds == null) {
            return defaultScanningInterval;
        } else {
            return seconds;
        }
    }

    public void start() {
        long interval = getScanningInterval();
        schedule(startupDelay, interval);
    }

    /**
     * Starts ARP sweeper if given interval is greater than zero
     *
     * @param startupDelay
     * @param interval
     */
    private void schedule(long startupDelay, long interval) {
        if (interval <= 0) {
            log.info("ARP scanning is disabled");
            return;
        }

        if (serverEnv) {
            log.info("ARP scanning disabled because eBlocker is running in server mode");
            return;
        }

        if (!isPrivateNetwork()) {
            log.warn("ARP scanning disabled due to non private ip address");
            return;
        }

        long requestInterval = 1000000 * interval / 256;
        log.info("Scheduling ARP request each {}ms", requestInterval / 1000.0);
        scheduledFuture = highPrioExecutorService.scheduleAtFixedRate(arpSweeper, startupDelay, requestInterval, TimeUnit.MICROSECONDS);
        currentScanningInterval = interval;
    }

    private boolean isPrivateNetwork() {
        String ip4vAddress = networkInterface.getFirstIPv4Address().toString();
        int ip = IpUtils.convertIpStringToInt(ip4vAddress);

        return privateNetworks.stream().anyMatch(network -> (ip & network[1]) == network[0]);
    }

    private List<Integer[]> createNetworks(String... networkStrings) {
        List<Integer[]> networks = new ArrayList<>();
        for (String networkString : networkStrings) {
            String[] tokens = networkString.split("/");
            if (tokens.length != 2) {
                throw new IllegalArgumentException("not a ipv4 net: " + networkString);
            }
            int network = IpUtils.convertIpStringToInt(tokens[0]);
            int subnet = IpUtils.convertCidrToNetMask(Integer.parseInt(tokens[1]));
            networks.add(new Integer[]{ network, subnet });
        }
        return networks;
    }
}
