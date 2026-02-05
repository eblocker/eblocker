package org.eblocker.server.http.controller.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import org.eblocker.server.http.controller.WireGuardServerController;
import org.eblocker.server.http.service.WireGuardPeerService;
import org.eblocker.server.http.service.WireGuardServerService;
import org.restexpress.Request;
import org.restexpress.Response;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class WireGuardServerControllerImpl implements WireGuardServerController {

    private static final String WG_CONTROL =
            "/opt/eblocker-icap/scripts/wireguard-server-control";

    // persistente UI-Config
    private static final String WG_CONFIG =
            "/opt/eblocker-icap/conf/wireguard-config.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WireGuardServerService wg;
    private final WireGuardPeerService wireGuardPeerService;

    @Inject
    public WireGuardServerControllerImpl(WireGuardServerService wg,
                                         WireGuardPeerService wireGuardPeerService) {
        this.wg = wg;
        this.wireGuardPeerService = wireGuardPeerService;
    }

    // =========================
    // STATUS
    // =========================
    @Override
    public Map<String, Object> getStatus(Request request, Response response) {
        String json = runStatusJson();
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return fallback("invalid-json");
        }
    }

    // =========================
    // ENABLE (= start)
    // =========================
    @Override
    public Map<String, Object> enable(Request request, Response response) {
        runControl("start");
        return getStatus(request, response);
    }

    // =========================
    // DISABLE (= stop)
    // =========================
    @Override
    public Map<String, Object> disable(Request request, Response response) {
        runControl("stop");
        return getStatus(request, response);
    }

    // =========================
    // CONFIG (read/write JSON)
    // =========================
    @Override
    public Map<String, Object> getConfig(Request request, Response response) {
        // Defaults (Subnetz ist fix → nur anzeigen)
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("externalHost", "");
        cfg.put("listenPort", 51820);
        cfg.put("allowLanAccess", Boolean.TRUE);
        cfg.put("portForwardConfirmed", Boolean.FALSE);

        cfg.put("wgNetworkCidr", "10.13.13.0/24");
        cfg.put("wgServerIpCidr", "10.13.13.1/24");

        // Datei lesen, wenn vorhanden
        try {
            java.nio.file.Path p = java.nio.file.Paths.get(WG_CONFIG);
            if (java.nio.file.Files.exists(p)) {
                byte[] bytes = java.nio.file.Files.readAllBytes(p);
                Map<String, Object> fileCfg = MAPPER.readValue(
                        bytes, new TypeReference<Map<String, Object>>() {}
                );

                if (fileCfg != null) {
                    if (fileCfg.containsKey("externalHost")) {
                        cfg.put("externalHost", fileCfg.get("externalHost"));
                    }
                    if (fileCfg.containsKey("listenPort")) {
                        cfg.put("listenPort", fileCfg.get("listenPort"));
                    }
                    if (fileCfg.containsKey("allowLanAccess")) {
                        cfg.put("allowLanAccess", fileCfg.get("allowLanAccess"));
                    }
                    if (fileCfg.containsKey("portForwardConfirmed")) {
                        cfg.put("portForwardConfirmed", fileCfg.get("portForwardConfirmed"));
                    }
                }
            }
        } catch (Exception ignored) {
            // Defaults reichen erstmal
        }

        return cfg;
    }

    @Override
    public Map<String, Object> setConfig(Request request, Response response) {
        Map<String, Object> result = new HashMap<>();

        try {
            // JSON aus Request lesen
            Object raw = request.getBody();
            String body;

            if (raw == null) {
                body = "";
            } else if (raw instanceof String) {
                body = (String) raw;
            } else if (raw instanceof byte[]) {
                body = new String((byte[]) raw, StandardCharsets.UTF_8);
            } else if (raw instanceof ByteBuf) {
                body = new String(ByteBufUtil.getBytes((ByteBuf) raw), StandardCharsets.UTF_8);
            } else {
                body = String.valueOf(raw);
            }

            if (body.trim().isEmpty()) {
                response.setResponseCode(400);
                result.put("ok", false);
                result.put("error", "empty body");
                return result;
            }

            Map<String, Object> in = MAPPER.readValue(
                    body, new TypeReference<Map<String, Object>>() {}
            );

            // Werte übernehmen (Subnetz NICHT editierbar)
            Map<String, Object> out = new HashMap<>();
            out.put("externalHost", in.get("externalHost") != null
                    ? String.valueOf(in.get("externalHost")).trim()
                    : "");

            out.put("listenPort", in.get("listenPort") != null
                    ? Integer.parseInt(String.valueOf(in.get("listenPort")))
                    : 51820);

            out.put("allowLanAccess", in.get("allowLanAccess") != null
                    && Boolean.parseBoolean(String.valueOf(in.get("allowLanAccess"))));

            out.put("portForwardConfirmed", in.get("portForwardConfirmed") != null
                    && Boolean.parseBoolean(String.valueOf(in.get("portForwardConfirmed"))));

            // minimal prüfen: Port und Checkbox
            int port = (Integer) out.get("listenPort");
            boolean confirmed = (Boolean) out.get("portForwardConfirmed");

            if (port < 1 || port > 65535) {
                response.setResponseCode(400);
                result.put("ok", Boolean.FALSE);
                result.put("error", "listenPort out of range");
                return result;
            }

            if (!confirmed) {
                response.setResponseCode(400);
                result.put("ok", Boolean.FALSE);
                result.put("error", "portForwardConfirmed required");
                return result;
            }

            // Schreiben
            java.nio.file.Path p = java.nio.file.Paths.get(WG_CONFIG);
            java.nio.file.Files.createDirectories(p.getParent());

            byte[] json = MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(out);

            java.nio.file.Files.write(p, json);

            // UI-Config anwenden (schreibt wg0.conf, restart falls wg0 up)
            runControl("apply-config");

            result.put("ok", Boolean.TRUE);
            return result;

        } catch (Exception e) {
            response.setResponseCode(500);
            result.put("ok", Boolean.FALSE);
            result.put("error", "exception");
            return result;
        }
    }

    // =========================
    // PEERS
    // =========================
    @Override
    public Object createPeer(Request request, Response response) {
        Object raw = request.getBody();
        String name;

        if (raw == null) {
            name = "Peer";
        } else if (raw instanceof String) {
            name = ((String) raw).trim();
        } else if (raw instanceof byte[]) {
            name = new String((byte[]) raw, java.nio.charset.StandardCharsets.UTF_8).trim();
        } else if (raw instanceof io.netty.buffer.ByteBuf) {
            name = new String(io.netty.buffer.ByteBufUtil.getBytes((io.netty.buffer.ByteBuf) raw),
                    java.nio.charset.StandardCharsets.UTF_8).trim();
        } else {
            name = String.valueOf(raw).trim();
        }

        if (name.isEmpty()) {
            name = "Peer";
        }

        org.eblocker.server.common.data.wireguard.WireGuardPeer peer =
                wireGuardPeerService.createPeer(name);

        // Nur Meta zurückgeben (keine Secrets!)
        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.put("id", peer.getId());
        out.put("name", peer.getName());
        out.put("allowedIp", peer.getAllowedIp());
        return out;
    }
    
    @Override
    public Object getPeers(Request request, Response response) {
        org.eblocker.server.common.data.wireguard.WireGuardPeerStore store =
                wireGuardPeerService.getStore(); // kommt im nächsten Schritt, falls noch nicht vorhanden

        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();

        for (org.eblocker.server.common.data.wireguard.WireGuardPeer p : store.getPeers()) {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("allowedIp", p.getAllowedIp());
            out.add(m);
        }

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("peers", out);
        return result;
    }

    @Override
    public io.netty.buffer.ByteBuf getPeerConfig(Request request, Response response) {
        String id = request.getHeader("id"); // so kommt es bei eBlocker an

        if (id == null || id.trim().isEmpty()) {
            response.setResponseCode(400);
            response.setContentType("text/plain; charset=utf-8");
            return io.netty.buffer.Unpooled.wrappedBuffer(
                    "missing id\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
        }

        String cfg = wireGuardPeerService.renderClientConfig(id.trim());

        response.setContentType("text/plain; charset=utf-8");
        response.addHeader(
                "Content-Disposition",
                "attachment; filename=\"wireguard-" + id.trim() + ".conf\""
        );

        // WICHTIG: ByteBuf zurückgeben → kein JSON-Serializer mehr
        return io.netty.buffer.Unpooled.wrappedBuffer(
                cfg.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }
 
    @Override
    public Map<String, Object> deletePeer(Request request, Response response) {

        String id = request.getHeader("id"); // Route {id} landet hier

        if (id == null || id.trim().isEmpty()) {
            response.setResponseCode(400);
            return java.util.Map.of(
                "ok", false,
                "error", "missing peer id"
            );
        }

        boolean removed = wireGuardPeerService.deletePeer(id.trim());

        if (!removed) {
            response.setResponseCode(404);
            return java.util.Map.of(
                "ok", false,
                "error", "peer not found"
            );
        }

        return java.util.Map.of("ok", true);
    }

    @Override
    public io.netty.buffer.ByteBuf getPeerQrCode(Request request, Response response) {
        String id = request.getHeader("id"); // bei euch kommt {id} als Header an

        if (id == null || id.trim().isEmpty()) {
            response.setResponseCode(400);
            response.setContentType("text/plain; charset=utf-8");
            return io.netty.buffer.Unpooled.wrappedBuffer(
                "missing id\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
        }

        byte[] png = wireGuardPeerService.renderClientConfigQrPng(id.trim());

        response.setContentType("image/png");
        response.addHeader(
            "Content-Disposition",
            "inline; filename=\"wireguard-" + id.trim() + ".png\""
        );

        return io.netty.buffer.Unpooled.wrappedBuffer(png);
    }


    // =========================
    // SCRIPT EXECUTION
    // =========================
    private void runControl(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "sudo", "-n", WG_CONTROL, cmd
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                while (br.readLine() != null) {
                    // bewusst leer – wir warten nur auf sauberes Ende
                }
            }

            p.waitFor();
        } catch (Exception ignored) {
            // Fehler sieht man im Status
        }
    }

    private String runStatusJson() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "sudo", "-n", WG_CONTROL, "status-json"
            );
            pb.redirectErrorStream(true);

            Process p = pb.start();
            String jsonLine = null;

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = br.readLine()) != null) {
                    String t = line.trim();
                    if (jsonLine == null && t.startsWith("{")) {
                        jsonLine = t;
                    }
                }
            }

            int exit = p.waitFor();

            if (jsonLine != null) {
                return jsonLine;
            }

            return "{\"iface\":\"wg0\",\"service\":\"unknown\",\"wg\":\"down\",\"peers\":0,\"error\":\"no-json\",\"exit\":" + exit + "}";

        } catch (Exception e) {
            return "{\"iface\":\"wg0\",\"service\":\"unknown\",\"wg\":\"down\",\"peers\":0,\"error\":\"exception\"}";
        }
    }

    private Map<String, Object> fallback(String reason) {
        Map<String, Object> fb = new HashMap<>();
        fb.put("iface", "wg0");
        fb.put("service", "unknown");
        fb.put("wg", "down");
        fb.put("peers", 0);
        fb.put("error", reason);
        return fb;
    }
}
