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

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.wireguard.WireGuardProfile;
import org.eblocker.server.common.data.vpn.VpnProfile;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.network.TorController;
import org.eblocker.server.common.wireguard.WireGuardService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;

public class AnonymousServiceTest {

    private AnonymousService anonymousService;
    private DeviceService deviceService;
    private WireGuardService wireGuardService;
    private TorController torController;
    private NetworkStateMachine networkStateMachine;

    @Before
    public void setup() {
        deviceService = Mockito.mock(DeviceService.class);
        wireGuardService = Mockito.mock(WireGuardService.class);
        torController = Mockito.mock(TorController.class);
        networkStateMachine = Mockito.mock(NetworkStateMachine.class);
        anonymousService = new AnonymousService(deviceService, wireGuardService, torController, networkStateMachine);
    }

    @Test
    public void testInit() {
        Device[] devices = {
                mockDevice("device:000000000000", false, false, null),
                mockDevice("device:000000000001", false, false, 1),
                mockDevice("device:000000000002", false, false, 2),
                mockDevice("device:000000000010", false, true, null),
                mockDevice("device:000000000101", true, false, 1),
                mockDevice("device:000000000102", true, false, 2)
        };
        Mockito.when(deviceService.getDevices(false)).thenReturn(Arrays.asList(devices));

        VpnProfile vpnProfile = new WireGuardProfile(1, "test");
        Mockito.when(wireGuardService.getVpnProfileById(1)).thenReturn(vpnProfile);

        anonymousService.init();

        // check device 4 is enabled for vpn 1
        Mockito.verify(deviceService).updateDevice(devices[4]);
        Mockito.verify(wireGuardService).routeClientThroughVpnTunnel(devices[4], vpnProfile);
        Mockito.verify(torController).removeDeviceNotUsingTor(devices[4]);
        Mockito.verify(networkStateMachine).deviceStateChanged(devices[4]);

        // check device 5 is disabled for vpn 2
        Mockito.verify(deviceService).updateDevice(devices[5]);
        Mockito.verify(wireGuardService).restoreNormalRoutingForClient(devices[5]);
        Mockito.verify(networkStateMachine).deviceStateChanged(devices[4]);
    }

    @Test
    public void testEnableTor() {
        // setup device
        Device device = new Device();

        // enable tor for device
        anonymousService.enableTor(device);

        // check state changes are correct
        Mockito.verify(deviceService).updateDevice(device);
        Mockito.verify(wireGuardService).restoreNormalRoutingForClient(device);
        Mockito.verify(torController).addDeviceUsingTor(device);
        Mockito.verify(networkStateMachine).deviceStateChanged(device);

        Assert.assertTrue(device.isUseAnonymizationService());
        Assert.assertTrue(device.isRoutedThroughTor());
        Assert.assertNull(device.getUseVPNProfileID());
    }

    @Test
    public void testDisableTor() {
        // setup device
        Device device = new Device();
        device.setUseAnonymizationService(true);
        device.setRouteThroughTor(true);

        // disable tor for device
        anonymousService.disableTor(device);

        // check state changes are correct
        Mockito.verify(deviceService).updateDevice(device);
        Mockito.verify(torController).removeDeviceNotUsingTor(device);
        Mockito.verify(networkStateMachine).deviceStateChanged(device);

        Assert.assertFalse(device.isUseAnonymizationService());
        Assert.assertTrue(device.isRoutedThroughTor());
        Assert.assertNull(device.getUseVPNProfileID());
    }

    @Test
    public void testEnableVpn() {
        // setup device
        Device device = new Device();
        VpnProfile vpnProfile = new WireGuardProfile(23, "test");

        // enable tor for device
        anonymousService.enableVpn(device, vpnProfile);

        // check state changes are correct
        Mockito.verify(deviceService).updateDevice(device);
        Mockito.verify(wireGuardService).routeClientThroughVpnTunnel(device, vpnProfile);
        Mockito.verify(torController).removeDeviceNotUsingTor(device);
        Mockito.verify(networkStateMachine).deviceStateChanged(device);

        Assert.assertTrue(device.isUseAnonymizationService());
        Assert.assertFalse(device.isRoutedThroughTor());
        Assert.assertEquals(vpnProfile.getId(), device.getUseVPNProfileID());
    }

    @Test
    public void testDisableVpn() {
        // setup device
        Device device = new Device();
        device.setUseAnonymizationService(true);
        device.setUseVPNProfileID(23);

        // disable vpn for device
        anonymousService.disableVpn(device);

        // check state changes are correct
        Mockito.verify(deviceService).updateDevice(device);
        Mockito.verify(wireGuardService).restoreNormalRoutingForClient(device);
        Mockito.verify(networkStateMachine).deviceStateChanged(device);

        Assert.assertFalse(device.isUseAnonymizationService());
        Assert.assertFalse(device.isRoutedThroughTor());
        Assert.assertEquals(Integer.valueOf(23), device.getUseVPNProfileID());
    }

    @Test
    public void testSwitchVpnToTor() {
        // setup device
        Device device = new Device();
        device.setUseAnonymizationService(true);
        device.setUseVPNProfileID(23);

        // disable vpn for device
        anonymousService.enableTor(device);

        // check state changes are correct
        Mockito.verify(deviceService).updateDevice(device);
        Mockito.verify(wireGuardService).restoreNormalRoutingForClient(device);
        Mockito.verify(torController).addDeviceUsingTor(device);
        Mockito.verify(networkStateMachine).deviceStateChanged(device);

        Assert.assertTrue(device.isUseAnonymizationService());
        Assert.assertTrue(device.isRoutedThroughTor());
        Assert.assertNull(device.getUseVPNProfileID());
    }

    @Test
    public void testSwitchTorToVpn() {
        // setup device
        Device device = new Device();
        device.setUseAnonymizationService(true);
        device.setRouteThroughTor(true);
        VpnProfile vpnProfile = new WireGuardProfile(23, "test");

        // disable vpn for device
        anonymousService.enableVpn(device, vpnProfile);

        // check state changes are correct
        Mockito.verify(deviceService).updateDevice(device);
        Mockito.verify(wireGuardService).routeClientThroughVpnTunnel(device, vpnProfile);
        Mockito.verify(torController).removeDeviceNotUsingTor(device);
        Mockito.verify(networkStateMachine).deviceStateChanged(device);

        Assert.assertTrue(device.isUseAnonymizationService());
        Assert.assertFalse(device.isRoutedThroughTor());
        Assert.assertEquals(vpnProfile.getId(), device.getUseVPNProfileID());
    }

    private Device mockDevice(String id, boolean useAnonymizationService, boolean routeThroughTor, Integer useVpnProfileId) {
        Device device = new Device();
        device.setId(id);
        device.setRouteThroughTor(routeThroughTor);
        device.setUseAnonymizationService(useAnonymizationService);
        device.setUseVPNProfileID(useVpnProfileId);
        return device;
    }
}
