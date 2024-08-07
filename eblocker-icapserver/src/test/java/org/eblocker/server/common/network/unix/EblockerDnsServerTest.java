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
package org.eblocker.server.common.network.unix;

import com.google.common.collect.Sets;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.data.NetworkStateId;
import org.eblocker.server.common.data.dns.DnsResolvers;
import org.eblocker.server.common.data.dns.DnsServerConfig;
import org.eblocker.server.common.data.dns.EblockerDnsServerState;
import org.eblocker.server.common.data.dns.LocalDnsRecord;
import org.eblocker.server.common.data.dns.NameServer;
import org.eblocker.server.common.data.dns.ResolverConfig;
import org.eblocker.server.common.network.DhcpBindListener;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.eblocker.server.common.network.RouterAdvertisementCache;
import org.eblocker.server.common.network.icmpv6.Option;
import org.eblocker.server.common.network.icmpv6.RecursiveDnsServerOption;
import org.eblocker.server.common.network.icmpv6.RouterAdvertisement;
import org.eblocker.server.common.pubsub.Channels;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.common.TestClock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EblockerDnsServerTest {
    private final String FLUSH_COMMAND = "unit-test-flush-command";
    private final String UPDATE_COMMAND = "unit-test-update-command";
    private final String DEFAULT_LOCAL_NAMES = "ns.unit.test, unit.test";
    private final String DEFAULT_CUSTOM_NAME_SERVERS = "10.10.10.200, 192.168.3.20";
    private final String DEFAULT_TOR_NAME_SERVERS = "udp:127.0.0.1:9053";
    private final String CONTROL_BAR_HOST_NAME = "controlbar.test-eblocker.com";
    private final Ip4Address CONTROL_BAR_IP_ADDRESS = Ip4Address.parse("222.222.222.222");
    private final String VPN_SUBNET_IP = "10.8.0.0";
    private final String VPN_SUBNET_NETMASK = "255.255.255.0";
    private final Ip4Address LOCAL_VPN_IP = Ip4Address.parse("10.8.0.1");

    private Path eblockerFlagFilePath;
    private TestClock clock;
    private DataSource dataSource;
    private DeviceService deviceService;
    private DhcpBindListener dhcpBindListener;
    private DhcpClientLeaseReader clientLeaseReader;
    private NetworkInterfaceWrapper networkInterfaceWrapper;
    private PubSubService pubSubService;
    private RouterAdvertisementCache routerAdvertisementCache;
    private EblockerDnsServer eblockerDnsServer;
    private EblockerDnsServer.Listener listener;

    private Device device;

    @Before
    public void setup() throws IOException {
        eblockerFlagFilePath = Files.createTempFile("eblocker-dns", "enabled");
        Files.delete(eblockerFlagFilePath);

        clock = new TestClock(LocalDateTime.of(2019, 3, 13, 13, 38));
        pubSubService = Mockito.mock(PubSubService.class);

        dataSource = Mockito.mock(DataSource.class);
        Mockito.when(dataSource.getCurrentNetworkState()).thenReturn(NetworkStateId.EXTERNAL_DHCP);

        device = createDevice("id", "192.168.3.3");
        deviceService = Mockito.mock(DeviceService.class);
        Mockito.when(deviceService.getDeviceById(device.getId())).thenReturn(device);

        dhcpBindListener = Mockito.mock(DhcpBindListener.class);

        clientLeaseReader = Mockito.mock(DhcpClientLeaseReader.class);
        Mockito.when(clientLeaseReader.readLease()).thenReturn(
                new DhcpClientLease("eth0",
                        "10.10.10.100",
                        Collections.singletonMap("domain-name-servers", "192.168.3.1, 192.168.3.2, 0.0.0.0")));

        networkInterfaceWrapper = Mockito.mock(NetworkInterfaceWrapper.class);
        Mockito.when(networkInterfaceWrapper.getFirstIPv4Address()).thenReturn(Ip4Address.parse("10.10.10.100"));
        Mockito.when(networkInterfaceWrapper.getVpnIpv4Address()).thenReturn(Ip4Address.parse("10.8.0.1"));

        routerAdvertisementCache = Mockito.mock(RouterAdvertisementCache.class);
        Mockito.when(routerAdvertisementCache.getEntries()).thenReturn(Collections.singletonList(
                createRouterAdvertisementEntry(clock.millis(), 86400, 86400, Ip6Address.parse("fe80::192:168:3:1"))
        ));

        listener = Mockito.mock(EblockerDnsServer.Listener.class);
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(eblockerFlagFilePath);
    }

    @Test
    public void testEnableDefaultConfigCreationDhcp() {
        Mockito.when(dataSource.getCurrentNetworkState()).thenReturn(NetworkStateId.PLUG_AND_PLAY);
        setupDnsServer();

        NetworkConfiguration networkConfiguration = new NetworkConfiguration();
        networkConfiguration.setAutomatic(true);

        eblockerDnsServer.enable(networkConfiguration);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(dataSource, Mockito.times(3)).save(captor.capture());

        DnsServerConfig config = filterLast(captor, DnsServerConfig.class);
        Assert.assertNotNull(config);
        Assert.assertEquals("dhcp", config.getDefaultResolver());
        Assert.assertNotNull(config.getResolverConfigs());

        // check custom-resolver
        Assert.assertNotNull(config.getResolverConfigs().get("custom"));
        Assert.assertNull(config.getResolverConfigs().get("custom").getOptions().get(ResolverConfig.OPTION_KEY_ORDER));
        Assert.assertEquals(Arrays.asList(new NameServer(NameServer.Protocol.UDP, IpAddress.parse("10.10.10.200"), 53),
                new NameServer(NameServer.Protocol.UDP, IpAddress.parse("192.168.3.20"), 53)),
                config.getResolverConfigs().get("custom").getNameServers());

        // check tor-resolver
        Assert.assertNotNull(config.getResolverConfigs().get("tor"));
        Assert.assertNull(config.getResolverConfigs().get("tor").getOptions().get(ResolverConfig.OPTION_KEY_ORDER));
        Assert.assertEquals(Arrays.asList(new NameServer(NameServer.Protocol.UDP, IpAddress.parse("127.0.0.1"), 9053)),
                config.getResolverConfigs().get("tor").getNameServers());

        // check dhcp-resolver
        Assert.assertNotNull(config.getResolverConfigs().get("dhcp"));
        Assert.assertNull(config.getResolverConfigs().get("dhcp").getOptions().get(ResolverConfig.OPTION_KEY_ORDER));
        Assert.assertEquals(Arrays.asList(new NameServer(NameServer.Protocol.UDP, IpAddress.parse("192.168.3.1"), 53),
                new NameServer(NameServer.Protocol.UDP, IpAddress.parse("192.168.3.2"), 53)),
                config.getResolverConfigs().get("dhcp").getNameServers());

        // check local entires
        Assert.assertNotNull(config.getLocalDnsRecords());
        Assert.assertEquals(3, config.getLocalDnsRecords().size());
        Assert.assertTrue(config.getLocalDnsRecords().get(0).isBuiltin());
        Assert.assertFalse(config.getLocalDnsRecords().get(0).isHidden());
        Assert.assertEquals("ns.unit.test", config.getLocalDnsRecords().get(0).getName());
        Assert.assertEquals(Ip4Address.parse("10.10.10.100"), config.getLocalDnsRecords().get(0).getIpAddress());
        Assert.assertTrue(config.getLocalDnsRecords().get(1).isBuiltin());
        Assert.assertFalse(config.getLocalDnsRecords().get(1).isHidden());
        Assert.assertEquals("unit.test", config.getLocalDnsRecords().get(1).getName());
        Assert.assertEquals(Ip4Address.parse("10.10.10.100"), config.getLocalDnsRecords().get(1).getIpAddress());
        Assert.assertTrue(config.getLocalDnsRecords().get(2).isBuiltin());
        Assert.assertTrue(config.getLocalDnsRecords().get(2).isHidden());
        Assert.assertEquals(CONTROL_BAR_HOST_NAME, config.getLocalDnsRecords().get(2).getName());
        Assert.assertEquals(CONTROL_BAR_IP_ADDRESS, config.getLocalDnsRecords().get(2).getIpAddress());

        EblockerDnsServerState state = filterLast(captor, EblockerDnsServerState.class);
        Assert.assertNotNull(state);
        Assert.assertTrue(state.isEnabled());
        Mockito.verify(pubSubService).publish(Channels.DNS_CONFIG, UPDATE_COMMAND);
    }

    @Test
    public void testEnableDefaultConfigCreation() {
        setupDnsServer();

        NetworkConfiguration networkConfiguration = new NetworkConfiguration();
        networkConfiguration.setAutomatic(false);
        networkConfiguration.setNameServerPrimary("10.10.10.10");
        networkConfiguration.setNameServerSecondary("192.168.3.20");
        eblockerDnsServer.enable(networkConfiguration);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(dataSource, Mockito.times(3)).save(captor.capture());

        DnsServerConfig config = filterLast(captor, DnsServerConfig.class);
        Assert.assertNotNull(config);
        Assert.assertEquals("custom", config.getDefaultResolver());
        Assert.assertNotNull(config.getResolverConfigs());

        // check custom-resolver
        Assert.assertNotNull(config.getResolverConfigs().get("custom"));
        Assert.assertNull(config.getResolverConfigs().get("custom").getOptions().get(ResolverConfig.OPTION_KEY_ORDER));
        Assert.assertEquals(Arrays.asList(new NameServer(NameServer.Protocol.UDP, IpAddress.parse("10.10.10.10"), 53),
                new NameServer(NameServer.Protocol.UDP, IpAddress.parse("192.168.3.20"), 53)),
                config.getResolverConfigs().get("custom").getNameServers());

        // check tor-resolver
        Assert.assertNotNull(config.getResolverConfigs().get("tor"));
        Assert.assertNull(config.getResolverConfigs().get("tor").getOptions().get(ResolverConfig.OPTION_KEY_ORDER));
        Assert.assertEquals(Arrays.asList(new NameServer(NameServer.Protocol.UDP, IpAddress.parse("127.0.0.1"), 9053)),
                config.getResolverConfigs().get("tor").getNameServers());

        // check dhcp-resolver
        Assert.assertNull(config.getResolverConfigs().get("dhcp"));

        // check local entries
        Assert.assertNotNull(config.getLocalDnsRecords());
        Assert.assertEquals(3, config.getLocalDnsRecords().size());
        Assert.assertTrue(config.getLocalDnsRecords().get(0).isBuiltin());
        Assert.assertFalse(config.getLocalDnsRecords().get(0).isHidden());
        Assert.assertEquals("ns.unit.test", config.getLocalDnsRecords().get(0).getName());
        Assert.assertEquals(Ip4Address.parse("10.10.10.100"), config.getLocalDnsRecords().get(0).getIpAddress());
        Assert.assertTrue(config.getLocalDnsRecords().get(1).isBuiltin());
        Assert.assertFalse(config.getLocalDnsRecords().get(1).isHidden());
        Assert.assertEquals("unit.test", config.getLocalDnsRecords().get(1).getName());
        Assert.assertEquals(Ip4Address.parse("10.10.10.100"), config.getLocalDnsRecords().get(1).getIpAddress());
        Assert.assertTrue(config.getLocalDnsRecords().get(2).isBuiltin());
        Assert.assertTrue(config.getLocalDnsRecords().get(2).isHidden());
        Assert.assertEquals(CONTROL_BAR_HOST_NAME, config.getLocalDnsRecords().get(2).getName());
        Assert.assertEquals(CONTROL_BAR_IP_ADDRESS, config.getLocalDnsRecords().get(2).getIpAddress());

        EblockerDnsServerState state = filterLast(captor, EblockerDnsServerState.class);
        Assert.assertTrue(state.isEnabled());
        Mockito.verify(pubSubService).publish(Channels.DNS_CONFIG, UPDATE_COMMAND);
    }

    @Test
    public void testEnableDefaultConfigCreationWithMisconfiguredResolvConf() {
        setupDnsServer();

        NetworkConfiguration networkConfiguration = new NetworkConfiguration();
        networkConfiguration.setAutomatic(false);
        networkConfiguration.setNameServerPrimary("127.0.0.1");
        networkConfiguration.setNameServerSecondary("192.168.3.20");
        eblockerDnsServer.enable(networkConfiguration);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(dataSource, Mockito.times(3)).save(captor.capture());

        DnsServerConfig config = filterLast(captor, DnsServerConfig.class);
        Assert.assertNotNull(config);
        Assert.assertEquals("custom", config.getDefaultResolver());
        Assert.assertNotNull(config.getResolverConfigs());

        // check custom-resolver
        Assert.assertNotNull(config.getResolverConfigs().get("custom"));
        Assert.assertNull(config.getResolverConfigs().get("custom").getOptions().get(ResolverConfig.OPTION_KEY_ORDER));
        Assert.assertEquals(1, config.getResolverConfigs().get("custom").getNameServers().size());
    }

    @Test
    public void testAlreadyEnabled() {
        // setup currently stored config
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(new DnsServerConfig());

        // setup state mock
        EblockerDnsServerState state = new EblockerDnsServerState();
        state.setEnabled(true);
        state.setResolverByDeviceId(Collections.emptyMap());
        Mockito.when(dataSource.get(EblockerDnsServerState.class)).thenReturn(state);

        setupDnsServer();
        Mockito.reset(dataSource);

        eblockerDnsServer.enable(new NetworkConfiguration());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(ArgumentCaptor.class);
        Mockito.verify(dataSource, Mockito.never()).save(captor.capture());
        Mockito.verifyNoInteractions(listener);
    }

    @Test
    public void testEnable() {
        setupDnsServer();
        Mockito.reset(dataSource);

        Assert.assertFalse(Files.exists(eblockerFlagFilePath));
        eblockerDnsServer.enable(new NetworkConfiguration());

        Assert.assertTrue(eblockerDnsServer.isEnabled());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(dataSource, Mockito.atLeastOnce()).save(captor.capture());

        EblockerDnsServerState state = filterLast(captor, EblockerDnsServerState.class);
        Assert.assertTrue(state.isEnabled());
        Assert.assertTrue(Files.exists(eblockerFlagFilePath));
        Mockito.verify(listener).onEnable(true);
    }

    @Test
    public void testEnableAutomaticRestoreTor() {
        setupDnsServer();
        Mockito.reset(dataSource);

        // Previously used configuration - resolve via TOR
        DnsServerConfig dnsServerConfig = new DnsServerConfig();
        dnsServerConfig.setDefaultResolver(EblockerDnsServer.RESOLVER_TOR);
        dnsServerConfig.setResolverConfigs(new HashMap<>());
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(dnsServerConfig);

        Assert.assertFalse(Files.exists(eblockerFlagFilePath));

        NetworkConfiguration networkConfiguration = new NetworkConfiguration();
        networkConfiguration.setAutomatic(true);
        eblockerDnsServer.enable(networkConfiguration);

        Assert.assertTrue(eblockerDnsServer.isEnabled());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(dataSource, Mockito.atLeastOnce()).save(captor.capture());

        EblockerDnsServerState state = filterLast(captor, EblockerDnsServerState.class);
        Assert.assertEquals(EblockerDnsServer.RESOLVER_TOR, ((DnsServerConfig) captor.getValue()).getDefaultResolver());

        // Lease has been read and used to update the config
        Mockito.verify(clientLeaseReader).readLease();
        Assert.assertTrue(dnsServerConfig.getResolverConfigs().containsKey(EblockerDnsServer.RESOLVER_DHCP));
        ResolverConfig resolverConfig = dnsServerConfig.getResolverConfigs().get(EblockerDnsServer.RESOLVER_DHCP);
        Assert.assertEquals(2, resolverConfig.getNameServers().size());
        Assert.assertTrue(resolverConfig.getNameServers()
                .contains(new NameServer(NameServer.Protocol.UDP, Ip4Address.parse("192.168.3.1"), 53)));
        Assert.assertTrue(resolverConfig.getNameServers()
                .contains(new NameServer(NameServer.Protocol.UDP, Ip4Address.parse("192.168.3.2"), 53)));

        Assert.assertTrue(state.isEnabled());
        Assert.assertTrue(Files.exists(eblockerFlagFilePath));
        Mockito.verify(listener).onEnable(true);
    }

    @Test
    public void testEnableManualRestoreTor() {
        setupDnsServer();
        Mockito.reset(dataSource);

        // Previously used configuration - resolve via TOR
        DnsServerConfig dnsServerConfig = new DnsServerConfig();
        dnsServerConfig.setDefaultResolver(EblockerDnsServer.RESOLVER_TOR);
        dnsServerConfig.setResolverConfigs(new HashMap<>());
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(dnsServerConfig);

        Assert.assertFalse(Files.exists(eblockerFlagFilePath));

        NetworkConfiguration networkConfiguration = new NetworkConfiguration();
        networkConfiguration.setAutomatic(false);
        networkConfiguration.setNameServerPrimary("192.168.3.4");
        networkConfiguration.setNameServerSecondary("192.168.3.5");
        eblockerDnsServer.enable(networkConfiguration);

        Assert.assertTrue(eblockerDnsServer.isEnabled());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(dataSource, Mockito.atLeastOnce()).save(captor.capture());

        EblockerDnsServerState state = filterLast(captor, EblockerDnsServerState.class);
        Assert.assertEquals(EblockerDnsServer.RESOLVER_TOR, ((DnsServerConfig) captor.getValue()).getDefaultResolver());

        // Lease has not been read and not used to update the config
        Mockito.verify(clientLeaseReader, Mockito.never()).readLease();
        Assert.assertFalse(dnsServerConfig.getResolverConfigs().containsKey(EblockerDnsServer.RESOLVER_DHCP));
        Assert.assertTrue(dnsServerConfig.getResolverConfigs().containsKey(EblockerDnsServer.RESOLVER_CUSTOM));
        ResolverConfig resolverConfig = dnsServerConfig.getResolverConfigs().get(EblockerDnsServer.RESOLVER_CUSTOM);
        Assert.assertTrue(resolverConfig.getNameServers()
                .contains(new NameServer(NameServer.Protocol.UDP, Ip4Address.parse("192.168.3.4"), 53)));
        Assert.assertTrue(resolverConfig.getNameServers()
                .contains(new NameServer(NameServer.Protocol.UDP, Ip4Address.parse("192.168.3.5"), 53)));

        Assert.assertTrue(state.isEnabled());
        Assert.assertTrue(Files.exists(eblockerFlagFilePath));
        Mockito.verify(listener).onEnable(true);
    }

    @Test
    public void testDisable() throws IOException {
        // setup currently stored config
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(new DnsServerConfig());

        // setup state mock
        EblockerDnsServerState state = new EblockerDnsServerState();
        state.setEnabled(true);
        state.setResolverByDeviceId(new HashMap<>());
        Mockito.when(dataSource.get(EblockerDnsServerState.class)).thenReturn(state);

        Files.createFile(eblockerFlagFilePath);
        setupDnsServer();

        eblockerDnsServer.disable();

        Assert.assertFalse(eblockerDnsServer.isEnabled());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(dataSource, Mockito.atLeast(0)).save(captor.capture());

        EblockerDnsServerState capturedState = filterLast(captor, EblockerDnsServerState.class);
        Assert.assertNotNull(capturedState);
        Assert.assertFalse(capturedState.isEnabled());
        Assert.assertFalse(Files.exists(eblockerFlagFilePath));
        Mockito.verify(listener).onEnable(false);
    }

    @Test
    public void testAlreadyDisabled() {
        setupDnsServer();
        Mockito.reset(dataSource);
        eblockerDnsServer.disable();
        Mockito.verify(dataSource, Mockito.times(0)).save(Mockito.any(EblockerDnsServerState.class));
        Mockito.verifyNoInteractions(listener);
    }

    @Test
    public void testGetUserDnsConfig() {
        // setup mock
        ResolverConfig customConfig = new ResolverConfig();
        customConfig.getOptions().put(ResolverConfig.OPTION_KEY_ORDER, "round_robin");
        customConfig.setNameServers(Arrays.asList(
                new NameServer(NameServer.Protocol.UDP, IpAddress.parse("10.10.10.10"), 53),
                new NameServer(NameServer.Protocol.UDP, IpAddress.parse("192.168.3.20"), 53)));

        ResolverConfig dhcpConfig = new ResolverConfig();
        dhcpConfig.setNameServers(Arrays.asList(
                new NameServer(NameServer.Protocol.UDP, IpAddress.parse("192.168.3.1"), 53),
                new NameServer(NameServer.Protocol.UDP, IpAddress.parse("192.168.3.2"), 53)));

        DnsServerConfig serverConfig = new DnsServerConfig();
        serverConfig.setDefaultResolver("custom");
        serverConfig.setResolverConfigs(new HashMap<>());
        serverConfig.getResolverConfigs().put("custom", customConfig);
        serverConfig.getResolverConfigs().put("dhcp", dhcpConfig);
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(serverConfig);

        setupDnsServer();

        // check service returns correct mapping
        DnsResolvers resolvers = eblockerDnsServer.getDnsResolvers();
        Assert.assertNotNull(resolvers);
        Assert.assertEquals("custom", resolvers.getDefaultResolver());
        Assert.assertEquals(Arrays.asList("10.10.10.10", "192.168.3.20"), resolvers.getCustomNameServers());
        Assert.assertEquals("round_robin", resolvers.getCustomResolverMode());
        Assert.assertEquals(Arrays.asList("192.168.3.1", "192.168.3.2"), resolvers.getDhcpNameServers());
    }

    @Test
    public void testSaveUserConfig() {
        setupDnsServer();
        Mockito.reset(dataSource);
        Mockito.reset(pubSubService);

        // setup currently stored config
        DnsServerConfig storedConfig = new DnsServerConfig();
        storedConfig.setResolverConfigs(new HashMap<>());
        storedConfig.getResolverConfigs().put("custom", null);
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(storedConfig);

        // setup config to be saved
        DnsResolvers resolvers = getDhcpResolver();

        // save config
        DnsResolvers savedResolvers = eblockerDnsServer.setDnsResolvers(resolvers);

        // check saved config
        Assert.assertFalse(resolvers == savedResolvers);
        Assert.assertEquals(resolvers.getCustomNameServers(), savedResolvers.getCustomNameServers());
        Assert.assertEquals(resolvers.getCustomResolverMode(), savedResolvers.getCustomResolverMode());
        Assert.assertEquals(resolvers.getDefaultResolver(), savedResolvers.getDefaultResolver());

        // check eblocker dns config
        ArgumentCaptor<DnsServerConfig> configCaptor = ArgumentCaptor.forClass(DnsServerConfig.class);
        Mockito.verify(dataSource).save(configCaptor.capture());

        DnsServerConfig config = configCaptor.getValue();
        Assert.assertEquals("dhcp", config.getDefaultResolver());
        Assert.assertNotNull(config.getResolverConfigs());

        // check dhcp-resolver
        Assert.assertNull(config.getResolverConfigs().get("dhcp"));

        // check custom-resolver
        Assert.assertNotNull(config.getResolverConfigs().get("custom"));
        Assert.assertEquals("random", config.getResolverConfigs().get("custom").getOptions().get(ResolverConfig.OPTION_KEY_ORDER));
        Assert.assertEquals(Arrays.asList(new NameServer(NameServer.Protocol.UDP, IpAddress.parse("77.88.8.8"), 53),
                new NameServer(NameServer.Protocol.UDP, IpAddress.parse("77.88.8.1"), 53)),
                config.getResolverConfigs().get("custom").getNameServers());

        Mockito.verify(pubSubService).publish(Channels.DNS_CONFIG, UPDATE_COMMAND);
    }

    private DnsResolvers getDhcpResolver() {
        DnsResolvers resolvers = new DnsResolvers();
        resolvers.setCustomNameServers(Arrays.asList("77.88.8.8", "77.88.8.1"));
        resolvers.setCustomResolverMode("random");
        resolvers.setDefaultResolver("dhcp");
        resolvers.setDhcpNameServers(Arrays.asList("8.8.8.8", "8.8.4.4"));
        return resolvers;
    }

    @Test
    public void testGetLocalRecords() {
        // setup mock
        Map<String, LocalDnsRecord> mockLocalRecordByName = new HashMap<>();
        mockLocalRecordByName.put("ns.eblocker", new LocalDnsRecord("ns.eblocker", false, false, Ip4Address.parse("10.10.10.100"), null, LOCAL_VPN_IP, null));
        mockLocalRecordByName.put("random-device", new LocalDnsRecord("random-device", false, false, Ip4Address.parse("10.10.10.200"), null, null, null));

        DnsServerConfig serverConfig = new DnsServerConfig();
        serverConfig.setLocalDnsRecords(new ArrayList<>(mockLocalRecordByName.values()));
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(serverConfig);

        eblockerDnsServer = new EblockerDnsServer(
                FLUSH_COMMAND,
                UPDATE_COMMAND,
                DEFAULT_CUSTOM_NAME_SERVERS,
                "",
                DEFAULT_TOR_NAME_SERVERS,
                eblockerFlagFilePath.toString(),
                CONTROL_BAR_HOST_NAME,
                CONTROL_BAR_IP_ADDRESS,
                VPN_SUBNET_IP,
                VPN_SUBNET_NETMASK,
                clock,
                dataSource,
                deviceService,
                dhcpBindListener,
                clientLeaseReader,
                networkInterfaceWrapper,
                pubSubService,
                routerAdvertisementCache);

        eblockerDnsServer.init();

        List<LocalDnsRecord> localRecords = eblockerDnsServer.getLocalDnsRecords();

        Assert.assertNotNull(localRecords);
        Assert.assertEquals(mockLocalRecordByName.size() + 1, localRecords.size());
        Map<String, LocalDnsRecord> recordsByName = localRecords.stream().collect(Collectors.toMap(LocalDnsRecord::getName, Function.identity()));
        Assert.assertNotNull(recordsByName.get(CONTROL_BAR_HOST_NAME));
        Assert.assertEquals(CONTROL_BAR_HOST_NAME, recordsByName.get(CONTROL_BAR_HOST_NAME).getName());
        Assert.assertEquals(CONTROL_BAR_IP_ADDRESS, recordsByName.get(CONTROL_BAR_HOST_NAME).getIpAddress());
        Assert.assertEquals(CONTROL_BAR_IP_ADDRESS, recordsByName.get(CONTROL_BAR_HOST_NAME).getVpnIpAddress());
        Assert.assertTrue(recordsByName.get(CONTROL_BAR_HOST_NAME).isBuiltin());
        Assert.assertTrue(recordsByName.get(CONTROL_BAR_HOST_NAME).isHidden());
        for (Map.Entry<String, LocalDnsRecord> e : mockLocalRecordByName.entrySet()) {
            Assert.assertNotNull(recordsByName.get(e.getKey()));
            Assert.assertEquals(e.getValue().isBuiltin(), recordsByName.get(e.getKey()).isBuiltin());
            Assert.assertEquals(e.getValue().isHidden(), recordsByName.get(e.getKey()).isHidden());
            Assert.assertEquals(e.getValue().getIpAddress(), recordsByName.get(e.getKey()).getIpAddress());
            Assert.assertEquals(e.getValue().getName(), recordsByName.get(e.getKey()).getName());
        }
    }

    @Test
    public void testSetLocalRecords() {
        setupDnsServer();
        Mockito.reset(dataSource);
        Mockito.reset(pubSubService);

        // setup currently stored config
        DnsServerConfig storedConfig = new DnsServerConfig();
        storedConfig.setLocalDnsRecords(Arrays.asList(
                new LocalDnsRecord("ns.unit.test", true, false, Ip4Address.parse("10.10.10.100"), null, null, null),
                new LocalDnsRecord("random.device", false, false, Ip4Address.parse("10.10.10.200"), null, null, null),
                new LocalDnsRecord("unit.test", true, false, Ip4Address.parse("10.10.10.100"), null, null, null),
                new LocalDnsRecord("must.be.deleted", false, false, Ip4Address.parse("4.3.2.1"), null, null, null)));
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(storedConfig);

        // setup config to be saved
        List<LocalDnsRecord> localRecords = Arrays.asList(
                new LocalDnsRecord("ns.unit.test", true, false, Ip4Address.parse("10.10.10.99"), null, null, null),
                new LocalDnsRecord("random.device", false, false, Ip4Address.parse("10.10.10.201"), null, null, null),
                new LocalDnsRecord(CONTROL_BAR_HOST_NAME, false, false, Ip4Address.parse("1.2.3.4"), null, LOCAL_VPN_IP, null));

        // save config
        eblockerDnsServer.setLocalDnsRecords(localRecords);

        // check eblocker dns config
        ArgumentCaptor<DnsServerConfig> configCaptor = ArgumentCaptor.forClass(DnsServerConfig.class);
        Mockito.verify(dataSource).save(configCaptor.capture());

        DnsServerConfig config = configCaptor.getValue();
        Assert.assertNotNull(config.getLocalDnsRecords());
        Assert.assertEquals(4, config.getLocalDnsRecords().size());

        Map<String, LocalDnsRecord> recordsByName = config.getLocalDnsRecords().stream()
                .collect(Collectors.toMap(LocalDnsRecord::getName, Function.identity()));
        // record altered in user config but must still be unmodified
        Assert.assertNotNull(recordsByName.get("ns.unit.test"));
        Assert.assertTrue(recordsByName.get("ns.unit.test").isBuiltin());
        Assert.assertFalse(recordsByName.get("ns.unit.test").isHidden());
        Assert.assertEquals("ns.unit.test", recordsByName.get("ns.unit.test").getName());
        Assert.assertEquals(Ip4Address.parse("10.10.10.100"), recordsByName.get("ns.unit.test").getIpAddress());

        // record was missing from user config but must still be there
        Assert.assertNotNull(recordsByName.get("unit.test"));
        Assert.assertTrue(recordsByName.get("unit.test").isBuiltin());
        Assert.assertFalse(recordsByName.get("unit.test").isHidden());
        Assert.assertEquals("unit.test", recordsByName.get("unit.test").getName());
        Assert.assertEquals(Ip4Address.parse("10.10.10.100"), recordsByName.get("unit.test").getIpAddress());

        // device
        Assert.assertNotNull(recordsByName.get("random.device"));
        Assert.assertFalse(recordsByName.get("random.device").isBuiltin());
        Assert.assertFalse(recordsByName.get("random.device").isHidden());
        Assert.assertEquals("random.device", recordsByName.get("random.device").getName());
        Assert.assertEquals(Ip4Address.parse("10.10.10.201"), recordsByName.get("random.device").getIpAddress());

        // controlbar
        Assert.assertNotNull(recordsByName.get(CONTROL_BAR_HOST_NAME));
        Assert.assertTrue(recordsByName.get(CONTROL_BAR_HOST_NAME).isBuiltin());
        Assert.assertTrue(recordsByName.get(CONTROL_BAR_HOST_NAME).isHidden());
        Assert.assertEquals(CONTROL_BAR_HOST_NAME, recordsByName.get(CONTROL_BAR_HOST_NAME).getName());
        Assert.assertEquals(CONTROL_BAR_IP_ADDRESS, recordsByName.get(CONTROL_BAR_HOST_NAME).getIpAddress());

        Mockito.verify(pubSubService).publish(Channels.DNS_CONFIG, UPDATE_COMMAND);
    }

    @Test
    public void testRefreshLocalRecords() {
        setupDnsServer();
        Mockito.reset(dataSource);
        Mockito.reset(pubSubService);

        // remove vpn address
        Mockito.when(networkInterfaceWrapper.getVpnIpv4Address()).thenReturn(null);
        eblockerDnsServer.refreshLocalDnsRecords();

        // get saved config
        ArgumentCaptor<DnsServerConfig> configCaptor = ArgumentCaptor.forClass(DnsServerConfig.class);
        Mockito.verify(dataSource).save(configCaptor.capture());

        // validate config
        DnsServerConfig config = configCaptor.getValue();
        List<LocalDnsRecord> records = config.getLocalDnsRecords().stream().sorted(Comparator.comparing(LocalDnsRecord::getName)).collect(Collectors.toList());
        Assert.assertNotNull(records);
        Assert.assertEquals(3, records.size());
        Assert.assertEquals(CONTROL_BAR_HOST_NAME, records.get(0).getName());
        Assert.assertEquals(CONTROL_BAR_IP_ADDRESS, records.get(0).getIpAddress());
        Assert.assertEquals(CONTROL_BAR_IP_ADDRESS, records.get(0).getVpnIpAddress());
        Assert.assertEquals("ns.unit.test", records.get(1).getName());
        Assert.assertEquals(Ip4Address.parse("10.10.10.100"), records.get(1).getIpAddress());
        Assert.assertNull(records.get(1).getVpnIpAddress());
        Assert.assertEquals("unit.test", records.get(2).getName());
        Assert.assertEquals(Ip4Address.parse("10.10.10.100"), records.get(2).getIpAddress());
        Assert.assertNull(records.get(2).getVpnIpAddress());

        // re-add vpn address
        Mockito.when(networkInterfaceWrapper.getVpnIpv4Address()).thenReturn(Ip4Address.parse("10.8.0.1"));
        eblockerDnsServer.refreshLocalDnsRecords();

        // validate config
        Mockito.verify(dataSource, Mockito.times(2)).save(configCaptor.capture());
        config = configCaptor.getValue();
        records = config.getLocalDnsRecords().stream().sorted(Comparator.comparing(LocalDnsRecord::getName)).collect(Collectors.toList());
        Assert.assertNotNull(records);
        Assert.assertEquals(3, records.size());
        Assert.assertEquals(CONTROL_BAR_HOST_NAME, records.get(0).getName());
        Assert.assertEquals(CONTROL_BAR_IP_ADDRESS, records.get(0).getIpAddress());
        Assert.assertEquals(CONTROL_BAR_IP_ADDRESS, records.get(0).getVpnIpAddress());
        Assert.assertEquals("ns.unit.test", records.get(1).getName());
        Assert.assertEquals(Ip4Address.parse("10.10.10.100"), records.get(1).getIpAddress());
        Assert.assertEquals(Ip4Address.parse("10.8.0.1"), records.get(1).getVpnIpAddress());
        Assert.assertEquals("unit.test", records.get(2).getName());
        Assert.assertEquals(Ip4Address.parse("10.10.10.100"), records.get(2).getIpAddress());
        Assert.assertEquals(Ip4Address.parse("10.8.0.1"), records.get(2).getVpnIpAddress());
    }

    // EB1-783
    @Test
    public void testBuiltinLocalRecordsUpdate() {
        // setup currently stored config
        DnsServerConfig storedConfig = new DnsServerConfig();
        storedConfig.setLocalDnsRecords(Arrays.asList(
                new LocalDnsRecord("obsolete.test", true, false, Ip4Address.parse("10.10.10.100"), null, null, null),
                new LocalDnsRecord("random.device", false, false, Ip4Address.parse("10.10.10.200"), null, null, null),
                new LocalDnsRecord("unit.test", true, false, Ip4Address.parse("10.10.10.100"), null, null, null)));
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(storedConfig);

        // init dns server
        setupDnsServer();

        // check fixed local entries have been added / updated
        // captor forClass is just syntactic sugar and does not ensure only instances of class are actually captured :(
        ArgumentCaptor<Object> configCaptor = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(dataSource, Mockito.atLeastOnce()).save(configCaptor.capture());

        Optional<DnsServerConfig> configOptional = configCaptor.getAllValues().stream()
                .filter(o -> o instanceof DnsServerConfig).map(o -> (DnsServerConfig) o).findFirst();
        Assert.assertTrue(configOptional.isPresent());
        DnsServerConfig config = configOptional.get();
        Assert.assertNotNull(config.getLocalDnsRecords());
        Assert.assertEquals(4, config.getLocalDnsRecords().size());

        Map<String, LocalDnsRecord> recordsByName = config.getLocalDnsRecords().stream()
                .collect(Collectors.toMap(LocalDnsRecord::getName, Function.identity()));
        // check new record has been inserted
        Assert.assertNotNull(recordsByName.get("ns.unit.test"));
        Assert.assertTrue(recordsByName.get("ns.unit.test").isBuiltin());
        Assert.assertFalse(recordsByName.get("ns.unit.test").isHidden());
        Assert.assertEquals("ns.unit.test", recordsByName.get("ns.unit.test").getName());
        Assert.assertEquals(Ip4Address.parse("10.10.10.100"), recordsByName.get("ns.unit.test").getIpAddress());

        // check second fixed entry is there
        Assert.assertNotNull(recordsByName.get("unit.test"));
        Assert.assertTrue(recordsByName.get("unit.test").isBuiltin());
        Assert.assertFalse(recordsByName.get("unit.test").isHidden());
        Assert.assertEquals("unit.test", recordsByName.get("unit.test").getName());
        Assert.assertEquals(Ip4Address.parse("10.10.10.100"), recordsByName.get("unit.test").getIpAddress());

        // check control entry is there
        Assert.assertNotNull(recordsByName.get(CONTROL_BAR_HOST_NAME));
        Assert.assertTrue(recordsByName.get(CONTROL_BAR_HOST_NAME).isBuiltin());
        Assert.assertTrue(recordsByName.get(CONTROL_BAR_HOST_NAME).isHidden());
        Assert.assertEquals(CONTROL_BAR_HOST_NAME, recordsByName.get(CONTROL_BAR_HOST_NAME).getName());
        Assert.assertEquals(CONTROL_BAR_IP_ADDRESS, recordsByName.get(CONTROL_BAR_HOST_NAME).getIpAddress());

        // user supplied entry has not been modified
        Assert.assertNotNull(recordsByName.get("random.device"));
        Assert.assertFalse(recordsByName.get("random.device").isBuiltin());
        Assert.assertFalse(recordsByName.get("random.device").isHidden());
        Assert.assertEquals("random.device", recordsByName.get("random.device").getName());
        Assert.assertEquals(Ip4Address.parse("10.10.10.200"), recordsByName.get("random.device").getIpAddress());
    }

    @Test
    public void testUpdateDhcpNameServersOnInit() {
        Mockito.when(dataSource.getCurrentNetworkState()).thenReturn(NetworkStateId.PLUG_AND_PLAY);

        // mock existing config
        DnsServerConfig config = new DnsServerConfig();
        config.setResolverConfigs(new HashMap<>());
        config.setResolverConfigNameByIp(Collections.emptyMap());
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(config);

        EblockerDnsServerState state = new EblockerDnsServerState();
        state.setResolverByDeviceId(Collections.emptyMap());
        state.setEnabled(true);
        Mockito.when(dataSource.get(EblockerDnsServerState.class)).thenReturn(state);

        setupDnsServer();

        Mockito.verify(clientLeaseReader).readLease();
        Mockito.verify(dataSource, Mockito.times(2)).save(config);

        Assert.assertNotNull(config.getResolverConfigs());
        Assert.assertNotNull(config.getResolverConfigs().get("dhcp"));
        Assert.assertEquals(3, config.getResolverConfigs().get("dhcp").getNameServers().size());
        Assert.assertEquals(IpAddress.parse("192.168.3.1"), config.getResolverConfigs().get("dhcp").getNameServers().get(0).getAddress());
        Assert.assertEquals(IpAddress.parse("192.168.3.2"), config.getResolverConfigs().get("dhcp").getNameServers().get(1).getAddress());
        Assert.assertEquals(IpAddress.parse("fe80::192:168:3:1"), config.getResolverConfigs().get("dhcp").getNameServers().get(2).getAddress());
    }

    @Test
    public void testUpdateDhcpNameServers() {
        Mockito.when(dataSource.getCurrentNetworkState()).thenReturn(NetworkStateId.PLUG_AND_PLAY);

        // mock existing config
        DnsServerConfig mockConfig = new DnsServerConfig();
        mockConfig.setResolverConfigs(new HashMap<>());
        mockConfig.setResolverConfigNameByIp(Collections.emptyMap());
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(mockConfig);

        EblockerDnsServerState state = new EblockerDnsServerState();
        state.setResolverByDeviceId(Collections.emptyMap());
        state.setEnabled(true);
        Mockito.when(dataSource.get(EblockerDnsServerState.class)).thenReturn(state);
        setupDnsServer();

        Mockito.reset(dataSource);
        Mockito.reset(pubSubService);

        // setup currently stored config
        DnsServerConfig storedConfig = new DnsServerConfig();
        storedConfig.setResolverConfigs(new HashMap<>());
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(storedConfig);

        // call dhcp bind listener callback
        ArgumentCaptor<DhcpBindListener.Listener> listenerCaptor = ArgumentCaptor.forClass(DhcpBindListener.Listener.class);
        Mockito.verify(dhcpBindListener).addListener(listenerCaptor.capture());
        listenerCaptor.getValue().updateDhcpNameServers(Arrays.asList("77.88.8.8", "77.88.8.1"));

        Mockito.verify(pubSubService).publish(Channels.DNS_CONFIG, UPDATE_COMMAND);

        ArgumentCaptor<DnsServerConfig> configCaptor = ArgumentCaptor.forClass(DnsServerConfig.class);
        Mockito.verify(dataSource).save(configCaptor.capture());
        DnsServerConfig config = configCaptor.getValue();

        Assert.assertNotNull(config.getResolverConfigs().get("dhcp"));
        Assert.assertEquals(
                Arrays.asList(
                        new NameServer(NameServer.Protocol.UDP, IpAddress.parse("77.88.8.8"), 53),
                        new NameServer(NameServer.Protocol.UDP, IpAddress.parse("77.88.8.1"), 53),
                        new NameServer(NameServer.Protocol.UDP, IpAddress.parse("fe80::192:168:3:1"), 53)),
                config.getResolverConfigs().get("dhcp").getNameServers());
    }

    @Test
    public void testUpdateRouterAdvertisedNameServers() {
        Mockito.when(dataSource.getCurrentNetworkState()).thenReturn(NetworkStateId.PLUG_AND_PLAY);

        // mock existing config
        DnsServerConfig mockConfig = new DnsServerConfig();
        mockConfig.setResolverConfigs(new HashMap<>());
        mockConfig.setResolverConfigNameByIp(Collections.emptyMap());
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(mockConfig);

        EblockerDnsServerState state = new EblockerDnsServerState();
        state.setResolverByDeviceId(Collections.emptyMap());
        state.setEnabled(true);
        Mockito.when(dataSource.get(EblockerDnsServerState.class)).thenReturn(state);
        setupDnsServer();

        Mockito.reset(dataSource);
        Mockito.reset(pubSubService);

        // setup currently stored config
        DnsServerConfig storedConfig = new DnsServerConfig();
        storedConfig.setResolverConfigs(new HashMap<>());
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(storedConfig);

        // call router advertisement listener callback
        ArgumentCaptor<RouterAdvertisementCache.Listener> listenerCaptor = ArgumentCaptor.forClass(RouterAdvertisementCache.Listener.class);
        Mockito.verify(routerAdvertisementCache).addListener(listenerCaptor.capture());
        long lastUpdate = clock.instant().minusSeconds(1000).toEpochMilli();
        listenerCaptor.getValue().onUpdate(Arrays.asList(
                createRouterAdvertisementEntry(lastUpdate, 2000, 5000, Ip6Address.parse("2001:4860:4802:32::a"), Ip6Address.parse("2001:4860:4802:34::a")),
                createRouterAdvertisementEntry(lastUpdate, 2000, 500, Ip6Address.parse("2001:4860:4802:36::a"), Ip6Address.parse("2001:4860:4802:38::a")),
                createRouterAdvertisementEntry(lastUpdate, 2000, 5000)
        ));

        Mockito.verify(pubSubService).publish(Channels.DNS_CONFIG, UPDATE_COMMAND);

        ArgumentCaptor<DnsServerConfig> configCaptor = ArgumentCaptor.forClass(DnsServerConfig.class);
        Mockito.verify(dataSource).save(configCaptor.capture());
        DnsServerConfig config = configCaptor.getValue();

        Assert.assertNotNull(config.getResolverConfigs().get("dhcp"));
        Assert.assertEquals(
                Arrays.asList(
                        new NameServer(NameServer.Protocol.UDP, IpAddress.parse("192.168.3.1"), 53),
                        new NameServer(NameServer.Protocol.UDP, IpAddress.parse("192.168.3.2"), 53),
                        new NameServer(NameServer.Protocol.UDP, Ip6Address.parse("2001:4860:4802:32::a"), 53),
                        new NameServer(NameServer.Protocol.UDP, Ip6Address.parse("2001:4860:4802:34::a"), 53)),
                config.getResolverConfigs().get("dhcp").getNameServers());
    }

    @Test
    public void testSetFilteredPeers() {
        setupDnsServer();
        Mockito.reset(dataSource);
        Mockito.reset(pubSubService);

        // setup currently stored config
        DnsServerConfig storedConfig = new DnsServerConfig();
        storedConfig.setFilteredPeers(Collections.emptySet());
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(storedConfig);

        Set<IpAddress> filteredPeers = Sets.newHashSet(IpAddress.parse("192.168.9.9"), IpAddress.parse("192.168.9.10"));
        Set<IpAddress> filteredPeerDefaultAllow = Sets.newHashSet(IpAddress.parse("192.168.9.11"));
        eblockerDnsServer.setFilteredPeers(filteredPeers, filteredPeerDefaultAllow);

        Mockito.verify(pubSubService).publish(Channels.DNS_CONFIG, UPDATE_COMMAND);

        ArgumentCaptor<DnsServerConfig> configCaptor = ArgumentCaptor.forClass(DnsServerConfig.class);
        Mockito.verify(dataSource).save(configCaptor.capture());
        DnsServerConfig config = configCaptor.getValue();
        Assert.assertEquals(filteredPeers.stream().map(IpAddress::toString).collect(Collectors.toSet()), config.getFilteredPeers());
        Assert.assertEquals(filteredPeerDefaultAllow.stream().map(IpAddress::toString).collect(Collectors.toSet()), config.getFilteredPeersDefaultAllow());
    }

    @Test
    public void testSetFilteredPeersNoChange() {
        setupDnsServer();
        Mockito.reset(dataSource);
        Mockito.reset(pubSubService);

        // setup currently stored config
        DnsServerConfig storedConfig = new DnsServerConfig();
        storedConfig.setFilteredPeers(Sets.newHashSet("192.168.9.9", "192.168.9.10"));
        storedConfig.setFilteredPeerDefaultAllow(Sets.newHashSet("192.168.9.11", "192.168.9.12"));
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(storedConfig);

        eblockerDnsServer.setFilteredPeers(Sets.newHashSet(IpAddress.parse("192.168.9.10"), IpAddress.parse("192.168.9.9")),
                Sets.newHashSet(IpAddress.parse("192.168.9.12"), IpAddress.parse("192.168.9.11")));

        Mockito.verify(pubSubService, Mockito.never()).publish(Channels.DNS_CONFIG, UPDATE_COMMAND);
        Mockito.verify(dataSource, Mockito.never()).save(DnsServerConfig.class);
    }

    @Test
    public void testFlushCache() {
        setupDnsServer();
        eblockerDnsServer.flushCache();
        Mockito.verify(pubSubService).publish(Channels.DNS_CONFIG, FLUSH_COMMAND);
    }

    @Test
    public void testAddVpnResolver() {
        setupDnsServer();
        Mockito.reset(dataSource);

        eblockerDnsServer.addVpnResolver(5, Arrays.asList("62.22.22.22", "62.22.22.20"), "10.10.10.10");

        ArgumentCaptor<DnsServerConfig> captor = ArgumentCaptor.forClass(DnsServerConfig.class);
        Mockito.verify(dataSource).save(captor.capture());
        Mockito.verify(pubSubService).publish(Channels.DNS_CONFIG, UPDATE_COMMAND);

        DnsServerConfig config = captor.getValue();
        Assert.assertNotNull(config.getResolverConfigs().containsKey("vpn-5"));
        Assert.assertEquals(2, config.getResolverConfigs().get("vpn-5").getNameServers().size());
        Assert.assertEquals(IpAddress.parse("62.22.22.22"), config.getResolverConfigs().get("vpn-5").getNameServers().get(0).getAddress());
        Assert.assertEquals(IpAddress.parse("62.22.22.20"), config.getResolverConfigs().get("vpn-5").getNameServers().get(1).getAddress());
        Assert.assertNotNull(config.getResolverConfigs().get("vpn-5").getOptions());
        Assert.assertEquals("10.10.10.10", config.getResolverConfigs().get("vpn-5").getOptions().get("bind_host"));
    }

    @Test
    public void testRemoveVpnResolver() {
        setupDnsServer();

        DnsServerConfig config = new DnsServerConfig();
        config.setResolverConfigs(new HashMap<>());
        config.getResolverConfigs().put("vpn-5", new ResolverConfig());
        config.setResolverConfigNameByIp(new HashMap<>());
        config.getResolverConfigNameByIp().put("192.168.1.252", "vpn-5");
        config.getResolverConfigNameByIp().put("192.168.1.253", "vpn-5");
        config.getResolverConfigNameByIp().put("192.168.1.254", "tor");
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(config);

        eblockerDnsServer.removeVpnResolver(5);

        Mockito.verify(dataSource).save(config);
        Mockito.verify(pubSubService).publish(Channels.DNS_CONFIG, UPDATE_COMMAND);
        Assert.assertNull(config.getResolverConfigs().get("vpn-5"));
        Assert.assertEquals(1, config.getResolverConfigNameByIp().size());
        Assert.assertNull(config.getResolverConfigNameByIp().get("192.168.1.252"));
        Assert.assertNull(config.getResolverConfigNameByIp().get("192.168.1.253"));
    }

    @Test
    public void useVpnResolver() {
        setupDnsServer();

        DnsServerConfig config = new DnsServerConfig();
        config.setResolverConfigs(new HashMap<>());
        config.getResolverConfigs().put("vpn-5", new ResolverConfig());
        config.setResolverConfigNameByIp(new HashMap<>());
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(config);

        eblockerDnsServer.useVpnResolver(device, 5);

        Mockito.verify(dataSource).save(config);
        Mockito.verify(pubSubService).publish(Channels.DNS_CONFIG, UPDATE_COMMAND);
        Assert.assertEquals("vpn-5", config.getResolverConfigNameByIp().get("192.168.3.3"));
    }

    @Test
    public void testUseTorResolver() {
        setupDnsServer();

        // setup mock config
        DnsServerConfig config = new DnsServerConfig();
        config.setResolverConfigNameByIp(new HashMap<>());
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(config);

        // set resolver
        eblockerDnsServer.useTorResolver(device);

        // check resolver has been configured
        Mockito.verify(dataSource).save(config);
        Assert.assertEquals("tor", config.getResolverConfigNameByIp().get("192.168.3.3"));
        Mockito.verify(pubSubService).publish(Channels.DNS_CONFIG, UPDATE_COMMAND);
    }

    @Test
    public void testUseDefaultResolver() {
        setupDnsServer();

        // setup mock config
        DnsServerConfig config = new DnsServerConfig();
        Map<String, String> configNameByIp = new HashMap<>();
        configNameByIp.put("10.10.10.10", "tor");
        config.setResolverConfigNameByIp(configNameByIp);
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(config);

        // set resolver
        eblockerDnsServer.useDefaultResolver(device);

        // check resolver has been configured
        Mockito.verify(dataSource).save(config);
        Assert.assertNull(config.getResolverConfigNameByIp().get("192.168.3.3"));
        Mockito.verify(pubSubService).publish(Channels.DNS_CONFIG, UPDATE_COMMAND);
    }

    @Test
    public void testGetCustomNameServers() {
        setupDnsServer();

        List<String> nameServers = Arrays.asList("192.168.3.3", "192.168.3.4");
        DnsServerConfig config = createDnsServerConfig("custom", nameServers);
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(config);

        Assert.assertEquals(nameServers, eblockerDnsServer.getCustomNameServers());
    }

    @Test
    public void testGetCustomNameServersWithoutConfiguration() {
        eblockerDnsServer = new EblockerDnsServer(
                FLUSH_COMMAND,
                UPDATE_COMMAND,
                DEFAULT_CUSTOM_NAME_SERVERS,
                DEFAULT_LOCAL_NAMES,
                DEFAULT_TOR_NAME_SERVERS,
                eblockerFlagFilePath.toString(),
                CONTROL_BAR_HOST_NAME,
                CONTROL_BAR_IP_ADDRESS,
                VPN_SUBNET_IP,
                VPN_SUBNET_NETMASK,
                clock,
                dataSource,
                deviceService,
                dhcpBindListener,
                clientLeaseReader,
                networkInterfaceWrapper,
                pubSubService,
                routerAdvertisementCache);
        Assert.assertEquals(Collections.emptyList(), eblockerDnsServer.getCustomNameServers());
    }

    @Test
    public void testGetDhcpNameServers() {
        setupDnsServer();

        List<String> nameServers = Arrays.asList("192.168.3.3", "192.168.3.4");
        DnsServerConfig config = createDnsServerConfig("dhcp", nameServers);
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(config);

        Assert.assertEquals(nameServers, eblockerDnsServer.getDhcpNameServers());
    }

    @Test
    public void testGetDhcpNameServersWithoutConfiguration() {
        eblockerDnsServer = new EblockerDnsServer(
                FLUSH_COMMAND,
                UPDATE_COMMAND,
                DEFAULT_CUSTOM_NAME_SERVERS,
                DEFAULT_LOCAL_NAMES,
                DEFAULT_TOR_NAME_SERVERS,
                eblockerFlagFilePath.toString(),
                CONTROL_BAR_HOST_NAME,
                CONTROL_BAR_IP_ADDRESS,
                VPN_SUBNET_IP,
                VPN_SUBNET_NETMASK,
                clock,
                dataSource,
                deviceService,
                dhcpBindListener,
                clientLeaseReader,
                networkInterfaceWrapper,
                pubSubService,
                routerAdvertisementCache);
        Assert.assertEquals(Collections.emptyList(), eblockerDnsServer.getDhcpNameServers());
    }

    @Test
    public void testDisableOnMissingConfiguration() throws IOException {
        // mock enabled dns but missing config
        Files.createFile(eblockerFlagFilePath);
        EblockerDnsServerState state = new EblockerDnsServerState();
        state.setEnabled(true);
        Mockito.when(dataSource.get(EblockerDnsServerState.class)).thenReturn(state);

        eblockerDnsServer = new EblockerDnsServer(
                FLUSH_COMMAND,
                UPDATE_COMMAND,
                DEFAULT_CUSTOM_NAME_SERVERS,
                DEFAULT_LOCAL_NAMES,
                DEFAULT_TOR_NAME_SERVERS,
                eblockerFlagFilePath.toString(),
                CONTROL_BAR_HOST_NAME,
                CONTROL_BAR_IP_ADDRESS,
                VPN_SUBNET_IP,
                VPN_SUBNET_NETMASK,
                clock,
                dataSource,
                deviceService,
                dhcpBindListener,
                clientLeaseReader,
                networkInterfaceWrapper,
                pubSubService,
                routerAdvertisementCache);
        eblockerDnsServer.init();

        Assert.assertFalse(Files.exists(eblockerFlagFilePath));
        Assert.assertFalse(eblockerDnsServer.isEnabled());
        ArgumentCaptor<EblockerDnsServerState> captor = ArgumentCaptor.forClass(EblockerDnsServerState.class);
        Mockito.verify(dataSource).save(captor.capture());
        Assert.assertFalse(captor.getValue().isEnabled());
    }

    @Test
    public void testIpChanges() {
        // setup mock device
        Device[] devices = {
                createDevice("0", "192.168.5.5"),
                createDevice("1", "192.168.5.6")
        };

        Mockito.when(deviceService.getDeviceById("0")).thenReturn(devices[0]);
        Mockito.when(deviceService.getDeviceById("1")).thenReturn(devices[1]);

        // setup mock state
        EblockerDnsServerState state = new EblockerDnsServerState();
        state.setEnabled(true);
        state.setResolverByDeviceId(new HashMap<>());
        state.getResolverByDeviceId().put("0", "unit-test-resolver-name");
        state.getResolverByDeviceId().put("1", "unit-test-resolver-name");
        Mockito.when(dataSource.get(EblockerDnsServerState.class)).thenReturn(state);

        // setup mock config
        DnsServerConfig config = new DnsServerConfig();
        config.setResolverConfigNameByIp(new HashMap<>());
        config.getResolverConfigNameByIp().put("192.168.5.5", "unit-test-resolver-name");
        config.getResolverConfigNameByIp().put("192.168.5.6", "unit-test-resolver-name");
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(config);

        setupDnsServer();
        Mockito.reset(dataSource);

        // check device-change callback has been registered
        ArgumentCaptor<DeviceService.DeviceChangeListener> listenerCaptor = ArgumentCaptor
                .forClass(DeviceService.DeviceChangeListener.class);
        Mockito.verify(deviceService).addListener(listenerCaptor.capture());

        // trigger device change event and check ip is updated
        devices[0].setIpAddresses(Collections.singletonList(IpAddress.parse("192.168.5.10")));
        devices[1].setIpAddresses(Collections.emptyList());
        listenerCaptor.getValue().onChange(device);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(dataSource, Mockito.atLeastOnce()).save(captor.capture());
        DnsServerConfig updatedConfig = filterLast(captor, DnsServerConfig.class);
        Assert.assertNotNull(updatedConfig);
        Assert.assertEquals(Collections.singletonMap("192.168.5.10", "unit-test-resolver-name"),
                updatedConfig.getResolverConfigNameByIp());
    }

    @Test
    public void testIpChangesOnInit() {
        // setup mock device
        Device[] devices = {
                createDevice("0", "192.168.5.10"),
                createDevice("1", null)
        };

        Mockito.when(deviceService.getDeviceById("0")).thenReturn(devices[0]);
        Mockito.when(deviceService.getDeviceById("1")).thenReturn(devices[1]);

        // setup mock state
        EblockerDnsServerState state = new EblockerDnsServerState();
        state.setEnabled(true);
        state.setResolverByDeviceId(new HashMap<>());
        state.getResolverByDeviceId().put("0", "unit-test-resolver-name");
        state.getResolverByDeviceId().put("1", "unit-test-resolver-name");
        Mockito.when(dataSource.get(EblockerDnsServerState.class)).thenReturn(state);

        // setup mock config
        DnsServerConfig config = new DnsServerConfig();
        config.setResolverConfigNameByIp(new HashMap<>());
        config.getResolverConfigNameByIp().put("192.168.5.5", "unit-test-resolver-name");
        config.getResolverConfigNameByIp().put("192.168.5.6", "unit-test-resolver-name");
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(config);

        setupDnsServer();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(dataSource, Mockito.atLeastOnce()).save(captor.capture());
        DnsServerConfig updatedConfig = filterLast(captor, DnsServerConfig.class);
        Assert.assertNotNull(updatedConfig);
        Assert.assertEquals(Collections.singletonMap("192.168.5.10", "unit-test-resolver-name"),
                updatedConfig.getResolverConfigNameByIp());
    }

    @Test
    public void testSetCustomResolver() {
        for (String mode : new String[]{ "dhcp", "custom", "tor" }) {
            DnsServerConfig config = new DnsServerConfig();
            config.setDefaultResolver(mode);
            Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(config);
            setupDnsServer();

            eblockerDnsServer.setDnsCustomResolver();

            Assert.assertEquals("custom", eblockerDnsServer.getConfig().getDefaultResolver());
        }
    }

    private DnsServerConfig createDnsServerConfig(String resolverName, List<String> nameServers) {
        ResolverConfig resolverConfig = new ResolverConfig();
        resolverConfig.setNameServers(nameServers.stream()
                .map(IpAddress::parse)
                .map(n -> new NameServer(NameServer.Protocol.UDP, n, 53)).collect(Collectors.toList()));
        DnsServerConfig dnsServerConfig = new DnsServerConfig();
        dnsServerConfig.setResolverConfigs(Collections.singletonMap(resolverName, resolverConfig));
        return dnsServerConfig;
    }

    private void setupDnsServer() {
        eblockerDnsServer = new EblockerDnsServer(
                FLUSH_COMMAND,
                UPDATE_COMMAND,
                DEFAULT_CUSTOM_NAME_SERVERS,
                DEFAULT_LOCAL_NAMES,
                DEFAULT_TOR_NAME_SERVERS,
                eblockerFlagFilePath.toString(),
                CONTROL_BAR_HOST_NAME,
                CONTROL_BAR_IP_ADDRESS,
                VPN_SUBNET_IP,
                VPN_SUBNET_NETMASK,
                clock,
                dataSource,
                deviceService,
                dhcpBindListener,
                clientLeaseReader,
                networkInterfaceWrapper,
                pubSubService,
                routerAdvertisementCache);
        eblockerDnsServer.init();
        eblockerDnsServer.addListener(listener);
    }

    private Device createDevice(String id, String ipAddress) {
        Device device = new Device();
        device.setId(id);
        if (ipAddress != null) {
            device.setIpAddresses(Collections.singletonList(IpAddress.parse(ipAddress)));
        }
        return device;
    }

    private <T> T filterLast(ArgumentCaptor<?> captor, Class<T> clazz) {
        List<T> filteredValues = filterValues(captor, clazz);
        for (int i = filteredValues.size() - 1; i >= 0; --i) {
            if (clazz.isInstance(filteredValues.get(i))) {
                return filteredValues.get(i);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> filterValues(ArgumentCaptor<?> captor, Class<T> clazz) {
        return captor.getAllValues().stream().filter(clazz::isInstance).map(o -> (T) o).collect(Collectors.toList());
    }

    private RouterAdvertisementCache.Entry createRouterAdvertisementEntry(long lastUpdate, int lifetime, int rdnsLifetime, Ip6Address... nameServer) {
        List<Option> options = nameServer.length != 0 ? Collections.singletonList(new RecursiveDnsServerOption(rdnsLifetime, Arrays.asList(nameServer))) : Collections.emptyList();
        return new RouterAdvertisementCache.Entry(null, lastUpdate, lifetime, new RouterAdvertisement(null, null, null, null, (short) 0, false, false, false, null, lifetime, 0, 0, options));
    }
}
