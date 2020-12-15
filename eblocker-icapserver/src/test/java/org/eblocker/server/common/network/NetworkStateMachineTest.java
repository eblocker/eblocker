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
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.data.NetworkStateId;
import org.eblocker.server.common.data.events.Event;
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.data.events.EventType;
import org.eblocker.server.common.network.unix.DnsEnableByDefaultChecker;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.network.unix.IpSets;
import org.eblocker.server.common.service.FeatureToggleRouter;
import org.eblocker.server.common.ssl.SslService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NetworkStateMachineTest {
    private NetworkStateMachine machine;
    private NetworkServices services;
    private DataSource dataSource;
    private DnsEnableByDefaultChecker dnsChecker;
    private EventLogger eventLogger;
    private FeatureToggleRouter featureToggleRouter;
    private IpSets ipSets;
    private SslService sslService;
    private EblockerDnsServer dnsServer;
    private boolean enableSSL = true;

    @Before
    public void setUp() throws Exception {
        services = Mockito.mock(NetworkServices.class);
        dataSource = Mockito.mock(DataSource.class);
        dnsChecker = Mockito.mock(DnsEnableByDefaultChecker.class);
        eventLogger = Mockito.mock(EventLogger.class);
        sslService = Mockito.mock(SslService.class);
        featureToggleRouter = Mockito.mock(FeatureToggleRouter.class);
        ipSets = Mockito.mock(IpSets.class);
        dnsServer = Mockito.mock(EblockerDnsServer.class);
        machine = new NetworkStateMachine(services, dataSource, dnsChecker, eventLogger, featureToggleRouter, ipSets, sslService, dnsServer);

        when(dataSource.getSSLEnabledState()).thenReturn(true);
    }

    @Test
    public void enableArpSpoofingWhenBootingInPlugAndPlayState() {
        when(dataSource.getCurrentNetworkState()).thenReturn(NetworkStateId.PLUG_AND_PLAY);
        machine.initialize();
        verify(services).enableArpSpoofer();
    }

    @Test
    public void notEnableArpSpoofingWhenBootingInNonPnpState() {
        when(dataSource.getCurrentNetworkState()).thenReturn(NetworkStateId.LOCAL_DHCP);
        machine.initialize();
        verify(services, never()).enableArpSpoofer();
    }

    @Test
    public void testBootEnableDnsCheck() {
        Mockito.when(dataSource.getCurrentNetworkState()).thenReturn(NetworkStateId.PLUG_AND_PLAY);
        machine.initialize();
        Mockito.verify(dnsChecker).check();
    }

    @Test
    public void transitionFromExternalDhcpToPlugAndPlay() {
        initializeToExternalDhcpState();
        assertTrue(machine.updateConfiguration(defaultPlugAndPlayConfiguration()));
        verify(services).enableArpSpoofer();
        verify(services).enableDhcpClient();
        verify(dnsServer, never()).setDnsCustomResolver();
        ArgumentCaptor<Event> argCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventLogger).log(argCaptor.capture());

        assertEquals(EventType.NETWORK_MODE_PNP, argCaptor.getValue().getType());
    }

    @Test
    public void transitionFromExternalDhcpToLocalDhcp() {
        initializeToExternalDhcpState();
        NetworkConfiguration cfg = defaultLocalDhcpConfiguration();
        assertTrue(machine.updateConfiguration(cfg));
        verify(services).enableDhcpServer(false);
        verify(services).enableStaticIp(cfg);
        verify(dnsServer).setDnsCustomResolver();
        ArgumentCaptor<Event> argCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventLogger).log(argCaptor.capture());

        assertEquals(EventType.NETWORK_MODE_LOCAL_DHCP, argCaptor.getValue().getType());
    }

    @Test
    public void transitionFromPlugAndPlayToExternalDhcp() {
        initializeToPlugAndPlayState();
        NetworkConfiguration cfg = defaultExternalDhcpConfiguration();
        assertTrue(machine.updateConfiguration(cfg));
        verify(services).disableArpSpoofer();
        verify(services).enableStaticIp(cfg);
        verify(dnsServer, never()).setDnsCustomResolver();
        ArgumentCaptor<Event> argCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventLogger).log(argCaptor.capture());

        assertEquals(EventType.NETWORK_MODE_EXTERNAL_DHCP, argCaptor.getValue().getType());
    }

    @Test
    public void transitionFromPlugAndPlayToLocalDhcp() {
        initializeToPlugAndPlayState();
        NetworkConfiguration cfg = defaultLocalDhcpConfiguration();
        assertTrue(machine.updateConfiguration(cfg));
        verify(services).disableArpSpoofer();
        verify(services).enableDhcpServer(false);
        verify(services).enableStaticIp(cfg);
        verify(dnsServer).setDnsCustomResolver();
        ArgumentCaptor<Event> argCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventLogger).log(argCaptor.capture());

        assertEquals(EventType.NETWORK_MODE_LOCAL_DHCP, argCaptor.getValue().getType());
    }

    @Test
    public void transitionFromLocalDhcpToPlugAndPlay() {
        initializeToLocalDhcpState();
        assertTrue(machine.updateConfiguration(defaultPlugAndPlayConfiguration()));
        verify(services).disableDhcpServer();
        verify(services).enableDhcpClient();
        verify(services).enableArpSpoofer();
        verify(dnsServer, never()).setDnsCustomResolver();
        ArgumentCaptor<Event> argCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventLogger).log(argCaptor.capture());

        assertEquals(EventType.NETWORK_MODE_PNP, argCaptor.getValue().getType());
    }

    @Test
    public void transitionFromLocalDhcpToExternalDhcp() {
        initializeToLocalDhcpState();
        NetworkConfiguration cfg = defaultExternalDhcpConfiguration();
        assertTrue(machine.updateConfiguration(cfg));
        verify(services).disableDhcpServer();
        verify(services).enableStaticIp(cfg);
        verify(dnsServer, never()).setDnsCustomResolver();
        ArgumentCaptor<Event> argCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventLogger).log(argCaptor.capture());

        assertEquals(EventType.NETWORK_MODE_EXTERNAL_DHCP, argCaptor.getValue().getType());
    }

    @Test
    public void transitionLocalDhcp() {
        initializeToLocalDhcpState();
        NetworkConfiguration cfg = defaultLocalDhcpConfiguration();
        cfg.setNameServerSecondary("192.168.3.21");
        //FIXME
        //assertTrue(machine.updateConfiguration(cfg));
        machine.updateConfiguration(cfg);
        verify(services).enableStaticIp(cfg);

        verify(services, never()).disableDhcpServer();
        verify(services, never()).enableDhcpServer(Mockito.anyBoolean());
        verify(services).configureDhcpServer(cfg);
        verify(dnsServer).setDnsCustomResolver();
    }

    @Test
    public void transitionExternalDhcp() {
        initializeToExternalDhcpState();
        NetworkConfiguration cfg = defaultExternalDhcpConfiguration();
        cfg.setIpAddress("192.168.0.3");
        assertTrue(machine.updateConfiguration(cfg));
        verify(services).enableStaticIp(cfg);
    }

    @Test
    public void transitionNoConfigurationChange() {
        initializeToExternalDhcpState();
        NetworkConfiguration cfg = defaultExternalDhcpConfiguration();
        //FIXME
        //assertFalse(machine.updateConfiguration(cfg));
        verify(dnsServer, never()).setDnsCustomResolver();
        verify(services, never()).enableStaticIp(cfg);
    }

    @Test
    public void changeDeviceStateLocalDhcp() {
        // When a device is put on/removed from the whitelist
        initializeToLocalDhcpState();
        machine.deviceStateChanged();
        verify(services).enableFirewall(true, enableSSL, false, false);
        NetworkConfiguration cfg = defaultLocalDhcpConfiguration();
        verify(services).configureDhcpServer(cfg);
    }

    @Test
    public void changeDeviceStateExternalDhcp() {
        // When a device is put on/removed from the whitelist
        initializeToExternalDhcpState();
        machine.deviceStateChanged();
        verify(services).enableFirewall(true, enableSSL, false, false);
        NetworkConfiguration cfg = defaultExternalDhcpConfiguration();
        verify(services, never()).configureDhcpServer(cfg);
        verify(dnsServer, never()).setDnsCustomResolver();
    }

    private NetworkConfiguration defaultExternalDhcpConfiguration() {
        NetworkConfiguration cfg = defaultConfiguration();
        cfg.setAutomatic(false);
        cfg.setDhcp(false);
        return cfg;
    }

    private NetworkConfiguration defaultLocalDhcpConfiguration() {
        NetworkConfiguration cfg = defaultConfiguration();
        cfg.setAutomatic(false);
        cfg.setDhcp(true);
        return cfg;
    }

    private NetworkConfiguration defaultPlugAndPlayConfiguration() {
        NetworkConfiguration cfg = defaultConfiguration();
        cfg.setAutomatic(true);
        return cfg;
    }

    private NetworkConfiguration defaultConfiguration() {
        NetworkConfiguration cfg = new NetworkConfiguration();
        cfg.setIpAddress("192.168.0.2");
        cfg.setNetworkMask("255.255.255.0");
        cfg.setGateway("192.168.0.1");
        cfg.setNameServerPrimary("192.168.3.20");
        cfg.setDhcpRangeFirst("192.168.0.100");
        cfg.setDhcpRangeLast("192.168.0.199");
        return cfg;
    }

    private void initializeToExternalDhcpState() {
        when(dataSource.getCurrentNetworkState()).thenReturn(NetworkStateId.EXTERNAL_DHCP);
        when(services.getCurrentNetworkConfiguration()).thenReturn(defaultExternalDhcpConfiguration());
    }

    private void initializeToLocalDhcpState() {
        when(dataSource.getCurrentNetworkState()).thenReturn(NetworkStateId.LOCAL_DHCP);
        when(services.getCurrentNetworkConfiguration()).thenReturn(defaultLocalDhcpConfiguration());
    }

    private void initializeToPlugAndPlayState() {
        when(dataSource.getCurrentNetworkState()).thenReturn(NetworkStateId.PLUG_AND_PLAY);
        when(services.getCurrentNetworkConfiguration()).thenReturn(defaultPlugAndPlayConfiguration());
    }
}
