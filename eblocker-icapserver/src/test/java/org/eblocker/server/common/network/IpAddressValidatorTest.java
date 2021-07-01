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

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.service.FeatureToggleRouter;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.common.TestClock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

public class IpAddressValidatorTest {

    private Table<String, IpAddress, Long> arpResponseTable;
    private TestClock clock;
    private DeviceService deviceService;
    private FeatureToggleRouter featureToggleRouter;
    private PubSubService pubSubService;
    private NetworkInterfaceWrapper networkInterface;
    private NetworkStateMachine networkStateMachine;
    private IpAddressValidator validator;

    private List<Device> devices;

    @Before
    public void setUp() {
        arpResponseTable = HashBasedTable.create();
        clock = new TestClock(ZonedDateTime.now());
        networkStateMachine = Mockito.mock(NetworkStateMachine.class);
        pubSubService = Mockito.mock(PubSubService.class);

        featureToggleRouter = Mockito.mock(FeatureToggleRouter.class);
        Mockito.when(featureToggleRouter.isIp6Enabled()).thenReturn(true);

        networkInterface = Mockito.mock(NetworkInterfaceWrapper.class);
        Mockito.when(networkInterface.getFirstIPv4Address()).thenReturn(Ip4Address.parse("192.168.7.1"));
        Mockito.when(networkInterface.getIp6LinkLocalAddress()).thenReturn(Ip6Address.parse("fe80::192:168:7:1"));
        Mockito.when(networkInterface.getHardwareAddressHex()).thenReturn("001122334455");
        Mockito.when(networkInterface.getHardwareAddress()).thenReturn(new byte[]{ 0x00, 0x11, 0x22, 0x33, 0x44, 0x55 });

        devices = Arrays.asList(
                createDevice("00aabbccddee", Arrays.asList(IpAddress.parse("192.168.7.20"), IpAddress.parse("fe80::192:168:7:20")), false),
                createDevice("00ff00112233", Arrays.asList(IpAddress.parse("192.168.7.21"), IpAddress.parse("192.168.7.22")), false),
                createDevice("008800000001", Arrays.asList(IpAddress.parse("192.168.7.100"), IpAddress.parse("10.8.0.9")), true),
                createDevice("008800000201", Arrays.asList(IpAddress.parse("192.168.7.200"), IpAddress.parse("192.168.7.201"), IpAddress.parse("10.8.0.10")), true));

        deviceService = Mockito.mock(DeviceService.class);
        Mockito.when(deviceService.getDevices(Mockito.anyBoolean())).thenReturn(devices);
        Mockito.when(deviceService.getDeviceById("device:00aabbccddee")).thenReturn(devices.get(0));
        Mockito.when(deviceService.getDeviceById("device:00ff00112233")).thenReturn(devices.get(1));
        Mockito.when(deviceService.getDeviceById("device:008800000001")).thenReturn(devices.get(2));
        Mockito.when(deviceService.getDeviceById("device:008800000201")).thenReturn(devices.get(3));

        validator = new IpAddressValidator(
                60,
                arpResponseTable,
                "10.8.0.0",
                "255.255.255.0",
                clock,
                deviceService,
                featureToggleRouter,
                networkInterface,
                networkStateMachine,
                pubSubService);
    }

    @Test
    public void testRequests() {
        validator.run();

        Mockito.verify(pubSubService).publish("arp:out", "1/001122334455/192.168.7.1/00aabbccddee/192.168.7.20");
        Mockito.verify(pubSubService).publish("arp:out", "1/001122334455/192.168.7.1/00ff00112233/192.168.7.21");
        Mockito.verify(pubSubService).publish("arp:out", "1/001122334455/192.168.7.1/00ff00112233/192.168.7.22");
        Mockito.verify(pubSubService).publish("ip6:out", "001122334455/fe800000000000000192016800070001/00aabbccddee/fe800000000000000192016800070020/icmp6/135/fe800000000000000192016800070020/1/001122334455");
        Mockito.verifyNoMoreInteractions(pubSubService);
    }

    @Test
    public void testKeepIpsDeviceWithoutRecentActivity() {
        long lastActivity = ZonedDateTime.now().minusHours(4).toInstant().toEpochMilli();

        arpResponseTable.put("00ff00112233", Ip4Address.parse("192.168.7.21"), lastActivity);
        arpResponseTable.put("00ff00112233", Ip6Address.parse("fe80::192:168:7:21"), lastActivity);
        arpResponseTable.put("00ff00112233", Ip4Address.parse("192.168.7.22"), lastActivity);
        arpResponseTable.put("008800000001", Ip4Address.parse("192.168.7.100"), lastActivity);

        validator.run();

        Assert.assertEquals(Arrays.asList(IpAddress.parse("192.168.7.20"), IpAddress.parse("fe80::192:168:7:20")), devices.get(0).getIpAddresses());
        Assert.assertEquals(Arrays.asList(IpAddress.parse("192.168.7.21"), IpAddress.parse("192.168.7.22")), devices.get(1).getIpAddresses());
        Assert.assertEquals(Arrays.asList(IpAddress.parse("192.168.7.100"), IpAddress.parse("10.8.0.9")), devices.get(2).getIpAddresses());
        Assert.assertTrue(arpResponseTable.contains("00ff00112233", Ip4Address.parse("192.168.7.21")));
        Assert.assertTrue(arpResponseTable.contains("00ff00112233", Ip4Address.parse("192.168.7.22")));
        Assert.assertTrue(arpResponseTable.contains("008800000001", Ip4Address.parse("192.168.7.100")));

        Mockito.verify(deviceService, Mockito.never()).updateDevice(Mockito.any(Device.class));
        Mockito.verify(networkStateMachine, Mockito.never()).deviceStateChanged(Mockito.any(Device.class));
    }

    @Test
    public void testRemoveIpsDeviceWithRecentActivity() {
        long now = System.currentTimeMillis();
        long lastActivity = ZonedDateTime.now().minusHours(4).toInstant().toEpochMilli();

        arpResponseTable.put("00aabbccddee", Ip4Address.parse("192.168.7.20"), now);
        arpResponseTable.put("00aabbccddee", Ip6Address.parse("fe80::192:168:7:20"), now);
        arpResponseTable.put("00ff00112233", Ip4Address.parse("192.168.7.21"), now);
        arpResponseTable.put("00ff00112233", Ip4Address.parse("192.168.7.22"), lastActivity);
        arpResponseTable.put("008800000001", Ip4Address.parse("192.168.7.100"), lastActivity);
        arpResponseTable.put("008800000201", Ip4Address.parse("192.168.7.200"), now);
        arpResponseTable.put("008800000201", Ip4Address.parse("192.168.7.201"), lastActivity);

        validator.run();

        Assert.assertEquals(Arrays.asList(IpAddress.parse("192.168.7.20"), IpAddress.parse("fe80::192:168:7:20")), devices.get(0).getIpAddresses());
        Assert.assertEquals(Arrays.asList(IpAddress.parse("192.168.7.21")), devices.get(1).getIpAddresses());
        Assert.assertEquals(Arrays.asList(IpAddress.parse("192.168.7.100"), IpAddress.parse("10.8.0.9")), devices.get(2).getIpAddresses());
        Assert.assertEquals(Arrays.asList(IpAddress.parse("192.168.7.200"), IpAddress.parse("10.8.0.10")), devices.get(3).getIpAddresses());
        Assert.assertTrue(arpResponseTable.contains("00aabbccddee", Ip4Address.parse("192.168.7.20")));
        Assert.assertTrue(arpResponseTable.contains("00ff00112233", Ip4Address.parse("192.168.7.21")));
        Assert.assertFalse(arpResponseTable.contains("00ff00112233", Ip4Address.parse("192.168.7.22")));
        Assert.assertTrue(arpResponseTable.contains("008800000001", Ip4Address.parse("192.168.7.100")));
        Assert.assertTrue(arpResponseTable.contains("008800000201", Ip4Address.parse("192.168.7.200")));

        Mockito.verify(deviceService).updateDevice(devices.get(1));
        Mockito.verify(networkStateMachine).deviceStateChanged(devices.get(1));
    }

    @Test
    public void testIp6Disabled() {
        Mockito.when(featureToggleRouter.isIp6Enabled()).thenReturn(false);
        validator.run();

        Mockito.verify(pubSubService).publish("arp:out", "1/001122334455/192.168.7.1/00aabbccddee/192.168.7.20");
        Mockito.verify(pubSubService).publish("arp:out", "1/001122334455/192.168.7.1/00ff00112233/192.168.7.21");
        Mockito.verify(pubSubService).publish("arp:out", "1/001122334455/192.168.7.1/00ff00112233/192.168.7.22");
        Mockito.verifyNoMoreInteractions(pubSubService);
    }

    private Device createDevice(String hwAddress, List<IpAddress> ipAddresses, boolean vpnClient) {
        Device device = new Device();
        device.setId("device:" + hwAddress);
        device.setIpAddresses(ipAddresses);
        device.setIsVpnClient(vpnClient);
        return device;
    }
}
