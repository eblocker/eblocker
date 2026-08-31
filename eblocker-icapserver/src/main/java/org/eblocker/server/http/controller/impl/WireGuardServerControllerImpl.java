package org.eblocker.server.http.controller.impl;

import com.google.inject.Inject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.eblocker.server.common.data.wireguard.WireGuardEndpointConfig;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.http.controller.WireGuardServerController;
import org.eblocker.server.http.model.WireGuardClientConfigurationView;
import org.eblocker.server.http.model.WireGuardPeerCreateRequest;
import org.eblocker.server.http.model.WireGuardPeerView;
import org.eblocker.server.http.model.WireGuardServerStatusView;
import org.eblocker.server.http.service.WireGuardClientConfigurationService;
import org.eblocker.server.http.service.WireGuardPeerService;
import org.eblocker.server.http.service.WireGuardServerControlService;
import org.eblocker.server.http.service.WireGuardServerService;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.NotFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Authenticated admin API facade for the WireGuard core services.
 *
 * This controller deliberately exposes no client configuration, QR code,
 * private key or preshared key.
 */
public class WireGuardServerControllerImpl
        implements WireGuardServerController {

    private final WireGuardServerService serverService;
    private final WireGuardServerControlService controlService;
    private final WireGuardPeerService peerService;
    private final WireGuardClientConfigurationService clientConfigurationService;

    @Inject
    public WireGuardServerControllerImpl(
            WireGuardServerService serverService,
            WireGuardServerControlService controlService,
            WireGuardPeerService peerService,
            WireGuardClientConfigurationService clientConfigurationService) {

        this.serverService = serverService;
        this.controlService = controlService;
        this.peerService = peerService;
        this.clientConfigurationService = clientConfigurationService;
    }

    @Override
    public WireGuardServerStatusView getStatus(
            Request request,
            Response response) {

        return currentStatus();
    }

    @Override
    public WireGuardServerStatusView enable(
            Request request,
            Response response) {

        serverService.enable();
        return currentStatus();
    }

    @Override
    public WireGuardServerStatusView disable(
            Request request,
            Response response) {

        serverService.disable();
        return currentStatus();
    }

    @Override
    public List<WireGuardPeerView> getPeers(
            Request request,
            Response response) {

        return peerService.getPeers()
                .stream()
                .map(WireGuardPeerView::fromPeer)
                .collect(Collectors.toList());
    }

    @Override
    public WireGuardPeerView createPeer(
            Request request,
            Response response) {

        WireGuardPeerCreateRequest body =
                request.getBodyAs(
                        WireGuardPeerCreateRequest.class
                );

        if (body == null
                || body.getName() == null
                || body.getName().trim().isEmpty()) {

            throw new BadRequestException(
                    "WireGuard peer name is required."
            );
        }

        WireGuardPeer peer =
                peerService.createPeer(
                        body.getName().trim()
                );

        response.setResponseCode(
                HttpResponseStatus.CREATED.code()
        );

        return WireGuardPeerView.fromPeer(peer);
    }

    @Override
    public boolean deletePeer(
            Request request,
            Response response) {

        int id = parsePeerId(request);

        if (!peerService.deletePeer(id)) {
            throw new NotFoundException(
                    "WireGuard peer not found."
            );
        }

        return true;
    }

    @Override
    public WireGuardPeerView setLanAccess(
            Request request,
            Response response) {

        int id = parsePeerId(request);

        Boolean allowLanAccess =
                request.getBodyAs(Boolean.class);

        if (allowLanAccess == null) {
            throw new BadRequestException(
                    "WireGuard LAN access state is required."
            );
        }

        if (!peerService.setLanAccess(
                id,
                allowLanAccess)) {

            throw new NotFoundException(
                    "WireGuard peer not found."
            );
        }

        WireGuardPeer peer =
                peerService.getPeer(id);

        if (peer == null) {
            throw new NotFoundException(
                    "WireGuard peer not found."
            );
        }

        return WireGuardPeerView.fromPeer(peer);
    }

    @Override
    public WireGuardEndpointConfig getEndpointConfig(
            Request request,
            Response response) {

        return clientConfigurationService.getEndpointConfig();
    }

    @Override
    public WireGuardEndpointConfig setEndpointConfig(
            Request request,
            Response response) {

        WireGuardEndpointConfig config =
                request.getBodyAs(
                        WireGuardEndpointConfig.class
                );

        try {
            return clientConfigurationService.setEndpointConfig(
                    config
            );
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    e.getMessage()
            );
        }
    }

    @Override
    public WireGuardClientConfigurationView getClientConfig(
            Request request,
            Response response) {

        int id = parsePeerId(request);

        if (peerService.getPeer(id) == null) {
            throw new NotFoundException(
                    "WireGuard peer not found."
            );
        }

        String configuration;
        try {
            configuration =
                    clientConfigurationService.renderClientConfig(
                            id
                    );
        } catch (IllegalArgumentException
                 | IllegalStateException e) {

            throw new BadRequestException(
                    e.getMessage()
            );
        }

        return new WireGuardClientConfigurationView(
                id,
                configuration
        );
    }

    @Override
    public ByteBuf getPeerQrCode(
            Request request,
            Response response) {

        int id = parsePeerId(request);

        if (peerService.getPeer(id) == null) {
            throw new NotFoundException(
                    "WireGuard peer not found."
            );
        }

        byte[] png;

        try {
            png =
                    clientConfigurationService
                            .renderClientConfigQrPng(id);

        } catch (IllegalArgumentException
                 | IllegalStateException e) {

            throw new BadRequestException(
                    e.getMessage()
            );
        }

        response.setContentType("image/png");

        response.addHeader(
                "Content-Disposition",
                "inline; filename=\"wireguard-"
                        + id
                        + ".png\""
        );

        return Unpooled.wrappedBuffer(png);
    }

    private WireGuardServerStatusView currentStatus() {
        return new WireGuardServerStatusView(
                serverService.isEnabled(),
                controlService.getStatus()
        );
    }

    private int parsePeerId(
            Request request) {

        String value = request.getHeader("id");

        if (value == null
                || value.trim().isEmpty()) {

            throw new BadRequestException(
                    "WireGuard peer id is required."
            );
        }

        try {
            int id = Integer.parseInt(
                    value.trim()
            );

            if (id <= 0) {
                throw new NumberFormatException(
                        "non-positive id"
                );
            }

            return id;

        } catch (NumberFormatException e) {
            throw new BadRequestException(
                    "Invalid WireGuard peer id."
            );
        }
    }
}
