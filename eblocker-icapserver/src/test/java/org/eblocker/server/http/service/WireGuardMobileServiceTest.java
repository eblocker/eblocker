/*
 * Copyright 2026 eBlocker Open Source UG (haftungsbeschraenkt)
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

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.vpn.PortForwardingMode;
import org.eblocker.server.common.data.vpn.VpnServerStatus;
import org.eblocker.server.common.data.wireguard.WireGuardKeyPair;
import org.eblocker.server.common.data.wireguard.WireGuardMobilePeer;
import org.eblocker.server.common.data.wireguard.WireGuardMobileServer;
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.upnp.UpnpManagementService;

import java.util.concurrent.ScheduledExecutorService;
import org.eblocker.server.common.wireguard.WireGuardKeyService;
import org.eblocker.server.common.wireguard.WireGuardMobileConfigurationRenderer;
import org.eblocker.server.common.wireguard.WireGuardMobileServerConfigurationRenderer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.InputStream;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;

public class WireGuardMobileServiceTest {
    private DataSource dataSource;
    private WireGuardKeyService keyService;
    private ScriptRunner scriptRunner;
    private DeviceService deviceService;
    private NetworkStateMachine networkStateMachine;
    private EblockerDnsServer dnsServer;
    private DnsService dnsService;
    private Path serverConfigPath;
    private WireGuardMobileService service;

    @Before
    public void setUp() throws Exception {
        dataSource = Mockito.mock(DataSource.class);
        keyService = Mockito.mock(WireGuardKeyService.class);
        scriptRunner = Mockito.mock(ScriptRunner.class);
        deviceService = Mockito.mock(DeviceService.class);
        networkStateMachine = Mockito.mock(NetworkStateMachine.class);
        dnsServer = Mockito.mock(EblockerDnsServer.class);
        dnsService = Mockito.mock(DnsService.class);
        serverConfigPath = Files.createTempDirectory("wireguard-mobile").resolve("eblocker-mobile.conf");
        service = new WireGuardMobileService(
                scriptRunner,
                dataSource,
                deviceService,
                networkStateMachine,
                Mockito.mock(UpnpManagementService.class),
                dnsServer,
                dnsService,
                Mockito.mock(DynDnsService.class),
                Mockito.mock(ScheduledExecutorService.class),
                Mockito.mock(EventLogger.class),
                keyService,
                new WireGuardMobileConfigurationRenderer(),
                new WireGuardMobileServerConfigurationRenderer(),
                1194,
                60,
                600,
                "eBlocker Mobile WireGuard",
                serverConfigPath.toString(),
                "wireguard_mobile_start",
                "wireguard_mobile_down",
                "wireguard_mobile_status",
                "wireguard_mobile_purge",
                "10.8.0.1/24, fd42:eb10:8::1/64",
                "10.8.0.",
                "fd42:eb10:8::",
                "10.8.0.1, fd42:eb10:8::1",
                "0.0.0.0/0, ::/0",
                25);
    }

    @Test
    public void generateClientConfigurationCreatesServerAndPeerKeys() throws Exception {
        Mockito.when(dataSource.get(WireGuardMobileServer.class)).thenReturn(null);
        Mockito.when(dataSource.getAll(WireGuardMobilePeer.class)).thenReturn(Collections.emptyList());
        Mockito.when(dataSource.nextId(WireGuardMobilePeer.class)).thenReturn(7);
        Mockito.when(keyService.generateKeyPair())
                .thenReturn(new WireGuardKeyPair("server-private-key", "server-public-key"))
                .thenReturn(new WireGuardKeyPair("peer-private-key", "peer-public-key"));
        Mockito.when(keyService.generatePresharedKey()).thenReturn("peer-psk");

        String config = service.generateClientConfiguration("device:001122334455", "vpn.example.org", 51820);

        ArgumentCaptor<WireGuardMobileServer> serverCaptor = ArgumentCaptor.forClass(WireGuardMobileServer.class);
        Mockito.verify(dataSource).save(serverCaptor.capture());
        Assert.assertEquals("server-private-key", serverCaptor.getValue().getPrivateKey());
        Assert.assertEquals("server-public-key", serverCaptor.getValue().getPublicKey());
        Assert.assertEquals("10.8.0.1/24, fd42:eb10:8::1/64", serverCaptor.getValue().getAddress());

        ArgumentCaptor<WireGuardMobilePeer> peerCaptor = ArgumentCaptor.forClass(WireGuardMobilePeer.class);
        Mockito.verify(dataSource).save(peerCaptor.capture(), Mockito.eq(7));
        Assert.assertEquals(Integer.valueOf(7), peerCaptor.getValue().getId());
        Assert.assertEquals("device:001122334455", peerCaptor.getValue().getDeviceId());
        Assert.assertEquals("peer-private-key", peerCaptor.getValue().getPrivateKey());
        Assert.assertEquals("peer-public-key", peerCaptor.getValue().getPublicKey());
        Assert.assertEquals("peer-psk", peerCaptor.getValue().getPresharedKey());
        Assert.assertEquals("10.8.0.8/32", peerCaptor.getValue().getAddress());
        Assert.assertEquals("fd42:eb10:8::8/128", peerCaptor.getValue().getAddressIp6());

        Assert.assertTrue(config.contains("PrivateKey = peer-private-key"));
        Assert.assertTrue(config.contains("Address = 10.8.0.8/32, fd42:eb10:8::8/128"));
        Assert.assertTrue(config.contains("DNS = 10.8.0.1, fd42:eb10:8::1"));
        Assert.assertTrue(config.contains("PublicKey = server-public-key"));
        Assert.assertTrue(config.contains("PresharedKey = peer-psk"));
        Assert.assertTrue(config.contains("Endpoint = vpn.example.org:51820"));
        Assert.assertTrue(config.contains("AllowedIPs = 0.0.0.0/0, ::/0"));
        Assert.assertTrue(config.contains("PersistentKeepalive = 25"));
    }

    @Test
    public void generateClientConfigurationMarksDeviceAsMobileVpnClient() throws Exception {
        WireGuardMobileServer server = new WireGuardMobileServer();
        server.setPrivateKey("server-private-key");
        server.setPublicKey("server-public-key");
        server.setAddress("10.8.0.1/24, fd42:eb10:8::1/64");
        WireGuardMobilePeer peer = new WireGuardMobilePeer(4, "device:001122334455");
        peer.setPrivateKey("peer-private-key");
        peer.setPublicKey("peer-public-key");
        peer.setPresharedKey("peer-psk");
        peer.setAddress("10.8.0.4/32");
        peer.setAddressIp6("fd42:eb10:8::4/128");
        Device device = new Device();
        device.setId("device:001122334455");
        device.setIpAddresses(List.of(IpAddress.parse("192.168.1.42")));

        Mockito.when(dataSource.get(WireGuardMobileServer.class)).thenReturn(server);
        Mockito.when(dataSource.getAll(WireGuardMobilePeer.class)).thenReturn(Arrays.asList(peer));
        Mockito.when(deviceService.getDeviceById("device:001122334455")).thenReturn(device);

        String config = service.generateClientConfiguration("device:001122334455", "vpn.example.org", 51820);

        Assert.assertTrue(config.contains("Address = 10.8.0.4/32, fd42:eb10:8::4/128"));
        Assert.assertTrue(device.isVpnClient());
        Assert.assertEquals(List.of(IpAddress.parse("192.168.1.42"), IpAddress.parse("10.8.0.4"), IpAddress.parse("fd42:eb10:8::4")), device.getIpAddresses());
        Mockito.verify(deviceService).updateDevice(device);
        Mockito.verify(networkStateMachine).deviceStateChanged(device);
    }

    @Test
    public void removePeerClearsMobileVpnStateFromDevice() {
        WireGuardMobilePeer peer = new WireGuardMobilePeer(4, "device:001122334455");
        peer.setAddress("10.8.0.4/32");
        peer.setAddressIp6("fd42:eb10:8::4/128");
        Device device = new Device();
        device.setId("device:001122334455");
        device.setIsVpnClient(true);
        device.setIpAddresses(List.of(IpAddress.parse("192.168.1.42"), IpAddress.parse("10.8.0.4"), IpAddress.parse("fd42:eb10:8::4")));

        Mockito.when(dataSource.getAll(WireGuardMobilePeer.class)).thenReturn(Arrays.asList(peer));
        Mockito.when(deviceService.getDeviceById("device:001122334455")).thenReturn(device);

        service.removePeer("device:001122334455");

        Assert.assertFalse(device.isVpnClient());
        Assert.assertEquals(List.of(IpAddress.parse("192.168.1.42")), device.getIpAddresses());
        Mockito.verify(deviceService).updateDevice(device);
        Mockito.verify(networkStateMachine).deviceStateChanged(device);
        Mockito.verify(dataSource).delete(WireGuardMobilePeer.class, 4);
    }

    @Test
    public void renderServerConfigurationUsesPersistedServerAndPeers() throws Exception {
        WireGuardMobileServer server = new WireGuardMobileServer();
        server.setPrivateKey("server-private-key");
        server.setPublicKey("server-public-key");
        server.setAddress("10.8.0.1/24, fd42:eb10:8::1/64");
        WireGuardMobilePeer peer = new WireGuardMobilePeer(4, "device:001122334455");
        peer.setPublicKey("peer-public-key");
        peer.setPresharedKey("peer-psk");
        peer.setAddress("10.8.0.4/32");
        peer.setAddressIp6("fd42:eb10:8::4/128");

        Mockito.when(dataSource.get(WireGuardMobileServer.class)).thenReturn(server);
        Mockito.when(dataSource.getAll(WireGuardMobilePeer.class)).thenReturn(Arrays.asList(peer));

        String config = service.renderServerConfiguration(51820);

        Assert.assertTrue(config.contains("PrivateKey = server-private-key"));
        Assert.assertTrue(config.contains("Address = 10.8.0.1/24, fd42:eb10:8::1/64"));
        Assert.assertTrue(config.contains("ListenPort = 51820"));
        Assert.assertTrue(config.contains("PublicKey = peer-public-key"));
        Assert.assertTrue(config.contains("AllowedIPs = 10.8.0.4/32, fd42:eb10:8::4/128"));
    }

    @Test
    public void renderServerConfigurationRepairsPersistedPeerIpv6Address() throws Exception {
        WireGuardMobileServer server = new WireGuardMobileServer();
        server.setPrivateKey("server-private-key");
        server.setPublicKey("server-public-key");
        server.setAddress("10.8.0.1/24, fd42:eb10:8::1/64");
        WireGuardMobilePeer peer = new WireGuardMobilePeer(4, "device:001122334455");
        peer.setPublicKey("peer-public-key");
        peer.setPresharedKey("peer-psk");
        peer.setAddress("10.8.0.4/32");

        Mockito.when(dataSource.get(WireGuardMobileServer.class)).thenReturn(server);
        Mockito.when(dataSource.getAll(WireGuardMobilePeer.class)).thenReturn(Arrays.asList(peer));

        String config = service.renderServerConfiguration(51820);

        Assert.assertEquals("fd42:eb10:8::4/128", peer.getAddressIp6());
        Assert.assertTrue(config.contains("AllowedIPs = 10.8.0.4/32, fd42:eb10:8::4/128"));
        Mockito.verify(dataSource).save(peer, 4);
    }

    @Test
    public void writeServerConfigurationWritesRenderedConfigToFile() throws Exception {
        WireGuardMobileServer server = new WireGuardMobileServer();
        server.setPrivateKey("server-private-key");
        server.setPublicKey("server-public-key");
        server.setAddress("10.8.0.1/24");
        WireGuardMobilePeer peer = new WireGuardMobilePeer(4, "device:001122334455");
        peer.setPublicKey("peer-public-key");
        peer.setPresharedKey("peer-psk");
        peer.setAddress("10.8.0.4/32");
        Path target = Files.createTempFile("wireguard-mobile-server", ".conf");

        Mockito.when(dataSource.get(WireGuardMobileServer.class)).thenReturn(server);
        Mockito.when(dataSource.getAll(WireGuardMobilePeer.class)).thenReturn(Arrays.asList(peer));

        service.writeServerConfiguration(target, 51820);

        String config = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
        Assert.assertTrue(config.contains("PrivateKey = server-private-key"));
        Assert.assertTrue(config.contains("PublicKey = peer-public-key"));
        Assert.assertTrue(config.contains("ListenPort = 51820"));
        Assert.assertEquals(
                EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(target));
    }

    @Test
    public void defaultMobileServerConfigPathIsWritableByIcapServerUser() throws Exception {
        Properties properties = new Properties();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("configuration.properties")) {
            properties.load(inputStream);
        }

        Assert.assertEquals(
                "/opt/eblocker-icap/network/wireguard/eblocker-mobile.conf",
                properties.getProperty("wireguard.mobile.server.config.path"));
        Assert.assertEquals(
                "eblocker-mobile",
                properties.getProperty("network.vpn.interface.name"));
        Assert.assertEquals(
                "fd42:eb10:8::",
                properties.getProperty("wireguard.mobile.peer.address.ip6.prefix"));
        Assert.assertEquals(
                "10.8.0.1, fd42:eb10:8::1",
                properties.getProperty("wireguard.mobile.dns"));
    }

    @Test
    public void startServerWritesConfigurationAndStartsConfiguredPath() throws Exception {
        WireGuardMobileServer server = new WireGuardMobileServer();
        server.setPrivateKey("server-private-key");
        server.setPublicKey("server-public-key");
        server.setAddress("10.8.0.1/24");
        Mockito.when(dataSource.get(WireGuardMobileServer.class)).thenReturn(server);
        Mockito.when(dataSource.getAll(WireGuardMobilePeer.class)).thenReturn(Collections.emptyList());
        Mockito.when(dataSource.getWireGuardMobileMappedPort()).thenReturn(1194);
        Mockito.when(dnsServer.isEnabled()).thenReturn(true);
        Mockito.when(scriptRunner.runScript("wireguard_mobile_status", serverConfigPath.toString())).thenReturn(1);
        Mockito.when(scriptRunner.runScript("wireguard_mobile_start", serverConfigPath.toString())).thenReturn(0);
        VpnServerStatus requestedStatus = new VpnServerStatus();
        requestedStatus.setRunning(true);
        requestedStatus.setMappedPort(1194);
        requestedStatus.setPortForwardingMode(PortForwardingMode.MANUAL);

        VpnServerStatus result = service.setServerStatus(requestedStatus);

        Assert.assertTrue(result.isRunning());
        String writtenConfig = new String(Files.readAllBytes(serverConfigPath), StandardCharsets.UTF_8);
        Assert.assertTrue(writtenConfig.contains("PrivateKey = server-private-key"));
        Assert.assertTrue(writtenConfig.contains("ListenPort = 1194"));
        Assert.assertEquals(
                EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(serverConfigPath));
        Mockito.verify(scriptRunner).runScript("wireguard_mobile_start", serverConfigPath.toString());
        Mockito.verify(dataSource).setWireGuardMobileServerState(true);
    }

    @Test
    public void reloadServerConfigurationWritesPeersAndRestartsRunningServer() throws Exception {
        WireGuardMobileServer server = new WireGuardMobileServer();
        server.setPrivateKey("server-private-key");
        server.setPublicKey("server-public-key");
        server.setAddress("10.8.0.1/24");
        WireGuardMobilePeer peer = new WireGuardMobilePeer(4, "device:001122334455");
        peer.setPublicKey("peer-public-key");
        peer.setPresharedKey("peer-psk");
        peer.setAddress("10.8.0.4/32");
        Mockito.when(dataSource.get(WireGuardMobileServer.class)).thenReturn(server);
        Mockito.when(dataSource.getAll(WireGuardMobilePeer.class)).thenReturn(Arrays.asList(peer));
        Mockito.when(dataSource.getWireGuardMobileMappedPort()).thenReturn(1194);
        Mockito.when(dnsServer.isEnabled()).thenReturn(true);
        Mockito.when(scriptRunner.runScript("wireguard_mobile_status", serverConfigPath.toString())).thenReturn(0);
        Mockito.when(scriptRunner.runScript("wireguard_mobile_down", serverConfigPath.toString())).thenReturn(0);
        Mockito.when(scriptRunner.runScript("wireguard_mobile_start", serverConfigPath.toString())).thenReturn(0);

        Assert.assertTrue(service.reloadServerConfiguration());

        String writtenConfig = new String(Files.readAllBytes(serverConfigPath), StandardCharsets.UTF_8);
        Assert.assertTrue(writtenConfig.contains("PublicKey = peer-public-key"));
        Assert.assertTrue(writtenConfig.contains("AllowedIPs = 10.8.0.4/32"));
        Assert.assertEquals(
                EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(serverConfigPath));
        Mockito.verify(scriptRunner).runScript("wireguard_mobile_down", serverConfigPath.toString());
        Mockito.verify(scriptRunner).runScript("wireguard_mobile_start", serverConfigPath.toString());
    }

    @Test
    public void generateClientConfigurationRepairsPeerAddressCollidingWithServerAddress() throws Exception {
        WireGuardMobileServer server = new WireGuardMobileServer();
        server.setPrivateKey("server-private-key");
        server.setPublicKey("server-public-key");
        server.setAddress("10.8.0.1/24");
        WireGuardMobilePeer peer = new WireGuardMobilePeer(1, "device:001122334455");
        peer.setPrivateKey("peer-private-key");
        peer.setPublicKey("peer-public-key");
        peer.setPresharedKey("peer-psk");
        peer.setAddress("10.8.0.1/32");
        Mockito.when(dataSource.get(WireGuardMobileServer.class)).thenReturn(server);
        Mockito.when(dataSource.getAll(WireGuardMobilePeer.class)).thenReturn(Arrays.asList(peer));

        String config = service.generateClientConfiguration("device:001122334455", "vpn.example.org", 51820);

        Assert.assertEquals("10.8.0.2/32", peer.getAddress());
        Assert.assertEquals("fd42:eb10:8::2/128", peer.getAddressIp6());
        Assert.assertEquals("10.8.0.1/24, fd42:eb10:8::1/64", server.getAddress());
        Assert.assertTrue(config.contains("Address = 10.8.0.2/32, fd42:eb10:8::2/128"));
        Mockito.verify(dataSource).save(server);
        Mockito.verify(dataSource).save(peer, 1);
    }

    @Test
    public void generateClientConfigurationReusesExistingPeer() throws Exception {
        WireGuardMobileServer server = new WireGuardMobileServer();
        server.setPrivateKey("server-private-key");
        server.setPublicKey("server-public-key");
        server.setAddress("10.8.0.1/24, fd42:eb10:8::1/64");
        WireGuardMobilePeer peer = new WireGuardMobilePeer(4, "device:001122334455");
        peer.setPrivateKey("peer-private-key");
        peer.setPublicKey("peer-public-key");
        peer.setPresharedKey("peer-psk");
        peer.setAddress("10.8.0.4/32");
        peer.setAddressIp6("fd42:eb10:8::4/128");

        Mockito.when(dataSource.get(WireGuardMobileServer.class)).thenReturn(server);
        Mockito.when(dataSource.getAll(WireGuardMobilePeer.class)).thenReturn(Arrays.asList(peer));

        String config = service.generateClientConfiguration("device:001122334455", "vpn.example.org", 51820);

        Mockito.verifyNoInteractions(keyService);
        Mockito.verify(dataSource, Mockito.never()).save(Mockito.any(WireGuardMobileServer.class));
        Mockito.verify(dataSource, Mockito.never()).save(Mockito.any(WireGuardMobilePeer.class), Mockito.anyInt());
        Assert.assertTrue(config.contains("Address = 10.8.0.4/32, fd42:eb10:8::4/128"));
        Assert.assertTrue(config.contains("PublicKey = server-public-key"));
    }

    @Test
    public void setServerStatusFallsBackToStoredPortForwardingMode() throws Exception {
        VpnServerStatus requestedStatus = new VpnServerStatus();
        requestedStatus.setRunning(true);
        Mockito.when(dataSource.getWireGuardMobilePortForwardingMode()).thenReturn(PortForwardingMode.MANUAL);

        VpnServerStatus result = service.setServerStatus(requestedStatus);

        Assert.assertEquals(PortForwardingMode.MANUAL, result.getPortForwardingMode());
        Mockito.verify(dataSource).setWireGuardMobilePortForwardingMode(PortForwardingMode.MANUAL);
    }
}
