package org.eblocker.server.http.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eblocker.server.common.data.wireguard.WireGuardPeerStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class WireGuardPeerStoreService {

    private static final Path PEERS_FILE =
            Paths.get("/opt/eblocker-icap/conf/wireguard-peers.json");

    private final Provider<ObjectMapper> objectMapperProvider;

    @Inject
    public WireGuardPeerStoreService(Provider<ObjectMapper> objectMapperProvider) {
        this.objectMapperProvider = objectMapperProvider;
    }

    public WireGuardPeerStore load() {
        if (!Files.exists(PEERS_FILE)) {
            return new WireGuardPeerStore();
        }

        try {
            byte[] raw = Files.readAllBytes(PEERS_FILE);
            String json = new String(raw, StandardCharsets.UTF_8).trim();
            if (json.isEmpty()) {
                return new WireGuardPeerStore();
            }

            ObjectMapper objectMapper = objectMapperProvider.get();
            return objectMapper.readValue(json, WireGuardPeerStore.class);

        } catch (IOException e) {
            throw new RuntimeException("Failed to read WireGuard peers from " + PEERS_FILE, e);
        }
    }

    public void save(WireGuardPeerStore store) {
        try {
            Files.createDirectories(PEERS_FILE.getParent());

            ObjectMapper objectMapper = objectMapperProvider.get();
            String json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(store);

            Files.write(PEERS_FILE, json.getBytes(StandardCharsets.UTF_8));

        } catch (IOException e) {
            throw new RuntimeException("Failed to write WireGuard peers to " + PEERS_FILE, e);
        }
    }
}
