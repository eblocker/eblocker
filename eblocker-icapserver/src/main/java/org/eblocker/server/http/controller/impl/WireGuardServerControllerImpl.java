package org.eblocker.server.http.controller.impl;

import com.google.inject.Inject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.wireguard.WireGuardEndpointConfig;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.common.data.wireguard.WireGuardTunnelMode;
import org.eblocker.server.http.controller.WireGuardServerController;
import org.eblocker.server.http.model.WireGuardAuthorizationOverviewView;
import org.eblocker.server.http.model.WireGuardAuthorizationView;
import org.eblocker.server.http.model.WireGuardClientConfigurationView;
import org.eblocker.server.http.model.WireGuardPeerCreateRequest;
import org.eblocker.server.http.model.WireGuardPeerRoutingRequest;
import org.eblocker.server.http.model.WireGuardPeerView;
import org.eblocker.server.http.model.WireGuardServerStatusView;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.WireGuardAuthorizationManagementService;
import org.eblocker.server.http.service.WireGuardClientConfigurationService;
import org.eblocker.server.http.service.WireGuardPeerService;
import org.eblocker.server.http.service.WireGuardServerControlService;
import org.eblocker.server.http.service.WireGuardServerService;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.NotFoundException;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Authenticated admin API facade for the WireGuard core services.
 *
 * Client configuration and QR material are available only through explicit
 * authenticated admin actions. Normal status and peer metadata responses stay
 * secret-free and never expose private keys or preshared keys.
 */
public class WireGuardServerControllerImpl
        implements WireGuardServerController {

    private final WireGuardServerService serverService;
    private final WireGuardServerControlService controlService;
    private final WireGuardPeerService peerService;
    private final WireGuardClientConfigurationService clientConfigurationService;
    private final DeviceService deviceService;
    private final WireGuardAuthorizationManagementService authorizationManagementService;

    @Inject
    public WireGuardServerControllerImpl(
            WireGuardServerService serverService,
            WireGuardServerControlService controlService,
            WireGuardPeerService peerService,
            WireGuardClientConfigurationService clientConfigurationService,
            DeviceService deviceService,
            WireGuardAuthorizationManagementService authorizationManagementService) {

        this.serverService = serverService;
        this.controlService = controlService;
        this.peerService = peerService;
        this.clientConfigurationService = clientConfigurationService;
        this.deviceService = deviceService;
        this.authorizationManagementService =
                authorizationManagementService;
    }

    // Kept for the pre-existing focused controller tests.
    WireGuardServerControllerImpl(
            WireGuardServerService serverService,
            WireGuardServerControlService controlService,
            WireGuardPeerService peerService,
            WireGuardClientConfigurationService clientConfigurationService) {

        this(
                serverService,
                controlService,
                peerService,
                clientConfigurationService,
                null,
                null
        );
    }

    // Kept for authorization controller tests.
    WireGuardServerControllerImpl(
            WireGuardServerService serverService,
            WireGuardServerControlService controlService,
            WireGuardPeerService peerService,
            WireGuardClientConfigurationService clientConfigurationService,
            WireGuardAuthorizationManagementService authorizationManagementService) {

        this(
                serverService,
                controlService,
                peerService,
                clientConfigurationService,
                null,
                authorizationManagementService
        );
    }

    // Focused WG-13 provisioning tests.
    WireGuardServerControllerImpl(
            WireGuardServerService serverService,
            WireGuardServerControlService controlService,
            WireGuardPeerService peerService,
            WireGuardClientConfigurationService clientConfigurationService,
            DeviceService deviceService) {

        this(
                serverService,
                controlService,
                peerService,
                clientConfigurationService,
                deviceService,
                null
        );
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
    public WireGuardPeerView createPeerForDevice(
            Request request,
            Response response) {

        String deviceId = parseRequiredTextHeader(
                request,
                "deviceId",
                "WireGuard device id is required."
        );

        Device device = deviceService.getDeviceById(deviceId);

        if (device == null) {
            throw new NotFoundException(
                    "WireGuard target device not found."
            );
        }

        WireGuardPeer existing =
                peerService.getPeerByDeviceId(deviceId);

        if (existing != null) {
            response.setResponseCode(
                    HttpResponseStatus.CONFLICT.code()
            );

            return WireGuardPeerView.fromPeer(existing);
        }

        String peerName = device.getUserFriendlyName();

        if (peerName == null || peerName.trim().isEmpty()) {
            peerName = deviceId;
        }

        WireGuardPeer peer =
                peerService.createPeerForDevice(
                        peerName.trim(),
                        deviceId
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
    public WireGuardAuthorizationOverviewView getDeviceAuthorizations(
            Request request,
            Response response) {

        return authorizationManagementService
                .getDeviceAuthorizations();
    }

    @Override
    public WireGuardAuthorizationView getDeviceAuthorization(
            Request request,
            Response response) {

        String deviceId = parseRequiredTextHeader(
                request,
                "deviceId",
                "WireGuard device id is required."
        );

        WireGuardAuthorizationView view =
                authorizationManagementService
                        .getDeviceAuthorization(deviceId);

        if (view == null) {
            throw new NotFoundException(
                    "WireGuard device not found."
            );
        }

        return view;
    }

    @Override
    public WireGuardAuthorizationView setDeviceAuthorization(
            Request request,
            Response response) {

        String deviceId = parseRequiredTextHeader(
                request,
                "deviceId",
                "WireGuard device id is required."
        );

        Boolean enabled = request.getBodyAs(Boolean.class);

        if (enabled == null) {
            throw new BadRequestException(
                    "WireGuard device authorization state is required."
            );
        }

        if (!authorizationManagementService
                .setDeviceAuthorization(deviceId, enabled)) {

            throw new NotFoundException(
                    "WireGuard device not found."
            );
        }

        return authorizationManagementService
                .getDeviceAuthorization(deviceId);
    }

    @Override
    public boolean setUserAuthorization(
            Request request,
            Response response) {

        String value = parseRequiredTextHeader(
                request,
                "userId",
                "WireGuard user id is required."
        );

        int userId;

        try {
            userId = Integer.parseInt(value);

            if (userId < 0) {
                throw new NumberFormatException(
                        "negative user id"
                );
            }
        } catch (NumberFormatException e) {
            throw new BadRequestException(
                    "Invalid WireGuard user id."
            );
        }

        Boolean enabled = request.getBodyAs(Boolean.class);

        if (enabled == null) {
            throw new BadRequestException(
                    "WireGuard user authorization state is required."
            );
        }

        try {
            return authorizationManagementService
                    .setUserAuthorization(userId, enabled);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public WireGuardPeerView setRouting(
            Request request,
            Response response) {

        int id = parsePeerId(request);

        WireGuardPeerRoutingRequest body =
                request.getBodyAs(
                        WireGuardPeerRoutingRequest.class
                );

        if (body == null
                || body.getTunnelMode() == null
                || body.getTunnelMode().trim().isEmpty()) {

            throw new BadRequestException(
                    "WireGuard tunnel mode is required."
            );
        }

        final WireGuardTunnelMode tunnelMode;

        try {
            tunnelMode = WireGuardTunnelMode.valueOf(
                    body.getTunnelMode()
                            .trim()
                            .toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    "Invalid WireGuard tunnel mode."
            );
        }

        try {
            if (!peerService.setRouting(
                    id,
                    tunnelMode,
                    body.getCustomAllowedIps())) {

                throw new NotFoundException(
                        "WireGuard peer not found."
                );
            }
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    e.getMessage()
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

    private String parseRequiredTextHeader(
            Request request,
            String name,
            String missingMessage) {

        String value = request.getHeader(name);

        if (value == null || value.trim().isEmpty()) {
            throw new BadRequestException(missingMessage);
        }

        return value.trim();
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
