package org.eblocker.server.http.controller.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.common.data.wireguard.WireGuardTunnelMode;
import org.eblocker.server.http.model.WireGuardPeerRoutingRequest;
import org.eblocker.server.http.model.WireGuardPeerView;
import org.eblocker.server.http.service.WireGuardClientConfigurationService;
import org.eblocker.server.http.service.WireGuardPeerService;
import org.eblocker.server.http.service.WireGuardServerControlService;
import org.eblocker.server.http.service.WireGuardServerService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.NotFoundException;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WireGuardRoutingApiTest {

    private WireGuardPeerService peerService;
    private WireGuardServerControllerImpl controller;
    private Request request;
    private Response response;

    @Before
    public void setUp() {
        WireGuardServerService serverService =
                Mockito.mock(
                        WireGuardServerService.class
                );

        WireGuardServerControlService controlService =
                Mockito.mock(
                        WireGuardServerControlService.class
                );

        peerService =
                Mockito.mock(
                        WireGuardPeerService.class
                );

        WireGuardClientConfigurationService clientService =
                Mockito.mock(
                        WireGuardClientConfigurationService.class
                );

        controller =
                new WireGuardServerControllerImpl(
                        serverService,
                        controlService,
                        peerService,
                        clientService
                );

        request =
                Mockito.mock(Request.class);

        response =
                Mockito.mock(Response.class);
    }

    @Test
    public void customRoutingCanBeUpdated() {
        WireGuardPeer peer =
                peer(
                        WireGuardTunnelMode.CUSTOM,
                        Arrays.asList(
                                "192.168.50.0/24",
                                "10.0.0.0/8"
                        )
                );

        Mockito.when(
                request.getHeader("id")
        ).thenReturn("7");

        WireGuardPeerRoutingRequest body =
                new WireGuardPeerRoutingRequest(
                        " custom ",
                        Arrays.asList(
                                "192.168.50.77/24",
                                "10.23.45.67/8"
                        )
                );

        Mockito.when(
                request.getBodyAs(
                        WireGuardPeerRoutingRequest.class
                )
        ).thenReturn(body);

        Mockito.when(
                peerService.setRouting(
                        7,
                        WireGuardTunnelMode.CUSTOM,
                        body.getCustomAllowedIps()
                )
        ).thenReturn(true);

        Mockito.when(
                peerService.getPeer(7)
        ).thenReturn(peer);

        WireGuardPeerView view =
                controller.setRouting(
                        request,
                        response
                );

        assertEquals(
                WireGuardTunnelMode.CUSTOM,
                view.getTunnelMode()
        );

        assertEquals(
                Arrays.asList(
                        "192.168.50.0/24",
                        "10.0.0.0/8"
                ),
                view.getCustomAllowedIps()
        );
    }

    @Test
    public void missingBodyIsBadRequest() {
        Mockito.when(
                request.getHeader("id")
        ).thenReturn("7");

        Mockito.when(
                request.getBodyAs(
                        WireGuardPeerRoutingRequest.class
                )
        ).thenReturn(null);

        expectBadRequest(
                () -> controller.setRouting(
                        request,
                        response
                )
        );
    }

    @Test
    public void invalidModeIsBadRequest() {
        Mockito.when(
                request.getHeader("id")
        ).thenReturn("7");

        Mockito.when(
                request.getBodyAs(
                        WireGuardPeerRoutingRequest.class
                )
        ).thenReturn(
                new WireGuardPeerRoutingRequest(
                        "MAGIC_MODE",
                        Collections.emptyList()
                )
        );

        expectBadRequest(
                () -> controller.setRouting(
                        request,
                        response
                )
        );
    }

    @Test
    public void invalidCustomRouteIsBadRequest() {
        Mockito.when(
                request.getHeader("id")
        ).thenReturn("7");

        WireGuardPeerRoutingRequest body =
                new WireGuardPeerRoutingRequest(
                        "CUSTOM",
                        Collections.singletonList(
                                "2001:db8::/32"
                        )
                );

        Mockito.when(
                request.getBodyAs(
                        WireGuardPeerRoutingRequest.class
                )
        ).thenReturn(body);

        Mockito.when(
                peerService.setRouting(
                        7,
                        WireGuardTunnelMode.CUSTOM,
                        body.getCustomAllowedIps()
                )
        ).thenThrow(
                new IllegalArgumentException(
                        "Invalid IPv4 CIDR."
                )
        );

        expectBadRequest(
                () -> controller.setRouting(
                        request,
                        response
                )
        );
    }

    @Test
    public void missingPeerIsNotFound() {
        Mockito.when(
                request.getHeader("id")
        ).thenReturn("7");

        WireGuardPeerRoutingRequest body =
                new WireGuardPeerRoutingRequest(
                        "FULL_TUNNEL",
                        Collections.emptyList()
                );

        Mockito.when(
                request.getBodyAs(
                        WireGuardPeerRoutingRequest.class
                )
        ).thenReturn(body);

        Mockito.when(
                peerService.setRouting(
                        7,
                        WireGuardTunnelMode.FULL_TUNNEL,
                        body.getCustomAllowedIps()
                )
        ).thenReturn(false);

        expectNotFound(
                () -> controller.setRouting(
                        request,
                        response
                )
        );
    }

    @Test
    public void peerViewSerializationRemainsSecretFree()
            throws Exception {

        WireGuardPeer peer =
                peer(
                        WireGuardTunnelMode.LAN_ONLY,
                        Collections.emptyList()
                );

        peer.setPrivateKey(
                "PRIVATE-SHOULD-NOT-LEAK"
        );

        peer.setPresharedKey(
                "PSK-SHOULD-NOT-LEAK"
        );

        String json =
                new ObjectMapper().writeValueAsString(
                        WireGuardPeerView.fromPeer(peer)
                );

        assertTrue(
                json.contains(
                        "\"tunnelMode\":\"LAN_ONLY\""
                )
        );

        assertTrue(
                json.contains(
                        "\"customAllowedIps\":[]"
                )
        );

        assertFalse(
                json.contains("privateKey")
        );

        assertFalse(
                json.contains("presharedKey")
        );

        assertFalse(
                json.contains(
                        "PRIVATE-SHOULD-NOT-LEAK"
                )
        );

        assertFalse(
                json.contains(
                        "PSK-SHOULD-NOT-LEAK"
                )
        );
    }

    private WireGuardPeer peer(
            WireGuardTunnelMode mode,
            java.util.List<String> routes) {

        WireGuardPeer peer =
                new WireGuardPeer();

        peer.setId(7);
        peer.setName("peer");
        peer.setPublicKey("PUBLIC");
        peer.setAllowedIp(
                "10.13.13.7/32"
        );
        peer.setDeviceId("device:7");
        peer.setAllowLanAccess(false);
        peer.setTunnelMode(mode);
        peer.setCustomAllowedIps(routes);

        return peer;
    }

    private void expectBadRequest(
            Runnable action) {

        try {
            action.run();
            fail("Expected BadRequestException");
        } catch (BadRequestException expected) {
            // expected
        }
    }

    private void expectNotFound(
            Runnable action) {

        try {
            action.run();
            fail("Expected NotFoundException");
        } catch (NotFoundException expected) {
            // expected
        }
    }
}
