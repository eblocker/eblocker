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
import org.eblocker.server.common.data.wireguard.WireGuardKeyPair;
import org.eblocker.server.common.data.wireguard.WireGuardMobilePeer;
import org.eblocker.server.common.data.wireguard.WireGuardMobileServer;
import org.eblocker.server.common.wireguard.WireGuardKeyService;
import org.eblocker.server.common.wireguard.WireGuardMobileConfigurationRenderer;
import org.eblocker.server.common.wireguard.WireGuardMobileServerConfigurationRenderer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

public class WireGuardMobileServiceTest {
    private DataSource dataSource;
    private WireGuardKeyService keyService;
    private WireGuardMobileService service;

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        keyService = Mockito.mock(WireGuardKeyService.class);
        service = new WireGuardMobileService(
                dataSource,
                keyService,
                new WireGuardMobileConfigurationRenderer(),
                new WireGuardMobileServerConfigurationRenderer(),
                "10.8.0.1/24",
                "10.8.0.",
                "10.8.0.1",
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
        Assert.assertEquals("10.8.0.1/24", serverCaptor.getValue().getAddress());

        ArgumentCaptor<WireGuardMobilePeer> peerCaptor = ArgumentCaptor.forClass(WireGuardMobilePeer.class);
        Mockito.verify(dataSource).save(peerCaptor.capture(), Mockito.eq(7));
        Assert.assertEquals(Integer.valueOf(7), peerCaptor.getValue().getId());
        Assert.assertEquals("device:001122334455", peerCaptor.getValue().getDeviceId());
        Assert.assertEquals("peer-private-key", peerCaptor.getValue().getPrivateKey());
        Assert.assertEquals("peer-public-key", peerCaptor.getValue().getPublicKey());
        Assert.assertEquals("peer-psk", peerCaptor.getValue().getPresharedKey());
        Assert.assertEquals("10.8.0.7/32", peerCaptor.getValue().getAddress());

        Assert.assertTrue(config.contains("PrivateKey = peer-private-key"));
        Assert.assertTrue(config.contains("Address = 10.8.0.7/32"));
        Assert.assertTrue(config.contains("DNS = 10.8.0.1"));
        Assert.assertTrue(config.contains("PublicKey = server-public-key"));
        Assert.assertTrue(config.contains("PresharedKey = peer-psk"));
        Assert.assertTrue(config.contains("Endpoint = vpn.example.org:51820"));
        Assert.assertTrue(config.contains("AllowedIPs = 0.0.0.0/0, ::/0"));
        Assert.assertTrue(config.contains("PersistentKeepalive = 25"));
    }

    @Test
    public void renderServerConfigurationUsesPersistedServerAndPeers() throws Exception {
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

        String config = service.renderServerConfiguration(51820);

        Assert.assertTrue(config.contains("PrivateKey = server-private-key"));
        Assert.assertTrue(config.contains("Address = 10.8.0.1/24"));
        Assert.assertTrue(config.contains("ListenPort = 51820"));
        Assert.assertTrue(config.contains("PublicKey = peer-public-key"));
        Assert.assertTrue(config.contains("AllowedIPs = 10.8.0.4/32"));
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
    }

    @Test
    public void generateClientConfigurationReusesExistingPeer() throws Exception {
        WireGuardMobileServer server = new WireGuardMobileServer();
        server.setPrivateKey("server-private-key");
        server.setPublicKey("server-public-key");
        server.setAddress("10.8.0.1/24");
        WireGuardMobilePeer peer = new WireGuardMobilePeer(4, "device:001122334455");
        peer.setPrivateKey("peer-private-key");
        peer.setPublicKey("peer-public-key");
        peer.setPresharedKey("peer-psk");
        peer.setAddress("10.8.0.4/32");

        Mockito.when(dataSource.get(WireGuardMobileServer.class)).thenReturn(server);
        Mockito.when(dataSource.getAll(WireGuardMobilePeer.class)).thenReturn(Arrays.asList(peer));

        String config = service.generateClientConfiguration("device:001122334455", "vpn.example.org", 51820);

        Mockito.verifyNoInteractions(keyService);
        Mockito.verify(dataSource, Mockito.never()).save(Mockito.any(WireGuardMobileServer.class));
        Mockito.verify(dataSource, Mockito.never()).save(Mockito.any(WireGuardMobilePeer.class), Mockito.anyInt());
        Assert.assertTrue(config.contains("Address = 10.8.0.4/32"));
        Assert.assertTrue(config.contains("PublicKey = server-public-key"));
    }
}
