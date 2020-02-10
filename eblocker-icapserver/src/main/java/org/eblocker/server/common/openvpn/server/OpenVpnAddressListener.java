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
package org.eblocker.server.common.openvpn.server;

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.pubsub.Channels;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.pubsub.Subscriber;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.common.util.IpUtils;
import org.eblocker.server.http.service.DeviceService;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * When a remote device connects to or disconnects from eBlocker's OpenVPN server,
 * a script is called which triggers this class via Redis' publish/subscribe mechanism.
 * The actions are:
 * <ul>
 *   <li>Add IP address for device
 *   <li>Remove IP address
 * </ul>
 */
@SubSystemService(SubSystem.BACKGROUND_TASKS)
public class OpenVpnAddressListener implements Runnable, Subscriber {
    private static final Logger log = LoggerFactory.getLogger(OpenVpnAddressListener.class);

    private static final Pattern MESSAGE_PATTERN_DEVICE_ADDRESS = Pattern.compile("(add|update|delete) ([0-9.]+)( (device:[a-f0-9]{12}))?");
    private static final Pattern MESSAGE_PATTERN_INTERFACE_ADDRESS = Pattern.compile("(up|down) (tun\\d+ \\d+ \\d+ (\\d+\\.\\d+\\.\\d+\\.\\d+) .*)?");
    private static final String CHANNEL = Channels.VPN_ADDRESS_UPDATE;

    private final int vpnSubnetIp;
    private final int vpnSubnetNetmask;
    private final DeviceService deviceService;
    private final EblockerDnsServer dnsServer;
    private final NetworkStateMachine networkStateMachine;
    private final PubSubService pubSubService;

    private final Map<String, IpAddress> ipAddressByDevice;

    @Inject
    public OpenVpnAddressListener(@Named("network.vpn.subnet.ip") String vpnSubnetIp,
                                  @Named("network.vpn.subnet.netmask") String vpnSubnetNetmask,
                                  DeviceService deviceService,
                                  EblockerDnsServer dnsServer,
                                  NetworkStateMachine networkStateMachine,
                                  PubSubService pubSubService) {

        this.vpnSubnetIp = IpUtils.convertIpStringToInt(vpnSubnetIp);
        this.vpnSubnetNetmask = IpUtils.convertIpStringToInt(vpnSubnetNetmask);

        this.deviceService = deviceService;
        this.dnsServer = dnsServer;
        this.networkStateMachine = networkStateMachine;
        this.pubSubService = pubSubService;

        ipAddressByDevice = new HashMap<>();
    }

    @SubSystemInit
    public void init() {
        deviceService.getDevices(false).stream()
            .filter(Device::isVpnClient)
            .forEach(device -> {
                Optional<IpAddress> vpnIpAddress = device.getIpAddresses().stream()
                    .filter(IpAddress::isIpv4)
                    .filter(ip -> (IpUtils.convertBytesToIp(ip.getAddress()) & vpnSubnetNetmask) == vpnSubnetIp)
                    .findAny();
                if (vpnIpAddress.isPresent()) {
                    ipAddressByDevice.put(device.getId(), vpnIpAddress.get());
                }
            });
    }

    public void run() {
        pubSubService.subscribeAndLoop(CHANNEL, this);
    }

    /**
     * Messages are:
     * <ul>
     *   <li>add IP CN
     *   <li>update IP CN
     *   <li>delete IP
     * </ul>
     * The CN is equal to the device ID.
     * @see "man openvpn, option --learn-address"
     */
    @Override
    public void process(String message) {
        log.debug("OpenVpnAddressListener - process: {}", message);
        Matcher deviceAddressMatcher = MESSAGE_PATTERN_DEVICE_ADDRESS.matcher(message);
        if (deviceAddressMatcher.matches()) {
            processDeviceAddressChange(message, deviceAddressMatcher);
            return;
        }

        Matcher interfaceUpMatcher = MESSAGE_PATTERN_INTERFACE_ADDRESS.matcher(message);
        if (interfaceUpMatcher.matches()) {
            processInterfaceUp();
            return;
        }

        log.error("Could not parse message '{}' from channel '{}'", message, CHANNEL);
    }

    private void processDeviceAddressChange(String message, Matcher matcher) {
        String command = matcher.group(1);
        String ipAddress = matcher.group(2);
        String deviceId = matcher.group(4);

        if (ipAddress == null || !IpUtils.isIPAddress(ipAddress)) {
            log.error("Ignoring message '{}', because '{}' does not seem to be an IP address", message, ipAddress);
            return;
        }

        if ("add".equals(command) || "update".equals(command)) {
            if (deviceId != null) {
                updateIpAddress(deviceId, ipAddress);
            } else {
                log.error("Could not add/update IP address, because no device ID was given in message '{}' in channel '{}'", message, CHANNEL);
            }
        } else if ("delete".equals(command)) {
            removeIpAddress(ipAddress);
        } else {
            throw new RuntimeException("Unexpected command '" + command + "'. Why did the message pattern match?");
        }
}

    private void updateIpAddress(String deviceId, String ipAddressString) {
        log.debug("OpenVpnAddressListener - updateIpAddress: Device: {}, ipAddress: {}", deviceId, ipAddressString);
        IpAddress ipAddress = IpAddress.parse(ipAddressString);
        Device device = deviceService.getDeviceById(deviceId);
        if (device != null) {
            List<IpAddress> ipAddresses = device.getIpAddresses();
            IpAddress previousIpAddress = ipAddressByDevice.put(deviceId, ipAddress);
            if (previousIpAddress != null) {
                ipAddresses.remove(previousIpAddress);
            }
            ipAddresses.add(ipAddress);
            device.setIpAddresses(ipAddresses);
            device.setIsVpnClient(true);
            deviceService.updateDevice(device);
            networkStateMachine.deviceStateChanged();
        } else {
            log.error("Could not find device with ID '{}'. Could not update IP address to '{}'", deviceId, ipAddress);
        }
    }

    private void removeIpAddress(String ipAddressString) {
        log.debug("OpenVpnAddressListener - removeIpAddress: ipAddress: {}", ipAddressString);
        IpAddress ipAddress = IpAddress.parse(ipAddressString);
        Device device = deviceService.getDeviceByIp(ipAddress);
        if (device != null) {
            ipAddressByDevice.remove(device.getId());
            device.setIsVpnClient(false);
            List<IpAddress> ipAddresses = device.getIpAddresses();
            ipAddresses.remove(ipAddress);
            device.setIpAddresses(ipAddresses);
            deviceService.updateDevice(device);
            networkStateMachine.deviceStateChanged();
        } else {
            log.error("Could not find device with IP address '{}'. Could not remove the IP address", ipAddress);
        }
    }

    private void processInterfaceUp() {
        dnsServer.refreshLocalDnsRecords();
    }
}
