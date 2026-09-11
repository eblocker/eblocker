package org.eblocker.server.common.data.wireguard;

/**
 * Persistent WireGuard endpoint selection.
 *
 * The port is intentionally not configurable here yet. The current
 * WireGuard runtime and firewall contract is fixed to UDP/51820.
 */
public class WireGuardEndpointConfig {

    private WireGuardEndpointType type;
    private String host;

    public WireGuardEndpointConfig() {
        // For JSON / Jackson
    }

    public WireGuardEndpointConfig(
            WireGuardEndpointType type,
            String host) {

        this.type = type;
        this.host = host;
    }

    public WireGuardEndpointType getType() {
        return type;
    }

    public void setType(
            WireGuardEndpointType type) {

        this.type = type;
    }

    public String getHost() {
        return host;
    }

    public void setHost(
            String host) {

        this.host = host;
    }
}
