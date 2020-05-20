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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.core.UriBuilder;

import org.eblocker.server.common.data.OperatingSystemType;
import org.eblocker.server.common.data.openvpn.ExternalAddressType;
import org.eblocker.server.common.data.openvpn.PortForwardingMode;
import org.eblocker.server.common.exceptions.UpnpPortForwardingException;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.eblocker.server.http.service.DynDnsService;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.openvpn.server.OpenVpnClientConfigurationService;
import org.eblocker.server.common.openvpn.server.VpnServerStatus;
import org.eblocker.server.common.squid.SquidConfigController;
import org.restexpress.Request;
import org.restexpress.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eblocker.server.http.controller.OpenVpnServerController;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.OpenVpnServerService;
import org.eblocker.server.http.utils.NormalizationUtils;
import org.eblocker.server.http.service.DeviceService.DeviceChangeListener;
import com.google.inject.Inject;
import com.google.inject.name.Named;

public class OpenVpnServerControllerImpl implements OpenVpnServerController {
    private static final Logger log = LoggerFactory.getLogger(OpenVpnServerControllerImpl.class);
    private final OpenVpnServerService openVpnServerService;
    private final DeviceService deviceService;
    private final DynDnsService dynDnsService;
    private final OpenVpnClientConfigurationService openVpnClientConfigurationService;
    private final SquidConfigController squidConfigController;
    private final DeviceRegistrationProperties deviceRegistrationProperties;
    private final NetworkStateMachine networkStateMachine;

    @Inject
    public OpenVpnServerControllerImpl(OpenVpnServerService openVpnServerService,
                                       OpenVpnClientConfigurationService openVpnClientConfigurationService,
                                       DeviceService deviceService,
                                       DynDnsService dynDnsService,
                                       SquidConfigController squidConfigController,
                                       DeviceRegistrationProperties deviceRegistrationProperties,
                                       NetworkStateMachine networkStateMachine) {
        this.openVpnServerService = openVpnServerService;
        this.dynDnsService = dynDnsService;
        this.openVpnClientConfigurationService = openVpnClientConfigurationService;
        this.squidConfigController = squidConfigController;
        this.deviceService = deviceService;
        this.deviceRegistrationProperties = deviceRegistrationProperties;
        this.networkStateMachine = networkStateMachine;
        this.deviceService.addListener(new DeviceChangeListener() {
            @Override
            public void onChange(Device device) {
                // Nothing to do here.
            }

            @Override
            public void onDelete(Device device) {
                revoke(device.getId());
            }

            @Override
            public void onReset(Device device) {
                // Nothing to do here
            }
        });
    }

    @Override
    public VpnServerStatus getOpenVpnServerStatus(Request request, Response response) {
        log.info("getStatus");

        VpnServerStatus result = new VpnServerStatus();
        result.setFirstStart(openVpnServerService.isOpenVpnServerfirstRun());
        result.setHost(openVpnServerService.getOpenVpnServerHost());
        result.setRunning(obtainServerStatus());
        result.setExternalAddressType(openVpnServerService.getOpenVpnExternalAddressType());
        result.setMappedPort(openVpnServerService.getOpenVpnMappedPort());
        result.setPortForwardingMode(openVpnServerService.getOpenVpnPortForwardingMode());

        return result;
    }

    @Override
    public VpnServerStatus setOpenVpnServerStatus(Request request, Response response) {
        VpnServerStatus newStatus = request.getBodyAs(VpnServerStatus.class);
        log.info("setStatus {}", newStatus.isRunning());
        VpnServerStatus result = new VpnServerStatus();

        if (newStatus.getExternalAddressType() == ExternalAddressType.EBLOCKER_DYN_DNS) {
            if (!dynDnsService.isEnabled()) {
                dynDnsService.enable();
                dynDnsService.update();
            }
            openVpnServerService.setOpenVpnServerHost(dynDnsService.getHostname());
        } else {
            if (dynDnsService.isEnabled()) {
                dynDnsService.disable();
            }
            String newHost = newStatus.getHost() != null ? newStatus.getHost() : "";
            openVpnServerService.setOpenVpnServerHost(newHost);
        }
        result.setHost(openVpnServerService.getOpenVpnServerHost());

        openVpnServerService.setOpenVpnExternalAddressType(newStatus.getExternalAddressType());

        Integer mappedPort = newStatus.getMappedPort();
        if (mappedPort != null && newStatus.getPortForwardingMode() == PortForwardingMode.AUTO) {
            openVpnServerService.setOpenVpnTempMappedPort(mappedPort);
        } else if (mappedPort != null) {
            // this fixes the issue where the wrong port was used in the connection test (EB1-1867)
            // TODO check if tempPort is still needed, doesn't seem to make sense anymore. See MobileConnectionCheckTask: but there we actually want to use the mappedPort, not tempPort
            openVpnServerService.setOpenVpnMappedPort(mappedPort);
        }

        openVpnServerService.setOpenVpnPortForwardingMode(newStatus.getPortForwardingMode());

        if (obtainServerStatus() == newStatus.isRunning()) {
            result.setRunning(true);
        } else {
            if (newStatus.isRunning()) {
                openVpnServerService.setOpenVpnMappedPort(mappedPort);
                try {
                    result.setRunning(openVpnServerService.startOpenVpnServer());
                } catch (UpnpPortForwardingException e) {
                    throw new InternalServerErrorException(e);
                }
            } else {
                result.setRunning(!openVpnServerService.stopOpenVpnServer());
                if (!result.isRunning()) {
                    stopOpenVpnServer();
                }
            }
            squidConfigController.tellSquidToReloadConfig();
        }

        result.setFirstStart(openVpnServerService.isOpenVpnServerfirstRun());

        return result;
    }

    @Override
    public void setPortForwarding(Request request, Response response) {
        String port = request.getHeader("port");
        log.info("setPortForwarding to {}", port);
        try {
            openVpnServerService.setAndMapExternalPortTemporarily(Integer.valueOf(port));
        } catch (UpnpPortForwardingException e) {
            throw new InternalServerErrorException(e);
        }
    }

    private void stopOpenVpnServer() {
        Collection<Device> devices = deviceService.getDevices(false);
        for (Device device : devices) {
            device.setIsVpnClient(false);
        }

        try {
            openVpnServerService.disableOpenVpnServer();
        } catch (UpnpPortForwardingException e) {
            throw new InternalServerErrorException(e);
        }
    }

    @Override
    public boolean resetOpenVpnServerStatus(Request request, Response response) {
        log.info("resetStatus");

        boolean result;

        // first we set 'first-run' to true. So if anything goes wrong during the purge, the next restart
        // of eBlocker mobile should clean up anything that is left.
        openVpnServerService.setOpenVpnServerfirstRun(true);
        result = openVpnServerService.stopOpenVpnServer();
        if (result) {
            try {
                // save consistent reset-state in redis: to avoid eBlocker mobile to be re-enabled
                // when the ICAP server boots after the reset.
                openVpnServerService.disableOpenVpnServer();
            } catch(UpnpPortForwardingException e){
                log.error("Unable to reset port forwarding during eBlocker mobile reset", e);
            }
            // even if port forwarding has not been removed, we have already disabled the server,
            // so we want to continue the reset.
            result = openVpnServerService.purgeOpenVpnServer();
        }

        return result;
    }

    @Override
    public List<String> getCertificates(Request request, Response response) throws IOException {
        return getCertificates();
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


        if (!getCertificates().contains(device.getId())) {
            if (!setCertificate(device.getId())) {
                log.error("Could not create certificate for device {}", device.getId());
                response.setResponseCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());
                return null;
            } else {
                log.info("Certificate for device {} created successfully.", device.getId());
            }
        }

        OperatingSystemType osType = getOsType(request);
        String downloadFilename = generateDownloadFileName(device, osType);
        response.setContentType("application/octet-stream");
        response.addHeader("Content-Disposition", "attachment; filename=\"" + downloadFilename + "\"");
        String stream = new String(openVpnClientConfigurationService.getOvpnProfile(device.getId(), osType));

        return Unpooled.wrappedBuffer(stream.getBytes());
    }

    /*
     * (non-Javadoc)
     *
     * @see OpenVpnServerController#
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
        String[] pathPrefix = request.getPath().split("/openvpn/certificates/");

        if (!device.isEblockerMobileEnabled()) {
            log.warn("Device {} is not allowed to use eBlocker mobile", device.getId());
            response.setResponseCode(HttpResponseStatus.CONFLICT.code());
            return null;
        }

        UriBuilder builder = UriBuilder.fromPath(String.format("%s/openvpn/certificates/downloadClientConf/{arg1}", pathPrefix[0]));
        builder.queryParam("deviceType", osType);
        builder.queryParam(HttpHeaders.Names.AUTHORIZATION, authToken);
        return builder.build(device.getId()).toString();
    }

    private String generateDownloadFileName(Device device, OperatingSystemType osType) {
        return String.format("eBlockerMobile-%s-%s-%s.ovpn",
                NormalizationUtils.normalizeStringForFilename(deviceRegistrationProperties.getDeviceName(), 12,
                        "My_eBlocker"),
                NormalizationUtils.normalizeStringForFilename(device.getName(), 12, device.getId()),
                osType.getFriendyName());
    }

    @Override
    public String getOpenVpnFileName(Request request, Response response) throws IOException {
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
            }
            catch (IllegalArgumentException e) {
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
            log.warn("Device {} is already disabled for eBlocker mobile, however trying to revoke certifcate if one exists.", device.getId());
            if (!getCertificates().contains(device.getId())) {
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
            if (!getCertificates().contains(deviceId)) {
                log.info("Device {} has no certificates", deviceId);
                return true;
            }
        } catch (IOException e) {
            log.error("Could not get list of active certificates. Could not revoke certificate for " + deviceId, e);
            return false;
        }

        boolean result = openVpnServerService.revokeClientCertificate(deviceId);

        if (result) {
            log.info("revoke of {} successfull.", deviceId);
        } else {
            log.error("revoke of {} failed.", deviceId);
        }

        return result;
    }

    private String decodeHeader(String header) throws UnsupportedEncodingException {
        if (header != null) {
            return URLDecoder.decode(header, "UTF-8");
        }

        return null;
    }

    private boolean obtainServerStatus() {
        return openVpnServerService.getOpenVpnServerStatus();
    }

    private boolean setCertificate(String deviceId) {
        if (openVpnServerService.createClientCertificate(deviceId)) {
            log.info("setCertificates of {} successfull.", deviceId);
            return true;
        } else {
            log.error("setCertificates of {} failed.", deviceId);
            return false;
        }
    }

    private List<String> getCertificates() throws IOException {
        return new ArrayList<String>(openVpnServerService.getDeviceIdsWithCertificates());
    }
}
