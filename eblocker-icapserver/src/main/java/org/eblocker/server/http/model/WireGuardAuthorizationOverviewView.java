package org.eblocker.server.http.model;

import java.util.List;

/**
 * Snapshot used by the centralized VPN access administration.
 *
 * globalEnabled is present even when there are no devices, avoiding an
 * ambiguous empty-list state in the frontend.
 */
public class WireGuardAuthorizationOverviewView {

    private final boolean globalEnabled;
    private final List<WireGuardAuthorizationView> devices;

    public WireGuardAuthorizationOverviewView(
            boolean globalEnabled,
            List<WireGuardAuthorizationView> devices) {

        this.globalEnabled = globalEnabled;
        this.devices = devices;
    }

    public boolean isGlobalEnabled() {
        return globalEnabled;
    }

    public List<WireGuardAuthorizationView> getDevices() {
        return devices;
    }
}
