/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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

import org.eblocker.server.common.data.openvpn.OpenVpnClientState;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

public class TableGeneratorIp4Test extends TableGeneratorTestBase {
    private final String anonVpnInterface = "tun0";

    private final String eBlockerIp = "192.168.1.2";
    private final String fallbackIp = "169.254.94.109";
    private final String anonSourceIp = "169.254.7.53";
    private final String parentalControlRedirectIp = "169.254.93.109";

    private final String disabledDevice = "192.168.1.41";
    private final String enabledDevice = "192.168.1.42";
    private final String sslEnabledDevice = "192.168.1.43";
    private final String mobileVpnDevice = "10.8.0.23";
    private final String mobileVpnLocalAccessDevice = "10.8.0.24";
    private final String anonVpnClientDevice = "192.168.1.44";
    private final String torClientDevice = "192.168.1.45";
    private final String otherLocalDevice = "192.168.1.23";
    private final String externalHost = "4.3.2.1";

    // eBlocker Mobile settings
    private final String mobileVpnIp = "10.8.0.1";
    private final String mobileVpnSubnet = "10.8.0.0";
    private final String mobileVpnNetmask = "255.255.255.0";

    // Setting for anonymization via OpenVPN
    private static final String anonVpnClientDeviceId = "anonVpnClientDeviceId";
    private final String anonVpnEndpointIp = "100.42.23.7";
    private final int anonVpnClientRoute = 1;

    private TableGeneratorIp4 generator;

    @Before
    public void setUp() {
        generator = new TableGeneratorIp4(standardInterface,
                mobileVpnInterface, mobileVpnSubnet, mobileVpnNetmask,
                proxyPort, proxyHTTPSPort,
                anonSocksPort, anonSourceIp,
                13,
                httpPort, httpsPort,
                parentalControlRedirectIp, parentalControlRedirectHttpPort, parentalControlRedirectHttpsPort,
                fallbackIp,
                "malware",
                1194, localDnsPort, 9053);

        deviceIpFilter = Mockito.mock(IpAddressFilter.class);
        Mockito.when(deviceIpFilter.getEnabledDevicesIps()).thenReturn(List.of(enabledDevice, sslEnabledDevice, mobileVpnDevice, mobileVpnLocalAccessDevice, torClientDevice, anonVpnClientDevice));
        Mockito.when(deviceIpFilter.getDisabledDevicesIps()).thenReturn(List.of(disabledDevice));
        Mockito.when(deviceIpFilter.getSslEnabledDevicesIps()).thenReturn(List.of(sslEnabledDevice, mobileVpnDevice, torClientDevice, anonVpnClientDevice));
        Mockito.when(deviceIpFilter.getDevicesIps(Set.of(anonVpnClientDeviceId))).thenReturn(List.of(anonVpnClientDevice));
        Mockito.when(deviceIpFilter.getTorDevicesIps()).thenReturn(List.of(torClientDevice));
        Mockito.when(deviceIpFilter.getMobileVpnDevicesIps()).thenReturn(List.of(mobileVpnDevice, mobileVpnLocalAccessDevice));
        Mockito.when(deviceIpFilter.getMobileVpnDevicesPrivateNetworkAccessIps()).thenReturn(List.of(mobileVpnLocalAccessDevice));

        OpenVpnClientState vpnClient = new OpenVpnClientState();
        vpnClient.setLocalEndpointIp(anonVpnEndpointIp);
        vpnClient.setDevices(Set.of(anonVpnClientDeviceId));
        vpnClient.setState(OpenVpnClientState.State.ACTIVE);
        vpnClient.setVirtualInterfaceName(anonVpnInterface);
        vpnClient.setRoute(anonVpnClientRoute);
        anonVpnClients = Set.of(vpnClient);

        generator.setOwnIpAddress(eBlockerIp);
        generator.setNetworkMask("255.255.255.0");
        generator.setMobileVpnIpAddress(mobileVpnIp);
        generator.setDnsEnabled(true);
        generator.setSslEnabled(true);
        generator.setMasqueradeEnabled(true);
        generator.setMobileVpnServerEnabled(true);

        createTablesAndSimulators(generator);
    }

    @Test
    public void testNatPreRouting() {
        Simulator sim = natPre;
        sim.setInput(standardInterface);

        // HTTP is redirected to Squid
        Assert.assertEquals(Action.redirectTo(eBlockerIp, proxyPort), sim.tcpPacket(enabledDevice, externalHost, 80));

        // ... but not for disabled devices
        Assert.assertEquals(Action.returnFromChain(), sim.tcpPacket(disabledDevice, externalHost, 80));

        // HTTPS is redirected to Squid
        Assert.assertEquals(Action.redirectTo(eBlockerIp, proxyHTTPSPort), sim.tcpPacket(sslEnabledDevice, externalHost, 443));

        // ... but not if SSL is disabled for the device (or the device is disabled)
        Assert.assertEquals(Action.returnFromChain(), sim.tcpPacket(enabledDevice, externalHost, 443));
        Assert.assertEquals(Action.returnFromChain(), sim.tcpPacket(disabledDevice, externalHost, 443));

        // UDP:80 passes through
        Assert.assertEquals(Action.returnFromChain(), sim.udpPacket(enabledDevice, externalHost, 80));

        // DNS sent explicitly to eBlocker is redirected to eblocker-dns
        Assert.assertEquals(Action.redirectTo(eBlockerIp, localDnsPort), sim.udpPacket(enabledDevice, eBlockerIp, 53));

        // DNS from enabled devices to external servers is redirected to eblocker-dns
        Assert.assertEquals(Action.redirectTo(eBlockerIp, localDnsPort), sim.udpPacket(enabledDevice, externalHost, 53));

        // DNS from disabled devices to external servers is also redirected to eblocker-dns
        // FIXME: shouldn't this pass through?
        // (In case traffic is routed to the eBlocker anyway, e.g. if the device is disabled, but still has a DHCP lease from eBlocker)
        Assert.assertEquals(Action.redirectTo(eBlockerIp, localDnsPort), sim.udpPacket(disabledDevice, externalHost, 53));

        // Local network traffic to other devices passes through
        Assert.assertEquals(Action.returnFromChain(), sim.tcpPacket(enabledDevice, otherLocalDevice, 80));
        Assert.assertEquals(Action.returnFromChain(), sim.udpPacket(enabledDevice, otherLocalDevice, 1234));
    }

    @Test
    public void testNatPostRouting() {
        Simulator sim = natPost;
        sim.setOutput(standardInterface);

        // Outgoing traffic is masqueraded
        Assert.assertEquals(Action.masquerade(), sim.tcpPacket(enabledDevice, externalHost, 80));
        Assert.assertEquals(Action.masquerade(), sim.tcpPacket(enabledDevice, externalHost, 443));
        Assert.assertEquals(Action.masquerade(), sim.tcpPacket(enabledDevice, externalHost, 22));
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
    public void testParentalControlRedirect() {
        // Accessing the blocking IP on ports 80 and 443 redirects to 3003 and 3004:
        Assert.assertEquals(Action.redirectTo(parentalControlRedirectIp, parentalControlRedirectHttpPort), natPre.tcpPacket(enabledDevice, parentalControlRedirectIp, 80));
        Assert.assertEquals(Action.redirectTo(parentalControlRedirectIp, parentalControlRedirectHttpsPort), natPre.tcpPacket(enabledDevice, parentalControlRedirectIp, 443));

        // Filter allows access to 3003 and 3004:
        Assert.assertEquals(Action.accept(), filterInput.tcpPacket(enabledDevice, parentalControlRedirectIp, parentalControlRedirectHttpPort));
        Assert.assertEquals(Action.accept(), filterInput.tcpPacket(enabledDevice, parentalControlRedirectIp, parentalControlRedirectHttpsPort));

        // Everything else is filtered:
        Assert.assertEquals(Action.drop(), filterInput.tcpPacket(enabledDevice, parentalControlRedirectIp, 1234));
        Assert.assertEquals(Action.drop(), filterInput.udpPacket(enabledDevice, parentalControlRedirectIp, 1234));
    }

    @Test
    public void testAccessSettings() {
        natPre.setInput(standardInterface);

        // Access settings from local network
        Assert.assertEquals(Action.redirectTo(eBlockerIp, httpPort), natPre.tcpPacket(enabledDevice, eBlockerIp, 80));
        Assert.assertEquals(Action.redirectTo(eBlockerIp, httpsPort), natPre.tcpPacket(enabledDevice, eBlockerIp, 443));

        // Access settings at emergency IP
        Assert.assertEquals(Action.redirectTo(fallbackIp, httpPort), natPre.tcpPacket(enabledDevice, fallbackIp, 80));
        Assert.assertEquals(Action.redirectTo(fallbackIp, httpsPort), natPre.tcpPacket(enabledDevice, fallbackIp, 443));

        // Access settings remotely
        natPre.setInput(mobileVpnInterface);
        Assert.assertEquals(Action.redirectTo(mobileVpnIp, httpPort), natPre.tcpPacket(mobileVpnDevice, mobileVpnIp, 80));
        Assert.assertEquals(Action.redirectTo(mobileVpnIp, httpsPort), natPre.tcpPacket(mobileVpnDevice, mobileVpnIp, 443));
    }

    @Test
    public void testTorRouting() {
        natPre.setInput(standardInterface);
        natOutput.setOutput(standardInterface);
        filterForward.setInput(standardInterface);

        // HTTP(S) traffic is routed to Squid
        Assert.assertEquals(Action.redirectTo(eBlockerIp, proxyPort), natPre.tcpPacket(torClientDevice, externalHost, 80));
        Assert.assertEquals(Action.redirectTo(eBlockerIp, proxyHTTPSPort), natPre.tcpPacket(torClientDevice, externalHost, 443));

        // Tor traffic from Squid uses a special IP address which is redirected to redsocks:
        Assert.assertEquals(Action.redirectTo(eBlockerIp, anonSocksPort), natOutput.tcpPacket(anonSourceIp, externalHost, 1234));

        // Other TCP traffic is directly routed to Tor via redsocks
        Assert.assertEquals(Action.redirectTo(eBlockerIp, anonSocksPort), natPre.tcpPacket(torClientDevice, externalHost, 1234));

        // UDP is not supported by Tor, so not processed in NAT table
        Assert.assertEquals(Action.returnFromChain(), natPre.udpPacket(torClientDevice, externalHost, 1234));

        // ... but it is not forwarded to the router
        Assert.assertEquals(Action.reject(), filterForward.udpPacket(torClientDevice, externalHost, 1234));
    }

    @Test
    public void testMobileVpn() {
        natPre.setInput(mobileVpnInterface);
        natPost.setOutput(standardInterface);
        filterForward.setInput(mobileVpnInterface);

        // Outgoing traffic from mobile clients is masqueraded
        Assert.assertEquals(Action.masquerade(), natPost.tcpPacket(mobileVpnDevice, externalHost, 1234));

        // mobile device with local access:
        Assert.assertEquals(Action.accept(), filterForward.tcpPacket(mobileVpnLocalAccessDevice, otherLocalDevice, 1234));
        Assert.assertEquals(Action.accept(), filterForward.udpPacket(mobileVpnLocalAccessDevice, otherLocalDevice, 1234));

        // mobile device without local access:
        Assert.assertEquals(Action.reject(), filterForward.tcpPacket(mobileVpnDevice, otherLocalDevice, 1234));
        Assert.assertEquals(Action.reject(), filterForward.udpPacket(mobileVpnDevice, otherLocalDevice, 1234));

        // ports 80 and 443 are redirected to Squid:
        Assert.assertEquals(Action.redirectTo(mobileVpnIp, proxyPort), natPre.tcpPacket(mobileVpnDevice, externalHost, 80));
        Assert.assertEquals(Action.redirectTo(mobileVpnIp, proxyHTTPSPort), natPre.tcpPacket(mobileVpnDevice, externalHost, 443));
    }

    @Test
    public void testAnonVpn() {
        natPre.setInput(standardInterface);

        // ports 80 and 443 are redirected to Squid:
        Assert.assertEquals(Action.redirectTo(eBlockerIp, proxyPort), natPre.tcpPacket(anonVpnClientDevice, externalHost, 80));
        Assert.assertEquals(Action.redirectTo(eBlockerIp, proxyHTTPSPort), natPre.tcpPacket(anonVpnClientDevice, externalHost, 443));

        // Outgoing traffic from VPN clients is masqueraded
        natPost.setOutput(anonVpnInterface);
        Assert.assertEquals(Action.masquerade(), natPost.tcpPacket(anonVpnClientDevice, externalHost, 80));

        // packets are marked for VPN routing
        Assert.assertEquals(Action.mark(anonVpnClientRoute), mangleVpn.tcpPacket(anonVpnClientDevice, externalHost, 1234));
        Assert.assertEquals(Action.mark(anonVpnClientRoute), mangleVpn.udpPacket(anonVpnClientDevice, externalHost, 1234));

        // packets from other devices are not marked:
        Assert.assertEquals(Action.returnFromChain(), mangleVpn.tcpPacket(enabledDevice, externalHost, 1234));

        // eblocker-dns binds to the VPN tunnel's endpoint IP for outgoing DNS packets.
        // If the destination IP (i.e. the DNS server) is not within the tunnel interface's IP range, the packets must also be marked.
        // Otherwise they do not go into the tunnel but take the default route.
        Assert.assertEquals(Action.mark(anonVpnClientRoute), mangleOutput.udpPacket(anonVpnEndpointIp, externalHost, 53));
    }

    @Test
    public void testAccessFromPublicAddresses() {
        filterInput.setInput(standardInterface);

        // Filter allows access from private IPs
        Assert.assertEquals(Action.accept(), filterInput.tcpPacket(enabledDevice, eBlockerIp, 1234));
        Assert.assertEquals(Action.accept(), filterInput.udpPacket(enabledDevice, eBlockerIp, 1234));

        // Filter denies access from public IPs opening new connections...
        Assert.assertEquals(Action.drop(), filterInput.tcpPacket(externalHost, eBlockerIp, 1234, Rule.State.NEW));
        Assert.assertEquals(Action.drop(), filterInput.udpPacket(externalHost, eBlockerIp, 1234, Rule.State.NEW));

        // ... except for eBlocker Mobile:
        Assert.assertEquals(Action.accept(), filterInput.udpPacket(externalHost, eBlockerIp, 1194, Rule.State.NEW));

        // But eBlocker may establish connections to public addresses and get responses:
        Assert.assertEquals(Action.accept(), filterInput.tcpPacket(externalHost, eBlockerIp, 1234, Rule.State.ESTABLISHED));
        Assert.assertEquals(Action.accept(), filterInput.udpPacket(externalHost, eBlockerIp, 1234, Rule.State.ESTABLISHED));
    }

    @Test
    public void testRules() {
        // There are some sanity checks in Rule#toString()
        getAllRules().forEach(rule -> Assert.assertNotNull(rule.toString()));
    }
}
