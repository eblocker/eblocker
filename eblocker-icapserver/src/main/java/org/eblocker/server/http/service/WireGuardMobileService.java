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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.wireguard.WireGuardKeyPair;
import org.eblocker.server.common.data.wireguard.WireGuardMobilePeer;
import org.eblocker.server.common.data.wireguard.WireGuardMobileServer;
import org.eblocker.server.common.wireguard.WireGuardKeyService;
import org.eblocker.server.common.wireguard.WireGuardMobileConfigurationRenderer;
import org.eblocker.server.common.wireguard.WireGuardMobileServerConfigurationRenderer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Singleton
public class WireGuardMobileService {
    private final DataSource dataSource;
    private final WireGuardKeyService keyService;
    private final WireGuardMobileConfigurationRenderer renderer;
    private final WireGuardMobileServerConfigurationRenderer serverRenderer;
    private final String serverAddress;
    private final String peerAddressPrefix;
    private final String dns;
    private final String allowedIps;
    private final int persistentKeepalive;

    @Inject
    public WireGuardMobileService(DataSource dataSource,
                                  WireGuardKeyService keyService,
                                  WireGuardMobileConfigurationRenderer renderer,
                                  WireGuardMobileServerConfigurationRenderer serverRenderer,
                                  @Named("wireguard.mobile.server.address") String serverAddress,
                                  @Named("wireguard.mobile.peer.address.prefix") String peerAddressPrefix,
                                  @Named("wireguard.mobile.dns") String dns,
                                  @Named("wireguard.mobile.allowed.ips") String allowedIps,
                                  @Named("wireguard.mobile.persistent.keepalive") int persistentKeepalive) {
        this.dataSource = dataSource;
        this.keyService = keyService;
        this.renderer = renderer;
        this.serverRenderer = serverRenderer;
        this.serverAddress = serverAddress;
        this.peerAddressPrefix = peerAddressPrefix;
        this.dns = dns;
        this.allowedIps = allowedIps;
        this.persistentKeepalive = persistentKeepalive;
    }

    public String generateClientConfiguration(String deviceId, String endpointHost, int endpointPort) throws IOException, InterruptedException {
        WireGuardMobileServer server = getOrCreateServer();
        WireGuardMobilePeer peer = getOrCreatePeer(deviceId);
        return renderer.render(server, peer, endpointHost, endpointPort, dns, allowedIps, persistentKeepalive);
    }

    public String renderServerConfiguration(int listenPort) throws IOException, InterruptedException {
        return serverRenderer.render(getOrCreateServer(), getPeers(), listenPort);
    }

    public void writeServerConfiguration(Path path, int listenPort) throws IOException, InterruptedException {
        Files.createDirectories(path.getParent());
        Files.write(path, renderServerConfiguration(listenPort).getBytes(StandardCharsets.UTF_8));
    }

    private WireGuardMobileServer getOrCreateServer() throws IOException, InterruptedException {
        WireGuardMobileServer server = dataSource.get(WireGuardMobileServer.class);
        if (server != null) {
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

    private WireGuardMobilePeer getOrCreatePeer(String deviceId) throws IOException, InterruptedException {
        WireGuardMobilePeer peer = getPeer(deviceId);
        if (peer != null) {
            return peer;
        }

        int id = dataSource.nextId(WireGuardMobilePeer.class);
        WireGuardKeyPair keyPair = keyService.generateKeyPair();
        peer = new WireGuardMobilePeer(id, deviceId);
        peer.setPrivateKey(keyPair.getPrivateKey());
        peer.setPublicKey(keyPair.getPublicKey());
        peer.setPresharedKey(keyService.generatePresharedKey());
        peer.setAddress(peerAddressPrefix + id + "/32");
        dataSource.save(peer, id);
        return peer;
    }

    private WireGuardMobilePeer getPeer(String deviceId) {
        List<WireGuardMobilePeer> peers = getPeers();
        return peers.stream()
                .filter(peer -> deviceId.equals(peer.getDeviceId()))
                .findFirst()
                .orElse(null);
    }

    private List<WireGuardMobilePeer> getPeers() {
        List<WireGuardMobilePeer> peers = dataSource.getAll(WireGuardMobilePeer.class);
        return peers != null ? peers : java.util.Collections.emptyList();
    }
}
