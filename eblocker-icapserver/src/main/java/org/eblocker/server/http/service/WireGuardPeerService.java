package org.eblocker.server.http.service;

import com.google.inject.Inject;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.common.data.wireguard.WireGuardPeerStore;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;


public class WireGuardPeerService {

    // Fix wie vereinbart:
    // WG-Netz: 10.13.13.0/24
    // Server:  10.13.13.1
    private static final String WG_NET_PREFIX = "10.13.13.";
    private static final int FIRST_PEER_HOST = 2;
    private static final int LAST_PEER_HOST = 254;

    private final WireGuardPeerStoreService storeService;

    @Inject
    public WireGuardPeerService(WireGuardPeerStoreService storeService) {
        this.storeService = storeService;
    }

    public WireGuardPeer createPeer(String name) {
        WireGuardPeerStore store = storeService.load();

        String allowedIp = allocateNextIp(store);

        String privateKey = runWg("genkey");
        String publicKey = runWgWithStdin("pubkey", privateKey);
        String presharedKey = runWg("genpsk");

        WireGuardPeer peer = new WireGuardPeer();
        peer.setId(UUID.randomUUID().toString());
        peer.setName((name == null || name.trim().isEmpty()) ? "Peer" : name.trim());
        peer.setAllowedIp(allowedIp);

        peer.setPrivateKey(privateKey);
        peer.setPublicKey(publicKey);
        peer.setPresharedKey(presharedKey);

        store.getPeers().add(peer);
        storeService.save(store);

        return peer;
    }

    private String allocateNextIp(WireGuardPeerStore store) {
        Set<String> used = new HashSet<>();
        for (WireGuardPeer p : store.getPeers()) {
            if (p.getAllowedIp() != null) {
                used.add(p.getAllowedIp().trim());
            }
        }

        for (int host = FIRST_PEER_HOST; host <= LAST_PEER_HOST; host++) {
            String candidate = WG_NET_PREFIX + host + "/32";
            if (!used.contains(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException("No free IPs left in 10.13.13.0/24");
    }

    private String runWg(String arg) {
        return runCommand(new String[]{"/usr/bin/wg", arg}, null);
    }

    private String runWgWithStdin(String arg, String stdin) {
        return runCommand(new String[]{"/usr/bin/wg", arg}, stdin);
    }

    private String runCommand(String[] cmd, String stdin) {
        Process p;
        try {
            p = new ProcessBuilder(cmd).start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start command: " + String.join(" ", cmd), e);
        }

        if (stdin != null) {
            try {
                p.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
                p.getOutputStream().flush();
                p.getOutputStream().close();
            } catch (IOException e) {
                p.destroy();
                throw new RuntimeException("Failed to write stdin to: " + String.join(" ", cmd), e);
            }
        } else {
            try {
                p.getOutputStream().close();
            } catch (IOException ignored) {
                // ignore
            }
        }

        String out = readAll(p.getInputStream());
        String err = readAll(p.getErrorStream());

        int rc;
        try {
            rc = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroy();
            throw new RuntimeException("Interrupted running: " + String.join(" ", cmd), e);
        }

        if (rc != 0) {
            throw new RuntimeException("Command failed (" + rc + "): " + String.join(" ", cmd)
                    + " stderr=" + err.trim());
        }

        return out.trim();
    }

    private String readAll(InputStream is) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = is.read(buf)) >= 0) {
                bos.write(buf, 0, r);
            }
            return bos.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read process stream", e);
        }
    }
    
    public WireGuardPeerStore getStore() {
        return storeService.load();
    }
    
    public String renderClientConfig(String peerId) {
        WireGuardPeerStore store = storeService.load();

        WireGuardPeer peer = null;
        for (WireGuardPeer p : store.getPeers()) {
            if (peerId.equals(p.getId())) {
                peer = p;
                break;
            }
        }
        if (peer == null) {
            throw new IllegalArgumentException("Peer not found: " + peerId);
        }

        // Server-Werte (aus wireguard-config.json lesen, sonst Defaults)
        String serverPublicKey = "<SERVER_PUBLIC_KEY_TODO>";

        // 1) bevorzugt: über sudo + wireguard-server-control (updatefest)
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "sudo", "-n",
                    "/opt/eblocker-icap/scripts/wireguard-server-control",
                    "public-key"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            String line = null;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                line = br.readLine();
            }

            int rc = p.waitFor();
            if (rc == 0 && line != null && !line.trim().isEmpty()) {
                serverPublicKey = line.trim();
            }
        } catch (Exception ignored) {
            // fallback unten
        }

        // 2) fallback: lokale Datei (falls sudo nicht verfügbar)
        if ("<SERVER_PUBLIC_KEY_TODO>".equals(serverPublicKey)) {
            try {
                java.nio.file.Path pub = java.nio.file.Paths.get("/opt/eblocker-icap/conf/wireguard-server.pub");
                if (java.nio.file.Files.exists(pub)) {
                    serverPublicKey = new String(java.nio.file.Files.readAllBytes(pub), StandardCharsets.UTF_8).trim();
                }
            } catch (Exception ignored) {
                // bleibt TODO
            }
        }



        String endpointHost = "YOUR_DDNS_OR_IP";
        int endpointPort = 51820;

        try {
            Path cfgPath = Paths.get("/opt/eblocker-icap/conf/wireguard-config.json");
            if (Files.exists(cfgPath)) {
                byte[] raw = Files.readAllBytes(cfgPath);
                String json = new String(raw, StandardCharsets.UTF_8).trim();
                if (!json.isEmpty()) {
                    Map<String, Object> cfg = new ObjectMapper().readValue(
                            json, new TypeReference<Map<String, Object>>() {}
                    );

                    Object eh = cfg.get("externalHost");
                    if (eh != null && !String.valueOf(eh).trim().isEmpty()) {
                        endpointHost = String.valueOf(eh).trim();
                    }

                    Object lp = cfg.get("listenPort");
                    if (lp != null) {
                        endpointPort = Integer.parseInt(String.valueOf(lp));
                    }
                }
            }
        } catch (Exception ignored) {
            // Defaults bleiben
        }


        // Minimal-Config (Client)
        // AllowedIPs: full tunnel oder split-tunnel entscheiden wir später in UI
        return ""
            + "[Interface]\n"
            + "PrivateKey = " + peer.getPrivateKey() + "\n"
            + "Address = " + peer.getAllowedIp().replace("/32", "/24") + "\n"
            + "DNS = 10.13.13.1\n"
            + "\n"
            + "[Peer]\n"
            + "PublicKey = " + serverPublicKey + "\n"
            + "PresharedKey = " + peer.getPresharedKey() + "\n"
            + "Endpoint = " + endpointHost + ":" + endpointPort + "\n"
            + "AllowedIPs = 0.0.0.0/0, ::/0\n"
            + "PersistentKeepalive = 25\n";
    }
    
    public boolean deletePeer(String peerId) {
        WireGuardPeerStore store = storeService.load();

        boolean removed = store.getPeers()
            .removeIf(p -> peerId.equals(p.getId()));

        if (removed) {
            storeService.save(store);
        }

        return removed;
    }
    
    public byte[] renderClientConfigQrPng(String peerId) {
        String cfg = renderClientConfig(peerId);

        try {
            java.util.Map<com.google.zxing.EncodeHintType, Object> hints = new java.util.EnumMap<>(com.google.zxing.EncodeHintType.class);
            hints.put(com.google.zxing.EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(com.google.zxing.EncodeHintType.MARGIN, 1);

            com.google.zxing.common.BitMatrix matrix =
                new com.google.zxing.qrcode.QRCodeWriter().encode(
                    cfg,
                    com.google.zxing.BarcodeFormat.QR_CODE,
                    360,
                    360,
                    hints
                );

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            com.google.zxing.client.j2se.MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create QR png for peer " + peerId, e);
        }
    }
}
