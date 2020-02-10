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

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.openvpn.OpenVpnConfigurationViewModel;
import org.eblocker.server.common.data.openvpn.OpenVpnProfile;
import org.eblocker.server.common.data.openvpn.VpnProfile;
import org.eblocker.server.common.data.openvpn.VpnStatus;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.openvpn.OpenVpnService;
import org.eblocker.server.common.openvpn.configuration.OpenVpnConfiguration;
import org.eblocker.server.common.openvpn.configuration.OpenVpnConfigurator;
import org.eblocker.server.common.openvpn.configuration.OpenVpnFileOptionValidator;
import org.eblocker.server.common.openvpn.configuration.Option;
import org.eblocker.server.common.openvpn.configuration.SimpleOption;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.common.transaction.TransactionIdentifier;
import org.eblocker.server.http.controller.OpenVpnController;
import org.eblocker.server.http.service.AnonymousService;
import org.eblocker.server.http.service.DeviceService;
import com.google.inject.Inject;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.io.IOUtils;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** This is the REST-Controller to provide the interface between frontend and backend for handling the routing of client
 *  traffic through OpenVPN instances (user configured VPN services).
 *
 */
public class OpenVpnControllerImpl implements OpenVpnController {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(OpenVpnControllerImpl.class);

    private final AnonymousService anonymousService;
    private final OpenVpnService openVpnService;
    private final SessionStore sessionStore;
    private final DeviceService deviceService;
    private final OpenVpnConfigurator configurator;

    @Inject
    public OpenVpnControllerImpl(AnonymousService anonymousService, OpenVpnService openVpnService, SessionStore sessionStore, DeviceService deviceService, OpenVpnConfigurator configurator) {
        this.anonymousService = anonymousService;
        this.openVpnService = openVpnService;
        this.sessionStore = sessionStore;
        this.deviceService = deviceService;
        this.configurator = configurator;
    }

    //
    // profile management
    //
    @Override
    public Collection<VpnProfile> getProfiles(Request request, Response response) {
        return openVpnService.getVpnProfiles();
    }

    @Override
    public VpnProfile createProfile(Request request, Response response) {
        OpenVpnProfile profile = request.getBodyAs(OpenVpnProfile.class);
        if (profile == null){
            profile = new OpenVpnProfile();
        }
        try {
            profile = openVpnService.saveProfile(profile);
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
        OpenVpnProfile openVpnProfile = request.getBodyAs(OpenVpnProfile.class);
        if (id != openVpnProfile.getId()) {
            throw new BadRequestException();
        }

        try {
            return openVpnService.saveProfile(openVpnProfile);
        } catch (IOException e) {
            throw new EblockerException("failed to save profile", e);
        }
    }

    @Override
    public void deleteProfile(Request request, Response response) {
        Integer id = getId(request);

        // disable vpn and reset devices
        deviceService.getDevices(true).stream()
                .filter(d->id.equals(d.getUseVPNProfileID()))
                .forEach(d->{
                    anonymousService.disableVpn(d);
                    d.setUseVPNProfileID(null);
                    deviceService.updateDevice(d);
                });

        openVpnService.deleteVpnProfile(id);
    }

    @Override
    public OpenVpnConfigurationViewModel getProfileConfig(Request request, Response response) {
        try {
            return mapConfiguration(openVpnService.getProfileClientConfig(getId(request)));
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
            return mapConfiguration(openVpnService.setProfileClientConfig(getId(request), config));
        } catch (IOException e) {
            throw new EblockerException("failed to get config from request", e);
        }
    }

    @Override
    public OpenVpnConfigurationViewModel uploadProfileConfigOption(Request request, Response response) {
        try {
            byte[] content = new byte[request.getBody().readableBytes()];
            request.getBody().readBytes(content);
            return mapConfiguration(openVpnService.setProfileClientConfigOptionFile(getId(request), getOption(request), content));
        } catch (OpenVpnFileOptionValidator.ValidationException e) {
            throw new BadRequestException(e);
        } catch (IOException e) {
            throw new EblockerException("failed to upload inline content", e);
        }
    }

    //
    // control / runtime information
    //
    @Override
    public VpnStatus getVpnStatusByDevice(Request request, Response response) {
        VpnStatus status = openVpnService.getStatusByDevice(getDevice(request));
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
        VpnStatus status = openVpnService.getStatus(profile);
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
            openVpnService.startVpn(profile);
        } else {
            openVpnService.stopVpn(profile);
        }

        return openVpnService.getStatus(profile);
    }

    @Override
    public boolean getVpnDeviceStatus(Request request, Response response) {
        VpnStatus status = openVpnService.getStatus(getProfile(request));
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
        VpnProfile profile = openVpnService.getVpnProfileById(id);
        if (profile == null) {
            throw new NotFoundException();
        }
        return profile;
    }

    private String getOption(Request request) {
        String optionParameter = request.getHeader("option");
        if (optionParameter == null) {
            throw new BadRequestException();
        }
        return optionParameter;
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

    private Session getSession(Request request) {
        return sessionStore.getSession((TransactionIdentifier) request.getAttachment("transactionIdentifier"));
    }

    private OpenVpnConfigurationViewModel mapConfiguration(OpenVpnConfiguration configuration) {
        OpenVpnConfigurationViewModel model = new OpenVpnConfigurationViewModel();

        Map<OpenVpnConfigurator.OptionState, Set<Option>> userOptionsByState = configurator.getUserOptionsByState(configuration.getUserOptions());
        model.setActiveOptions(configurator.getActiveConfiguration(configuration, "credentials.txt", new HashMap<>()).stream()
                .map(o -> mapActiveOption(configuration, userOptionsByState, o)).collect(Collectors.toList()));
        model.setBlacklistedOptions(mapOptions(userOptionsByState.get(OpenVpnConfigurator.OptionState.BLACKLISTED)));
        model.setIgnoredOptions(mapOptions(userOptionsByState.get(OpenVpnConfigurator.OptionState.IGNORED)));
        model.setRequiredFiles(userOptionsByState.get(OpenVpnConfigurator.OptionState.FILE_REQUIRED).stream()
                .map(o -> mapInliningRequiredOption(configuration, (SimpleOption) o))
                .collect(Collectors.toList()));
        model.setCredentialsRequired(configurator.credentialsRequired(configuration));
        model.setValidationErrors(configurator.validateConfiguration(configuration));
        return model;
    }

    private List<OpenVpnConfigurationViewModel.ConfigLine> mapOptions(Set<Option> options) {
        if (options == null) {
            return Collections.emptyList();
        }
        return options.stream()
            .map(this::mapOption)
            .sorted(Comparator.comparingInt(a -> a.lineNumber))
            .collect(Collectors.toList());
    }

    private OpenVpnConfigurationViewModel.ConfigLine mapActiveOption(OpenVpnConfiguration configuration,
                                                                     Map<OpenVpnConfigurator.OptionState,
                                                                             Set<Option>> userOptionsByState,
                                                                     Option option) {
        Optional <Option> userOption = findOptionByName(configuration.getUserOptions(), option.getName());
        boolean isEblockerOption = findOptionByName(configurator.getEblockerOptions(), option.getName()).isPresent();
        Option newOption = option;

        // show orginal user option instead of inlined one
        if (userOption.isPresent() && findOptionByName(userOptionsByState.get(OpenVpnConfigurator.OptionState.FILE_REQUIRED), option.getName()).isPresent()) {
            isEblockerOption = false;
            newOption = userOption.get();
        }

        OpenVpnConfigurationViewModel.ConfigLine line = new OpenVpnConfigurationViewModel.ConfigLine();
        if (isEblockerOption) {
            line.source = "eblocker";
            if (userOption.isPresent()) {
                Option overwrittenUserOption = userOption.get();
                line.overriddenLineNumber = overwrittenUserOption.getLineNumber();
                line.overriddenLine = overwrittenUserOption.toString();
            }
        }  else {
            line.source = "user";
        }

        line.lineNumber = newOption.getLineNumber();
        line.line = newOption.toString();

        return line;
    }

    private Optional<Option> findOptionByName(Collection<Option> options, String name) {
        return options.stream().filter(o->o.getName().equals(name)).findAny();
    }

    private OpenVpnConfigurationViewModel.ConfigLine mapOption(Option option) {
        OpenVpnConfigurationViewModel.ConfigLine line = new OpenVpnConfigurationViewModel.ConfigLine();
        line.lineNumber = option.getLineNumber();
        line.line = option.toString();
        line.source = "user";
        return line;
    }

    private OpenVpnConfigurationViewModel.RequiredFile mapInliningRequiredOption(OpenVpnConfiguration configuration, SimpleOption option) {
        OpenVpnConfigurationViewModel.RequiredFile requiredFile = new OpenVpnConfigurationViewModel.RequiredFile();
        requiredFile.option = option.getName();
        requiredFile.name = option.getArguments() != null && option.getArguments().length > 0 ? option.getArguments()[0] : "";
        requiredFile.uploaded = configuration.getInlinedContentByName().containsKey(option.getName());
        return requiredFile;
    }
}
