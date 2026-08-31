package org.eblocker.server.http.controller.impl;

import com.google.inject.Inject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.http.controller.WireGuardDashboardController;
import org.eblocker.server.http.model.WireGuardClientConfigurationView;
import org.eblocker.server.http.model.WireGuardPeerView;
import org.eblocker.server.http.security.DashboardAuthorizationProcessor;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.WireGuardClientConfigurationService;
import org.eblocker.server.http.service.WireGuardPeerService;
import org.eblocker.server.http.service.WireGuardServerService;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.NotFoundException;

public class WireGuardDashboardControllerImpl
        implements WireGuardDashboardController {

    private final WireGuardPeerService peerService;
    private final DeviceService deviceService;
    private final WireGuardClientConfigurationService clientConfigurationService;
    private final WireGuardServerService serverService;

    @Inject
    public WireGuardDashboardControllerImpl(
            WireGuardPeerService peerService,
            DeviceService deviceService,
            WireGuardClientConfigurationService clientConfigurationService,
            WireGuardServerService serverService) {

        this.peerService = peerService;
        this.deviceService = deviceService;
        this.clientConfigurationService = clientConfigurationService;
        this.serverService = serverService;
    }

    @Override
    public boolean getStatus(
            Request request,
            Response response) {

        String deviceId = getDeviceId(request);
        requireDevice(deviceId);

        return serverService.isEnabled();
    }

    @Override
    public WireGuardPeerView getPeer(
            Request request,
            Response response) {

        String deviceId = getDeviceId(request);
        requireDevice(deviceId);

        WireGuardPeer peer =
                peerService.getPeerByDeviceId(deviceId);

        if (peer == null) {
            throw new NotFoundException(
                    "No WireGuard peer exists for device."
            );
        }

        return WireGuardPeerView.fromPeer(peer);
    }

    @Override
    public WireGuardPeerView createPeer(
            Request request,
            Response response) {

        String deviceId = getDeviceId(request);
        Device device = requireDevice(deviceId);

        if (peerService.getPeerByDeviceId(deviceId) != null) {
            response.setResponseCode(
                    HttpResponseStatus.CONFLICT.code()
            );

            return null;
        }

        WireGuardPeer peer =
                peerService.createPeerForDevice(
                        device.getUserFriendlyName(),
                        deviceId
                );

        response.setResponseCode(
                HttpResponseStatus.CREATED.code()
        );

        return WireGuardPeerView.fromPeer(peer);
    }

    @Override
    public WireGuardPeerView setLanAccess(
            Request request,
            Response response) {

        String deviceId = getDeviceId(request);
        requireDevice(deviceId);

        Boolean allowLanAccess =
                request.getBodyAs(Boolean.class);

        if (allowLanAccess == null) {
            throw new BadRequestException(
                    "WireGuard LAN access value is required."
            );
        }

        WireGuardPeer peer =
                peerService.getPeerByDeviceId(deviceId);

        if (peer == null) {
            throw new NotFoundException(
                    "No WireGuard peer exists for device."
            );
        }

        if (!peerService.setLanAccess(
                peer.getId(),
                allowLanAccess)) {

            throw new NotFoundException(
                    "WireGuard peer disappeared before update."
            );
        }

        WireGuardPeer updated =
                peerService.getPeer(peer.getId());

        if (updated == null) {
            throw new NotFoundException(
                    "Updated WireGuard peer could not be loaded."
            );
        }

        return WireGuardPeerView.fromPeer(updated);
    }

    @Override
    public WireGuardClientConfigurationView getClientConfig(
            Request request,
            Response response) {

        String deviceId = getDeviceId(request);
        requireDevice(deviceId);

        WireGuardPeer peer =
                peerService.getPeerByDeviceId(deviceId);

        if (peer == null) {
            throw new NotFoundException(
                    "No WireGuard peer exists for device."
            );
        }

        String configuration;

        try {
            configuration =
                    clientConfigurationService.renderClientConfig(
                            peer.getId()
                    );

        } catch (IllegalArgumentException
                 | IllegalStateException e) {

            throw new BadRequestException(
                    e.getMessage()
            );
        }

        return new WireGuardClientConfigurationView(
                peer.getId(),
                configuration
        );
    }

    @Override
    public ByteBuf getPeerQrCode(
            Request request,
            Response response) {

        String deviceId = getDeviceId(request);
        requireDevice(deviceId);

        WireGuardPeer peer =
                peerService.getPeerByDeviceId(deviceId);

        if (peer == null) {
            throw new NotFoundException(
                    "No WireGuard peer exists for device."
            );
        }

        byte[] png;

        try {
            png =
                    clientConfigurationService
                            .renderClientConfigQrPng(
                                    peer.getId()
                            );

        } catch (IllegalArgumentException
                 | IllegalStateException e) {

            throw new BadRequestException(
                    e.getMessage()
            );
        }

        response.setContentType("image/png");

        response.addHeader(
                "Content-Disposition",
                "inline; filename=\"wireguard-device.png\""
        );

        return Unpooled.wrappedBuffer(png);
    }

    @Override
    public void deletePeer(
            Request request,
            Response response) {

        String deviceId = getDeviceId(request);
        requireDevice(deviceId);

        WireGuardPeer peer =
                peerService.getPeerByDeviceId(deviceId);

        if (peer == null) {
            throw new NotFoundException(
                    "No WireGuard peer exists for device."
            );
        }

        if (!peerService.deletePeer(peer.getId())) {
            throw new NotFoundException(
                    "WireGuard peer disappeared before deletion."
            );
        }

        response.setResponseCode(
                HttpResponseStatus.NO_CONTENT.code()
        );
    }

    private String getDeviceId(Request request) {
        String deviceId = request.getHeader(
                DashboardAuthorizationProcessor.DEVICE_ID_KEY
        );

        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new BadRequestException(
                    "Required parameter deviceId is missing."
            );
        }

        return deviceId.trim();
    }

    private Device requireDevice(String deviceId) {
        Device device =
                deviceService.getDeviceById(deviceId);

        if (device == null) {
            throw new NotFoundException(
                    "Device not found."
            );
        }

        return device;
    }
}
