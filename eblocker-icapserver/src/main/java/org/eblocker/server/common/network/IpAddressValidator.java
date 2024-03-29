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
import com.google.inject.name.Named;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.network.icmpv6.NeighborSolicitation;
import org.eblocker.server.common.network.icmpv6.Option;
import org.eblocker.server.common.network.icmpv6.SourceLinkLayerAddressOption;
import org.eblocker.server.common.pubsub.Channels;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.service.FeatureToggleRouter;
import org.eblocker.server.common.util.Ip4Utils;
import org.eblocker.server.http.service.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.time.Clock;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class IpAddressValidator {
    private static final Logger log = LoggerFactory.getLogger(IpAddressValidator.class);

    private final long recentActivityThreshold;
    private final IpResponseTable ipResponseTable;
    private final Clock clock;
    private final DeviceService deviceService;
    private final FeatureToggleRouter featureToggleRouter;
    private final NetworkStateMachine networkStateMachine;
    private final PubSubService pubSubService;
    private final NetworkInterfaceWrapper networkInterface;
    private final String vpnSubnetIp;
    private final String vpnSubnetNetmask;
    private long lastSendMillis = 0;
    private final long lastSendMaxDeltaMillis;

    @Inject
    public IpAddressValidator(@Named("arp.ip.grace.period.seconds") long recentActivityThreshold,
                              @Named("executor.arpValidator.fixedRate") long schedulerRate,
                              IpResponseTable ipResponseTable,
                              @Named("network.vpn.subnet.ip") String vpnSubnetIp,
                              @Named("network.vpn.subnet.netmask") String vpnSubnetNetmask,
                              Clock clock,
                              DeviceService deviceService,
                              FeatureToggleRouter featureToggleRouter,
                              NetworkInterfaceWrapper networkInterface,
                              NetworkStateMachine networkStateMachine,
                              PubSubService pubSubService) {
        this.recentActivityThreshold = recentActivityThreshold * 1000;
        this.lastSendMaxDeltaMillis = schedulerRate * 2 * 1000;
        this.ipResponseTable = ipResponseTable;
        this.vpnSubnetIp = vpnSubnetIp;
        this.vpnSubnetNetmask = vpnSubnetNetmask;
        this.clock = clock;
        this.deviceService = deviceService;
        this.featureToggleRouter = featureToggleRouter;
        this.networkStateMachine = networkStateMachine;
        this.networkInterface = networkInterface;
        this.pubSubService = pubSubService;
    }

    public void run() {
        long now = clock.millis();
        if (now - lastSendMillis < lastSendMaxDeltaMillis) {
            checkResponses();
        } else {
            log.debug("Not checking responses to avoid removing addresses that are still in use but have not been pinged yet");
        }

        sendRequests();
    }

    private void sendRequests() {
        log.debug("sending requests");
        MessageSender sender = new MessageSender();
        deviceService.getDevices(false).stream()
                .filter(device -> !device.isVpnClient())
                .forEach(sender::sendMessages);
        lastSendMillis = clock.millis();
    }

    private void checkResponses() {
        log.debug("checking responses");

        // 1. get all rows with recent entries -> device is online
        // 2. drop all ips which are not active for those devices
        long now = clock.millis();
        if (log.isTraceEnabled()) {
            log.trace("now: {}", now);
            log.trace("response table: {}", ipResponseTable);
        }
        long then = now - recentActivityThreshold;
        for (String hardwareAddress: ipResponseTable.allActiveSince(then)) {
            Set<IpAddress> activeAddresses = ipResponseTable.activeAddressesSince(hardwareAddress, then);
            if (activeAddresses != null) {
                dropInactive(hardwareAddress, activeAddresses);
            } else {
                log.warn("Could not get active addresses of device {}", hardwareAddress);
            }
        }
    }

    private void dropInactive(String hardwareAddress, Set<IpAddress> activeIpAddresses) {
        Device device = deviceService.getDeviceById(Device.ID_PREFIX + hardwareAddress);

        if (device == null) {
            log.warn("arp responses for unknown device: {}", hardwareAddress);
            return;
        }

        List<IpAddress> deviceIpAddresses = device.getIpAddresses();
        if (device.isVpnClient()) {
            if (deviceIpAddresses.stream().anyMatch(ip -> activeIpAddresses.contains(ip))) {
                // Preserve vpn ip addresses for mobile clients
                deviceIpAddresses.stream()
                        .filter(IpAddress::isIpv4)
                        .filter(ip -> Ip4Utils.isInSubnet(ip.toString(), vpnSubnetIp, vpnSubnetNetmask))
                        .forEach(activeIpAddresses::add);
            } else {
                log.debug("ignoring vpn client {}", hardwareAddress);
                return;
            }
        }

        if (activeIpAddresses.size() == deviceIpAddresses.size()
                && activeIpAddresses.containsAll(deviceIpAddresses)) {
            log.trace("{}: ip-addresses unchanged", hardwareAddress);
            return;
        }

        log.debug("{}: ip-address has changed from {} to {}\n{}", hardwareAddress, deviceIpAddresses, activeIpAddresses, ipResponseTable);

        keepIpAddresses(device, activeIpAddresses);

        // TODO: refactor to use DeviceIpUpdater
        deviceService.updateDevice(device);
        networkStateMachine.deviceStateChanged(device); // this may take some time!

        Set<IpAddress> inactiveIpAddresses = new HashSet(deviceIpAddresses);
        inactiveIpAddresses.removeAll(activeIpAddresses);
        ipResponseTable.removeAll(hardwareAddress, inactiveIpAddresses);
    }

    private void keepIpAddresses(Device device, Set<IpAddress> newIpAddresses) {
        device.setIpAddresses(device.getIpAddresses().stream()
                .filter(newIpAddresses::contains)
                .collect(Collectors.toList()));
    }

    private class MessageSender {
        private final byte[] eblockerHardwareAddress;
        private final String eblockerHardwareAddressHex;
        private final Ip4Address eblockerIp4Address;
        private final Ip6Address eblockerIp6LinkLocalAddress;
        private final List<Option> sourceLinkLayerAddressOption;

        MessageSender() {
            eblockerHardwareAddress = networkInterface.getHardwareAddress();
            eblockerHardwareAddressHex = DatatypeConverter.printHexBinary(eblockerHardwareAddress).toLowerCase();
            eblockerIp4Address = networkInterface.getFirstIPv4Address();
            eblockerIp6LinkLocalAddress = networkInterface.getIp6LinkLocalAddress();
            sourceLinkLayerAddressOption = Collections.singletonList(new SourceLinkLayerAddressOption(eblockerHardwareAddress));
        }

        void sendMessages(Device device) {
            for (IpAddress ipAddress : device.getIpAddresses()) {
                byte[] hardwareAddress = DatatypeConverter.parseHexBinary(device.getHardwareAddress(false));
                if (ipAddress.isIpv4()) {
                    sendArpMessage(hardwareAddress, (Ip4Address) ipAddress);
                } else if (featureToggleRouter.isIp6Enabled() && ipAddress.isIpv6()) {
                    sendNeighborDiscoverySolicitation(hardwareAddress, (Ip6Address) ipAddress);
                }
            }
        }

        private void sendArpMessage(byte[] targetHardwareAddress, Ip4Address targetIpAddress) {
            ArpMessage message = new ArpMessage();
            message.type = ArpMessageType.ARP_REQUEST;
            message.sourceIPAddress = eblockerIp4Address.toString();
            message.sourceHardwareAddress = eblockerHardwareAddressHex;
            message.targetHardwareAddress = DatatypeConverter.printHexBinary(targetHardwareAddress).toLowerCase();
            message.targetIPAddress = targetIpAddress.toString();
            pubSubService.publish(Channels.ARP_OUT, message.format());
        }

        private void sendNeighborDiscoverySolicitation(byte[] targetHardwareAddress, Ip6Address targetIpAddress) {
            NeighborSolicitation solicitation = new NeighborSolicitation(
                    eblockerHardwareAddress,
                    eblockerIp6LinkLocalAddress,
                    targetHardwareAddress,
                    targetIpAddress,
                    targetIpAddress,
                    sourceLinkLayerAddressOption);
            pubSubService.publish(Channels.IP6_OUT, solicitation.toString());
        }
    }
}
