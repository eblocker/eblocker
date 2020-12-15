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
package org.eblocker.server.common.network.unix;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.exceptions.DhcpDiscoveryException;
import org.eblocker.server.common.system.LoggingProcess;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.http.service.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class DhcpDiscoveryService {
    private static final Logger log = LoggerFactory.getLogger(DhcpDiscoveryService.class);

    private static final Pattern OFFER_PATTERN = Pattern.compile("^offer from (.*): (.*) / (.*)$");
    private Random random = new Random();
    private final String interfaceName;
    private final String discoveryCommand;
    private final int timeout;
    private final DeviceService deviceService;
    private final ScriptRunner scriptRunner;

    @Inject
    public DhcpDiscoveryService(@Named("network.interface.name") String interfaceName,
                                @Named("network.unix.dhcp.discovery.command") String discoveryCommand,
                                @Named("network.unix.dhcp.discovery.timeout") int timeout,
                                DeviceService deviceService,
                                ScriptRunner scriptRunner) {
        this.interfaceName = interfaceName;
        this.discoveryCommand = discoveryCommand;
        this.timeout = timeout;
        this.scriptRunner = scriptRunner;
        this.deviceService = deviceService;
    }

    public Set<String> getDhcpServers() throws DhcpDiscoveryException {
        String hardwareAddress = getUnusedHardwareAddress();
        LoggingProcess process = runDiscovery(hardwareAddress);
        return parseOutput(process);
    }

    private String getUnusedHardwareAddress() {
        for (int i = 0; i < 100; ++i) {
            long hardwareAddress = random.nextLong() & 0xfcffffffffffL; // clear lower bits of first octet to mark mac as globally unique unicast address
            String hex = String.format("%012x", hardwareAddress);
            if (deviceService.getDeviceById("device:" + hex) == null) {
                return hex;
            }
        }
        log.warn("could not generate unused hardware address?! ¯\\_(ツ)_/¯");
        return "000000000000";
    }

    private LoggingProcess runDiscovery(String hardwareAddress) throws DhcpDiscoveryException {
        try {
            log.debug("running discovery process with timeout: %d and hardware address: %s", timeout, hardwareAddress);
            LoggingProcess process = scriptRunner
                    .startScript(discoveryCommand, "-w " + timeout, "-h " + hardwareAddress, interfaceName);
            int exitValue = process.waitFor();
            if (exitValue != 0) {
                throw new DhcpDiscoveryException("discovery script exited with " + exitValue);
            }
            return process;
        } catch (IOException e) {
            throw new DhcpDiscoveryException("failed to run discovery", e);
        } catch (InterruptedException e) {
            log.error("interrupted while executing discovery", e);
            Thread.currentThread().interrupt();
            throw new DhcpDiscoveryException("discovery interrupted", e);
        }
    }

    private Set<String> parseOutput(LoggingProcess process) {
        Set<String> dhcpOffers = new HashSet<>();
        String line;
        while ((line = process.pollStdout()) != null) {
            Matcher matcher = OFFER_PATTERN.matcher(line);
            if (matcher.matches()) {
                String server = matcher.group(1);
                String ip = matcher.group(2);
                String subnet = matcher.group(3);
                dhcpOffers.add(server);
                log.debug("found dhcp offer from {}: {} / {}", server, ip, subnet);
            }
        }
        return dhcpOffers;
    }
}
