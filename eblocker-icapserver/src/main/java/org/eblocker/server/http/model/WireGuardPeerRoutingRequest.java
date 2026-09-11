package org.eblocker.server.http.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin request for changing WireGuard client routing intent.
 *
 * tunnelMode intentionally remains a String at the HTTP boundary so invalid
 * enum names can be mapped explicitly to HTTP 400 by the controller.
 */
public class WireGuardPeerRoutingRequest {

    private String tunnelMode;
    private List<String> customAllowedIps =
            new ArrayList<>();

    public WireGuardPeerRoutingRequest() {
    }

    public WireGuardPeerRoutingRequest(
            String tunnelMode,
            List<String> customAllowedIps) {

        this.tunnelMode = tunnelMode;
        setCustomAllowedIps(customAllowedIps);
    }

    public String getTunnelMode() {
        return tunnelMode;
    }

    public void setTunnelMode(String tunnelMode) {
        this.tunnelMode = tunnelMode;
    }

    public List<String> getCustomAllowedIps() {
        return new ArrayList<>(
                customAllowedIps
        );
    }

    public void setCustomAllowedIps(
            List<String> customAllowedIps) {

        this.customAllowedIps =
                customAllowedIps == null
                        ? new ArrayList<>()
                        : new ArrayList<>(
                                customAllowedIps
                        );
    }
}
