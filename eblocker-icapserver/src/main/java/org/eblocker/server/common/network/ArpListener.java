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
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DeviceFactory;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.pubsub.Channels;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.pubsub.Subscriber;
import org.eblocker.server.common.util.IpUtils;
import org.eblocker.server.http.service.DeviceOnlineStatusCache;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

public class ArpListener implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ArpListener.class);
    private final Clock clock;
    private final PubSubService pubSubService;
    private final DataSource dataSource;
    private final DeviceService deviceService;
    private final DeviceOnlineStatusCache deviceOnlineStatusCache;
    private final NetworkInterfaceWrapper networkInterface;
    private final NetworkStateMachine networkStateMachine;
    private final ConcurrentMap<String, Long> arpProbeCache;
    private final Table<String, IpAddress, Long> arpResponseTable;
    private final DeviceFactory deviceFactory;
    private final UserService userService;

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
                       @Named("arpResponseTable") Table<String, IpAddress, Long> arpResponseTable,
                       DataSource dataSource,
                       DeviceService deviceService,
                       DeviceOnlineStatusCache deviceOnlineStatusCache,
                       PubSubService pubSubService,
                       NetworkInterfaceWrapper networkInterface,
                       NetworkStateMachine networkStateMachine,
                       Clock clock,
                       DeviceFactory deviceFactory,
                       UserService userService) {
        this.arpProbeCache = arpProbeCache;
        this.arpResponseTable = arpResponseTable;
        this.dataSource = dataSource;
        this.deviceService = deviceService;
        this.deviceOnlineStatusCache = deviceOnlineStatusCache;
        this.pubSubService = pubSubService;
        this.networkInterface = networkInterface;
        this.networkStateMachine = networkStateMachine;
        this.clock = clock;
        this.deviceFactory = deviceFactory;
        this.userService = userService;
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

            String deviceID = "device:" + message.sourceHardwareAddress;
            Device device = deviceService.getDeviceById(deviceID);

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
                synchronized (arpResponseTable) {
                    arpResponseTable.put(message.sourceHardwareAddress, sourceAddress, clock.millis());
                }
                reactToRespondingDevice(device, deviceID, sourceAddress);
            }

            // Mark corresponding device as active
            deviceOnlineStatusCache.updateOnlineStatus(deviceID);
        }
    }

    private boolean isInEblockerNet(Ip4Address sourceIPAddress) {
        Ip4Address eblockerIp = networkInterface.getFirstIPv4Address();
        int eblockerIpInt = IpUtils.convertIpStringToInt(eblockerIp.toString());
        int cidr = networkInterface.getNetworkPrefixLength(eblockerIp);
        int netMask = IpUtils.convertCidrToNetMask(cidr);
        int network = eblockerIpInt & netMask;
        return IpUtils.isInSubnet(IpUtils.convertBytesToIp(sourceIPAddress.getAddress()), network, netMask);
    }

    /**
     * When we receive an ArpMessage from a device, that was responding to the ArpSweep (sent ARP response/reply)
     *
     * @param device
     */
    private void reactToRespondingDevice(Device device, String deviceId, Ip4Address sourceIPAddress) {
        if (!isInEblockerNet(sourceIPAddress)) {
            return;
        }
        if (device != null) { // device exists already,just update IP address
            if (device.getIpAddresses().contains(sourceIPAddress)) {
                // there is no new information in this ARP message:
                return;
            }

            List<IpAddress> ipAddresses = new ArrayList<>(device.getIpAddresses());
            ipAddresses.add(sourceIPAddress);
            device.setIpAddresses(ipAddresses);

        } else {
            // TODO: This should be combined into a service method.
            //       The problem is that DeviceFactory may not depend on UserService,
            //       because it is used by SchemaMigrations.
            //       Also, the following lines that writes the device to the DB and notifies listeners
            //       should be part of the service. So there is anyway a larger refactoring necessary.
            device = deviceFactory.createDevice(deviceId, Collections.singletonList(sourceIPAddress),
                    dataSource.isIpFixedByDefault());
            userService.restoreDefaultSystemUserAsUsers(device);
        }

        updateIsGateway(device);
        deviceService.updateDevice(device);

        networkStateMachine.deviceStateChanged();
    }

    // TODO: this should be consolidated with JedisDataSource.getDevice() and moved to the DeviceService
    private void updateIsGateway(Device device) {
        String gateway = dataSource.getGateway();
        device.setIsGateway(gateway != null && device.getIpAddresses().contains(Ip4Address.parse(gateway)));
    }

    @Override
    public void run() {
        log.info("Subscribing to channel {}...", Channels.ARP_IN);
        pubSubService.subscribeAndLoop(Channels.ARP_IN, new ArpListenerSubscriber());
    }
}
