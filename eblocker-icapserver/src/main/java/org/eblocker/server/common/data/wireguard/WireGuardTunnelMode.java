package org.eblocker.server.common.data.wireguard;

/**
 * Client-side WireGuard routing intent.
 *
 * The enum stores policy, not the currently resolved route list. In
 * particular LAN_ONLY stays independent from the implementation that resolves
 * the LAN/private-network routes.
 */
public enum WireGuardTunnelMode {
    FULL_TUNNEL,
    LAN_ONLY,
    CUSTOM
}
