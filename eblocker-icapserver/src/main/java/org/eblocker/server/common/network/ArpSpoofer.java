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

import com.google.common.collect.Table;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.pubsub.Channels;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.util.IpUtils;
import org.eblocker.server.http.service.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

@Singleton
public class ArpSpoofer implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ArpSpoofer.class);

    private final ConcurrentMap<String, Long> arpProbeCache;
    private final Table<String, IpAddress, Long> arpResponseTable;
    private final Clock clock;
    private final DataSource dataSource;
    private final DeviceService deviceService;
    private final PubSubService pubSubService;
    private final NetworkInterfaceWrapper networkInterface;
    private final int healingNumPackets;
    private final long onlineThreshold;
    private final long suspendPeriod;
    private final Ip4Address emergencyIp;

    @Inject
    public ArpSpoofer(@Named("arp.spoof.healing.packets") int healingNumPackets,
                      @Named("arp.spoof.online.threshold") long onlineThreshold,
                      @Named("arp.spoof.suspend.period") long suspendPeriod,
                      @Named("network.emergency.ip") Ip4Address emergencyIp,
                      @Named("arpProbeCache") ConcurrentMap<String, Long> arpProbeCache,
                      @Named("arpResponseTable") Table<String, IpAddress, Long> arpResponseTable,
                      Clock clock,
                      DataSource dataSource,
                      DeviceService deviceService,
                      PubSubService pubSubService,
                      NetworkInterfaceWrapper networkInterface) {
        this.arpProbeCache = arpProbeCache;
        this.arpResponseTable = arpResponseTable;
        this.clock = clock;
        this.pubSubService = pubSubService;
        this.dataSource = dataSource;
        this.deviceService = deviceService;
        this.networkInterface = networkInterface;
        this.healingNumPackets = healingNumPackets;
        this.onlineThreshold = onlineThreshold * 1000;
        this.suspendPeriod = suspendPeriod * 1000;
        this.emergencyIp = emergencyIp;
    }

    private Device findRouter(IpAddress routerIpAddress) {
        String hardwareAddress = networkInterface.getHardwareAddressHex();

        if (routerIpAddress == null || hardwareAddress == null) {//only when there is a gateway and the ethernet Interface has an IPv4 address assigned (and therefore a valid MAC address)
            return null;
        }

        Device device = deviceService.getDeviceByIp(routerIpAddress);
        if (device != null && !hardwareAddress.equals(device.getHardwareAddress(false))) {
            log.debug("Found router: {}", device);
            return device;
        }
        return null;
    }

    @Override
    public void run() {
        try {
            processEnabledDevices();
            log.debug("Started ARPSpoofing by processing enabled devices...");
        } catch (Exception e) {
            log.error("Failed to process enabled devices.", e);
        }
    }

    private void processEnabledDevices() {
        log.debug("Spoofing...");

        Ip4Address eblockerIp = networkInterface.getFirstIPv4Address();
        if (eblockerIp == null || eblockerIp.equals(emergencyIp)) {
            log.debug("no primary ip address assigned to interface {}. Will try again later.", networkInterface.getInterfaceName());
            return;
        }

        IpAddress gatewayIpAddress = IpAddress.parse(dataSource.getGateway());
        Device router = findRouter(gatewayIpAddress);
        if (router == null) {
            log.debug("Gateway not found. Will try again later");
            networkInterface.findGatewayAndWriteToRedis();
            return;
        }

        String hardwareAddress = networkInterface.getHardwareAddressHex();

        ArpMessage announceEblockerMessage = new ArpMessage();
        announceEblockerMessage.type = ArpMessageType.ARP_REQUEST;
        announceEblockerMessage.sourceHardwareAddress = hardwareAddress;
        announceEblockerMessage.sourceIPAddress = eblockerIp.toString();
        announceEblockerMessage.targetHardwareAddress = "ffffffffffff";
        announceEblockerMessage.targetIPAddress = eblockerIp.toString();
        send(announceEblockerMessage);

        ArpMessage spoofResponseMessage = new ArpMessage();
        spoofResponseMessage.type = ArpMessageType.ARP_RESPONSE;
        spoofResponseMessage.sourceHardwareAddress = hardwareAddress;
        spoofResponseMessage.sourceIPAddress = gatewayIpAddress.toString();

        ArpMessage spoofRequestMessage = new ArpMessage();
        spoofRequestMessage.type = ArpMessageType.ARP_REQUEST;
        spoofRequestMessage.sourceHardwareAddress = hardwareAddress;
        spoofRequestMessage.sourceIPAddress = gatewayIpAddress.toString();

        ArpMessage indirectRequestMessage = new ArpMessage();
        indirectRequestMessage.type = ArpMessageType.ARP_REQUEST;
        indirectRequestMessage.sourceHardwareAddress = router.getHardwareAddress(false);
        indirectRequestMessage.sourceIPAddress = "0.0.0.0";
        indirectRequestMessage.targetHardwareAddress = "000000000000";

        int ip = IpUtils.convertIpStringToInt(eblockerIp.toString());
        int cidr = networkInterface.getNetworkPrefixLength(eblockerIp);
        int netMask = IpUtils.convertCidrToNetMask(cidr);
        int network = ip & netMask;

        for (Device device : deviceService.getDevices(false)) {
            log.debug("Found device {}", device.getId());
            if (device.getId().equals(router.getId())) {
                // don't spoof the router
                continue;
            }

            String deviceHardwareAddress = device.getHardwareAddress(false);
            // ignore unconfigured devices and ourselves
            if (device.getIpAddresses().isEmpty() || hardwareAddress.equals(deviceHardwareAddress)) {
                continue;
            }

            boolean isIndirectSpoofingEnabled = isIndirectSpoofingEnabled(device);

            device.getIpAddresses().stream()
                    .filter(IpAddress::isIpv4)
                    .map(IpAddress::toString)
                    .forEach(ipAddress -> {
                        // ignore if device is outside current network
                        if (!IpUtils.isInSubnet(IpUtils.convertIpStringToInt(ipAddress), network, netMask)) {
                            return;
                        }

                        // only spoof enabled devices
                        if (device.isEnabled()) {
                            spoofResponseMessage.targetHardwareAddress = deviceHardwareAddress;
                            spoofResponseMessage.targetIPAddress = ipAddress;
                            send(spoofResponseMessage);

                            spoofRequestMessage.targetHardwareAddress = deviceHardwareAddress;
                            spoofRequestMessage.targetIPAddress = ipAddress;
                            send(spoofRequestMessage);
                        }

                        // Forge a gateway request to indirectly tell the gateway the device's address.
                        // This is done for all known devices to prevent router broadcasts.
                        if (isIndirectSpoofingEnabled) {
                            indirectRequestMessage.targetIPAddress = ipAddress;
                            indirectRequestMessage.ethernetTargetHardwareAddress = deviceHardwareAddress;
                            send(indirectRequestMessage);
                            log.debug("indirect arp spoofing enabled for device {} (now: {} last arp: {})", device.getId(), clock.millis(), arpProbeCache.get(device.getId()));
                        } else {
                            log.debug("indirect arp spoofing disabled for device {} (now: {} last arp: {})", device.getId(), clock.millis(), arpProbeCache.get(device.getId()));
                        }
                    });
        }
    }

    private void send(ArpMessage message) {
        log.debug("ARP-Spoofing: Sending out ARP packet: {}", message);
        pubSubService.publish(Channels.ARP_OUT, message.format());
    }

    /**
     * Restore original, "normal" state for device (tell it who is the real router)
     *
     * @param device
     * @return
     */
    public boolean heal(Device device) {
        IpAddress gatewayIpAddress = IpAddress.parse(dataSource.getGateway());
        Device router = findRouter(gatewayIpAddress);
        if (router != null && device != null && !device.getIpAddresses().isEmpty()) {
            //construct valid (non spoofed) ARP response for client to tell it who the router really is
            ArpMessage healMessage = new ArpMessage();
            healMessage.type = ArpMessageType.ARP_RESPONSE;
            healMessage.sourceHardwareAddress = router.getHardwareAddress(false);
            healMessage.sourceIPAddress = gatewayIpAddress.toString();
            healMessage.targetHardwareAddress = device.getHardwareAddress(false);

            device.getIpAddresses().stream()
                    .filter(IpAddress::isIpv4)
                    .forEach(ip -> {
                        healMessage.targetIPAddress = ip.toString();

                        //send multiple times to be sure that the client changes everything back to normal
                        for (int i = 0; i < healingNumPackets; i++) {
                            log.debug("Sending healing ARP response to device {} / ip {}", device.getHardwareAddress(), ip);
                            send(healMessage);
                        }
                    });
            return true;
        }
        return false;
    }

    private boolean isIndirectSpoofingEnabled(Device device) {
        return isOnline(device) && !isArpProbing(device);
    }

    private boolean isOnline(Device device) {
        long now = clock.millis();

        Map<IpAddress, Long> row;
        synchronized (arpResponseTable) {
            row = arpResponseTable.row(device.getHardwareAddress(false));
        }

        return row != null && row.values().stream().anyMatch(t -> t + onlineThreshold > now);
    }

    private boolean isArpProbing(Device device) {
        Long lastArpProbeSeen = arpProbeCache.get(device.getId());
        return lastArpProbeSeen != null && lastArpProbeSeen + suspendPeriod > clock.millis();
    }
}
