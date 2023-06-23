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

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.TestDeviceFactory;
import org.eblocker.server.common.pubsub.Channels;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.common.TestClock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class ArpSpooferTest {
    private ArpSpoofer arpSpoofer;
    private TestClock clock;
    private ConcurrentMap<String, Long> arpProbeCache;
    private IpResponseTable ipResponseTable;
    private DataSource dataSource;
    private DeviceService deviceService;
    private PubSubService pubSubService;
    private NetworkInterfaceWrapper networkInterface;
    private static final String GATEWAY = "192.168.0.1";
    private static final Ip4Address EMERGENCY_IP = Ip4Address.parse("169.254.17.23");

    @Before
    public void setUp() throws Exception {
        arpProbeCache = new ConcurrentHashMap<>(32, 0.75f, 1);
        ipResponseTable = new IpResponseTable();
        clock = new TestClock(ZonedDateTime.now());
        dataSource = Mockito.mock(DataSource.class);
        deviceService = Mockito.mock(DeviceService.class);
        pubSubService = Mockito.mock(PubSubService.class);
        networkInterface = Mockito.mock(NetworkInterfaceWrapper.class);
        when(networkInterface.getFirstIPv4Address()).thenReturn(Ip4Address.parse("192.168.0.10"));
        when(networkInterface.getNetworkPrefixLength(IpAddress.parse("192.168.0.10"))).thenReturn(24);
        when(networkInterface.getHardwareAddressHex()).thenReturn("c82a144dc40e");

        arpSpoofer = new ArpSpoofer(5, 10, 5, EMERGENCY_IP, arpProbeCache, ipResponseTable, clock, dataSource, deviceService, pubSubService, networkInterface);

        TestDeviceFactory tdf = new TestDeviceFactory(deviceService);
        tdf.addDevice("abcdef012345", GATEWAY, true);
        tdf.addDevice("c82a144dc40e", "192.168.0.99", true);   // ourselves
        tdf.addDevice("11aabbccddee", "192.168.0.100", true);  // to be spoofed
        tdf.addDevice("00aabbccddee", "192.168.0.101", false); // not to be spoofed
        tdf.addDevice("00aabbccddbb", "192.168.1.101", true); // not to be spoofed because it is in a different subnet
        tdf.addDevice("00aabbccddff", null, true); //not to be spoofed, because its inactive (ipAddress == null)
        tdf.addDevice("22aabbccddff", "192.168.0.104", true); // not to be spoofed, because its inactive (no recent arp traffic)
        tdf.addDevice("11f000000000", null, true);  // to be spoofed with multiple ips

        // device should not be indirectly spoof router as a arp probe has been seen shortly
        Device device = TestDeviceFactory.createDevice("00aabbccdd00", "192.168.0.102", true);
        arpProbeCache.put(device.getId(), clock.millis());
        tdf.addDevice(device);

        // device should be indirectly spoofed as last arp probe has been seen a while ago
        Device device2 = TestDeviceFactory.createDevice("00aabbccdd01", "192.168.0.103", true);
        arpProbeCache.put(device2.getId(), clock.millis() - 10 * 1000);
        tdf.addDevice(device2);

        tdf.commit();
        when(dataSource.getGateway()).thenReturn(GATEWAY);

        tdf.getDevice("device:11f000000000").setIpAddresses(Arrays.asList(IpAddress.parse("192.168.0.200"), IpAddress.parse("192.168.0.201")));

        // fill arp table for devices
        ipResponseTable.put("abcdef012345", Ip4Address.parse(GATEWAY), clock.millis());
        ipResponseTable.put("c82a144dc40e", Ip4Address.parse("192.168.0.99"), clock.millis());
        ipResponseTable.put("11aabbccddee", Ip4Address.parse("192.168.0.100"), clock.millis());
        ipResponseTable.put("00aabbccddee", Ip4Address.parse("192.168.0.101"), clock.millis());
        ipResponseTable.put("00aabbccddbb", Ip4Address.parse("192.168.1.101"), clock.millis());
        ipResponseTable.put("11f000000000", Ip4Address.parse("192.168.0.200"), clock.millis());
        ipResponseTable.put("11f000000000", Ip4Address.parse("192.168.0.201"), clock.millis());
        ipResponseTable.put("00aabbccdd00", Ip4Address.parse("192.168.0.102"), clock.millis());
        ipResponseTable.put("00aabbccdd01", Ip4Address.parse("192.168.0.103"), clock.millis());
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void dontSpoofGateway() {
        arpSpoofer.run();
        verify(pubSubService, never()).publish(Channels.ARP_OUT, "2/c82a144dc40e/192.168.0.1/abcdef012345/" + GATEWAY);
    }

    @Test
    public void dontSpoofGatewayMultipleIps() {
        deviceService.getDeviceById("device:abcdef012345").setIpAddresses(Arrays.asList(IpAddress.parse("192.168.0.200"), IpAddress.parse(GATEWAY)));

        arpSpoofer.run();
        verify(pubSubService, never()).publish(Channels.ARP_OUT, "2/c82a144dc40e/192.168.0.1/abcdef012345/" + GATEWAY);
        verify(pubSubService, never()).publish(Channels.ARP_OUT, "2/c82a144dc40e/192.168.0.200/abcdef012345/" + GATEWAY);
    }

    @Test
    public void dontSpoofOurselves() {
        arpSpoofer.run();
        verify(pubSubService, never()).publish(Channels.ARP_OUT, "2/c82a144dc40e/192.168.0.1/c82a144dc40e/192.168.0.99");
    }

    @Test
    public void dontSpoofDifferentSubnet() {
        arpSpoofer.run();
        verify(pubSubService, never()).publish(Channels.ARP_OUT, "2/c82a144dc40e/192.168.0.1/00aabbccddbb/192.168.1.101");
    }

    @Test
    public void spoofEnabledDeviceSingleIp() {
        arpSpoofer.run();
        verify(pubSubService).publish(Channels.ARP_OUT, "1/c82a144dc40e/192.168.0.1/11aabbccddee/192.168.0.100");
        verify(pubSubService).publish(Channels.ARP_OUT, "2/c82a144dc40e/192.168.0.1/11aabbccddee/192.168.0.100");
    }

    @Test
    public void spoofEnabledDeviceWithMultipleIps() {
        arpSpoofer.run();
        verify(pubSubService).publish(Channels.ARP_OUT, "1/c82a144dc40e/192.168.0.1/11f000000000/192.168.0.200");
        verify(pubSubService).publish(Channels.ARP_OUT, "2/c82a144dc40e/192.168.0.1/11f000000000/192.168.0.200");
        verify(pubSubService).publish(Channels.ARP_OUT, "1/c82a144dc40e/192.168.0.1/11f000000000/192.168.0.201");
        verify(pubSubService).publish(Channels.ARP_OUT, "2/c82a144dc40e/192.168.0.1/11f000000000/192.168.0.201");
    }

    @Test
    public void spoofEnabledDeviceSingleIpWithGatewayMultipleIp() {
        deviceService.getDeviceById("device:abcdef012345").setIpAddresses(Arrays.asList(IpAddress.parse("192.168.0.5"), IpAddress.parse(GATEWAY), IpAddress.parse("192.168.0.10")));

        arpSpoofer.run();
        verify(pubSubService).publish(Channels.ARP_OUT, "1/c82a144dc40e/192.168.0.1/11aabbccddee/192.168.0.100");
        verify(pubSubService).publish(Channels.ARP_OUT, "2/c82a144dc40e/192.168.0.1/11aabbccddee/192.168.0.100");
    }

    @Test
    public void publishDeviceIndirectly() {
        arpSpoofer.run();
        // Silence the gateway, so that it does not ask for the IP of any device
        // (because that would tell the device the real gateway hardware address):
        // This is done indirectly for any device but the eblocker:
        // eblocker -(gw_hw, 0.0.0.0, 00000000000000, dev_ip)-> device
        // In turn the device replies with its ip: device -(dev_hw, dev_ip, gw_hw, 0.0.0.0)-> gateway
        verify(pubSubService).publish(Channels.ARP_OUT, "1/abcdef012345/0.0.0.0/000000000000/192.168.0.100/11aabbccddee");
        verify(pubSubService).publish(Channels.ARP_OUT, "1/abcdef012345/0.0.0.0/000000000000/192.168.0.101/00aabbccddee");
        verify(pubSubService, never()).publish(Channels.ARP_OUT, "1/abcdef012345/0.0.0.0/000000000000/192.168.0.99/c82a144dc40e");
    }

    @Test
    public void dontPublishDeviceIndirectlyOffline() {
        arpSpoofer.run();
        verify(pubSubService, never()).publish(Channels.ARP_OUT, "1/abcdef012345/0.0.0.0/000000000000/192.168.0.104/22aabbccddff");
    }

    @Test
    public void dontPublishDeviceIndirectlyWithRecentArpProbe() {
        arpSpoofer.run();
        verify(pubSubService, never()).publish(Channels.ARP_OUT, "1/abcdef012345/0.0.0.0/000000000000/192.168.0.102/00aabbccdd00");
    }

    @Test
    public void dontPublishDeviceIndirectlyWithRecentArpProbeWait() {
        arpSpoofer.run();
        verify(pubSubService, never()).publish(Channels.ARP_OUT, "1/abcdef012345/0.0.0.0/000000000000/192.168.0.102/00aabbccdd00");

        clock.setInstant(clock.instant().plusMillis(5000));

        Mockito.reset(pubSubService);
        arpSpoofer.run();
        verify(pubSubService).publish(Channels.ARP_OUT, "1/abcdef012345/0.0.0.0/000000000000/192.168.0.102/00aabbccdd00");
    }

    @Test
    public void publishDeviceIndirectlyWithOldArpProbe() {
        arpSpoofer.run();
        verify(pubSubService).publish(Channels.ARP_OUT, "1/abcdef012345/0.0.0.0/000000000000/192.168.0.103/00aabbccdd01");
    }

    @Test
    public void publishOwnAddress() {
        arpSpoofer.run();
        verify(pubSubService).publish(Channels.ARP_OUT, "1/c82a144dc40e/192.168.0.10/ffffffffffff/192.168.0.10");
    }

    @Test
    public void dontSpoofDisabledDevice() {
        arpSpoofer.run();

        verify(pubSubService, never()).publish(Channels.ARP_OUT, "2/c82a144dc40e/192.168.0.1/00aabbccddee/192.168.0.101");
    }

    @Test
    public void dontSpoofInactiveDevice() {
        arpSpoofer.run();
        verify(pubSubService, never()).publish(eq(Channels.ARP_OUT), startsWith("2/c82a144dc40e/192.168.0.1/00aabbccddff/"));
    }

    @Test
    public void noAssignedIp() {
        when(networkInterface.getFirstIPv4Address()).thenReturn(null);
        arpSpoofer.run();
        verifyZeroInteractions(pubSubService);
    }

    @Test
    public void emergencyIp() {
        when(networkInterface.getFirstIPv4Address()).thenReturn(EMERGENCY_IP);
        arpSpoofer.run();
        verifyZeroInteractions(pubSubService);
    }
}
