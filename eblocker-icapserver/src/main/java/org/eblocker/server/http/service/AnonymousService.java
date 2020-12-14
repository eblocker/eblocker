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
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.openvpn.VpnProfile;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.network.TorController;
import org.eblocker.server.common.openvpn.OpenVpnService;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// depends on OpenVpnService and DeviceService for initialization
@Singleton
@SubSystemService(value = SubSystem.SERVICES)
public class AnonymousService {

    private final Logger log = LoggerFactory.getLogger(AnonymousService.class);

    private final DeviceService deviceService;
    private final OpenVpnService openVpnService;
    private final TorController torController;
    private final NetworkStateMachine networkStateMachine;

    @Inject
    public AnonymousService(DeviceService deviceService, OpenVpnService openVpnService, TorController torController, NetworkStateMachine networkStateMachine) {
        this.deviceService = deviceService;
        this.openVpnService = openVpnService;
        this.torController = torController;
        this.networkStateMachine = networkStateMachine;
    }

    @SubSystemInit
    public void init() {
        log.info("restoring vpn states for devices");
        deviceService.getDevices(false)
                .stream()
                .filter(Device::isUseAnonymizationService)
                .filter(device -> device.getUseVPNProfileID() != null)
                .forEach(device -> {
                    VpnProfile profile = openVpnService.getVpnProfileById(device.getUseVPNProfileID());
                    if (profile != null) {
                        enableVpn(device, profile);
                    } else {
                        log.warn("{} uses deleted vpn {}", device.getId(), device.getUseVPNProfileID());
                        disableVpn(device);
                    }
                });
    }

    public void enableTor(Device device) {
        log.info("enabling tor for {}", device.getId());
        openVpnService.restoreNormalRoutingForClient(device);
        torController.addDeviceUsingTor(device);
        device.setUseAnonymizationService(true);
        device.setRouteThroughTor(true);
        device.setUseVPNProfileID(null);
        deviceService.updateDevice(device);
        networkStateMachine.deviceStateChanged(device);
    }

    public void disableTor(Device device) {
        log.info("disabling tor for {}", device.getId());
        torController.removeDeviceNotUsingTor(device);
        device.setUseAnonymizationService(false);
        deviceService.updateDevice(device);
        networkStateMachine.deviceStateChanged(device);
    }

    public void enableVpn(Device device, VpnProfile profile) {
        log.info("enabling vpn {} for {}", profile.getId(), device.getId());
        torController.removeDeviceNotUsingTor(device);
        openVpnService.routeClientThroughVpnTunnel(device, profile);
        device.setUseAnonymizationService(true);
        device.setRouteThroughTor(false);
        device.setUseVPNProfileID(profile.getId());
        deviceService.updateDevice(device);
        networkStateMachine.deviceStateChanged(device);
    }

    public void disableVpn(Device device) {
        log.info("disabling vpn {}", device.getId());
        openVpnService.restoreNormalRoutingForClient(device);
        device.setUseAnonymizationService(false);
        deviceService.updateDevice(device);
        networkStateMachine.deviceStateChanged(device);
    }
}

