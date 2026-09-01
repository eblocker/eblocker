package org.eblocker.server.http.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.common.data.wireguard.WireGuardTunnelMode;

import java.util.Collections;
import java.util.List;

/**
 * Single source of truth for client-side WireGuard AllowedIPs.
 *
 * Persisted peers store routing intent. This resolver translates that intent
 * into the effective route list used by all client configuration rendering.
 */
@Singleton
public class WireGuardClientRouteResolver {

    private static final String FULL_TUNNEL_IPV4 =
            "0.0.0.0/0";

    private final WireGuardLanRouteProvider lanRouteProvider;
    private final WireGuardCustomRouteValidator customRouteValidator;

    @Inject
    public WireGuardClientRouteResolver(
            WireGuardLanRouteProvider lanRouteProvider,
            WireGuardCustomRouteValidator customRouteValidator) {

        this.lanRouteProvider = lanRouteProvider;
        this.customRouteValidator = customRouteValidator;
    }

    public List<String> resolve(
            WireGuardPeer peer) {

        if (peer == null) {
            throw new IllegalArgumentException(
                    "WireGuard peer is required."
            );
        }

        WireGuardTunnelMode mode =
                peer.getTunnelMode();

        if (mode == WireGuardTunnelMode.FULL_TUNNEL) {
            return Collections.singletonList(
                    FULL_TUNNEL_IPV4
            );
        }

        if (mode == WireGuardTunnelMode.LAN_ONLY) {
            return lanRouteProvider.getRoutes();
        }

        if (mode == WireGuardTunnelMode.CUSTOM) {
            return customRouteValidator.normalize(
                    peer.getCustomAllowedIps()
            );
        }

        throw new IllegalStateException(
                "Unsupported WireGuard tunnel mode."
        );
    }
}
