package org.eblocker.server.http.controller.impl;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.http.model.WireGuardClientConfigurationView;
import org.eblocker.server.http.model.WireGuardPeerView;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.WireGuardClientConfigurationService;
import org.eblocker.server.http.service.WireGuardPeerService;
import org.eblocker.server.http.service.WireGuardServerService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.NotFoundException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class WireGuardDashboardControllerImplTest {

    private WireGuardPeerService peerService;
    private DeviceService deviceService;
    private WireGuardClientConfigurationService clientConfigurationService;
    private WireGuardServerService serverService;
    private Request request;
    private Response response;
    private WireGuardDashboardControllerImpl controller;

    @Before
    public void setUp() {
        peerService =
                Mockito.mock(WireGuardPeerService.class);

        deviceService =
                Mockito.mock(DeviceService.class);

        clientConfigurationService =
                Mockito.mock(
                        WireGuardClientConfigurationService.class
                );

        serverService =
                Mockito.mock(WireGuardServerService.class);

        request = Mockito.mock(Request.class);
        response = Mockito.mock(Response.class);

        controller =
                new WireGuardDashboardControllerImpl(
                        peerService,
                        deviceService,
                        clientConfigurationService,
                        serverService
                );

        Mockito.when(
                request.getHeader("deviceId")
        ).thenReturn("device:001122334455");

        Device device = new Device();
        device.setId("device:001122334455");
        device.setName("Phone");

        Mockito.when(
                deviceService.getDeviceById(
                        "device:001122334455"
                )
        ).thenReturn(device);
    }

    @Test
    public void getStatusReturnsPersistedEnabledState() {
        Mockito.when(
                serverService.isEnabled()
        ).thenReturn(true);

        boolean enabled =
                controller.getStatus(
                        request,
                        response
                );

        assertEquals(true, enabled);

        Mockito.verify(serverService)
                .isEnabled();

        Mockito.verify(deviceService)
                .getDeviceById(
                        "device:001122334455"
                );
    }

    @Test
    public void getStatusReturnsPersistedDisabledState() {
        Mockito.when(
                serverService.isEnabled()
        ).thenReturn(false);

        boolean enabled =
                controller.getStatus(
                        request,
                        response
                );

        assertEquals(false, enabled);

        Mockito.verify(serverService)
                .isEnabled();

        Mockito.verify(deviceService)
                .getDeviceById(
                        "device:001122334455"
                );
    }

    @Test
    public void getPeerReturnsSecretFreeView() {
        WireGuardPeer peer = peer();

        Mockito.when(
                peerService.getPeerByDeviceId(
                        "device:001122334455"
                )
        ).thenReturn(peer);

        WireGuardPeerView view =
                controller.getPeer(request, response);

        assertNotNull(view);
        assertEquals(7, view.getId());
        assertEquals("Phone", view.getName());
        assertEquals(
                "10.13.13.2/32",
                view.getAllowedIp()
        );
        assertEquals(
                "device:001122334455",
                view.getDeviceId()
        );
    }

    @Test(expected = NotFoundException.class)
    public void getPeerRejectsMissingBinding() {
        controller.getPeer(request, response);
    }

    @Test
    public void createPeerCreatesDeviceBinding() {
        WireGuardPeer peer = peer();

        Mockito.when(
                peerService.createPeerForDevice(
                        Mockito.anyString(),
                        Mockito.eq("device:001122334455")
                )
        ).thenReturn(peer);

        WireGuardPeerView view =
                controller.createPeer(
                        request,
                        response
                );

        assertNotNull(view);

        Mockito.verify(peerService)
                .createPeerForDevice(
                        Mockito.anyString(),
                        Mockito.eq(
                                "device:001122334455"
                        )
                );

        Mockito.verify(response)
                .setResponseCode(
                        HttpResponseStatus.CREATED.code()
                );
    }

    @Test
    public void createPeerReturnsConflictForExistingBinding() {
        Mockito.when(
                peerService.getPeerByDeviceId(
                        "device:001122334455"
                )
        ).thenReturn(peer());

        WireGuardPeerView view =
                controller.createPeer(
                        request,
                        response
                );

        assertNull(view);

        Mockito.verify(response)
                .setResponseCode(
                        HttpResponseStatus.CONFLICT.code()
                );

        Mockito.verify(
                peerService,
                Mockito.never()
        ).createPeerForDevice(
                Mockito.anyString(),
                Mockito.anyString()
        );
    }

    @Test
    public void setLanAccessUpdatesOnlyBoundPeer() {
        WireGuardPeer peer = peer();
        WireGuardPeer updated = peer();
        updated.setAllowLanAccess(true);

        Mockito.when(
                request.getBodyAs(Boolean.class)
        ).thenReturn(true);

        Mockito.when(
                peerService.getPeerByDeviceId(
                        "device:001122334455"
                )
        ).thenReturn(peer);

        Mockito.when(
                peerService.setLanAccess(7, true)
        ).thenReturn(true);

        Mockito.when(
                peerService.getPeer(7)
        ).thenReturn(updated);

        WireGuardPeerView view =
                controller.setLanAccess(
                        request,
                        response
                );

        assertNotNull(view);
        assertEquals(true, view.isAllowLanAccess());

        Mockito.verify(peerService)
                .setLanAccess(7, true);
    }

    @Test(expected = NotFoundException.class)
    public void setLanAccessRejectsMissingDeviceBinding() {
        Mockito.when(
                request.getBodyAs(Boolean.class)
        ).thenReturn(true);

        controller.setLanAccess(
                request,
                response
        );
    }

    @Test
    public void getClientConfigUsesOnlyDeviceBoundPeer() {
        WireGuardPeer peer = peer();

        Mockito.when(
                peerService.getPeerByDeviceId(
                        "device:001122334455"
                )
        ).thenReturn(peer);

        String configuration =
                "[Interface]\n"
                        + "PrivateKey = SENSITIVE\n";

        Mockito.when(
                clientConfigurationService.renderClientConfig(7)
        ).thenReturn(configuration);

        WireGuardClientConfigurationView view =
                controller.getClientConfig(
                        request,
                        response
                );

        assertNotNull(view);
        assertEquals(7, view.getPeerId());
        assertEquals(
                configuration,
                view.getConfiguration()
        );

        Mockito.verify(peerService)
                .getPeerByDeviceId(
                        "device:001122334455"
                );

        Mockito.verify(clientConfigurationService)
                .renderClientConfig(7);
    }

    @Test(expected = NotFoundException.class)
    public void getClientConfigRejectsMissingDeviceBinding() {
        controller.getClientConfig(
                request,
                response
        );
    }

    @Test(expected = BadRequestException.class)
    public void getClientConfigMapsRenderFailureToBadRequest() {
        WireGuardPeer peer = peer();

        Mockito.when(
                peerService.getPeerByDeviceId(
                        "device:001122334455"
                )
        ).thenReturn(peer);

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

    @Test
    public void getPeerQrCodeUsesOnlyDeviceBoundPeer() {
        WireGuardPeer peer = peer();

        Mockito.when(
                peerService.getPeerByDeviceId(
                        "device:001122334455"
                )
        ).thenReturn(peer);

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
                peerService
        ).getPeerByDeviceId(
                "device:001122334455"
        );

        Mockito.verify(
                clientConfigurationService
        ).renderClientConfigQrPng(7);
    }

    @Test(expected = NotFoundException.class)
    public void getPeerQrCodeRejectsMissingDeviceBinding() {
        controller.getPeerQrCode(
                request,
                response
        );
    }

    @Test
    public void deletePeerDeletesBoundPeerOnly() {
        WireGuardPeer peer = peer();

        Mockito.when(
                peerService.getPeerByDeviceId(
                        "device:001122334455"
                )
        ).thenReturn(peer);

        Mockito.when(
                peerService.deletePeer(7)
        ).thenReturn(true);

        controller.deletePeer(request, response);

        Mockito.verify(peerService)
                .deletePeer(7);

        Mockito.verify(response)
                .setResponseCode(
                        HttpResponseStatus.NO_CONTENT.code()
                );
    }

    private WireGuardPeer peer() {
        WireGuardPeer peer = new WireGuardPeer();
        peer.setId(7);
        peer.setName("Phone");
        peer.setPublicKey("public");
        peer.setAllowedIp("10.13.13.2/32");
        peer.setDeviceId("device:001122334455");
        peer.setAllowLanAccess(false);
        return peer;
    }
}
