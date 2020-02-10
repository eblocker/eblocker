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

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.service.FeatureToggleRouter;
import org.eblocker.server.http.service.DeviceService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;

public class Ip6NetworkScanTest {

    private DeviceService deviceService;
    private FeatureToggleRouter featureToggleRouter;
    private NetworkInterfaceWrapper networkInterface;
    private PubSubService pubSubService;
    private Ip6NetworkScan ip6NetworkScan;

    @Before
    public void setUp() {
        deviceService = Mockito.mock(DeviceService.class);
        Mockito.when(deviceService.getDevices(false)).thenReturn(Arrays.asList(
            createDevice("000010101010", IpAddress.parse("10.10.10.10"), IpAddress.parse("fe80::10:10:10:10")),
            createDevice("000010101020", IpAddress.parse("10.10.10.20"), IpAddress.parse("fe80::10:10:10:20"), IpAddress.parse("2317::10:10:10:20")),
            createDevice("000010101030", IpAddress.parse("10.10.10.30"), IpAddress.parse("fe80::10:10:10:30"), IpAddress.parse("fe80::10:10:10:31")),
            createDevice("000010101040", IpAddress.parse("10.10.10.40"), IpAddress.parse("fe80::10:10:10:40"), IpAddress.parse("2305::10:10:10:40"), IpAddress.parse("2317::10:10:10:40")),
            createDevice("0000101010f")
        ));

        featureToggleRouter = Mockito.mock(FeatureToggleRouter.class);

        networkInterface = Mockito.mock(NetworkInterfaceWrapper.class);
        Mockito.when(networkInterface.getHardwareAddress()).thenReturn(new byte[] { 0x00, 0x00, 0x10, 0x10, 0x10, 0x00 });
        Mockito.when(networkInterface.getIp6LinkLocalAddress()).thenReturn(Ip6Address.parse("fe80::10:10:10:00"));
        Mockito.when(networkInterface.getAddresses()).thenReturn(Arrays.asList(
            IpAddress.parse("10.10.10.1"),
            IpAddress.parse("fe80::10:10:10:00"),
            IpAddress.parse("2305::10:10:10:00"),
            IpAddress.parse("2317::10:10:10:00")
        ));
        Mockito.when(networkInterface.getNetworkPrefixLength(IpAddress.parse("2305::10:10:10:00"))).thenReturn(64);
        Mockito.when(networkInterface.getNetworkPrefixLength(IpAddress.parse("2317::10:10:10:00"))).thenReturn(64);

        pubSubService = Mockito.mock(PubSubService.class);

        ip6NetworkScan = new Ip6NetworkScan(deviceService, featureToggleRouter, networkInterface, pubSubService);
    }

    @Test
    public void testScan() {
        Mockito.when(featureToggleRouter.isIp6Enabled()).thenReturn(true);
        ip6NetworkScan.run();
        Mockito.verify(pubSubService).publish("ip6:out", "000010101000/fe800000000000000010001000100000/000010101010/23050000000000000010001000100010/icmp6/135/23050000000000000010001000100010/1/000010101000");
        Mockito.verify(pubSubService).publish("ip6:out", "000010101000/fe800000000000000010001000100000/000010101010/23170000000000000010001000100010/icmp6/135/23170000000000000010001000100010/1/000010101000");
        Mockito.verify(pubSubService).publish("ip6:out", "000010101000/fe800000000000000010001000100000/000010101020/23050000000000000010001000100020/icmp6/135/23050000000000000010001000100020/1/000010101000");
        Mockito.verify(pubSubService).publish("ip6:out", "000010101000/fe800000000000000010001000100000/000010101030/23050000000000000010001000100030/icmp6/135/23050000000000000010001000100030/1/000010101000");
        Mockito.verify(pubSubService).publish("ip6:out", "000010101000/fe800000000000000010001000100000/000010101030/23050000000000000010001000100031/icmp6/135/23050000000000000010001000100031/1/000010101000");
        Mockito.verify(pubSubService).publish("ip6:out", "000010101000/fe800000000000000010001000100000/000010101030/23170000000000000010001000100030/icmp6/135/23170000000000000010001000100030/1/000010101000");
        Mockito.verify(pubSubService).publish("ip6:out", "000010101000/fe800000000000000010001000100000/000010101030/23170000000000000010001000100031/icmp6/135/23170000000000000010001000100031/1/000010101000");
        Mockito.verifyNoMoreInteractions(pubSubService);
    }

    @Test
    public void testFeatureDisabled() {
        Mockito.when(featureToggleRouter.isIp6Enabled()).thenReturn(false);
        ip6NetworkScan.run();
        Mockito.verifyZeroInteractions(pubSubService);
    }

    private Device createDevice(String hardwareAddress, IpAddress... ipAddress) {
        Device device = new Device();
        device.setId("device:" + hardwareAddress);
        device.setIpAddresses(Arrays.asList(ipAddress));
        return device;
    }
}
