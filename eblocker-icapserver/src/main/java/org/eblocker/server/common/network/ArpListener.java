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
import org.eblocker.server.common.pubsub.Channels;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.pubsub.Subscriber;
import org.eblocker.server.common.util.Ip4Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.concurrent.ConcurrentMap;

public class ArpListener implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ArpListener.class);
    private final Clock clock;
    private final PubSubService pubSubService;
    private final NetworkInterfaceWrapper networkInterface;
    private final ConcurrentMap<String, Long> arpProbeCache;
    private final IpResponseTable ipResponseTable;
    private final DeviceIpUpdater deviceIpUpdater;

    private class ArpListenerSubscriber implements Subscriber {
        @Override
        public void process(String messageString) {
            try {
                ArpMessage message = ArpMessage.parse(messageString);
                log.debug("Got ARP message: {}", message);
                processARPMessage(message);

            } catch (ArpMessageParsingException e) {
                log.error("Could not parse ARP message {}", messageString, e);
            }
        }
    }

    @Inject
    public ArpListener(@Named("arpProbeCache") ConcurrentMap<String, Long> arpProbeCache,
                       IpResponseTable ipResponseTable,
                       PubSubService pubSubService,
                       NetworkInterfaceWrapper networkInterface,
                       Clock clock,
                       DeviceIpUpdater deviceIpUpdater) {
        this.arpProbeCache = arpProbeCache;
        this.ipResponseTable = ipResponseTable;
        this.pubSubService = pubSubService;
        this.networkInterface = networkInterface;
        this.clock = clock;
        this.deviceIpUpdater = deviceIpUpdater;
    }

    /**
     * Processes the incoming ARP message and updates the devices IP address or creates a new entry for the device
     *
     * @param message
     */
    public void processARPMessage(ArpMessage message) {
        // ignore local packages
        String hardwareAddress = networkInterface.getHardwareAddressHex();
        if (hardwareAddress != null && !hardwareAddress.equals(message.sourceHardwareAddress)) {
            // Only process responses, gratuitous requests and ARP probes
            if (message.type == ArpMessageType.ARP_REQUEST && !message.isGratuitousRequest() && !message.isArpProbe()) {
                return;
            }

            String deviceID = Device.ID_PREFIX + message.sourceHardwareAddress;

            if (log.isDebugEnabled()) {
                log.debug("Got ARP message from device: " + message.sourceIPAddress + " - " + message.sourceHardwareAddress);
                log.debug("ARP-MESSAGE {} {} {} {}", message.sourceHardwareAddress, message.sourceIPAddress, message.targetHardwareAddress, message.targetIPAddress);
            }

            // suspend sending forged probes to avoid ip-address conflict detection
            if (message.isArpProbe()) {
                log.debug("arp probe seen for device {}", deviceID);
                arpProbeCache.put(deviceID, clock.millis());
            } else {
                Ip4Address sourceAddress = Ip4Address.parse(message.sourceIPAddress);
                ipResponseTable.put(message.sourceHardwareAddress, sourceAddress, clock.millis());
                reactToRespondingDevice(deviceID, sourceAddress);
            }
        }
    }

    private boolean isInEblockerNet(Ip4Address sourceIPAddress) {
        Ip4Address eblockerIp = networkInterface.getFirstIPv4Address();
        int eblockerIpInt = Ip4Utils.convertIpStringToInt(eblockerIp.toString());
        int cidr = networkInterface.getNetworkPrefixLength(eblockerIp);
        int netMask = Ip4Utils.convertCidrToNetMask(cidr);
        int network = eblockerIpInt & netMask;
        return Ip4Utils.isInSubnet(Ip4Utils.convertBytesToIp(sourceIPAddress.getAddress()), network, netMask);
    }

    /**
     * When we receive an ArpMessage from a device, that was responding to the ArpSweep (sent ARP response/reply)
     */
    private void reactToRespondingDevice(String deviceId, Ip4Address sourceIPAddress) {
        if (!isInEblockerNet(sourceIPAddress)) {
            return;
        }
        deviceIpUpdater.refresh(deviceId, sourceIPAddress);
    }

    @Override
    public void run() {
        log.info("Subscribing to channel {}...", Channels.ARP_IN);
        pubSubService.subscribeAndLoop(Channels.ARP_IN, new ArpListenerSubscriber());
    }
}
