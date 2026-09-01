package org.eblocker.server.common.data.wireguard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WireGuardTunnelModeDataModelTest {

    @Test
    public void newPeerDefaultsToFullTunnel() {
        WireGuardPeer peer =
                new WireGuardPeer();

        assertEquals(
                WireGuardTunnelMode.FULL_TUNNEL,
                peer.getTunnelMode()
        );

        assertTrue(
                peer.getCustomAllowedIps().isEmpty()
        );
    }

    @Test
    public void legacyJsonWithoutRoutingFieldsDefaultsToFullTunnel()
            throws Exception {

        ObjectMapper mapper =
                new ObjectMapper();

        WireGuardPeer peer =
                mapper.readValue(
                        "{"
                                + "\"id\":7,"
                                + "\"name\":\"legacy\","
                                + "\"allowedIp\":\"10.13.13.7/32\""
                                + "}",
                        WireGuardPeer.class
                );

        assertEquals(
                WireGuardTunnelMode.FULL_TUNNEL,
                peer.getTunnelMode()
        );

        assertTrue(
                peer.getCustomAllowedIps().isEmpty()
        );
    }

    @Test
    public void explicitNullRoutingFieldsRemainSafe() {
        WireGuardPeer peer =
                new WireGuardPeer();

        peer.setTunnelMode(null);
        peer.setCustomAllowedIps(null);

        assertEquals(
                WireGuardTunnelMode.FULL_TUNNEL,
                peer.getTunnelMode()
        );

        assertTrue(
                peer.getCustomAllowedIps().isEmpty()
        );
    }

    @Test
    public void customRouteListIsDefensivelyCopied() {
        WireGuardPeer peer =
                new WireGuardPeer();

        List<String> input =
                new ArrayList<>(
                        Arrays.asList(
                                "192.168.1.0/24"
                        )
                );

        peer.setCustomAllowedIps(input);
        input.clear();

        assertEquals(
                Arrays.asList(
                        "192.168.1.0/24"
                ),
                peer.getCustomAllowedIps()
        );

        List<String> output =
                peer.getCustomAllowedIps();

        output.clear();

        assertEquals(
                Arrays.asList(
                        "192.168.1.0/24"
                ),
                peer.getCustomAllowedIps()
        );
    }
}
