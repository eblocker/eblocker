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
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.system.ScriptRunner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.xml.bind.DatatypeConverter;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

public class NetworkInterfaceWrapperTest {
    private static final Ip4Address EMERGENCY_ADDRESS = Ip4Address.parse("169.254.94.109");
    private static final String INTERFACE_NAME = "eth0";
    private static final String VPN_INTERFACE_NAME = "tun33";
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
    public void dhcpAssignedAddress() {
        wrapper.init();
        Assert.assertEquals(EMERGENCY_ADDRESS, wrapper.getFirstIPv4Address());
        NetworkInterfaceWrapper.IpAddressChangeListener listener = Mockito.mock(NetworkInterfaceWrapper.IpAddressChangeListener.class);
        wrapper.addIpAddressChangeListener(listener);
        assignAddresses(networkInterface, "192.168.1.2");
        Ip4Address newIp = Ip4Address.parse("192.168.1.2");
        wrapper.notifyIPAddressChanged(newIp);
        Assert.assertEquals(newIp, wrapper.getFirstIPv4Address());
        Mockito.verify(listener).onIpAddressChange(true, false);
    }

    @Test
    public void ip6AddressesNotChanged() {
        assignAddresses(networkInterface, "2003:42::1:2:3:4");
        wrapper.init();
        NetworkInterfaceWrapper.IpAddressChangeListener listener = Mockito.mock(NetworkInterfaceWrapper.IpAddressChangeListener.class);
        wrapper.addIpAddressChangeListener(listener);
        wrapper.notifyIp6AddressChanged();
        Mockito.verifyNoInteractions(listener);
    }

    @Test
    public void ip6AddressesChanged() {
        assignAddresses(networkInterface, "2003:42::1:2:3:4");
        wrapper.init();
        NetworkInterfaceWrapper.IpAddressChangeListener listener = Mockito.mock(NetworkInterfaceWrapper.IpAddressChangeListener.class);
        wrapper.addIpAddressChangeListener(listener);
        assignAddresses(networkInterface, "2003:42::5:6:7:8");
        wrapper.notifyIp6AddressChanged();
        Mockito.verify(listener).onIpAddressChange(false, true);
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

    @Test
    public void networkPrefixLength() {
        InterfaceAddress address1 = mockInterfaceAddress("192.168.1.2", 24);
        InterfaceAddress address2 = mockInterfaceAddress("2003:42::1:2:3:4", 64);
        Mockito.when(networkInterface.getInterfaceAddresses()).thenReturn(List.of(address1, address2));
        wrapper.init();

        Assert.assertEquals(24, wrapper.getNetworkPrefixLength(IpAddress.parse("192.168.1.2")));
        Assert.assertEquals(64, wrapper.getNetworkPrefixLength(IpAddress.parse("2003:42::1:2:3:4")));

        Assert.assertEquals(-1, wrapper.getNetworkPrefixLength(IpAddress.parse("192.168.1.7")));
        Assert.assertEquals(-1, wrapper.getNetworkPrefixLength(IpAddress.parse("2003:42::5:6:7:8")));
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

    private InterfaceAddress mockInterfaceAddress(String ip, int prefixLength) {
        InterfaceAddress address = Mockito.mock(InterfaceAddress.class);
        Mockito.when(address.getAddress()).thenReturn(getInetAddress(ip));
        Mockito.when(address.getNetworkPrefixLength()).thenReturn((short) prefixLength);
        return address;
    }
}
