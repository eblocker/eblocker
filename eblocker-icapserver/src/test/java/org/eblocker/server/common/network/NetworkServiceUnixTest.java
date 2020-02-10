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
import org.eblocker.server.common.data.DhcpRange;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.data.NetworkStateId;
import org.eblocker.server.common.data.TestDeviceFactory;
import org.eblocker.server.common.network.unix.DnsConfiguration;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.network.unix.FirewallConfiguration;
import org.eblocker.server.common.network.unix.IscDhcpServer;
import org.eblocker.server.common.network.unix.NetworkInterfaceConfiguration;
import org.eblocker.server.common.network.unix.NetworkServicesUnix;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.common.system.ScriptRunner;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NetworkServiceUnixTest {
    private NetworkServicesUnix networkService;
    private IscDhcpServer dhcpServer;
    private DataSource dataSource;
    private NetworkInterfaceWrapper networkInterface;
    private DnsConfiguration dnsConfiguration;
    private NetworkInterfaceConfiguration interfaceConfiguration;
    private FirewallConfiguration firewallConfiguration;
    private ScheduledExecutorService executorService;
    private ArpSpoofer arpSpoofer;
    private ScriptRunner scriptRunner;
    private EblockerDnsServer eblockerDnsServer;

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        dnsConfiguration = Mockito.mock(DnsConfiguration.class);
        interfaceConfiguration = Mockito.mock(NetworkInterfaceConfiguration.class);
        dhcpServer = Mockito.mock(IscDhcpServer.class);
        firewallConfiguration = Mockito.mock(FirewallConfiguration.class);
        dnsConfiguration = Mockito.mock(DnsConfiguration.class);
        executorService = Mockito.mock(ScheduledExecutorService.class);
        networkInterface = Mockito.mock(NetworkInterfaceWrapper.class);
        arpSpoofer = Mockito.mock(ArpSpoofer.class);
        scriptRunner = Mockito.mock(ScriptRunner.class);
        eblockerDnsServer = Mockito.mock(EblockerDnsServer.class);

        apply();
    }

    private void apply() {
        networkService = new NetworkServicesUnix(
            dataSource,
            dnsConfiguration,
            interfaceConfiguration,
            dhcpServer,
            firewallConfiguration,
            executorService,
            networkInterface,
            arpSpoofer,
            scriptRunner,
            0L,
            0L,
            "applyNetworkConfigurationCommand",
            "applyFirewallConfigurationCommand",
            "enable_ip6",
            eblockerDnsServer);
    }

    @Test
    public void TestGetCurrentConfiguration () throws IOException {
        TestGetCurrentConfigurationSetup();
        apply();

        NetworkConfiguration config = networkService.getCurrentNetworkConfiguration();
        assertEquals("192.168.0.2", config.getIpAddress());
        assertEquals("192.168.0.1", config.getGateway());
        assertEquals("255.255.255.0", config.getNetworkMask());
        assertTrue(config.isDnsServer());
    }

    @Test
    public void TestGetCurrentConfigurationNoInterface () throws IOException {
        TestGetCurrentConfigurationSetup();

        Mockito.when(networkInterface.getNetworkPrefixLength(IpAddress.parse("192.168.0.2"))).thenReturn(-1);
        apply();

        NetworkConfiguration config = networkService.getCurrentNetworkConfiguration();

        assertEquals("255.255.0.0", config.getNetworkMask());
    }

    @Test
    public void TestGetCurrentConfigurationNoDhcpRange () throws IOException {
        TestGetCurrentConfigurationSetup();

        Mockito.when(dataSource.getDhcpRange()).thenReturn(new DhcpRange(null, null));
        apply();

        NetworkConfiguration config = networkService.getCurrentNetworkConfiguration();

        assertEquals("192.168.0.20", config.getDhcpRangeFirst());
        assertEquals("192.168.0.200", config.getDhcpRangeLast());
    }

    @Test
    public void TestGetCurrentConfigurationDnsAndAutoMode () throws IOException {
        TestGetCurrentConfigurationSetup();

        Mockito.when(eblockerDnsServer.isEnabled()).thenReturn(true);
        Mockito.when(dataSource.getCurrentNetworkState()).thenReturn(NetworkStateId.PLUG_AND_PLAY);
        apply();

        NetworkConfiguration config = networkService.getCurrentNetworkConfiguration();

        assertEquals("192.168.0.1", config.getNameServerPrimary());
        assertNull(config.getNameServerSecondary());
        assertEquals("192.168.0.2", config.getAdvisedNameServer());
    }

    @Test
    public void TestGetCurrentConfigurationNoDnsAndAutoMode () throws IOException {
        TestGetCurrentConfigurationSetup();

        Mockito.when(eblockerDnsServer.isEnabled()).thenReturn(false);
        Mockito.when(dataSource.getCurrentNetworkState()).thenReturn(NetworkStateId.PLUG_AND_PLAY);
        apply();

        NetworkConfiguration config = networkService.getCurrentNetworkConfiguration();

        assertEquals("192.168.0.1", config.getNameServerPrimary());
        assertNull(config.getNameServerSecondary());
        assertEquals("192.168.0.1", config.getAdvisedNameServer());
    }

    @Test
    public void TestGetCurrentConfigurationDnsAndLocalDHCP () throws IOException {
        TestGetCurrentConfigurationSetup();

        Mockito.when(eblockerDnsServer.isEnabled()).thenReturn(true);
        Mockito.when(dataSource.getCurrentNetworkState()).thenReturn(NetworkStateId.LOCAL_DHCP);
        apply();

        NetworkConfiguration config = networkService.getCurrentNetworkConfiguration();

        assertEquals("192.168.0.1", config.getNameServerPrimary());
        assertNull(config.getNameServerSecondary());
        assertEquals("192.168.0.2", config.getAdvisedNameServer());
    }

    @Test
    public void TestGetCurrentConfigurationNoDnsAndLocalDHCP () throws IOException {
        TestGetCurrentConfigurationSetup();

        Mockito.when(eblockerDnsServer.isEnabled()).thenReturn(false);
        Mockito.when(dataSource.getCurrentNetworkState()).thenReturn(NetworkStateId.LOCAL_DHCP);
        apply();

        NetworkConfiguration config = networkService.getCurrentNetworkConfiguration();

        assertEquals("192.168.0.1", config.getNameServerPrimary());
        assertNull(config.getNameServerSecondary());
        assertEquals("192.168.0.1", config.getAdvisedNameServer());
    }

    @Test
    public void TestGetCurrentConfigurationDnsAndExternalDHCP () throws IOException {
        TestGetCurrentConfigurationSetup();

        Mockito.when(eblockerDnsServer.isEnabled()).thenReturn(true);
        Mockito.when(dataSource.getCurrentNetworkState()).thenReturn(NetworkStateId.EXTERNAL_DHCP);
        apply();

        NetworkConfiguration config = networkService.getCurrentNetworkConfiguration();

        assertEquals("192.168.0.1", config.getNameServerPrimary());
        assertNull(config.getNameServerSecondary());
        assertEquals("192.168.0.2", config.getAdvisedNameServer());
    }

    @Test
    public void TestGetCurrentConfigurationNoDnsAndExternal () throws IOException {
        TestGetCurrentConfigurationSetup();

        Mockito.when(eblockerDnsServer.isEnabled()).thenReturn(false);
        Mockito.when(dataSource.getCurrentNetworkState()).thenReturn(NetworkStateId.EXTERNAL_DHCP);
        apply();

        NetworkConfiguration config = networkService.getCurrentNetworkConfiguration();

        assertEquals("192.168.0.1", config.getNameServerPrimary());
        assertNull(config.getNameServerSecondary());
        assertEquals("192.168.0.1", config.getAdvisedNameServer());
    }

    @Test
    public void TestGetCurrentConfigurationVpn() throws IOException {
        TestGetCurrentConfigurationSetup();

        Mockito.when(networkInterface.getVpnIpv4Address()).thenReturn(Ip4Address.parse("10.8.0.1"));
        NetworkConfiguration config = networkService.getCurrentNetworkConfiguration();
        assertEquals("10.8.0.1", config.getVpnIpAddress());
    }

    @Test
    public void TestConfigureDhcp () {
        DeviceService deviceService = Mockito.mock(DeviceService.class);
        TestDeviceFactory tdf = new TestDeviceFactory(deviceService);
        tdf.addDevice("0123456789ab", "10.2.3.4", true);
        tdf.addDevice("caffee001122", "10.2.3.5", false);
        tdf.addDevice("1cc11afedcba", "10.2.3.6", false);
        tdf.commit();
        Mockito.when(dataSource.getDevices()).thenReturn(tdf.getDevices());

        NetworkConfiguration cfg = Mockito.mock(NetworkConfiguration.class);
        Mockito.when(cfg.getIpAddress()).thenReturn("192.168.0.2");
        Mockito.when(cfg.getNetworkMask()).thenReturn("255.255.255.0");
        Mockito.when(cfg.getGateway()).thenReturn("192.168.0.1");

        networkService.configureDhcpServer(cfg);

        ArgumentCaptor<DhcpServerConfiguration> argCaptor = ArgumentCaptor.forClass(DhcpServerConfiguration.class);

        Mockito.verify(dhcpServer).setConfiguration(argCaptor.capture());

        DhcpServerConfiguration actualConfiguration = argCaptor.getValue();
        assertEquals("192.168.0.2", actualConfiguration.getIpAddress());
        assertEquals("255.255.255.0", actualConfiguration.getNetmask());
        assertEquals("192.168.0.1", actualConfiguration.getGateway());
        assertEquals(3, actualConfiguration.getDevices().size());
    }

    @Test
    public void TestConfigureDhcpRange () {
        NetworkConfiguration cfg = Mockito.mock(NetworkConfiguration.class);
        Mockito.when(cfg.getIpAddress()).thenReturn("192.168.0.2");
        Mockito.when(cfg.getDhcpRangeFirst()).thenReturn("192.168.0.100");
        Mockito.when(cfg.getDhcpRangeLast()).thenReturn("192.168.0.200");

        networkService.configureDhcpServer(cfg);

        ArgumentCaptor<DhcpServerConfiguration> argCaptor = ArgumentCaptor.forClass(DhcpServerConfiguration.class);

        Mockito.verify(dhcpServer).setConfiguration(argCaptor.capture());

        DhcpServerConfiguration actualConfiguration = argCaptor.getValue();

        assertEquals("192.168.0.100", actualConfiguration.getRange().getFirstIpAddress());
        assertEquals("192.168.0.200", actualConfiguration.getRange().getLastIpAddress());
    }

    @Test
    public void TestConfigureDhcpDnsEnabled () {
        NetworkConfiguration cfg = Mockito.mock(NetworkConfiguration.class);
        Mockito.when(cfg.isDnsServer()).thenReturn(true);
        Mockito.when(cfg.getIpAddress()).thenReturn("192.168.0.2");

        networkService.configureDhcpServer(cfg);

        ArgumentCaptor<DhcpServerConfiguration> argCaptor = ArgumentCaptor.forClass(DhcpServerConfiguration.class);

        Mockito.verify(dhcpServer).setConfiguration(argCaptor.capture());

        DhcpServerConfiguration actualConfiguration = argCaptor.getValue();

        assertEquals("192.168.0.2", actualConfiguration.getNameServerPrimary());
        assertNull(actualConfiguration.getNameServerSecondary());
    }

    @Test
    public void TestConfigureDhcpDnsDisabled () {
        NetworkConfiguration cfg = Mockito.mock(NetworkConfiguration.class);
        Mockito.when(cfg.isDnsServer()).thenReturn(false);
        Mockito.when(cfg.getNameServerPrimary()).thenReturn("1.2.3.4");
        Mockito.when(cfg.getNameServerSecondary()).thenReturn("1.2.3.5");

        networkService.configureDhcpServer(cfg);

        ArgumentCaptor<DhcpServerConfiguration> argCaptor = ArgumentCaptor.forClass(DhcpServerConfiguration.class);

        Mockito.verify(dhcpServer).setConfiguration(argCaptor.capture());

        DhcpServerConfiguration actualConfiguration = argCaptor.getValue();

        assertEquals("1.2.3.4", actualConfiguration.getNameServerPrimary());
        assertEquals("1.2.3.5", actualConfiguration.getNameServerSecondary());
    }

    private void TestGetCurrentConfigurationSetup() throws IOException {
        Mockito.when(networkInterface.getFirstIPv4Address()).thenReturn(Ip4Address.parse("192.168.0.2"));
        Mockito.when(networkInterface.getNetworkPrefixLength(Ip4Address.parse("192.168.0.2"))).thenReturn(24);
        Mockito.when(dataSource.getDhcpRange()).thenReturn(new DhcpRange("192.168.0.100", "192.168.0.200"));
        Mockito.when(dataSource.getGateway()).thenReturn("192.168.0.1");
        Mockito.when(eblockerDnsServer.isEnabled()).thenReturn(true);
        Mockito.when(dnsConfiguration.getNameserverAddresses()).thenReturn(Arrays.asList("192.168.0.1"));
    }
}
