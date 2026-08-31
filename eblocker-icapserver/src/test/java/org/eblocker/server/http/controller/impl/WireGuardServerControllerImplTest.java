package org.eblocker.server.http.controller.impl;

import io.netty.buffer.ByteBuf;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eblocker.server.common.data.wireguard.WireGuardEndpointConfig;
import org.eblocker.server.common.data.wireguard.WireGuardEndpointType;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.http.model.WireGuardClientConfigurationView;
import org.eblocker.server.http.model.WireGuardPeerCreateRequest;
import org.eblocker.server.http.model.WireGuardPeerView;
import org.eblocker.server.http.model.WireGuardServerStatusView;
import org.eblocker.server.http.model.WireGuardStatus;
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
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class WireGuardServerControllerImplTest {

    private WireGuardServerService serverService;
    private WireGuardServerControlService controlService;
    private WireGuardPeerService peerService;
    private WireGuardClientConfigurationService clientConfigurationService;
    private WireGuardServerControllerImpl controller;
    private Request request;
    private Response response;

    @Before
    public void setUp() {
        serverService =
                Mockito.mock(
                        WireGuardServerService.class
                );

        controlService =
                Mockito.mock(
                        WireGuardServerControlService.class
                );

        peerService =
                Mockito.mock(
                        WireGuardPeerService.class
                );

        clientConfigurationService =
                Mockito.mock(
                        WireGuardClientConfigurationService.class
                );

        controller =
                new WireGuardServerControllerImpl(
                        serverService,
                        controlService,
                        peerService,
                        clientConfigurationService
                );

        request = Mockito.mock(Request.class);
        response = new Response();
    }

    @Test
    public void getStatusReturnsDesiredAndRuntimeState() {
        WireGuardStatus runtime =
                new WireGuardStatus();

        Mockito.when(
                serverService.isEnabled()
        ).thenReturn(true);

        Mockito.when(
                controlService.getStatus()
        ).thenReturn(runtime);

        WireGuardServerStatusView result =
                controller.getStatus(
                        request,
                        response
                );

        assertTrue(result.isEnabled());
        assertSame(
                runtime,
                result.getRuntime()
        );
    }

    @Test
    public void enableUsesServerService() {
        WireGuardStatus runtime =
                new WireGuardStatus();

        Mockito.when(
                serverService.isEnabled()
        ).thenReturn(true);

        Mockito.when(
                controlService.getStatus()
        ).thenReturn(runtime);

        WireGuardServerStatusView result =
                controller.enable(
                        request,
                        response
                );

        Mockito.verify(
                serverService
        ).enable();

        assertTrue(result.isEnabled());
        assertSame(
                runtime,
                result.getRuntime()
        );
    }

    @Test
    public void disableUsesServerService() {
        WireGuardStatus runtime =
                new WireGuardStatus();

        Mockito.when(
                serverService.isEnabled()
        ).thenReturn(false);

        Mockito.when(
                controlService.getStatus()
        ).thenReturn(runtime);

        WireGuardServerStatusView result =
                controller.disable(
                        request,
                        response
                );

        Mockito.verify(
                serverService
        ).disable();

        assertFalse(result.isEnabled());
        assertSame(
                runtime,
                result.getRuntime()
        );
    }

    @Test
    public void createPeerReturnsSecretFreeView() {
        WireGuardPeerCreateRequest body =
                new WireGuardPeerCreateRequest(
                        "  phone  "
                );

        Mockito.when(
                request.getBodyAs(
                        WireGuardPeerCreateRequest.class
                )
        ).thenReturn(body);

        WireGuardPeer peer =
                peer(
                        7,
                        "phone",
                        "10.13.13.7/32",
                        true
                );

        peer.setPrivateKey(
                "PRIVATE-SECRET"
        );
        peer.setPresharedKey(
                "PSK-SECRET"
        );

        Mockito.when(
                peerService.createPeer("phone")
        ).thenReturn(peer);

        WireGuardPeerView result =
                controller.createPeer(
                        request,
                        response
                );

        assertEquals(7, result.getId());
        assertEquals(
                "phone",
                result.getName()
        );
        assertEquals(
                "public-7",
                result.getPublicKey()
        );
        assertEquals(
                "10.13.13.7/32",
                result.getAllowedIp()
        );
        assertNull(
                result.getDeviceId()
        );
        assertTrue(
                result.isAllowLanAccess()
        );

        assertEquals(
                201,
                response.getResponseStatus().code()
        );

        Mockito.verify(
                peerService
        ).createPeer("phone");
    }

    @Test(expected = BadRequestException.class)
    public void createPeerRejectsEmptyName() {
        Mockito.when(
                request.getBodyAs(
                        WireGuardPeerCreateRequest.class
                )
        ).thenReturn(
                new WireGuardPeerCreateRequest("   ")
        );

        controller.createPeer(
                request,
                response
        );
    }

    @Test
    public void getPeersReturnsSecretFreeViews() {
        Mockito.when(
                peerService.getPeers()
        ).thenReturn(
                Arrays.asList(
                        peer(
                                2,
                                "phone",
                                "10.13.13.2/32",
                                false
                        ),
                        peer(
                                3,
                                "tablet",
                                "10.13.13.3/32",
                                true
                        )
                )
        );

        List<WireGuardPeerView> result =
                controller.getPeers(
                        request,
                        response
                );

        assertEquals(2, result.size());
        assertEquals(
                "phone",
                result.get(0).getName()
        );
        assertFalse(
                result.get(0).isAllowLanAccess()
        );
        assertNull(
                result.get(0).getDeviceId()
        );
        assertTrue(
                result.get(1).isAllowLanAccess()
        );
        assertNull(
                result.get(1).getDeviceId()
        );
    }

    @Test
    public void deletePeerDeletesExistingPeer() {
        Mockito.when(
                request.getHeader("id")
        ).thenReturn("23");

        Mockito.when(
                peerService.deletePeer(23)
        ).thenReturn(true);

        assertTrue(
                controller.deletePeer(
                        request,
                        response
                )
        );

        Mockito.verify(
                peerService
        ).deletePeer(23);
    }

    @Test(expected = NotFoundException.class)
    public void deletePeerReturnsNotFoundForUnknownPeer() {
        Mockito.when(
                request.getHeader("id")
        ).thenReturn("23");

        Mockito.when(
                peerService.deletePeer(23)
        ).thenReturn(false);

        controller.deletePeer(
                request,
                response
        );
    }

    @Test(expected = BadRequestException.class)
    public void deletePeerRejectsMalformedId() {
        Mockito.when(
                request.getHeader("id")
        ).thenReturn("../23");

        controller.deletePeer(
                request,
                response
        );
    }

    @Test
    public void setLanAccessReturnsUpdatedSecretFreeView() {
        Mockito.when(
                request.getHeader("id")
        ).thenReturn("3");

        Mockito.when(
                request.getBodyAs(Boolean.class)
        ).thenReturn(true);

        Mockito.when(
                peerService.setLanAccess(
                        3,
                        true
                )
        ).thenReturn(true);

        WireGuardPeer peer =
                peer(
                        3,
                        "tablet",
                        "10.13.13.3/32",
                        true
                );

        Mockito.when(
                peerService.getPeer(3)
        ).thenReturn(peer);

        WireGuardPeerView result =
                controller.setLanAccess(
                        request,
                        response
                );

        assertEquals(3, result.getId());
        assertTrue(
                result.isAllowLanAccess()
        );

        Mockito.verify(
                peerService
        ).setLanAccess(
                3,
                true
        );
    }

    @Test(expected = NotFoundException.class)
    public void setLanAccessReturnsNotFoundForUnknownPeer() {
        Mockito.when(
                request.getHeader("id")
        ).thenReturn("99");

        Mockito.when(
                request.getBodyAs(Boolean.class)
        ).thenReturn(false);

        Mockito.when(
                peerService.setLanAccess(
                        99,
                        false
                )
        ).thenReturn(false);

        controller.setLanAccess(
                request,
                response
        );
    }

    @Test
    public void peerViewSerializationCannotExposeSecrets()
            throws Exception {

        WireGuardPeer peer =
                peer(
                        2,
                        "phone",
                        "10.13.13.2/32",
                        false
                );

        peer.setPrivateKey(
                "PRIVATE-SECRET"
        );
        peer.setPresharedKey(
                "PSK-SECRET"
        );

        String json =
                new ObjectMapper()
                        .writeValueAsString(
                                WireGuardPeerView.fromPeer(
                                        peer
                                )
                        );

        assertTrue(
                json.contains(
                        "\"publicKey\":\"public-2\""
                )
        );

        assertFalse(
                json.contains("PRIVATE-SECRET")
        );
        assertFalse(
                json.contains("PSK-SECRET")
        );
        assertFalse(
                json.contains("privateKey")
        );
        assertFalse(
                json.contains("presharedKey")
        );
    }


    @Test
    public void getEndpointConfigReturnsConfiguredEndpoint() {
        WireGuardEndpointConfig config =
                new WireGuardEndpointConfig(
                        WireGuardEndpointType.DYN_DNS,
                        "vpn.example.org"
                );

        Mockito.when(
                clientConfigurationService.getEndpointConfig()
        ).thenReturn(config);

        WireGuardEndpointConfig result =
                controller.getEndpointConfig(
                        request,
                        response
                );

        assertSame(config, result);
    }

    @Test
    public void setEndpointConfigPersistsThroughClientService() {
        WireGuardEndpointConfig requested =
                new WireGuardEndpointConfig(
                        WireGuardEndpointType.FIXED_IP,
                        "198.51.100.23"
                );

        Mockito.when(
                request.getBodyAs(
                        WireGuardEndpointConfig.class
                )
        ).thenReturn(requested);

        Mockito.when(
                clientConfigurationService.setEndpointConfig(
                        requested
                )
        ).thenReturn(requested);

        WireGuardEndpointConfig result =
                controller.setEndpointConfig(
                        request,
                        response
                );

        assertSame(requested, result);

        Mockito.verify(
                clientConfigurationService
        ).setEndpointConfig(requested);
    }

    @Test(expected = BadRequestException.class)
    public void setEndpointConfigRejectsInvalidEndpoint() {
        WireGuardEndpointConfig requested =
                new WireGuardEndpointConfig(
                        WireGuardEndpointType.DYN_DNS,
                        "https://vpn.example.org"
                );

        Mockito.when(
                request.getBodyAs(
                        WireGuardEndpointConfig.class
                )
        ).thenReturn(requested);

        Mockito.when(
                clientConfigurationService.setEndpointConfig(
                        requested
                )
        ).thenThrow(
                new IllegalArgumentException(
                        "WireGuard endpoint host is invalid."
                )
        );

        controller.setEndpointConfig(
                request,
                response
        );
    }

    @Test
    public void getClientConfigReturnsDedicatedSensitiveView() {
        WireGuardPeer existing =
                peer(
                        7,
                        "phone",
                        "10.13.13.7/32",
                        false
                );

        Mockito.when(
                request.getHeader("id")
        ).thenReturn("7");

        Mockito.when(
                peerService.getPeer(7)
        ).thenReturn(existing);

        String config =
                "[Interface]\n"
                        + "PrivateKey = PRIVATE\n"
                        + "Address = 10.13.13.7/32\n";

        Mockito.when(
                clientConfigurationService.renderClientConfig(7)
        ).thenReturn(config);

        WireGuardClientConfigurationView result =
                controller.getClientConfig(
                        request,
                        response
                );

        assertEquals(7, result.getPeerId());
        assertEquals(
                config,
                result.getConfiguration()
        );
    }

    @Test
    public void getPeerQrCodeUsesCentralRenderer() {
        WireGuardPeer existing =
                peer(
                        7,
                        "phone",
                        "10.13.13.7/32",
                        false
                );

        Mockito.when(
                request.getHeader("id")
        ).thenReturn("7");

        Mockito.when(
                peerService.getPeer(7)
        ).thenReturn(existing);

        byte[] png =
                new byte[]{
                        (byte) 0x89,
                        0x50,
                        0x4e,
                        0x47
                };

        Mockito.when(
                clientConfigurationService
                        .renderClientConfigQrPng(7)
        ).thenReturn(png);

        ByteBuf result =
                controller.getPeerQrCode(
                        request,
                        response
                );

        byte[] actual =
                new byte[result.readableBytes()];

        result.getBytes(
                result.readerIndex(),
                actual
        );

        assertEquals(4, actual.length);
        assertEquals((byte) 0x89, actual[0]);
        assertEquals((byte) 0x50, actual[1]);
        assertEquals((byte) 0x4e, actual[2]);
        assertEquals((byte) 0x47, actual[3]);

        Mockito.verify(
                clientConfigurationService
        ).renderClientConfigQrPng(7);
    }

    @Test(expected = NotFoundException.class)
    public void getPeerQrCodeRejectsUnknownPeer() {
        Mockito.when(
                request.getHeader("id")
        ).thenReturn("99");

        controller.getPeerQrCode(
                request,
                response
        );
    }

    @Test(expected = NotFoundException.class)
    public void getClientConfigRejectsUnknownPeer() {
        Mockito.when(
                request.getHeader("id")
        ).thenReturn("99");

        Mockito.when(
                peerService.getPeer(99)
        ).thenReturn(null);

        controller.getClientConfig(
                request,
                response
        );
    }

    @Test(expected = BadRequestException.class)
    public void getClientConfigRejectsUnconfiguredEndpoint() {
        WireGuardPeer existing =
                peer(
                        7,
                        "phone",
                        "10.13.13.7/32",
                        false
                );

        Mockito.when(
                request.getHeader("id")
        ).thenReturn("7");

        Mockito.when(
                peerService.getPeer(7)
        ).thenReturn(existing);

        Mockito.when(
                clientConfigurationService.renderClientConfig(7)
        ).thenThrow(
                new IllegalStateException(
                        "WireGuard endpoint is not configured."
                )
        );

        controller.getClientConfig(
                request,
                response
        );
    }

    private WireGuardPeer peer(
            int id,
            String name,
            String allowedIp,
            boolean allowLanAccess) {

        WireGuardPeer peer =
                new WireGuardPeer();

        peer.setId(id);
        peer.setName(name);
        peer.setPublicKey(
                "public-" + id
        );
        peer.setAllowedIp(
                allowedIp
        );
        peer.setAllowLanAccess(
                allowLanAccess
        );

        return peer;
    }
}
