package org.eblocker.server.common.data.wireguard;

/**
 * Source used to determine the externally reachable WireGuard endpoint.
 *
 * This type is intentionally independent from OpenVPN state.
 */
public enum WireGuardEndpointType {
    FIXED_IP,
    DYN_DNS,
    EBLOCKER_DYN_DNS
}
