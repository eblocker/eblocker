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
import org.eblocker.server.common.data.TestDeviceFactory;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.pubsub.Channels;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.http.service.DeviceService;
import com.google.common.collect.Sets;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class OpenVpnAddressListenerTest {
   private OpenVpnAddressListener listener;
   private PubSubService pubSubService;
   private DeviceService deviceService;
   private EblockerDnsServer dnsServer;
   private NetworkStateMachine networkStateMachine;

    @Before
    public void setUp() throws Exception {
        deviceService = Mockito.mock(DeviceService.class);
        dnsServer = Mockito.mock(EblockerDnsServer.class);
        networkStateMachine = Mockito.mock(NetworkStateMachine.class);
        pubSubService = Mockito.mock(PubSubService.class);
        listener = new OpenVpnAddressListener("10.8.0.0", "255.255.255.0", deviceService, dnsServer, networkStateMachine, pubSubService);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void subscribesToAddressChanges() {
        listener.run();
        Mockito.verify(pubSubService).subscribeAndLoop(
                Mockito.eq(Channels.VPN_ADDRESS_UPDATE),
                Mockito.eq(listener));
    }

    @Test
    public void processAdd() {
        String message = "add 10.8.0.6 device:abcdef012345";
        Device device = TestDeviceFactory.createDevice("abcdef012345", "192.168.1.23", true);
        Mockito.when(deviceService.getDeviceById("device:abcdef012345")).thenReturn(device);
        listener.process(message);
        assertEquals(Sets.newHashSet(IpAddress.parse("10.8.0.6"), IpAddress.parse("192.168.1.23")), new HashSet<>(device.getIpAddresses()));
        Mockito.verify(deviceService).updateDevice(device);
        Mockito.verify(networkStateMachine).deviceStateChanged();
    }

    @Test
    public void processUpdate() {
        Device device = TestDeviceFactory.createDevice("abcdef012345", "192.168.1.23", true);
        Mockito.when(deviceService.getDeviceById("device:abcdef012345")).thenReturn(device);

        listener.process("add 10.8.0.4 device:abcdef012345");

        assertEquals(Sets.newHashSet(IpAddress.parse("10.8.0.4"), IpAddress.parse("192.168.1.23")), new HashSet<>(device.getIpAddresses()));
        Mockito.verify(deviceService).updateDevice(device);
        Mockito.verify(networkStateMachine).deviceStateChanged();

        listener.process("update 10.8.0.6 device:abcdef012345");

        assertEquals(Sets.newHashSet(IpAddress.parse("10.8.0.6"), IpAddress.parse("192.168.1.23")), new HashSet<>(device.getIpAddresses()));
        Mockito.verify(deviceService, Mockito.times(2)).updateDevice(device);
        Mockito.verify(networkStateMachine, Mockito.times(2)).deviceStateChanged();
    }

    @Test
    public void processDelete() {
        Device device = TestDeviceFactory.createDevice("abcdef012345", "192.168.1.23", true);
        device.setIsVpnClient(true);
        List<IpAddress> ipAddresses = device.getIpAddresses();
        ipAddresses.add(IpAddress.parse("10.8.0.6"));
        device.setIpAddresses(ipAddresses);
        Mockito.when(deviceService.getDeviceByIp(IpAddress.parse("10.8.0.6"))).thenReturn(device);

        listener.init();
        listener.process("delete 10.8.0.6");

        Mockito.verify(deviceService).updateDevice(device);
        Mockito.verify(networkStateMachine).deviceStateChanged();
        Assert.assertEquals(Collections.singletonList(IpAddress.parse("192.168.1.23")), device.getIpAddresses());
    }

    @Test
    public void badMesssage() {
        String message = "add me please";
        listener.process(message);
        Mockito.verifyZeroInteractions(deviceService);
        Mockito.verifyZeroInteractions(networkStateMachine);
    }

    @Test
    public void missingDeviceId() {
        String message = "add 10.8.0.6";
        listener.process(message);
        Mockito.verifyZeroInteractions(deviceService);
        Mockito.verifyZeroInteractions(networkStateMachine);
    }

    @Test
    public void badIpAddress() {
        String message = "add 1234 device:abcdef012345";
        listener.process(message);
        Mockito.verifyZeroInteractions(deviceService);
        Mockito.verifyZeroInteractions(networkStateMachine);
    }

    @Test
    public void testInit() {
        Device device = TestDeviceFactory.createDevice("abcdef012345", "192.168.1.23", "10.8.0.4", true);
        device.setIsVpnClient(true);
        List<IpAddress> ipAddresses = device.getIpAddresses();
        ipAddresses.add(IpAddress.parse("10.8.0.4"));
        device.setIpAddresses(ipAddresses);

        Mockito.when(deviceService.getDeviceById("device:abcdef012345")).thenReturn(device);
        Mockito.when(deviceService.getDevices(Mockito.anyBoolean())).thenReturn(Collections.singletonList(device));

        listener.init();
        listener.process("update 10.8.0.6 device:abcdef012345");

        assertEquals(Arrays.asList(IpAddress.parse("192.168.1.23"), IpAddress.parse("10.8.0.6")), device.getIpAddresses());
        Mockito.verify(deviceService).updateDevice(device);
        Mockito.verify(networkStateMachine).deviceStateChanged();
    }

    @Test
    public void testProcessVpnInterfaceUp() {
        listener.process("up tun33 1500 1570 10.8.0.1 10.8.0.2 init");
        Mockito.verify(dnsServer).refreshLocalDnsRecords();
    }

    @Test
    public void testProcessVpnInterfaceDown() {
        listener.process("down tun33 1500 1570 10.8.0.1 10.8.0.2 init");
        Mockito.verify(dnsServer).refreshLocalDnsRecords();
    }
}
