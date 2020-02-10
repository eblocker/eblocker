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
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.network.DhcpBindListener;
import org.eblocker.server.common.network.DhcpUtils;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.eblocker.server.common.network.RouterAdvertisementCache;
import org.eblocker.server.common.network.icmpv6.RecursiveDnsServerOption;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.DeviceService.DeviceChangeListener;
import com.google.common.base.Splitter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
@SubSystemService(value = SubSystem.EVENT_LISTENER, allowUninitializedCalls = false)
public class EblockerDnsServer {

    private static final Logger log = LoggerFactory.getLogger(EblockerDnsServer.class);

    static final String RESOLVER_DHCP = "dhcp";
    static final String RESOLVER_CUSTOM = "custom";
    static final String RESOLVER_TOR = "tor";
    private static final String RESOLVER_VPN_PREFIX = "vpn-";

    private static final String RESOLVER_MODE_DEFAULT = "default";
    private static final String RESOLVER_MODE_ROUND_ROBIN = "round_robin";
    private static final String RESOLVER_MODE_RANDOM = "random";

    private final String channelName;
    private final String updateCommand;
    private final String flushCommand;
    private final String defaultCustomNameServers;
    private final String defaultTorNameServers;
    private final String defaultLocalNames;
    private final String enabledFlagFile;
    private final String controlBarHostname;
    private final Ip4Address controlBarIpAddress;
    private final String vpnSubnetNetmask;
    private final String vpnSubnetIp;
    private final Clock clock;
    private final DataSource dataSource;
    private final DeviceService deviceService;
    private final DhcpBindListener dhcpBindListener;
    private final DhcpClientLeaseReader dhcpClientLeaseReader;
    private final NetworkInterfaceWrapper networkInterface;
    private final PubSubService pubSubService;
    private final RouterAdvertisementCache routerAdvertisementCache;

    private final List<Listener> listeners = new ArrayList<>();
    private List<String> dhcpLeaseNameServers = Collections.emptyList();
    private List<String> routerAdvertisedNameServers = Collections.emptyList();
    private EblockerDnsServerState state;

    @Inject
    public EblockerDnsServer(@Named("dns.server.channel.name") String channelName,
                             @Named("dns.server.channel.command.flush") String flushCommand,
                             @Named("dns.server.channel.command.update") String updateCommand,
                             @Named("dns.server.default.custom.name.servers") String defaultCustomNameServers,
                             @Named("dns.server.default.local.names") String defaultLocalNames,
                             @Named("dns.server.default.tor.name.servers") String defaultTorNameServers,
                             @Named("dns.server.enabled.file") String enabledFlagFile,
                             @Named("network.control.bar.host.name") String controlBarHostname,
                             @Named("network.control.bar.host.fallback.ip") Ip4Address controlBarIpAddress,
                             @Named("network.vpn.subnet.ip") String vpnSubnetIp,
                             @Named("network.vpn.subnet.netmask") String vpnSubnetNetmask,
                             Clock clock,
                             DataSource dataSource,
                             DeviceService deviceService,
                             DhcpBindListener dhcpBindListener,
                             DhcpClientLeaseReader dhcpClientLeaseReader,
                             NetworkInterfaceWrapper networkInterface,
                             PubSubService pubSubService,
                             RouterAdvertisementCache routerAdvertisementCache) {
        this.channelName = channelName;
        this.flushCommand = flushCommand;
        this.updateCommand = updateCommand;
        this.defaultCustomNameServers = defaultCustomNameServers;
        this.defaultLocalNames = defaultLocalNames;
        this.defaultTorNameServers = defaultTorNameServers;
        this.enabledFlagFile = enabledFlagFile;
        this.controlBarHostname = controlBarHostname;
        this.controlBarIpAddress = controlBarIpAddress;
        this.vpnSubnetNetmask = vpnSubnetNetmask;
        this.vpnSubnetIp = vpnSubnetIp;
        this.clock = clock;
        this.dataSource = dataSource;
        this.deviceService = deviceService;
        this.dhcpBindListener = dhcpBindListener;
        this.dhcpClientLeaseReader = dhcpClientLeaseReader;
        this.networkInterface = networkInterface;
        this.pubSubService = pubSubService;
        this.routerAdvertisementCache = routerAdvertisementCache;
    }

    @SubSystemInit
    public void init(){
        loadState();

        // update built-in local entries
        DnsServerConfig config = dataSource.get(DnsServerConfig.class);
        if (config != null) {
            setLocalDnsRecords(config.getLocalDnsRecords() != null ? config.getLocalDnsRecords() : Collections.emptyList());
        }

        if (state.isEnabled()) {
            if (dataSource.get(DnsServerConfig.class) == null) {
                log.error("missing dns server configuration despite being enabled, disabling dns service");
                disable();
            } else {
                if (dataSource.getCurrentNetworkState() == NetworkStateId.PLUG_AND_PLAY) {
                    log.debug("updating dhcp nameservers");
                    dhcpLeaseNameServers = readDhcpLeaseNameServers();
                    routerAdvertisedNameServers = extractNameServers(routerAdvertisementCache.getEntries());
                    updateDhcpNameServers();
                }
                createEnabledFlagFile();
                updateResolverByIpMapping();
            }
        } else {
            deleteEnabledFlagFile();
        }

        // register callback to trigger on device changes
        deviceService.addListener(new DeviceChangeListener() {
            @Override
            public void onDelete(Device device) {
                // Nothing to do here
            }

            @Override
            public void onChange(Device device) {
                updateResolverByIpMapping();
            }

            @Override
            public void onReset(Device device) {
                // Nothing to do here
            }
        });

        dhcpBindListener.addListener(nameServers -> {
            dhcpLeaseNameServers = nameServers;
            updateDhcpNameServers();
        });

        routerAdvertisementCache.addListener(advertisements -> {
            routerAdvertisedNameServers = extractNameServers(advertisements);
            updateDhcpNameServers();
        });
    }

    public boolean isEnabled() {
        return state.isEnabled();
    }

    public synchronized void enable(NetworkConfiguration networkConfiguration) {
        if (state.isEnabled()) {
            return;
        }
        state.setEnabled(true);
        dataSource.save(state);

        createEnabledFlagFile();

        DnsServerConfig config;
        if (!networkConfiguration.isAutomatic()) {
            // in manual mode carry over configured nameservers
            config = getOrCreateDefaultConfig(RESOLVER_CUSTOM);
            List<String> nameServers = new ArrayList<>();
            addNonLocalNameServer(nameServers, networkConfiguration.getNameServerPrimary());
            addNonLocalNameServer(nameServers, networkConfiguration.getNameServerSecondary());
            ResolverConfig manualConfig = createResolverConfig(nameServers);
            config.getResolverConfigs().put(RESOLVER_CUSTOM, manualConfig);
        } else {
            // in automatic mode update dhcp config with current lease
            config = getOrCreateDefaultConfig(RESOLVER_DHCP);
            List<String> nameServers = readDhcpLeaseNameServers();
            config.getResolverConfigs().put(RESOLVER_DHCP, createResolverConfig(nameServers));
        }

        saveConfig(config);
        listeners.forEach(listener -> listener.onEnable(true));
    }

    public synchronized void disable() {
        if (!state.isEnabled()) {
            return;
        }
        state.setEnabled(false);
        dataSource.save(state);
        deleteEnabledFlagFile();
        listeners.forEach(listener -> listener.onEnable(false));
    }

    public List<String> getCustomNameServers() {
        return getNameServers(RESOLVER_CUSTOM);
    }

    public List<String> getDhcpNameServers() {
        return getNameServers(RESOLVER_DHCP);
    }

    public void addListener(Listener listener) {
        this.listeners.add(listener);
    }

    private List<String> extractNameServers(List<RouterAdvertisementCache.Entry> advertisements) {
        long now = clock.millis();
        return advertisements.stream()
            .map(e -> new Tuple<>(e.getLastUpdate(), e.getAdvertisement().getOptions().stream()
                .filter(o -> RecursiveDnsServerOption.TYPE == o.getType())
                .map(o -> (RecursiveDnsServerOption) o)
                .findFirst().orElse(null)))
            .filter(t -> t.v() != null)
            .filter(t -> now < t.u + t.v.getLifetime() * 1000)
            .map(Tuple::v)
            .flatMap(o -> o.getDnsServers().stream())
            .map(IpAddress::toString)
            .collect(Collectors.toList());
    }

    /*
     * 127.0.0.1 isn't a valid DNS server for eblocker-dns, because if applied eblocker-dns will recursively query itself
     */
    private void addNonLocalNameServer(List<String> nameServerList, String nameServer) {
        if ("127.0.0.1".equals(nameServer)) {
            log.error("Using 127.0.0.1 as nameserver is not allowed, resolvconf seems to be in an invalid state");
        }
        else {
            addNonNull(nameServerList, nameServer);
        }
    }

    private List<String> getNameServers(String configName) {
        DnsServerConfig serverConfig = dataSource.get(DnsServerConfig.class);
        if (serverConfig == null) {
            return Collections.emptyList();
        }

        ResolverConfig resolverConfig = serverConfig.getResolverConfigs().get(configName);
        return mapNameServers(resolverConfig);
    }

    public DnsResolvers getDnsResolvers() {
        DnsServerConfig config = dataSource.get(DnsServerConfig.class);
        DnsResolvers dnsResolvers = new DnsResolvers();
        if (config != null) {
            dnsResolvers.setCustomNameServers(mapNameServers(config.getResolverConfigs().get(RESOLVER_CUSTOM)));
            dnsResolvers.setDhcpNameServers(mapNameServers(config.getResolverConfigs().get(RESOLVER_DHCP)));
            dnsResolvers.setDefaultResolver(config.getDefaultResolver());
            dnsResolvers.setCustomResolverMode(mapOrderOption(config.getResolverConfigs().get(RESOLVER_CUSTOM)));
        }
        return dnsResolvers;
    }

    public synchronized DnsResolvers setDnsResolvers(DnsResolvers dnsResolvers) {
        ResolverConfig customResolverConfig = new ResolverConfig();
        List<NameServer> customNameServers = dnsResolvers.getCustomNameServers().stream()
                .map(NameServer::parse)
                .collect(Collectors.toList());
        customResolverConfig.setNameServers(customNameServers);

        if (RESOLVER_MODE_RANDOM.equals(dnsResolvers.getCustomResolverMode())
                || RESOLVER_MODE_ROUND_ROBIN.equals(dnsResolvers.getCustomResolverMode())) {
                customResolverConfig.getOptions().put(ResolverConfig.OPTION_KEY_ORDER, dnsResolvers.getCustomResolverMode());
        }

        DnsServerConfig config = getConfig();
        config.getResolverConfigs().put(RESOLVER_CUSTOM, customResolverConfig);
        config.setDefaultResolver(dnsResolvers.getDefaultResolver());

        if (RESOLVER_CUSTOM.equals(config.getDefaultResolver())) {
            log.info("updating dns config: {} - {}", config.getDefaultResolver(), customResolverConfig.getOptions().get(ResolverConfig.OPTION_KEY_ORDER));
        } else {
            log.info("updating dns config: {}", config.getDefaultResolver());
        }

        saveConfig(config);

        return getDnsResolvers();
    }

    public List<LocalDnsRecord> getLocalDnsRecords() {
        return getConfig().getLocalDnsRecords();
    }

    public List<LocalDnsRecord> setLocalDnsRecords(List<LocalDnsRecord> localDnsRecords) {
        // create new list of records based on supplied and builtin records
        Map<String, LocalDnsRecord> newRecordsByName = localDnsRecords.stream()
            .filter(r -> !r.isBuiltin())
            .map(r -> new LocalDnsRecord(r.getName(), false, false, r.getIpAddress(), r.getIp6Address(), r.getVpnIpAddress(), r.getVpnIp6Address()))
            .collect(Collectors.toMap(LocalDnsRecord::getName, Function.identity()));
        Map<String, LocalDnsRecord> builtinRecordsByName = getBuiltinLocalDnsRecords().stream()
            .collect(Collectors.toMap(LocalDnsRecord::getName, Function.identity()));
        newRecordsByName.putAll(builtinRecordsByName);

        DnsServerConfig config = getConfig();
        config.setLocalDnsRecords(new ArrayList<>(newRecordsByName.values()));
        config.setVpnSubnetIp(vpnSubnetIp);
        config.setVpnSubnetNetmask(vpnSubnetNetmask);

        saveConfig(config);

        return config.getLocalDnsRecords();
    }

    public void refreshLocalDnsRecords() {
        setLocalDnsRecords(getConfig().getLocalDnsRecords());
    }

    public void setFilteredPeers(Set<IpAddress> peerIps, Set<IpAddress> peerDefaultAllowIps) {
        Set<String> peers = mapIpAddressesToStrings(peerIps);
        Set<String> peersDefaultAllow = mapIpAddressesToStrings(peerDefaultAllowIps);

        DnsServerConfig config = getConfig();
        if (!peers.equals(config.getFilteredPeers()) || !(peersDefaultAllow.equals(config.getFilteredPeersDefaultAllow()))) {
            config.setFilteredPeers(peers);
            config.setFilteredPeerDefaultAllow(peersDefaultAllow);
            saveConfig(config);
        }
    }

    public void flushCache() {
        pubSubService.publish(channelName, flushCommand);
    }

    public synchronized void addVpnResolver(int id, List<String> nameServers, String bindHost) {
        ResolverConfig resolverConfig = createResolverConfig(nameServers);
        resolverConfig.getOptions().put(ResolverConfig.OPTION_KEY_BIND_HOST, bindHost);
        DnsServerConfig serverConfig = getConfig();
        serverConfig.getResolverConfigs().put(RESOLVER_VPN_PREFIX + id, resolverConfig);
        saveConfig(serverConfig);
    }

    public synchronized void removeVpnResolver(int id) {
        String key = RESOLVER_VPN_PREFIX + id;
        DnsServerConfig config = getConfig();
        config.getResolverConfigs().remove(key);
        config.setResolverConfigNameByIp(config.getResolverConfigNameByIp().entrySet().stream()
                .filter(e -> !e.getValue().equals(key))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        saveConfig(config);
    }

    public synchronized void useVpnResolver(Device device, int id) {
        setClientResolverConfig(device, RESOLVER_VPN_PREFIX + id);
    }

    public synchronized void useTorResolver(Device device) {
        setClientResolverConfig(device, RESOLVER_TOR);
    }

    public synchronized void useDefaultResolver(Device device) {
        setClientResolverConfig(device, null);
    }

    private synchronized void updateDhcpNameServers() {
        List<String> nameServers = new ArrayList<>(dhcpLeaseNameServers);
        nameServers.addAll(routerAdvertisedNameServers);
        log.info("updating dhcp name servers: {}", nameServers);
        DnsServerConfig config = getConfig();
        config.getResolverConfigs().put(RESOLVER_DHCP, createResolverConfig(nameServers));
        saveConfig(config);
    }

    private Set<String> mapIpAddressesToStrings(Set<IpAddress> ipAddresses) {
        return ipAddresses.stream().map(IpAddress::toString).collect(Collectors.toSet());
    }

    private void setClientResolverConfig(Device device, String resolver) {
        DnsServerConfig config = getConfig();
        if (resolver == null || resolver.equals(config.getDefaultResolver())) {
            state.getResolverByDeviceId().remove(device.getId());
        } else {
            state.getResolverByDeviceId().put(device.getId(), resolver);
        }
        dataSource.save(state);
        updateResolverByIpMapping();
    }

    private void updateResolverByIpMapping() {
        cleanUpResolverByDeviceId();

        DnsServerConfig config = getConfig();
        Map<String, String> nameById = createResolverConfigNameByIpMapping();
        if (!nameById.equals(config.getResolverConfigNameByIp())) {
            config.setResolverConfigNameByIp(nameById);
            saveConfig(config);
        }
    }

    private void cleanUpResolverByDeviceId() {
        state.setResolverByDeviceId(state.getResolverByDeviceId().entrySet().stream()
            .filter(e -> deviceService.getDeviceById(e.getKey()) != null)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        dataSource.save(state);
    }

    private Map<String, String> createResolverConfigNameByIpMapping() {
        Map<String, String> nameByIp = new HashMap<>();
        for(Map.Entry<String, String> e : state.getResolverByDeviceId().entrySet()) {
            Device device = deviceService.getDeviceById(e.getKey());
            device.getIpAddresses().forEach(ip -> nameByIp.put(ip.toString(), e.getValue()));
        }
        return nameByIp;
    }

    private void loadState() {
        state = dataSource.get(EblockerDnsServerState.class);
        if (state == null) {
            state = new EblockerDnsServerState();
            state.setResolverByDeviceId(new HashMap<>());
            dataSource.save(state);
        }
    }

    private List<String> readDhcpLeaseNameServers() {
        try {
            DhcpClientLease lease = dhcpClientLeaseReader.readLease();
            if (lease == null) {
                log.error("No client leases found, can not initialize name servers.");
            } else if (lease.getOptions() == null || lease.getOptions().get("domain-name-servers") == null) {
                log.warn("Client lease contains no nameservers.");
            } else {
                String nameServerOption = lease.getOptions().get("domain-name-servers");
                return DhcpUtils.parseDnsServersOption(nameServerOption);
            }
        } catch (EblockerException e) {
            log.error("failed to read dhclient leases", e);
        }
        return Collections.emptyList();
    }

    /**
     * Loads stored dns configuration or create default if none exists
     */
    DnsServerConfig getConfig() {
        DnsServerConfig config = dataSource.get(DnsServerConfig.class);
        if (config == null) {
            config = createDefaultConfig(RESOLVER_CUSTOM);
        }
        return config;
    }

    private DnsServerConfig getOrCreateDefaultConfig(String defaultResolver) {
        DnsServerConfig config = dataSource.get(DnsServerConfig.class);
        if (config == null) {
            config = createDefaultConfig(defaultResolver);
        }
        return config;
    }

    private DnsServerConfig createDefaultConfig(String defaultResolver) {
        DnsServerConfig config = new DnsServerConfig();
        config.setResolverConfigs(new HashMap<>());
        config.setResolverConfigNameByIp(new HashMap<>());
        config.setDefaultResolver(defaultResolver);

        Splitter splitter = Splitter.on(',').trimResults().omitEmptyStrings();
        config.getResolverConfigs().put(RESOLVER_CUSTOM,
                createResolverConfig(splitter.splitToList(defaultCustomNameServers)));
        config.getResolverConfigs().put(RESOLVER_TOR,
                createResolverConfig(splitter.splitToList(defaultTorNameServers)));

        config.setLocalDnsRecords(getBuiltinLocalDnsRecords());
        config.setFilteredPeers(Collections.emptySet());
        config.setVpnSubnetIp(vpnSubnetIp);
        config.setVpnSubnetNetmask(vpnSubnetNetmask);

        return config;
    }

    private ResolverConfig createResolverConfig(List<String> nameservers) {
        ResolverConfig config = new ResolverConfig();
        config.setNameServers(nameservers.stream()
                .map(NameServer::parse).collect(Collectors.toList()));
        return config;
    }

    private List<String> mapNameServers(ResolverConfig config) {
        if (config == null) {
            return Collections.emptyList();
        }
        return config.getNameServers().stream().map(NameServer::toString).collect(Collectors.toList());
    }

    private String mapOrderOption(ResolverConfig config) {
        String value = config.getOptions().get(ResolverConfig.OPTION_KEY_ORDER);
        if (RESOLVER_MODE_RANDOM.equals(value) || RESOLVER_MODE_ROUND_ROBIN.equals(value)) {
            return value;
        }
        return RESOLVER_MODE_DEFAULT;
    }

    private <T> void addNonNull(Collection<T> collection, T item) {
        if (item != null) {
            collection.add(item);
        }
    }

    private void saveConfig(DnsServerConfig config) {
        dataSource.save(config);
        pubSubService.publish(channelName, updateCommand);
    }

    private void createEnabledFlagFile() {
        Path path = Paths.get(enabledFlagFile);
        if (!path.toFile().exists()) {
            try {
                Files.createFile(path);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to create flag file", e);
            }
        }
    }

    private void deleteEnabledFlagFile() {
        try {
            Files.deleteIfExists(Paths.get(enabledFlagFile));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete flag file", e);
        }
    }

    private List<LocalDnsRecord> getBuiltinLocalDnsRecords() {
        List<LocalDnsRecord> records = new ArrayList<>();

        Ip4Address localIp = networkInterface.getFirstIPv4Address();
        Ip6Address localIp6 = networkInterface.getIp6LinkLocalAddress();
        Ip4Address vpnIp = networkInterface.getVpnIpv4Address();
        Splitter splitter = Splitter.on(',').trimResults().omitEmptyStrings();
        splitter.splitToList(defaultLocalNames).stream()
            .map(n -> new LocalDnsRecord(n, true, false, localIp, localIp6, vpnIp, null))
            .forEach(records::add);

        records.add(new LocalDnsRecord(controlBarHostname, true, true, controlBarIpAddress, null, controlBarIpAddress, null));
        return records;
    }

    public void setDnsCustomResolver() {
        DnsServerConfig config = getConfig();

        if (!RESOLVER_CUSTOM.equals(config.getDefaultResolver())) {
            config.setDefaultResolver(RESOLVER_CUSTOM);
            log.info("updating dns config: {}", RESOLVER_CUSTOM);

            saveConfig(config);
        }
    }

    public interface Listener {
        void onEnable(boolean enabled);
    }

    private static class Tuple<U, V> {
        private final U u;
        private final V v;

        public Tuple(U u, V v) {
            this.u = u;
            this.v = v;
        }

        U u() {
            return u;
        }

        V v() {
            return v;
        }
    }

}
