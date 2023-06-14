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
package org.eblocker.server.common.network;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.pubsub.Channels;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.util.IpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ArpSweeper implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ArpSweeper.class);

    private final int maxSize;
    private final PubSubService pubSubService;
    private final NetworkInterfaceWrapper networkInterface;

    private int offset = 0;

    @Inject
    public ArpSweeper(@Named("arp.sweep.max.size") int maxSize,
                      PubSubService pubSubService,
                      NetworkInterfaceWrapper networkInterface) {
        this.maxSize = maxSize;
        this.pubSubService = pubSubService;
        this.networkInterface = networkInterface;
    }

    public boolean isFullScanAvailable() {
        return getNetworkConfig().hosts <= maxSize;
    }

    public void fullScan() {
        NetworkConfig config = getNetworkConfig();
        if (config.hosts <= maxSize) {
            scan(config, config.hosts);
        } else {
            log.warn("ignoring full-scan request because network is too large: {}", config.hosts);
        }
    }

    public void run() {
        NetworkConfig config = getNetworkConfig();
        scan(config, 1);
    }

    private void scan(NetworkConfig config, int maxHosts) {
        try {
            String hardwareAddress = networkInterface.getHardwareAddressHex();
            ArpMessage message = new ArpMessage();
            message.type = ArpMessageType.ARP_REQUEST;
            message.sourceIPAddress = config.ip4vAddress;
            message.sourceHardwareAddress = hardwareAddress;
            message.targetHardwareAddress = "000000000000";
            message.ethernetTargetHardwareAddress = "ffffffffffff"; // broadcast

            for (int i = 0; i < Math.min(maxHosts, config.hosts); ++i) {
                int targetIp = config.network + 1 + offset;
                offset = (offset + 1) % config.hosts;
                message.targetIPAddress = IpUtils.convertIpIntToString(targetIp);
                log.debug("Sending {}", message);
                pubSubService.publish(Channels.ARP_OUT, message.format());
            }

            log.debug("ARP sweep done");
        } catch (Exception e) {
            log.error("Could not perform ARP sweep", e);
        }
    }

    private NetworkConfig getNetworkConfig() {
        Ip4Address ip4Address = networkInterface.getFirstIPv4Address();
        NetworkConfig config = new NetworkConfig();
        config.ip4vAddress = ip4Address.toString();
        config.ip = IpUtils.convertIpStringToInt(config.ip4vAddress);
        config.cidr = networkInterface.getNetworkPrefixLength(ip4Address);
        config.netMask = IpUtils.convertCidrToNetMask(config.cidr);
        config.network = config.ip & config.netMask;
        config.broadcast = config.ip | config.netMask ^ 0xffffffff;
        config.hosts = config.broadcast - config.network - 1;
        return config;
    }

    private class NetworkConfig {
        String ip4vAddress;
        int ip;
        int cidr;
        int netMask;
        int network;
        int broadcast;
        int hosts;
    }
}
