/*
 * Copyright 2026 eBlocker Open Source GmbH
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
package org.eblocker.server.http.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.data.events.Events;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.data.vpn.ExternalAddressType;
import org.eblocker.server.common.data.vpn.PortForwardingMode;
import org.eblocker.server.common.data.vpn.VpnServerStatus;
import org.eblocker.server.common.exceptions.UpnpPortForwardingException;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.common.data.wireguard.WireGuardKeyPair;
import org.eblocker.server.common.data.wireguard.WireGuardMobilePeer;
import org.eblocker.server.common.data.wireguard.WireGuardMobileServer;
import org.eblocker.server.common.wireguard.WireGuardKeyService;
import org.eblocker.server.common.wireguard.WireGuardMobileConfigurationRenderer;
import org.eblocker.server.common.wireguard.WireGuardMobileServerConfigurationRenderer;
import org.eblocker.server.upnp.UpnpManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

@Singleton
@SubSystemService(value = SubSystem.SERVICES)
public class WireGuardMobileService extends VpnServerService {
    private static final Logger log = LoggerFactory.getLogger(WireGuardMobileService.class);
    private static final Set<PosixFilePermission> SERVER_CONFIG_FILE_PERMISSIONS = Collections.unmodifiableSet(EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE));

    private final DataSource dataSource;
    private final ScriptRunner scriptRunner;
    private final DeviceService deviceService;
    private final NetworkStateMachine networkStateMachine;
    private final EblockerDnsServer dnsServer;
    private final DnsService dnsService;
    private final DynDnsService dynDnsService;
    private final ScheduledExecutorService executorService;
    private final EventLogger eventLogger;
    private final WireGuardKeyService keyService;
    private final WireGuardMobileConfigurationRenderer renderer;
    private final WireGuardMobileServerConfigurationRenderer serverRenderer;
    private final Path serverConfigPath;
    private final String serverAddress;
    private final String peerAddressPrefix;
    private final String peerAddressIp6Prefix;
    private final String dns;
    private final String allowedIps;
    private final int persistentKeepalive;
    private final String startCommand;
    private final String stopCommand;
    private final String statusCommand;
    private final String purgeCommand;

    @Inject
    public WireGuardMobileService(ScriptRunner scriptRunner,
                                  DataSource dataSource,
                                  DeviceService deviceService,
                                  NetworkStateMachine networkStateMachine,
                                  UpnpManagementService upnpService,
                                  EblockerDnsServer dnsServer,
                                  DnsService dnsService,
                                  DynDnsService dynDnsService,
                                  @Named("lowPrioScheduledExecutor") ScheduledExecutorService executorService,
                                  EventLogger eventLogger,
                                  WireGuardKeyService keyService,
                                  WireGuardMobileConfigurationRenderer renderer,
                                  WireGuardMobileServerConfigurationRenderer serverRenderer,
                                  @Named("wireguard.mobile.server.port") int port,
                                  @Named("wireguard.mobile.server.portforwarding.duration.initial") int tempDuration,
                                  @Named("wireguard.mobile.server.portforwarding.duration.use") int duration,
                                  @Named("wireguard.mobile.server.portforwarding.description") String portForwardingDescription,
                                  @Named("wireguard.mobile.server.config.path") String serverConfigPath,
                                  @Named("wireguard.mobile.server.start.command") String startCommand,
                                  @Named("wireguard.mobile.server.stop.command") String stopCommand,
                                  @Named("wireguard.mobile.server.status.command") String statusCommand,
                                  @Named("wireguard.mobile.server.purge.command") String purgeCommand,
                                  @Named("wireguard.mobile.server.address") String serverAddress,
                                  @Named("wireguard.mobile.peer.address.prefix") String peerAddressPrefix,
                                  @Named("wireguard.mobile.peer.address.ip6.prefix") String peerAddressIp6Prefix,
                                  @Named("wireguard.mobile.dns") String dns,
                                  @Named("wireguard.mobile.allowed.ips") String allowedIps,
                                  @Named("wireguard.mobile.persistent.keepalive") int persistentKeepalive) {
        super(upnpService, port, portForwardingDescription, tempDuration, duration);
        this.scriptRunner = scriptRunner;
        this.dataSource = dataSource;
        this.deviceService = deviceService;
        this.networkStateMachine = networkStateMachine;
        this.dnsServer = dnsServer;
        this.dnsService = dnsService;
        this.dynDnsService = dynDnsService;
        this.executorService = executorService;
        this.eventLogger = eventLogger;
        this.keyService = keyService;
        this.renderer = renderer;
        this.serverRenderer = serverRenderer;
        this.serverConfigPath = Path.of(serverConfigPath);
        this.startCommand = startCommand;
        this.stopCommand = stopCommand;
        this.statusCommand = statusCommand;
        this.purgeCommand = purgeCommand;
        this.serverAddress = serverAddress;
        this.peerAddressPrefix = peerAddressPrefix;
        this.peerAddressIp6Prefix = peerAddressIp6Prefix;
        this.dns = dns;
        this.allowedIps = allowedIps;
        this.persistentKeepalive = persistentKeepalive;
    }

    @SubSystemInit
    public void init() {
        initDeviceListener();
        if (isServerEnabled()) {
            executorService.execute(this::initStartServer);
        }
    }

    private void initDeviceListener() {
        deviceService.addListener(new DeviceService.DeviceChangeListener() {
            @Override
            public void onChange(Device device) {
            }

            @Override
            public void onDelete(Device device) {
                removePeer(device.getId());
            }

            @Override
            public void onReset(Device device) {
            }
        });
    }

    private void initStartServer() {
        if (startServer()) {
            try {
                enablePortForwarding();
            } catch (UpnpPortForwardingException e) {
                log.error("Problem starting the WireGuard mobile server", e);
                eventLogger.log(Events.upnpPortForwardingFailed());
            }
        }
    }

    public VpnServerStatus getServerStatus() {
        VpnServerStatus result = new VpnServerStatus();
        result.setFirstStart(isFirstRun());
        result.setHost(getServerHost());
        result.setRunning(isServerRunning());
        result.setExternalAddressType(getExternalAddressType());
        result.setMappedPort(getMappedPort());
        result.setPortForwardingMode(getPortForwardingMode());
        return result;
    }

    public VpnServerStatus setServerStatus(VpnServerStatus requestedStatus) {
        VpnServerStatus result = updateServerAccess(requestedStatus);
        if (isServerRunning() == requestedStatus.isRunning()) {
            result.setRunning(requestedStatus.isRunning());
        } else if (requestedStatus.isRunning()) {
            result.setRunning(startServer());
        } else {
            boolean stopped = stopServer();
            result.setRunning(!stopped);
            if (stopped) {
                disableServer();
            }
        }
        result.setFirstStart(isFirstRun());
        return result;
    }

    public boolean resetServer() {
        setFirstRun(true);
        boolean result = stopServer();
        if (result) {
            disableServer();
            try {
                disablePortForwarding();
            } catch (UpnpPortForwardingException e) {
                log.error("Unable to reset port forwarding during WireGuard mobile reset", e);
            }
            result = purgeServer();
            dataSource.delete(WireGuardMobileServer.class);
            getPeers().forEach(peer -> dataSource.delete(WireGuardMobilePeer.class, peer.getId()));
        }
        return result;
    }

    public String generateClientConfiguration(String deviceId, String endpointHost, int endpointPort) throws IOException, InterruptedException {
        WireGuardMobileServer server = getOrCreateServer();
        WireGuardMobilePeer peer = getOrCreatePeer(deviceId);
        markDeviceConnectedToMobileVpn(peer);
        return renderer.render(server, peer, endpointHost, endpointPort, dns, allowedIps, persistentKeepalive);
    }

    public String renderServerConfiguration(int listenPort) throws IOException, InterruptedException {
        return serverRenderer.render(getOrCreateServer(), getRepairedPeers(), listenPort);
    }

    public void writeServerConfiguration(Path path, int listenPort) throws IOException, InterruptedException {
        Files.createDirectories(path.getParent());
        Files.write(path, renderServerConfiguration(listenPort).getBytes(StandardCharsets.UTF_8));
        Files.setPosixFilePermissions(path, SERVER_CONFIG_FILE_PERMISSIONS);
    }

    public boolean reloadServerConfiguration() {
        try {
            writeServerConfiguration(serverConfigPath, getMappedPort());
            if (!isServerRunning()) {
                return true;
            }
            return stopServer() && startServer();
        } catch (IOException e) {
            log.error("WireGuard mobile server configuration could not be reloaded", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("WireGuard mobile server configuration reload interrupted", e);
        }
        return false;
    }

    public List<WireGuardMobilePeer> getPeers() {
        List<WireGuardMobilePeer> peers = dataSource.getAll(WireGuardMobilePeer.class);
        return peers != null ? peers : Collections.emptyList();
    }

    public void removePeer(String deviceId) {
        WireGuardMobilePeer peer = getPeer(deviceId);
        if (peer != null) {
            markDeviceDisconnectedFromMobileVpn(peer);
            dataSource.delete(WireGuardMobilePeer.class, peer.getId());
        }
    }

    public String getServerHost() {
        return dataSource.getWireGuardMobileServerHost();
    }

    public Integer getWireGuardMappedPort() {
        Integer mappedPort = dataSource.getWireGuardMobileMappedPort();
        return mappedPort != null ? mappedPort : serverPort;
    }

    @Override
    protected int getMappedPort() {
        return getWireGuardMappedPort();
    }

    @Override
    protected PortForwardingMode getPortForwardingMode() {
        return dataSource.getWireGuardMobilePortForwardingMode();
    }

    private boolean startServer() {
        if (!dnsServer.isEnabled() && !dnsService.setStatus(true)) {
            log.error("DNS server could not be started, refusing to start WireGuard mobile server");
            return false;
        }
        try {
            writeServerConfiguration(serverConfigPath, getMappedPort());
            if (runCommand(startCommand)) {
                setFirstRun(false);
                dataSource.setWireGuardMobileServerState(true);
                markPeerDevicesConnectedToMobileVpn();
                return true;
            }
        } catch (IOException e) {
            log.error("WireGuard mobile server configuration could not be written", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("WireGuard mobile server start interrupted", e);
        }
        return false;
    }

    private boolean stopServer() {
        return runCommand(stopCommand);
    }

    private boolean isServerRunning() {
        return runCommand(statusCommand);
    }

    private boolean purgeServer() {
        return runCommand(purgeCommand);
    }

    private boolean runCommand(String command) {
        try {
            return scriptRunner.runScript(command, serverConfigPath.toString()) == 0;
        } catch (IOException e) {
            log.error("Could not run WireGuard mobile command {}", command, e);
        } catch (InterruptedException e) {
            log.error("WireGuard mobile command {} interrupted", command, e);
            Thread.currentThread().interrupt();
        }
        return false;
    }

    private VpnServerStatus updateServerAccess(VpnServerStatus requestedStatus) {
        VpnServerStatus result = new VpnServerStatus();
        if (requestedStatus.getExternalAddressType() == ExternalAddressType.EBLOCKER_DYN_DNS) {
            if (!dynDnsService.isEnabled()) {
                dynDnsService.enable();
                dynDnsService.update();
            }
            setServerHost(dynDnsService.getHostname());
        } else {
            if (dynDnsService.isEnabled()) {
                dynDnsService.disable();
            }
            setServerHost(requestedStatus.getHost() != null ? requestedStatus.getHost() : "");
        }
        result.setHost(getServerHost());

        setExternalAddressType(requestedStatus.getExternalAddressType());
        result.setExternalAddressType(requestedStatus.getExternalAddressType());

        Integer mappedPort = requestedStatus.getMappedPort();
        if (mappedPort != null) {
            setMappedPort(mappedPort);
        }
        result.setMappedPort(mappedPort);

        PortForwardingMode portForwardingMode = requestedStatus.getPortForwardingMode();
        if (portForwardingMode == null) {
            portForwardingMode = getPortForwardingMode();
        }
        result.setPortForwardingMode(portForwardingMode);
        setPortForwardingMode(portForwardingMode);
        return result;
    }

    private void disableServer() {
        markAllPeerDevicesDisconnectedFromMobileVpn();
        dataSource.setWireGuardMobileServerState(false);
    }

    private boolean isServerEnabled() {
        return dataSource.getWireGuardMobileServerState();
    }

    private boolean isFirstRun() {
        return dataSource.getWireGuardMobileServerFirstRun();
    }

    private void setFirstRun(boolean state) {
        dataSource.setWireGuardMobileServerFirstRun(state);
    }

    private void setServerHost(String host) {
        dataSource.setWireGuardMobileServerHost(host);
    }

    private ExternalAddressType getExternalAddressType() {
        return dataSource.getWireGuardMobileExternalAddressType();
    }

    private void setExternalAddressType(ExternalAddressType type) {
        dataSource.setWireGuardMobileExternalAddressType(type);
    }

    private void setMappedPort(Integer port) {
        dataSource.setWireGuardMobileMappedPort(port);
    }

    private void setPortForwardingMode(PortForwardingMode mode) {
        dataSource.setWireGuardMobilePortForwardingMode(mode);
    }

    private void markPeerDevicesConnectedToMobileVpn() {
        getRepairedPeers().forEach(this::markDeviceConnectedToMobileVpn);
    }

    private List<WireGuardMobilePeer> getRepairedPeers() {
        List<WireGuardMobilePeer> peers = getPeers();
        peers.forEach(this::repairPeerAddressIfNecessary);
        return peers;
    }

    private void markAllPeerDevicesDisconnectedFromMobileVpn() {
        getPeers().forEach(this::markDeviceDisconnectedFromMobileVpn);

        deviceService.getDevices(false).stream()
                .filter(device -> device.isVpnClient() || device.getIpAddresses().stream().anyMatch(this::isMobileVpnPeerIp))
                .forEach(this::clearMobileVpnState);
    }

    private void markDeviceConnectedToMobileVpn(WireGuardMobilePeer peer) {
        Device device = deviceService.getDeviceById(peer.getDeviceId());
        if (device == null) {
            log.warn("Could not mark WireGuard mobile peer {} as connected: device {} not found", peer.getId(), peer.getDeviceId());
            return;
        }

        List<IpAddress> peerIps = peerIpAddresses(peer);
        List<IpAddress> ipAddresses = new ArrayList<>(device.getIpAddresses());
        boolean changed = false;

        for (IpAddress peerIp : peerIps) {
            if (!ipAddresses.contains(peerIp)) {
                ipAddresses.add(peerIp);
                changed = true;
            }
        }

        if (!device.isVpnClient()) {
            device.setIsVpnClient(true);
            changed = true;
        }

        if (changed) {
            device.setIpAddresses(ipAddresses);
            deviceService.updateDevice(device);
        }
        networkStateMachine.deviceStateChanged(device);
    }

    private void markDeviceDisconnectedFromMobileVpn(WireGuardMobilePeer peer) {
        Device device = deviceService.getDeviceById(peer.getDeviceId());
        if (device == null) {
            return;
        }

        List<IpAddress> peerIps = peerIpAddresses(peer);
        List<IpAddress> ipAddresses = new ArrayList<>(device.getIpAddresses());
        boolean changed = ipAddresses.removeAll(peerIps);

        if (device.isVpnClient()) {
            device.setIsVpnClient(false);
            changed = true;
        }

        if (changed) {
            device.setIpAddresses(ipAddresses);
            deviceService.updateDevice(device);
            networkStateMachine.deviceStateChanged(device);
        }
    }

    private void clearMobileVpnState(Device device) {
        List<IpAddress> ipAddresses = new ArrayList<>(device.getIpAddresses());
        boolean changed = ipAddresses.removeIf(this::isMobileVpnPeerIp);

        if (device.isVpnClient()) {
            device.setIsVpnClient(false);
            changed = true;
        }

        if (changed) {
            device.setIpAddresses(ipAddresses);
            deviceService.updateDevice(device);
            networkStateMachine.deviceStateChanged(device);
        }
    }

    private List<IpAddress> peerIpAddresses(WireGuardMobilePeer peer) {
        List<IpAddress> addresses = new ArrayList<>();
        addresses.add(IpAddress.parse(addressWithoutCidr(peer.getAddress())));
        if (peer.getAddressIp6() != null && !peer.getAddressIp6().isEmpty()) {
            addresses.add(IpAddress.parse(addressWithoutCidr(peer.getAddressIp6())));
        }
        return addresses;
    }

    private String addressWithoutCidr(String address) {
        return address.replaceFirst("/.*$", "");
    }

    private boolean isMobileVpnPeerIp(IpAddress ipAddress) {
        return ipAddress.toString().startsWith(peerAddressPrefix) || ipAddress.toString().startsWith(peerAddressIp6Prefix);
    }

    private WireGuardMobileServer getOrCreateServer() throws IOException, InterruptedException {
        WireGuardMobileServer server = dataSource.get(WireGuardMobileServer.class);
        if (server != null) {
            repairServerAddressIfNecessary(server);
            return server;
        }
        WireGuardKeyPair keyPair = keyService.generateKeyPair();
        server = new WireGuardMobileServer();
        server.setPrivateKey(keyPair.getPrivateKey());
        server.setPublicKey(keyPair.getPublicKey());
        server.setAddress(serverAddress);
        dataSource.save(server);
        return server;
    }

    private void repairServerAddressIfNecessary(WireGuardMobileServer server) {
        if (server.getAddress() == null || !server.getAddress().contains(":")) {
            server.setAddress(serverAddress);
            dataSource.save(server);
        }
    }

    private WireGuardMobilePeer getOrCreatePeer(String deviceId) throws IOException, InterruptedException {
        WireGuardMobilePeer peer = getPeer(deviceId);
        if (peer != null) {
            repairPeerAddressIfNecessary(peer);
            return peer;
        }
        int id = dataSource.nextId(WireGuardMobilePeer.class);
        WireGuardKeyPair keyPair = keyService.generateKeyPair();
        peer = new WireGuardMobilePeer(id, deviceId);
        peer.setPrivateKey(keyPair.getPrivateKey());
        peer.setPublicKey(keyPair.getPublicKey());
        peer.setPresharedKey(keyService.generatePresharedKey());
        peer.setAddress(allocatePeerAddress(id, peer));
        peer.setAddressIp6(allocatePeerAddressIp6(id, peer));
        dataSource.save(peer, id);
        return peer;
    }

    private void repairPeerAddressIfNecessary(WireGuardMobilePeer peer) {
        String serverPeerAddress = serverAddress.replaceFirst("/.*$", "/32");
        boolean changed = false;
        if (peer.getAddress() == null || serverPeerAddress.equals(peer.getAddress())) {
            peer.setAddress(allocatePeerAddress(peer.getId(), peer));
            changed = true;
        }
        if (peer.getAddressIp6() == null || peer.getAddressIp6().isEmpty()) {
            peer.setAddressIp6(allocatePeerAddressIp6(peer.getId(), peer));
            changed = true;
        }
        if (changed) {
            dataSource.save(peer, peer.getId());
        }
    }

    private String allocatePeerAddress(Integer id, WireGuardMobilePeer currentPeer) {
        Set<String> usedAddresses = getPeers().stream()
                .filter(peer -> !peer.getId().equals(currentPeer.getId()))
                .map(WireGuardMobilePeer::getAddress)
                .collect(Collectors.toSet());

        int preferredHost = id != null ? id + 1 : 2;
        for (int offset = 0; offset < 253; offset++) {
            int host = 2 + Math.floorMod(preferredHost - 2 + offset, 253);
            String address = peerAddressPrefix + host + "/32";
            if (!usedAddresses.contains(address)) {
                return address;
            }
        }

        throw new IllegalStateException("No WireGuard mobile peer address available");
    }

    private String allocatePeerAddressIp6(Integer id, WireGuardMobilePeer currentPeer) {
        Set<String> usedAddresses = getPeers().stream()
                .filter(peer -> !peer.getId().equals(currentPeer.getId()))
                .map(WireGuardMobilePeer::getAddressIp6)
                .collect(Collectors.toSet());

        int preferredHost = getPreferredIp6Host(id, currentPeer);
        for (int offset = 0; offset < 253; offset++) {
            int host = 2 + Math.floorMod(preferredHost - 2 + offset, 253);
            String address = peerAddressIp6Prefix + Integer.toHexString(host) + "/128";
            if (!usedAddresses.contains(address)) {
                return address;
            }
        }

        throw new IllegalStateException("No WireGuard mobile IPv6 peer address available");
    }

    private int getPreferredIp6Host(Integer id, WireGuardMobilePeer currentPeer) {
        Integer hostFromIp4 = getIp4Host(currentPeer.getAddress());
        if (hostFromIp4 != null) {
            return hostFromIp4;
        }
        return id != null ? id + 1 : 2;
    }

    private Integer getIp4Host(String address) {
        if (address == null) {
            return null;
        }
        String ip = addressWithoutCidr(address);
        int lastDot = ip.lastIndexOf('.');
        if (lastDot < 0 || lastDot == ip.length() - 1) {
            return null;
        }
        try {
            int host = Integer.parseInt(ip.substring(lastDot + 1));
            return host >= 2 && host <= 254 ? host : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private WireGuardMobilePeer getPeer(String deviceId) {
        return getPeers().stream()
                .filter(peer -> deviceId.equals(peer.getDeviceId()))
                .findFirst()
                .orElse(null);
    }
}
