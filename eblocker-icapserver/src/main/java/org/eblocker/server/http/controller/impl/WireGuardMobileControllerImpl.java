/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License. You may obtain a copy of the License at:
 *
 *   https://joinup.ec.europa.eu/page/eupl-text-11-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.eblocker.server.http.controller.impl;

import com.google.inject.Inject;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.OperatingSystemType;
import org.eblocker.server.common.exceptions.UpnpPortForwardingException;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.data.vpn.VpnServerStatus;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.eblocker.server.http.controller.WireGuardMobileController;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.WireGuardMobileService;
import org.eblocker.server.http.utils.NormalizationUtils;
import org.restexpress.Request;
import org.restexpress.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

public class WireGuardMobileControllerImpl implements WireGuardMobileController {
    private static final Logger log = LoggerFactory.getLogger(WireGuardMobileControllerImpl.class);
    private final DeviceService deviceService;
    private final WireGuardMobileService wireGuardMobileService;
    private final DeviceRegistrationProperties deviceRegistrationProperties;
    private final NetworkStateMachine networkStateMachine;

    @Inject
    public WireGuardMobileControllerImpl(WireGuardMobileService wireGuardMobileService,
                                       DeviceService deviceService,
                                       DeviceRegistrationProperties deviceRegistrationProperties,
                                       NetworkStateMachine networkStateMachine) {
        this.wireGuardMobileService = wireGuardMobileService;
        this.deviceService = deviceService;
        this.deviceRegistrationProperties = deviceRegistrationProperties;
        this.networkStateMachine = networkStateMachine;
    }

    @Override
    public VpnServerStatus getWireGuardMobileStatus(Request request, Response response) {
        log.info("getStatus");

        return wireGuardMobileService.getServerStatus();
    }

    @Override
    public VpnServerStatus setWireGuardMobileStatus(Request request, Response response) {
        VpnServerStatus newStatus = request.getBodyAs(VpnServerStatus.class);
        log.info("setStatus {}", newStatus.isRunning());

        VpnServerStatus result = wireGuardMobileService.setServerStatus(newStatus);
        try {
            if (result.isRunning()) {
                wireGuardMobileService.enablePortForwarding();
            } else {
                wireGuardMobileService.disablePortForwarding();
            }
        } catch (UpnpPortForwardingException e) {
            throw new InternalServerErrorException(e);
        }
        return result;
    }

    @Override
    public void setPortForwarding(Request request, Response response) {
        String port = request.getHeader("port");
        log.info("setPortForwarding to {}", port);
        try {
            wireGuardMobileService.setAndMapExternalPortTemporarily(Integer.valueOf(port));
        } catch (UpnpPortForwardingException e) {
            throw new InternalServerErrorException(e);
        }
    }

    @Override
    public boolean resetWireGuardMobileStatus(Request request, Response response) {
        log.info("resetStatus");

        return wireGuardMobileService.resetServer();
    }

    @Override
    public List<String> getConfigurations(Request request, Response response) throws IOException {
        return getConfigurations();
    }

    private Device getDevice(Request request) throws UnsupportedEncodingException {
        String deviceId = decodeHeader(request.getHeader("deviceId"));

        if (deviceId == null) {
            return null;
        }
        return deviceService.getDeviceById(deviceId);
    }

    @Override
    public Object downloadClientConf(Request request, Response response) throws IOException {
        Device device = getDevice(request);

        if (device == null) {
            response.setResponseCode(HttpResponseStatus.BAD_REQUEST.code());
            return null;
        }

        if (!device.isEblockerMobileEnabled()) {
            log.warn("Device {} is not allowed to use eBlocker mobile", device.getId());
            response.setResponseCode(HttpResponseStatus.CONFLICT.code());
            return null;
        }

        OperatingSystemType osType = getOsType(request);
        String downloadFilename = generateDownloadFileName(device, osType);
        response.setContentType("application/octet-stream");
        response.addHeader("Content-Disposition", "attachment; filename=\"" + downloadFilename + "\"");

        try {
            Integer mappedPort = wireGuardMobileService.getWireGuardMappedPort();
            String stream = wireGuardMobileService.generateClientConfiguration(
                    device.getId(),
                    wireGuardMobileService.getServerHost(),
                    mappedPort != null ? mappedPort : 1194);
            return Unpooled.wrappedBuffer(stream.getBytes());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Could not generate WireGuard configuration for device {}", device.getId(), e);
            response.setResponseCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());
            return null;
        } catch (IOException e) {
            log.error("Could not generate WireGuard configuration for device {}", device.getId(), e);
            response.setResponseCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());
            return null;
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see WireGuardMobileController#
     * generateDownloadUrl(org.restexpress.Request, org.restexpress.Response)
     * Generate a valid download URL with an authorization token as query
     * parameter
     */
    @Override
    public String generateDownloadUrl(Request request, Response response) throws IOException {
        Device device = getDevice(request);

        OperatingSystemType osType = getOsType(request);

        if (device == null) {
            response.setResponseCode(HttpResponseStatus.BAD_REQUEST.code());
            return null;
        }

        String authToken = decodeHeader(request.getHeader("Authorization"));
        String[] pathPrefix = request.getPath().split("/wireguard/configurations/");

        if (!device.isEblockerMobileEnabled()) {
            log.warn("Device {} is not allowed to use eBlocker mobile", device.getId());
            response.setResponseCode(HttpResponseStatus.CONFLICT.code());
            return null;
        }

        UriBuilder builder = UriBuilder.fromPath(String.format("%s/wireguard/configurations/downloadClientConf/{arg1}", pathPrefix[0]));
        builder.queryParam("deviceType", osType);
        builder.queryParam(HttpHeaders.Names.AUTHORIZATION, authToken);
        return builder.build(device.getId()).toString();
    }

    private String generateDownloadFileName(Device device, OperatingSystemType osType) {
        return String.format("eBlockerMobile-%s-%s-%s.conf",
                NormalizationUtils.normalizeStringForFilename(deviceRegistrationProperties.getDeviceName(), 12,
                        "My_eBlocker"),
                NormalizationUtils.normalizeStringForFilename(device.getName(), 12, device.getId()),
                osType.getFriendyName());
    }

    @Override
    public String getWireGuardConfigurationFileName(Request request, Response response) throws IOException {
        Device device = getDevice(request);

        if (device == null) {
            response.setResponseCode(HttpResponseStatus.BAD_REQUEST.code());
            return null;
        }
        OperatingSystemType osType = getOsType(request);
        return generateDownloadFileName(device, osType);
    }

    private OperatingSystemType getOsType(Request request) throws UnsupportedEncodingException {
        OperatingSystemType osType = OperatingSystemType.OTHER;
        String type = decodeHeader(request.getHeader("deviceType"));

        if (type != null) {
            try {
                osType = OperatingSystemType.valueOf(type);
            } catch (IllegalArgumentException e) {
                log.warn("Unkown operating system.", e);
            }
        }

        return osType;
    }

    @Override
    public boolean enableDevice(Request request, Response response) throws IOException {
        Device device = getDevice(request);

        if (device == null) {
            log.warn("Enabling {}: Device not found.", decodeHeader(request.getHeader("deviceId")));
            response.setResponseCode(HttpResponseStatus.BAD_REQUEST.code());
            return false;
        }

        if (device.isEblockerMobileEnabled()) {
            log.warn("Device {} already enabled for eBlocker mobile", device.getId());
        } else {
            device.setMobileState(true);
            deviceService.updateDevice(device);
        }

        return true;
    }

    @Override
    public boolean disableDevice(Request request, Response response) throws IOException {
        Device device = getDevice(request);

        if (device == null) {
            log.warn("revoke of {}: Device not found.", decodeHeader(request.getHeader("deviceId")));
            response.setResponseCode(HttpResponseStatus.BAD_REQUEST.code());
            return false;
        }

        if (!device.isEblockerMobileEnabled()) {
            log.warn("Device {} is already disabled for eBlocker mobile, however trying to remove its WireGuard peer if one exists.", device.getId());
            if (!getConfigurations().contains(device.getId())) {
                return true;
            }
        }

        if (!revoke(device.getId())) {
            response.setResponseCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());
            return false;
        }

        device.setMobileState(false);
        deviceService.updateDevice(device);

        return true;
    }

    @Override
    public boolean setPrivateNetworkAccess(Request request, Response response) throws IOException {
        boolean privateNetworkAccess = request.getBodyAs(Boolean.class);
        Device device = getDevice(request);
        if (device == null) {
            throw new BadRequestException("device unavailable");
        }
        device.setMobilePrivateNetworkAccess(privateNetworkAccess);
        deviceService.updateDevice(device);
        networkStateMachine.deviceStateChanged(device);
        return device.isMobilePrivateNetworkAccess();
    }

    private boolean revoke(String deviceId) {
        try {
            if (!getConfigurations().contains(deviceId)) {
                log.info("Device {} has no WireGuard peer", deviceId);
                return true;
            }
        } catch (IOException e) {
            log.error("Could not get list of active WireGuard peers. Could not remove peer for {}", deviceId, e);
            return false;
        }

        wireGuardMobileService.removePeer(deviceId);
        boolean result = true;

        if (result) {
            log.info("WireGuard peer removal of {} successful.", deviceId);
        } else {
            log.error("WireGuard peer removal of {} failed.", deviceId);
        }

        return result;
    }

    private String decodeHeader(String header) throws UnsupportedEncodingException {
        if (header != null) {
            return URLDecoder.decode(header, "UTF-8");
        }

        return null;
    }


    private List<String> getConfigurations() throws IOException {
        List<String> deviceIds = new ArrayList<>();
        wireGuardMobileService.getPeers().forEach(peer -> deviceIds.add(peer.getDeviceId()));
        return deviceIds;
    }
}
