/*
 * Copyright 2023 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.network.unix.firewall;

import org.eblocker.server.common.data.vpn.VpnClientState;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

public class TableGeneratorIp6Test extends TableGeneratorTestBase {
    protected final String anonVpnInterface = "tun0";
    private TableGeneratorIp6 generator;
    private final String eBlockerIp = "fe80::eb10";

    private final String prefixHome = "2a00:1111::/64";
    private final String disabledDevice = "2a00:1111::d15a:b1ed";
    private final String enabledDevice = "2a00:1111::ea:b1ed";
    private final String sslEnabledDevice = "2a00:1111::551:ea:b1ed";
    private final String torClientDevice = "2a00:1111::709";
    private final String torClientDeviceLocal = "fe80::709";
    private final String anonVpnWithIp6Device = "2a00:1111::a6";
    private final String anonVpnOnlyIp4Device = "2a00:1111::a4";
    private final String anonVpnWithIp6DeviceLocal = "fe80::a6";
    private final String anonVpnOnlyIp4DeviceLocal = "fe80::a4";
    private final String externalHost = "2a00:eeee::4321";

    private final String anonVpnWithIp6Interface = "tun0";
    private final String anonVpnOnlyIp4Interface = "tun1";
    private final int anonVpnWithIp6Route = 1;
    private final int anonVpnOnlyIp4Route = 2;
    private final String anonVpnWithIp6DeviceId = "anonVpnWithIp6DeviceId";
    private final String anonVpnOnlyIp4DeviceId = "anonVpnIp4OnlyDeviceId";
    private final String anonVpnWithIp6Gateway = "2000:23::1";

    private final String malwareIp6SetName = "malware6";
    @Before
    public void setUp() {
        generator = new TableGeneratorIp6(standardInterface, mobileVpnInterface, httpPort, httpsPort, proxyPort, proxyHTTPSPort, localDnsPort, malwareIp6SetName, torPort, torDnsPort, torMark);

        deviceIpFilter = Mockito.mock(IpAddressFilter.class);
        Mockito.when(deviceIpFilter.getEnabledDevicesIps()).thenReturn(List.of(enabledDevice, sslEnabledDevice, torClientDevice));
        Mockito.when(deviceIpFilter.getDisabledDevicesIps()).thenReturn(List.of(disabledDevice));
        Mockito.when(deviceIpFilter.getSslEnabledDevicesIps()).thenReturn(List.of(sslEnabledDevice, torClientDevice));
        Mockito.when(deviceIpFilter.getDevicesIps(Set.of(anonVpnWithIp6DeviceId))).thenReturn(List.of(anonVpnWithIp6Device, anonVpnWithIp6DeviceLocal));
        Mockito.when(deviceIpFilter.getDevicesIps(Set.of(anonVpnOnlyIp4DeviceId))).thenReturn(List.of(anonVpnOnlyIp4Device, anonVpnOnlyIp4DeviceLocal));
        Mockito.when(deviceIpFilter.getTorDevicesIps()).thenReturn(List.of(torClientDevice, torClientDeviceLocal));

        anonVpnClients = Set.of(
                createAnonVpnClient(anonVpnWithIp6Interface, anonVpnWithIp6Route, anonVpnWithIp6DeviceId, anonVpnWithIp6Gateway),
                createAnonVpnClient(anonVpnOnlyIp4Interface, anonVpnOnlyIp4Route, anonVpnOnlyIp4DeviceId, null)
        );

        generator.setOwnIpAddress(eBlockerIp);
        generator.setSslEnabled(true);
        generator.setPrefixes(Set.of(prefixHome));

        createTablesAndSimulators(generator);
    }

    @Test
    public void testFilterForward() {
        // HTTP/3 is blocked only for SSL enabled devices
        Assert.assertEquals(Action.reject(), filterForward.udpPacket(sslEnabledDevice, externalHost, 443));
        Assert.assertEquals(Action.returnFromChain(), filterForward.udpPacket(enabledDevice, externalHost, 443));

        // HTTPS via TCP is not blocked:
        Assert.assertEquals(Action.returnFromChain(), filterForward.tcpPacket(sslEnabledDevice, externalHost, 443));
        Assert.assertEquals(Action.returnFromChain(), filterForward.tcpPacket(enabledDevice, externalHost, 443));
    }

    @Test
    public void testFilterBlockIpv6() {
        // Block IPv6 to VPN without IPv6 support:
        Assert.assertEquals(Action.rejectWithTcpReset(), filterForward.tcpPacket(anonVpnOnlyIp4Device, externalHost, 1234));
        Assert.assertEquals(Action.rejectWithTcpReset(), filterInput.tcpPacket(anonVpnOnlyIp4Device, externalHost, 443));

        // Do *not* block IPv6 to Tor (version in Bookworm now supports IPv6):
        Assert.assertEquals(Action.returnFromChain(), filterForward.tcpPacket(torClientDevice, externalHost, 1234));
        Assert.assertEquals(Action.returnFromChain(), filterInput.tcpPacket(torClientDevice, externalHost, 443));

        // Pass IPv6 to VPN with IPv6 support
        Assert.assertEquals(Action.returnFromChain(), filterForward.tcpPacket(anonVpnWithIp6Device, externalHost, 1234));
        Assert.assertEquals(Action.returnFromChain(), filterInput.tcpPacket(anonVpnWithIp6Device, externalHost, 443));

        // Traffic from link-local addresses always passes:
        Assert.assertEquals(Action.returnFromChain(), filterForward.tcpPacket(anonVpnWithIp6DeviceLocal, externalHost, 1234));
        Assert.assertEquals(Action.returnFromChain(), filterForward.tcpPacket(anonVpnOnlyIp4DeviceLocal, externalHost, 1234));
        Assert.assertEquals(Action.returnFromChain(), filterForward.tcpPacket(torClientDeviceLocal, externalHost, 1234));
    }

    @Test
    public void testTorRouting() {
        natPre.setInput(standardInterface);
        natOutput.setOutput(standardInterface);
        filterForward.setInput(standardInterface);

        // HTTP(S) traffic is routed to Squid
        Assert.assertEquals(Action.redirectTo(eBlockerIp, proxyPort), natPre.tcpPacket(torClientDevice, externalHost, 80));
        Assert.assertEquals(Action.redirectTo(eBlockerIp, proxyHTTPSPort), natPre.tcpPacket(torClientDevice, externalHost, 443));

        // Squid marks traffic from Tor enabled devices with 0x100:
        Assert.assertEquals(Action.redirectTo(eBlockerIp, torPort), natOutput.tcpPacket(eBlockerIp, externalHost, 443, Rule.State.NEW, 256));

        // Unmarked packets are *not* redirected to Tor:
        Assert.assertEquals(Action.returnFromChain(), natOutput.tcpPacket(eBlockerIp, externalHost, 443));

        // Other TCP traffic is directly routed to Tor:
        Assert.assertEquals(Action.redirectTo(eBlockerIp, torPort), natPre.tcpPacket(torClientDevice, externalHost, 1234));

        // UDP is not supported by Tor, so not processed in NAT table
        Assert.assertEquals(Action.returnFromChain(), natPre.udpPacket(torClientDevice, externalHost, 1234));

        // ... but it is not forwarded to the router
        Assert.assertEquals(Action.reject(), filterForward.udpPacket(torClientDevice, externalHost, 1234));
    }

    @Test
    public void testMasquerading() {
        natPost.setOutput(standardInterface);

        // enabled device: masquerading
        Assert.assertEquals(Action.masquerade(), natPost.tcpPacket(enabledDevice, externalHost, 1234));

        // disabled device: no masquerading
        Assert.assertEquals(Action.returnFromChain(), natPost.tcpPacket(disabledDevice, externalHost, 1234));
    }

    @Test
    public void testMissingLinkLocalAddress() {
        generator.setOwnIpAddress(null);
        createTablesAndSimulators(generator);
        Assert.assertEquals(Action.returnFromChain(), natPre.tcpPacket(enabledDevice, externalHost, 80));
    }

    private VpnClientState createAnonVpnClient(String anonVpnInterface, int anonVpnRoute, String anonVpnDeviceId, String gateway) {
        VpnClientState client = new VpnClientState();
        client.setState(VpnClientState.State.ACTIVE);
        client.setDevices(Set.of(anonVpnDeviceId));
        client.setVirtualInterfaceName(anonVpnInterface);
        client.setRoute(anonVpnRoute);
        client.setGatewayIp6(gateway);
        return client;
    }

    @Test
    public void testRules() {
        // There are some sanity checks in Rule#toString()
        getAllRules().forEach(rule -> Assert.assertNotNull(rule.toString()));
    }
}
