package org.eblocker.server.http.service;

import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.common.data.wireguard.WireGuardTunnelMode;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class WireGuardClientRouteResolverTest {

    private final WireGuardClientRouteResolver resolver =
            new WireGuardClientRouteResolver(
                    new WireGuardFirewallContractLanRouteProvider(),
                    new WireGuardCustomRouteValidator()
            );

    @Test
    public void defaultPeerResolvesToIpv4FullTunnel() {
        WireGuardPeer peer =
                new WireGuardPeer();

        assertEquals(
                Collections.singletonList(
                        "0.0.0.0/0"
                ),
                resolver.resolve(peer)
        );
    }

    @Test
    public void lanOnlyUsesExistingFirewallContractOrder() {
        WireGuardPeer peer =
                new WireGuardPeer();

        peer.setTunnelMode(
                WireGuardTunnelMode.LAN_ONLY
        );

        List<String> expected =
                Arrays.asList(
                        "192.168.0.0/16",
                        "172.16.0.0/12",
                        "10.0.0.0/8",
                        "169.254.0.0/16"
                );

        peer.setAllowLanAccess(false);
        assertEquals(
                expected,
                resolver.resolve(peer)
        );

        peer.setAllowLanAccess(true);
        assertEquals(
                expected,
                resolver.resolve(peer)
        );
    }

    @Test
    public void fullTunnelIgnoresStaleCustomRoutes() {
        WireGuardPeer peer =
                new WireGuardPeer();

        peer.setTunnelMode(
                WireGuardTunnelMode.FULL_TUNNEL
        );

        peer.setCustomAllowedIps(
                Collections.singletonList(
                        "invalid stale value"
                )
        );

        assertEquals(
                Collections.singletonList(
                        "0.0.0.0/0"
                ),
                resolver.resolve(peer)
        );
    }

    @Test
    public void lanOnlyIgnoresStaleCustomRoutes() {
        WireGuardPeer peer =
                new WireGuardPeer();

        peer.setTunnelMode(
                WireGuardTunnelMode.LAN_ONLY
        );

        peer.setCustomAllowedIps(
                Collections.singletonList(
                        "invalid stale value"
                )
        );

        assertEquals(
                Arrays.asList(
                        "192.168.0.0/16",
                        "172.16.0.0/12",
                        "10.0.0.0/8",
                        "169.254.0.0/16"
                ),
                resolver.resolve(peer)
        );
    }

    @Test
    public void customUsesCanonicalValidatedRoutes() {
        WireGuardPeer peer =
                new WireGuardPeer();

        peer.setTunnelMode(
                WireGuardTunnelMode.CUSTOM
        );

        peer.setCustomAllowedIps(
                Arrays.asList(
                        "192.168.77.99/24",
                        "10.99.1.2/8",
                        "192.168.77.0/24"
                )
        );

        assertEquals(
                Arrays.asList(
                        "192.168.77.0/24",
                        "10.0.0.0/8"
                ),
                resolver.resolve(peer)
        );
    }
}
