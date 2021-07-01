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
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DeviceFactory;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.Language;
import org.eblocker.server.common.data.TestDeviceFactory;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.pubsub.Subscriber;
import org.eblocker.server.http.service.DeviceOnlineStatusCache;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.common.TestClock;
import org.eblocker.server.http.service.UserService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class ArpListenerTest {
    private static final String GATEWAY_IP = "192.168.0.1";
    private static final String GATEWAY_MAC = "abcdef123456";

    private ArpListener listener;
    private ConcurrentMap<String, Long> arpProbeCache;
    private Table<String, IpAddress, Long> arpResponseTable;
    private DataSource dataSource;
    private DeviceService deviceService;
    private DeviceOnlineStatusCache deviceOnlineStatusCache;
    private PubSubService pubSubService;
    private NetworkInterfaceWrapper networkInterface;
    private NetworkStateMachine networkStateMachine;
    private TestClock clock;
    private DeviceFactory deviceFactory;

    protected Subscriber subscriber;

    private TestDeviceFactory tdf;

    @Before
    public void setUp() throws Exception {
        dataSource = Mockito.mock(DataSource.class);
        deviceService = Mockito.mock(DeviceService.class);
        deviceOnlineStatusCache = Mockito.mock(DeviceOnlineStatusCache.class);
        networkInterface = Mockito.mock(NetworkInterfaceWrapper.class);
        when(networkInterface.getHardwareAddressHex()).thenReturn("caffee012345");
        when(networkInterface.getFirstIPv4Address()).thenReturn(Ip4Address.parse("192.168.0.2"));
        when(networkInterface.getNetworkPrefixLength(IpAddress.parse("192.168.0.2"))).thenReturn(24);
        networkStateMachine = Mockito.mock(NetworkStateMachine.class);
        clock = new TestClock(ZonedDateTime.now());
        UserService userService = Mockito.mock(UserService.class);

        tdf = new TestDeviceFactory(deviceService);
        tdf.addDevice("012345abcdef", "192.168.0.100", true);
        tdf.addDevice("012345aaaaaa", "192.168.0.22", true);
        tdf.addDevice("001122334455", null, false);
        tdf.commit();

        when(dataSource.getGateway()).thenReturn(GATEWAY_IP);
        Language lang = new Language("de", "German");
        when(dataSource.getCurrentLanguage()).thenReturn(lang);

        UserModule user = new UserModule(123456, null, null, null, null, null, true, null, null, null, null, null);
        Mockito.when(userService.restoreDefaultSystemUser(any())).thenReturn(user);

        pubSubService = new PubSubService() {
            @Override
            public void subscribeAndLoop(String channel, Subscriber aSubscriber) {
                assertEquals("arp:in", channel);
                subscriber = aSubscriber;
            }

            @Override
            public void publish(String channel, String message) {
                fail("publish should never be used by the ArpListener");
            }

            @Override
            public void unsubscribe(Subscriber subscriber) {
                fail("unsubscribe should never be called by the ArpListener");
            }
        };

        arpProbeCache = new ConcurrentHashMap<>(32, 0.75f, 1);
        arpResponseTable = HashBasedTable.create();

        deviceFactory = new DeviceFactory(dataSource);

        listener = new ArpListener(arpProbeCache, arpResponseTable, dataSource, deviceService, deviceOnlineStatusCache, pubSubService, networkInterface, networkStateMachine, clock, deviceFactory, userService);
    }

    @Test
    public void testInvalidMessage() {
        listener.run();
        subscriber.process("warbl");
        verifyZeroInteractions(deviceService);
    }

    @Test
    public void testResponseMessageSameIP() {
        listener.run();
        subscriber.process(response("012345abcdef", "192.168.0.100"));
        verifyZeroInteractions(networkStateMachine);
        verify(deviceService, never()).updateDevice(any());
        verify(deviceOnlineStatusCache).updateOnlineStatus("device:012345abcdef");
    }

    @Test
    public void testResponseMessageNewIP() {
        listener.run();
        // before, this device was known but had no IP:
        subscriber.process(response("001122334455", "192.168.0.100"));
        Device device = TestDeviceFactory.createDevice("001122334455", "192.168.0.100", false);
        verify(deviceService).updateDevice(device);
        verify(deviceOnlineStatusCache).updateOnlineStatus("device:001122334455");
        verify(networkStateMachine).deviceStateChanged();
    }

    @Test
    public void testResponseMessageNewDevice() {
        listener.run();
        subscriber.process(response("112345abcdee", "192.168.0.100"));
        Device device = TestDeviceFactory.createDevice("112345abcdee", "192.168.0.100", true);
        verify(deviceService).updateDevice(device);
        verify(networkStateMachine).deviceStateChanged();
    }

    @Test
    public void testResponseMessageNewDeviceDifferentSubnet() {
        listener.run();
        subscriber.process(response("112345abcdee", "192.168.23.100"));
        verify(deviceService, Mockito.never()).updateDevice(Mockito.any(Device.class));
        verify(networkStateMachine, Mockito.never()).deviceStateChanged();
    }

    @Test
    public void testResponseMessageKnownDeviceDifferentSubnet() {
        listener.run();
        Device device = tdf.getDevice("device:012345abcdef");
        subscriber.process(response("012345abcdef", "192.168.23.100"));
        verify(deviceService, Mockito.never()).updateDevice(Mockito.any(Device.class));
        verify(networkStateMachine, Mockito.never()).deviceStateChanged();
        Assert.assertNotEquals("192.168.23.100", device.getIpAddresses().get(0));
    }

    @Test
    public void testRequestMessage() {
        listener.run();
        subscriber.process("1/012345abcdef/192.168.0.100/ffffffffffff/192.168.0.1");
        verifyZeroInteractions(networkStateMachine, deviceService, deviceOnlineStatusCache);
    }

    @Test
    public void testGratuitousRequestNewDevice() {
        listener.run();
        subscriber.process("1/112345abcdee/192.168.0.100/ffffffffffff/192.168.0.100");
        Device device = TestDeviceFactory.createDevice("112345abcdee", "192.168.0.100", true);
        verify(deviceService).updateDevice(device);
        verify(networkStateMachine).deviceStateChanged();
    }

    @Test
    public void testArpProbe() {
        listener.run();
        subscriber.process("1/012345aaaaaa/0.0.0.0/000000000000/192.168.0.101");

        Device device = tdf.getDevice("device:012345aaaaaa");
        verify(deviceService, Mockito.never()).updateDevice(device);
        verify(networkStateMachine, Mockito.never()).deviceStateChanged();
        Assert.assertNotNull(arpProbeCache.get(device.getId()));
        Assert.assertTrue(Math.abs(System.currentTimeMillis() - arpProbeCache.get(device.getId())) < 1000);
        Assert.assertTrue(arpResponseTable.isEmpty());
    }

    @Test
    public void testGratuitousRequestIpChange() {
        Device device = deviceService.getDeviceById("device:012345abcdef");
        Assert.assertNotNull(device);
        Assert.assertEquals(Collections.singletonList(Ip4Address.parse("192.168.0.100")), device.getIpAddresses());

        listener.run();
        subscriber.process("1/012345abcdef/192.168.0.200/ffffffffffff/192.168.0.200");

        Assert.assertEquals(Arrays.asList(Ip4Address.parse("192.168.0.100"), Ip4Address.parse("192.168.0.200")), device.getIpAddresses());
        verify(deviceService).updateDevice(device);
        verify(networkStateMachine).deviceStateChanged();
    }

    @Test
    public void testBrokenArpRequest() {
        listener.run();
        // ARP messages with both source and target IP set to 0.0.0.0 sometimes occur,
        // see: https://osqa-ask.wireshark.org/questions/5178/why-gratuitous-arps-for-0000
        subscriber.process("1/112345abcdee/0.0.0.0/000000000000/0.0.0.0");

        // make sure we don't process them
        Device device = tdf.getDevice("device:012345aaaaaa");
        verify(deviceService, Mockito.never()).updateDevice(device);
        verify(networkStateMachine, Mockito.never()).deviceStateChanged();
        Assert.assertTrue(arpProbeCache.isEmpty());
        Assert.assertTrue(arpResponseTable.isEmpty());
    }

    @Test
    public void ignoreLocalMessages() {
        listener.run();
        subscriber.process("2/caffee012345/192.168.0.100/abcdef012345/192.168.0.99");
        verifyZeroInteractions(deviceService);
    }

    @Test
    public void testIpAddressConflictResolving() {
        //added one device in the setup method first with IP address: 192.168.0.22
        listener.run();
        //now add second device with for same IP address
        // -> this should "deactivate" the first device, which means that the ipAddress of the first device should be "null" afterwards
        // -> devices with ipAddress == null will be ignored by the ArpSpoofer
        subscriber.process("2/112345aaaaee/192.168.0.22/abcdef012345/192.168.0.99");
        Device newDevice = TestDeviceFactory.createDevice("112345aaaaee", "192.168.0.22", true);
        Device existingDevice = TestDeviceFactory.createDevice("012345aaaaaa", (String) null, true);

        InOrder inOrder = Mockito.inOrder(deviceService, networkStateMachine);
        inOrder.verify(deviceService).updateDevice(newDevice);
        //inOrder.verify(deviceService).updateDevice(existingDevice);
        inOrder.verify(networkStateMachine).deviceStateChanged();

        Assert.assertEquals(1, newDevice.getIpAddresses().size());
        Assert.assertTrue(existingDevice.getIpAddresses().isEmpty());
    }

    @Test
    public void testResponseMessageNewGatewayIP() {
        Device gateway = TestDeviceFactory.createDevice(GATEWAY_MAC, (String) null, false);
        gateway.setIsGateway(false); // the gateway flag is not set since the device does not have an IP address yet
        tdf.addDevice(gateway);

        listener.run();
        subscriber.process("2/" + GATEWAY_MAC + "/" + GATEWAY_IP + "/abcdef012345/192.168.0.99");
        Assert.assertTrue(gateway.isGateway());
        verify(deviceService).updateDevice(gateway);
    }

    @Test
    public void testResponseMessageNewGatewayDevice() {
        listener.run();
        subscriber.process("2/" + GATEWAY_MAC + "/" + GATEWAY_IP + "/abcdef012345/192.168.0.99");

        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);

        verify(deviceService).updateDevice(deviceCaptor.capture());

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

        listener.run();
        subscriber.process("2/" + GATEWAY_MAC + "/192.168.0.201/abcdef012345/192.168.0.99"); // second IP

        Assert.assertTrue(gateway.isGateway());
        Assert.assertEquals(Arrays.asList(Ip4Address.parse("192.168.0.1"), Ip4Address.parse("192.168.0.201")), gateway.getIpAddresses());
        verify(deviceService).updateDevice(gateway);
    }

    @Test
    public void testDeviceWithMultipleIps() {
        listener.run();

        subscriber.process(response("012345aaaaaa", "192.168.0.22"));
        subscriber.process(response("012345aaaaaa", "192.168.0.42"));
        subscriber.process(response("012345aaaaaa", "192.168.0.242"));

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        Mockito.verify(deviceService, Mockito.times(2)).updateDevice(captor.capture());

        Device device = captor.getValue();
        Assert.assertEquals(Arrays.asList(Ip4Address.parse("192.168.0.22"), Ip4Address.parse("192.168.0.42"), Ip4Address.parse("192.168.0.242")), device.getIpAddresses());
        Assert.assertEquals(clock.instant().toEpochMilli(), arpResponseTable.get("012345aaaaaa", Ip4Address.parse("192.168.0.22")).longValue());
        Assert.assertEquals(clock.instant().toEpochMilli(), arpResponseTable.get("012345aaaaaa", Ip4Address.parse("192.168.0.42")).longValue());
        Assert.assertEquals(clock.instant().toEpochMilli(), arpResponseTable.get("012345aaaaaa", Ip4Address.parse("192.168.0.242")).longValue());

    }

    private String response(String senderMac, String senderIp) {
        return String.format("2/%s/%s/abcdef012345/192.168.0.99", senderMac, senderIp);
    }
}
