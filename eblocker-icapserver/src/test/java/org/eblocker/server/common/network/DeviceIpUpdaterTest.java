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

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DeviceFactory;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.Language;
import org.eblocker.server.common.data.TestDeviceFactory;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.UserService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;

public class DeviceIpUpdaterTest {
    private static final String GATEWAY_IP = "192.168.0.1";
    private static final String GATEWAY_MAC = "abcdef123456";

    private DeviceIpUpdater updater;
    private DataSource dataSource;
    private DeviceService deviceService;
    private NetworkStateMachine networkStateMachine;
    private DeviceFactory deviceFactory;
    private UserService userService;
    private TestDeviceFactory tdf;

    @Before
    public void setUp() throws Exception {
        dataSource = Mockito.mock(DataSource.class);
        deviceService = Mockito.mock(DeviceService.class);
        networkStateMachine = Mockito.mock(NetworkStateMachine.class);
        deviceFactory = new DeviceFactory(dataSource);
        userService = Mockito.mock(UserService.class);

        tdf = new TestDeviceFactory(deviceService);
        tdf.addDevice("012345abcdef", "192.168.0.100", true);
        tdf.addDevice("012345aaaaaa", "192.168.0.22", true);
        tdf.addDevice("001122334455", null, false);
        tdf.commit();

        Mockito.when(dataSource.getGateway()).thenReturn(GATEWAY_IP);

        Language lang = new Language("de", "German");
        Mockito.when(dataSource.getCurrentLanguage()).thenReturn(lang);

        updater = new DeviceIpUpdater(dataSource, deviceService, networkStateMachine, deviceFactory, userService);
    }

    @Test
    public void testSameIp() {
        updater.refresh("device:012345abcdef", Ip4Address.parse("192.168.0.100"));
        Mockito.verifyNoInteractions(networkStateMachine);
        Mockito.verify(deviceService, Mockito.never()).updateDevice(Mockito.any());
    }

    @Test
    public void testNewIp() {
        updater.refresh("device:001122334455", Ip4Address.parse("192.168.0.100"));
        Device device = TestDeviceFactory.createDevice("001122334455", "192.168.0.100", false);
        Mockito.verify(deviceService).updateDevice(device);
        Mockito.verify(networkStateMachine).deviceStateChanged();
    }

    @Test
    public void testNewDevice() {
        updater.refresh("device:112345abcdee", Ip4Address.parse("192.168.0.100"));
        Device device = TestDeviceFactory.createDevice("112345abcdee", "192.168.0.100", true);
        Mockito.verify(deviceService).updateDevice(device);
        Mockito.verify(networkStateMachine).deviceStateChanged();
    }

    @Test
    public void testAdditionalIp() {
        String deviceId = "device:012345abcdef";
        Device device = deviceService.getDeviceById(deviceId);
        Assert.assertEquals(Collections.singletonList(Ip4Address.parse("192.168.0.100")), device.getIpAddresses());
        updater.refresh(deviceId, Ip4Address.parse("192.168.0.200"));
        Assert.assertEquals(Arrays.asList(Ip4Address.parse("192.168.0.100"), Ip4Address.parse("192.168.0.200")), device.getIpAddresses());
        Mockito.verify(deviceService).updateDevice(device);
        Mockito.verify(networkStateMachine).deviceStateChanged();
    }

    @Test
    public void testGatewayIP() {
        Device gateway = TestDeviceFactory.createDevice(GATEWAY_MAC, (String) null, false);
        gateway.setIsGateway(false); // the gateway flag is not set since the device does not have an IP address yet
        tdf.addDevice(gateway);

        updater.refresh(Device.ID_PREFIX + GATEWAY_MAC, Ip4Address.parse(GATEWAY_IP));
        Assert.assertTrue(gateway.isGateway());
        Mockito.verify(deviceService).updateDevice(gateway);
    }

    @Test
    public void testNewGatewayDevice() {
        updater.refresh(Device.ID_PREFIX + GATEWAY_MAC, Ip4Address.parse(GATEWAY_IP));
        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);

        Mockito.verify(deviceService).updateDevice(deviceCaptor.capture());

        Device device = deviceCaptor.getValue();
        Assert.assertTrue(device.isGateway());
        Assert.assertEquals(GATEWAY_MAC, device.getHardwareAddress(false));
        Assert.assertEquals(Collections.singletonList(Ip4Address.parse(GATEWAY_IP)), device.getIpAddresses());
    }

    /**
     * Verify that the gateway IP address is not overwritten if the router has a second IP
     * address (e.g. a Fritzbox with VPN enabled, see EB1-777)
     */
    @Test
    public void routerWithSecondIpAddress() {
        Device gateway = TestDeviceFactory.createDevice(GATEWAY_MAC, GATEWAY_IP, false);
        gateway.setIsGateway(true);
        tdf.addDevice(gateway);

        updater.refresh(Device.ID_PREFIX + GATEWAY_MAC, Ip4Address.parse("192.168.0.201")); // second IP

        Assert.assertTrue(gateway.isGateway());
        Assert.assertEquals(Arrays.asList(Ip4Address.parse("192.168.0.1"), Ip4Address.parse("192.168.0.201")), gateway.getIpAddresses());
        Mockito.verify(deviceService).updateDevice(gateway);
    }
}
