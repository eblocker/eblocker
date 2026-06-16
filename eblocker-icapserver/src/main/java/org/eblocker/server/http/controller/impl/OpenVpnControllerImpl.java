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
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.io.IOUtils;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.openvpn.OpenVpnConfigurationViewModel;
import org.eblocker.server.common.data.openvpn.VpnProfile;
import org.eblocker.server.common.data.openvpn.VpnStatus;
import org.eblocker.server.common.data.wireguard.WireGuardProfile;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.common.transaction.TransactionIdentifier;
import org.eblocker.server.common.wireguard.WireGuardService;
import org.eblocker.server.common.wireguard.configuration.WireGuardConfiguration;
import org.eblocker.server.common.wireguard.configuration.WireGuardPeer;
import org.eblocker.server.http.controller.OpenVpnController;
import org.eblocker.server.http.service.AnonymousService;
import org.eblocker.server.http.service.DeviceService;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.NotFoundException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * REST controller for user configured VPN provider profiles.
 *
 * The route/interface names still contain OpenVPN for frontend compatibility, but provider profiles are stored and
 * executed as WireGuard configurations.
 */
public class OpenVpnControllerImpl implements OpenVpnController {
    private final AnonymousService anonymousService;
    private final WireGuardService wireGuardService;
    private final SessionStore sessionStore;
    private final DeviceService deviceService;

    @Inject
    public OpenVpnControllerImpl(AnonymousService anonymousService, WireGuardService wireGuardService,
                                 SessionStore sessionStore, DeviceService deviceService) {
        this.anonymousService = anonymousService;
        this.wireGuardService = wireGuardService;
        this.sessionStore = sessionStore;
        this.deviceService = deviceService;
    }

    //
    // profile management
    //
    @Override
    public Collection<VpnProfile> getProfiles(Request request, Response response) {
        return wireGuardService.getVpnProfiles();
    }

    @Override
    public VpnProfile createProfile(Request request, Response response) {
        WireGuardProfile profile = request.getBodyAs(WireGuardProfile.class);
        if (profile == null) {
            profile = new WireGuardProfile();
        }
        try {
            profile = wireGuardService.saveProfile(profile);
            response.setResponseCode(HttpResponseStatus.CREATED.code());
            response.addHeader("Location", "/anonymous/vpn/profile/" + profile.getId());
            return profile;
        } catch (IOException e) {
            throw new EblockerException("failed to create profile", e);
        }
    }

    @Override
    public VpnProfile getProfile(Request request, Response response) {
        return getProfile(request);
    }

    @Override
    public VpnProfile updateProfile(Request request, Response response) {
        int id = getId(request);
        WireGuardProfile wireGuardProfile = request.getBodyAs(WireGuardProfile.class);
        if (wireGuardProfile == null || id != wireGuardProfile.getId()) {
            throw new BadRequestException();
        }

        try {
            return wireGuardService.saveProfile(wireGuardProfile);
        } catch (IOException e) {
            throw new EblockerException("failed to save profile", e);
        }
    }

    @Override
    public void deleteProfile(Request request, Response response) {
        Integer id = getId(request);

        // disable vpn and reset devices
        deviceService.getDevices(true).stream()
                .filter(d -> id.equals(d.getUseVPNProfileID()))
                .forEach(d -> {
                    anonymousService.disableVpn(d);
                    d.setUseVPNProfileID(null);
                    deviceService.updateDevice(d);
                });

        wireGuardService.deleteVpnProfile(id);
    }

    @Override
    public OpenVpnConfigurationViewModel getProfileConfig(Request request, Response response) {
        try {
            WireGuardConfiguration configuration = wireGuardService.getProfileClientConfig(getId(request));
            if (configuration == null) {
                throw new NotFoundException();
            }
            return mapConfiguration(configuration);
        } catch (FileNotFoundException e) {
            throw new NotFoundException(e);
        } catch (IOException e) {
            throw new EblockerException("failed to load profile config", e);
        }
    }

    @Override
    public OpenVpnConfigurationViewModel uploadProfileConfig(Request request, Response response) {
        try {
            String config = IOUtils.toString(request.getBodyAsStream());
            WireGuardConfiguration configuration = wireGuardService.setProfileClientConfig(getId(request), config);
            if (configuration == null) {
                throw new BadRequestException();
            }
            return mapConfiguration(configuration);
        } catch (IOException e) {
            throw new EblockerException("failed to get config from request", e);
        }
    }

    @Override
    public OpenVpnConfigurationViewModel uploadProfileConfigOption(Request request, Response response) {
        throw new BadRequestException("WireGuard provider configurations do not use external OpenVPN option files");
    }

    //
    // control / runtime information
    //
    @Override
    public VpnStatus getVpnStatusByDevice(Request request, Response response) {
        VpnStatus status = wireGuardService.getStatusByDevice(getDevice(request));
        if (status != null) {
            return status;
        } else {
            response.setResponseCode(HttpResponseStatus.NO_CONTENT.code());
            return null;
        }
    }

    @Override
    public VpnStatus getVpnStatus(Request request, Response response) {
        VpnProfile profile = getProfile(request);
        VpnStatus status = wireGuardService.getStatus(profile);
        status.setProfileId(profile.getId());
        return status;
    }

    @Override
    public VpnStatus setVpnStatus(Request request, Response response) {
        VpnProfile profile = getProfile(request);
        VpnStatus status = request.getBodyAs(VpnStatus.class);
        if (status == null) {
            throw new BadRequestException();
        }

        if (status.isActive()) {
            wireGuardService.startVpn(profile);
        } else {
            wireGuardService.stopVpn(profile);
        }

        return wireGuardService.getStatus(profile);
    }

    @Override
    public boolean getVpnDeviceStatus(Request request, Response response) {
        VpnStatus status = wireGuardService.getStatus(getProfile(request));
        return status.getDevices().contains(getDevice(request).getId());
    }

    @Override
    public void setVpnDeviceStatus(Request request, Response response) {
        VpnProfile profile = getProfile(request);
        Device device = getDevice(request);

        Boolean status = request.getBodyAs(Boolean.class);
        if (status == null) {
            throw new BadRequestException();
        }

        if (status) {
            anonymousService.enableVpn(device, profile);
        } else {
            anonymousService.disableVpn(device);
        }
    }

    /*
     * This function is only called by the squid error page and it is assumed to
     * activate/deactivate VPN for the current device
     */
    @Override
    public void setVpnThisDeviceStatus(Request request, Response response) {
        VpnProfile profile = getProfile(request);
        Device device = deviceService.getDeviceById(getSession(request).getDeviceId());

        Boolean status = request.getBodyAs(Boolean.class);
        if (status == null) {
            throw new BadRequestException();
        }

        if (status) {
            anonymousService.enableVpn(device, profile);
        } else {
            anonymousService.disableVpn(device);
        }
    }

    //
    // helper methods
    //
    private int getId(Request request) {
        String idParameter = request.getHeader("id");
        if (idParameter == null) {
            throw new BadRequestException();
        }
        return Integer.valueOf(idParameter);
    }

    private VpnProfile getProfile(Request request) {
        int id = getId(request);
        VpnProfile profile = wireGuardService.getVpnProfileById(id);
        if (profile == null) {
            throw new NotFoundException();
        }
        return profile;
    }

    private String getDeviceId(Request request) {
        String device = request.getHeader("device");
        if (device == null) {
            throw new BadRequestException();
        }
        return device;
    }

    private Device getDevice(Request request) {
        String id = getDeviceId(request);

        Device device;
        if ("me".equals(id)) {
            device = deviceService.getDeviceById(getSession(request).getDeviceId());
        } else {
            device = deviceService.getDeviceById(id);
        }

        if (device == null) {
            throw new NotFoundException();
        }
        return device;
    }

    // TODO: why not subclass SessionContextController?
    private Session getSession(Request request) {
        return sessionStore.getSession((TransactionIdentifier) request.getAttachment("transactionIdentifier"));
    }

    private OpenVpnConfigurationViewModel mapConfiguration(WireGuardConfiguration configuration) {
        OpenVpnConfigurationViewModel model = new OpenVpnConfigurationViewModel();
        List<OpenVpnConfigurationViewModel.ConfigLine> activeOptions = new ArrayList<>();
        int[] lineNumber = new int[]{ 1 };

        addLine(activeOptions, lineNumber, "PrivateKey = " + configuration.getPrivateKey());
        addJoinedLine(activeOptions, lineNumber, "Address", configuration.getAddresses());
        addJoinedLine(activeOptions, lineNumber, "DNS", configuration.getDnsServers());
        if (configuration.getMtu() != null) {
            addLine(activeOptions, lineNumber, "MTU = " + configuration.getMtu());
        }
        for (WireGuardPeer peer: configuration.getPeers()) {
            addLine(activeOptions, lineNumber, "PublicKey = " + peer.getPublicKey());
            if (peer.getPresharedKey() != null) {
                addLine(activeOptions, lineNumber, "PresharedKey = " + peer.getPresharedKey());
            }
            if (peer.getEndpoint() != null) {
                addLine(activeOptions, lineNumber, "Endpoint = " + peer.getEndpoint());
            }
            addJoinedLine(activeOptions, lineNumber, "AllowedIPs", peer.getAllowedIps());
            if (peer.getPersistentKeepalive() != null) {
                addLine(activeOptions, lineNumber, "PersistentKeepalive = " + peer.getPersistentKeepalive());
            }
        }

        model.setActiveOptions(activeOptions);
        model.setBlacklistedOptions(Collections.emptyList());
        model.setIgnoredOptions(Collections.emptyList());
        model.setRequiredFiles(Collections.emptyList());
        model.setCredentialsRequired(false);
        model.setValidationErrors(Collections.emptyList());
        return model;
    }

    private void addJoinedLine(List<OpenVpnConfigurationViewModel.ConfigLine> lines, int[] lineNumber,
                               String option, List<String> values) {
        if (values != null && !values.isEmpty()) {
            addLine(lines, lineNumber, option + " = " + String.join(", ", values));
        }
    }

    private void addLine(List<OpenVpnConfigurationViewModel.ConfigLine> lines, int[] lineNumber, String value) {
        OpenVpnConfigurationViewModel.ConfigLine line = new OpenVpnConfigurationViewModel.ConfigLine();
        line.source = "user";
        line.lineNumber = lineNumber[0]++;
        line.line = value;
        lines.add(line);
    }
}
