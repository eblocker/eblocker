package org.eblocker.server.http.service;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;

import java.util.List;

/**
 * Resolves a WireGuard tunnel source address to the eBlocker device explicitly
 * bound to that peer.
 *
 * This is intentionally a read-only identity projection. WireGuard addresses
 * are not persisted into Device.ipAddresses and do not set the OpenVPN-specific
 * vpnClient flag.
 */
final class WireGuardDeviceIpResolver {

    private static final String HOST_ROUTE_SUFFIX = "/32";

    private WireGuardDeviceIpResolver() {
    }

    static String resolveDeviceId(
            DataSource dataSource,
            IpAddress ipAddress) {

        if (dataSource == null) {
            return null;
        }

        return resolveDeviceId(
                ipAddress,
                dataSource.getAll(WireGuardPeer.class)
        );
    }

    static String resolveDeviceId(
            IpAddress ipAddress,
            List<WireGuardPeer> peers) {

        if (ipAddress == null || peers == null) {
            return null;
        }

        String matchedDeviceId = null;

        for (WireGuardPeer peer : peers) {
            if (peer == null
                    || !hasText(peer.getDeviceId())
                    || !hasText(peer.getAllowedIp())) {
                continue;
            }

            String allowedIp = peer.getAllowedIp().trim();

            if (!allowedIp.endsWith(HOST_ROUTE_SUFFIX)) {
                continue;
            }

            String peerAddressText = allowedIp.substring(
                    0,
                    allowedIp.length() - HOST_ROUTE_SUFFIX.length()
            ).trim();

            IpAddress peerAddress;

            try {
                peerAddress = IpAddress.parse(peerAddressText);
            } catch (IllegalArgumentException e) {
                continue;
            }

            if (!peerAddress.isIpv4()
                    || !peerAddress.equals(ipAddress)) {
                continue;
            }

            String deviceId = peer.getDeviceId().trim();

            // More than one matching peer makes the source-IP identity
            // ambiguous. Do not authorize either mapping.
            if (matchedDeviceId != null) {
                return null;
            }

            matchedDeviceId = deviceId;
        }

        return matchedDeviceId;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
