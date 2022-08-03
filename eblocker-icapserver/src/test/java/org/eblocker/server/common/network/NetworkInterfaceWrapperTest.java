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

import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.system.ScriptRunner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.xml.bind.DatatypeConverter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

public class NetworkInterfaceWrapperTest {
    private static Ip4Address EMERGENCY_ADDRESS = Ip4Address.parse("169.254.94.109");
    private static String INTERFACE_NAME = "eth0";
    private static String VPN_INTERFACE_NAME = "tun33";
    private NetworkInterface networkInterface;
    private NetworkInterface vpnInterface;
    private NetworkInterfaceWrapper wrapper;

    @Before
    public void setUp() throws Exception {
        NetworkInterfaceFactory factory = Mockito.mock(NetworkInterfaceFactory.class);
        networkInterface = Mockito.mock(NetworkInterface.class);
        vpnInterface = Mockito.mock(NetworkInterface.class);
        ScriptRunner scriptRunner = Mockito.mock(ScriptRunner.class);

        Mockito.when(factory.getNetworkInterfaceByName(INTERFACE_NAME)).thenReturn(networkInterface);
        Mockito.when(factory.getNetworkInterfaceByName(VPN_INTERFACE_NAME)).thenReturn(vpnInterface);
        Mockito.when(networkInterface.getHardwareAddress()).thenReturn(DatatypeConverter.parseHexBinary("012345abcdef"));

        wrapper = new NetworkInterfaceWrapper(factory, INTERFACE_NAME, EMERGENCY_ADDRESS, "get_gateway", "tun33", scriptRunner);
    }

    @Test
    public void getFirstIPv4Address() {
        assignAddresses(networkInterface, "192.168.0.2", "192.168.0.3", "2003:42::abcd");
        wrapper.init();
        Assert.assertEquals(Ip4Address.parse("192.168.0.2"), wrapper.getFirstIPv4Address());
    }

    @Test
    public void noDhcp() {
        // there is no IPv4 address assigned yet
        wrapper.init();
        Assert.assertEquals(EMERGENCY_ADDRESS, wrapper.getFirstIPv4Address());
    }

    @Test
    public void noIp6Address() {
        wrapper.init();
        Assert.assertFalse(wrapper.hasGlobalIp6Address());
    }

    @Test
    public void onlyLinkLocalIp6Addresses() {
        assignAddresses(networkInterface, "fe80::abcd", "fe80::cafe");
        wrapper.init();
        Assert.assertFalse(wrapper.hasGlobalIp6Address());
        Assert.assertEquals(Ip6Address.parse("fe80::abcd"), wrapper.getIp6LinkLocalAddress());
    }

    @Test
    public void hasGlobalIp6Address() {
        assignAddresses(networkInterface, "fe80::abcd", "2003:42::cafe");
        wrapper.init();
        Assert.assertTrue(wrapper.hasGlobalIp6Address());
    }

    @Test
    public void getHardwareAddressHex() {
        wrapper.init();
        Assert.assertEquals("012345abcdef", wrapper.getHardwareAddressHex());
    }

    @Test
    public void getVpnIpv4Address() {
        assignAddresses(vpnInterface, "10.8.0.17", "fe80::cafe");
        wrapper.init();
        Assert.assertEquals(Ip4Address.parse("10.8.0.17"), wrapper.getVpnIpv4Address());
    }

    private void assignAddresses(NetworkInterface networkInterface, String... addresses) {
        Mockito.when(networkInterface.inetAddresses()).thenAnswer(invocation -> Arrays.stream(addresses).map(this::getInetAddress));
    }

    private InetAddress getInetAddress(String ip) {
        try {
            return InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            throw new RuntimeException("Could not create InetAddress from IP", e);
        }
    }
}
