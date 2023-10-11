/*
 * Copyright 2022 eBlocker Open Source UG (haftungsbeschraenkt)
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
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DeviceFactory;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.UserService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service to update IP addresses of devices.
 */
public class DeviceIpUpdater {
    private final DataSource dataSource;
    private final DeviceService deviceService;
    private final NetworkStateMachine networkStateMachine;
    private final DeviceFactory deviceFactory;
    private final UserService userService;

    @Inject
    public DeviceIpUpdater(DataSource dataSource,
                           DeviceService deviceService,
                           NetworkStateMachine networkStateMachine,
                           DeviceFactory deviceFactory,
                           UserService userService) {
        this.dataSource = dataSource;
        this.deviceService = deviceService;
        this.networkStateMachine = networkStateMachine;
        this.deviceFactory = deviceFactory;
        this.userService = userService;
    }

    /**
     * Add an IP address to a given device. If the device does not exist, it is created.
     * @param deviceId the device ID. The device is created if it does not exist.
     * @param ipAddress the detected IP address
     */
    public void refresh(String deviceId, IpAddress ipAddress) {
        Device device = deviceService.getDeviceById(deviceId);

        if (device != null) { // device exists already
            if (device.getIpAddresses().contains(ipAddress)) {
                // there is no new information
                return;
            }

            List<IpAddress> ipAddresses = new ArrayList<>(device.getIpAddresses());
            ipAddresses.add(ipAddress);
            device.setIpAddresses(ipAddresses);

        } else {
            device = deviceFactory.createDevice(deviceId, Collections.singletonList(ipAddress), dataSource.isIpFixedByDefault());
            userService.restoreDefaultSystemUserAsUsers(device);
        }

        updateIsGateway(device);
        deviceService.updateDevice(device);

        networkStateMachine.deviceStateChanged();
    }

    private void updateIsGateway(Device device) {
        String gateway = dataSource.getGateway();
        device.setIsGateway(gateway != null && device.getIpAddresses().contains(Ip4Address.parse(gateway)));
    }
}
