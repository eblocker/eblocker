package org.eblocker.server.http.controller.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import org.eblocker.server.http.controller.WireGuardServerController;
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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WireGuardServerService wg;

    @Inject
    public WireGuardServerControllerImpl(WireGuardServerService wg) {
        this.wg = wg;
    }

    @Override
    public Map<String, Object> getStatus(Request request, Response response) {
        // Wenn du irgendwann init/start/stop brauchst, machen wir das sauber über Service + Routen.
        // Für status reicht das hier.

        String json = runStatusJson();

        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return fallback("invalid-json");
        }
    }

    private String runStatusJson() {
        try {
            ProcessBuilder pb = new ProcessBuilder(WG_CONTROL, "status-json");
            pb.redirectErrorStream(true);

            Process p = pb.start();

            String jsonLine = null;

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = br.readLine()) != null) {
                    String t = line.trim();
                    // wir nehmen die erste Zeile, die nach JSON aussieht
                    if (jsonLine == null && t.startsWith("{")) {
                        jsonLine = t;
                        // nicht breaken: wir lesen weiter, damit der Prozess sauber durchlaufen kann
                    }
                }
            }

            // Optional: Exit-Code lesen (falls Script Fehler meldet)
            // (ohne Timeout – wenn du willst, bauen wir Timeout dazu)
            int exit = p.waitFor();

            if (jsonLine != null) {
                return jsonLine;
            }

            // Wenn Script nix brauchbares liefert, geben wir Fallback-JSON zurück
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
