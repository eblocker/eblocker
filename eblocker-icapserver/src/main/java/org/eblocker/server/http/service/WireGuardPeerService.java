package org.eblocker.server.http.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.common.data.wireguard.WireGuardPeerStore;
import org.eblocker.server.common.system.LoggingProcess;
import org.eblocker.server.common.system.ScriptRunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WireGuardPeerService {

    // Fix wie vereinbart:
    // WG-Netz: 10.13.13.0/24
    // Server:  10.13.13.1
    private static final String WG_NET_PREFIX = "10.13.13.";
    private static final int FIRST_PEER_HOST = 2;
    private static final int LAST_PEER_HOST = 254;

    private static final Path WG_CONFIG_FILE =
            Paths.get("/opt/eblocker-icap/conf/wireguard-config.json");
    private final DataSource dataSource;
    private final Provider<ObjectMapper> objectMapperProvider;
    private final ScriptRunner scriptRunner;
    private final String wireGuardServerCommand;
    private final String wireGuardCommand;

    @Inject
    public WireGuardPeerService(DataSource dataSource,
                                Provider<ObjectMapper> objectMapperProvider,
                                ScriptRunner scriptRunner,
                                @Named("wireguard.server.command") String wireGuardServerCommand,
                                @Named("wireguard.command") String wireGuardCommand) {
        this.dataSource = dataSource;
        this.objectMapperProvider = objectMapperProvider;
        this.scriptRunner = scriptRunner;
        this.wireGuardServerCommand = wireGuardServerCommand;
        this.wireGuardCommand = wireGuardCommand;
    }

    // -------------------------
    // CRUD / UI
    // -------------------------

    public WireGuardPeer createPeer(String name) {
        // Alle Peers holen (damit IP-Alloc funktioniert)
        List<WireGuardPeer> peers = dataSource.getAll(WireGuardPeer.class);

        String allowedIp = allocateNextIp(peers);

        String privateKey = runWg("genkey");
        String publicKey = runWgWithStdin("pubkey", privateKey);
        String presharedKey = runWg("genpsk");

        WireGuardPeer peer = new WireGuardPeer();
        int id = dataSource.nextId(WireGuardPeer.class);
        peer.setId(String.valueOf(id));
        peer.setName((name == null || name.trim().isEmpty()) ? "Peer" : name.trim());
        peer.setAllowedIp(allowedIp);

        peer.setPrivateKey(privateKey);
        peer.setPublicKey(publicKey);
        peer.setPresharedKey(presharedKey);

        dataSource.save(peer, id);
        return peer;
    }

    public boolean deletePeer(String peerId) {
        int id;
        try {
            id = Integer.parseInt(peerId);
        } catch (NumberFormatException e) {
            return false;
        }

        WireGuardPeer existing = dataSource.get(WireGuardPeer.class, id);
        if (existing == null) {
            return false;
        }

        dataSource.delete(WireGuardPeer.class, id);
        return true;
    }

    /**
     * Für UI: Secrets maskieren (PrivateKey/PresharedKey niemals an UI ausliefern).
     */
    public List<WireGuardPeer> listPeersMasked() {
        List<WireGuardPeer> peers = dataSource.getAll(WireGuardPeer.class);

        for (WireGuardPeer p : peers) {
            p.setPrivateKey(null);
            p.setPresharedKey(null);
            // publicKey kann bleiben (harmlos), wenn du willst kannst du ihn auch nullen
        }
        return peers;
    }

    /**
     * Für Controller, der WireGuardPeerStore erwartet.
     */
    public WireGuardPeerStore getStore() {
        WireGuardPeerStore store = new WireGuardPeerStore();
        store.setPeers(listPeersMasked());
        return store;
    }

    // -------------------------
    // Client config + QR
    // -------------------------

    public String renderClientConfig(String peerId) {
        WireGuardPeer peer = getPeerOrThrow(peerId);

        String serverPublicKey = resolveServerPublicKey();
        Endpoint ep = resolveEndpointFromConfig();

        // Hinweis: Address als /32 ist für WireGuard üblich.
        return ""
                + "[Interface]\n"
                + "PrivateKey = " + peer.getPrivateKey() + "\n"
                + "Address = " + peer.getAllowedIp() + "\n"
                + "DNS = 10.13.13.1\n"
                + "\n"
                + "[Peer]\n"
                + "PublicKey = " + serverPublicKey + "\n"
                + "PresharedKey = " + peer.getPresharedKey() + "\n"
                + "Endpoint = " + ep.host + ":" + ep.port + "\n"
                + "AllowedIPs = 0.0.0.0/0, ::/0\n"
                + "PersistentKeepalive = 25\n";
    }

    public byte[] renderClientConfigQrPng(String peerId) {
        String cfg = renderClientConfig(peerId);

        try {
            java.util.Map<com.google.zxing.EncodeHintType, Object> hints =
                    new java.util.EnumMap<>(com.google.zxing.EncodeHintType.class);
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

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            com.google.zxing.client.j2se.MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create QR png for peer " + peerId, e);
        }
    }

    // -------------------------
    // helpers
    // -------------------------

    private String allocateNextIp(List<WireGuardPeer> peers) {
        Set<String> used = new HashSet<>();
        for (WireGuardPeer p : peers) {
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

    private WireGuardPeer getPeerOrThrow(String peerId) {
        if (peerId == null || peerId.trim().isEmpty()) {
            throw new IllegalArgumentException("peerId is empty");
        }

        int id;
        try {
            id = Integer.parseInt(peerId.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid peerId: " + peerId);
        }

        WireGuardPeer peer = dataSource.get(WireGuardPeer.class, id);
        if (peer == null) {
            throw new IllegalArgumentException("Peer not found: " + peerId);
        }
        return peer;
    }

    private String resolveServerPublicKey() {
        try {
            LoggingProcess process = scriptRunner.startScript(wireGuardServerCommand, "public-key");
            int rc = process.waitFor();

            if (rc == 0) {
                String line;
                while ((line = process.pollStdout()) != null) {
                    if (!line.trim().isEmpty()) {
                        return line.trim();
                    }
                }
            }

            throw new IllegalStateException(
                    "Server public key not available (script failed with exit code " + rc + ").");
        } catch (IOException e) {
            throw new IllegalStateException("Could not run WireGuard server control script.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading WireGuard server public key.", e);
        }
    }

    private Endpoint resolveEndpointFromConfig() {
        // Defaults
        String endpointHost = "YOUR_DDNS_OR_IP";
        int endpointPort = 51820;

        try {
            if (Files.exists(WG_CONFIG_FILE)) {
                String json = new String(Files.readAllBytes(WG_CONFIG_FILE), StandardCharsets.UTF_8).trim();
                if (!json.isEmpty()) {
                    ObjectMapper mapper = objectMapperProvider.get();
                    Map<String, Object> cfg = mapper.readValue(
                        json, new TypeReference<Map<String, Object>>() {}
                    );

                    Object eh = cfg.get("externalHost");
                    if (eh != null && !String.valueOf(eh).trim().isEmpty()) {
                        endpointHost = String.valueOf(eh).trim();
                    }

                    Object lp = cfg.get("listenPort");
                    if (lp != null && !String.valueOf(lp).trim().isEmpty()) {
                        endpointPort = Integer.parseInt(String.valueOf(lp).trim());
                    }
                }
            }
        } catch (Exception ignored) {
            // Defaults bleiben
        }

        return new Endpoint(endpointHost, endpointPort);
    }

    private static class Endpoint {
        final String host;
        final int port;

        Endpoint(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    private String runWg(String arg) {
        return runCommand(new String[]{wireGuardCommand, arg}, null);
    }

    private String runWgWithStdin(String arg, String stdin) {
        return runCommand(new String[]{wireGuardCommand, arg}, stdin);
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
}
