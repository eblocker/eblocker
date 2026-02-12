package org.eblocker.server.http.service;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal placeholder service so that PR #408 compiles.
 * Real implementation exists in full feature branch.
 */
public class WireGuardServerService {

    public Map<String, Object> getStatus() {
        return new HashMap<>();
    }

    public void start() {
        // no-op
    }

    public void stop() {
        // no-op
    }
}
