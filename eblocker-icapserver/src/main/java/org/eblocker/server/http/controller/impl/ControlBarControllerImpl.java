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
import com.google.inject.name.Named;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserModuleTransport;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.messagecenter.IconState;
import org.eblocker.server.common.data.messagecenter.MessageCenterMessage;
import org.eblocker.server.common.data.messagecenter.MessageSeverity;
import org.eblocker.server.common.data.openvpn.VpnStatus;
import org.eblocker.server.common.network.BaseURLs;
import org.eblocker.server.common.openvpn.OpenVpnService;
import org.eblocker.server.common.page.PageContextStore;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.common.util.IpUtils;
import org.eblocker.server.http.controller.ControlBarController;
import org.eblocker.server.http.controller.converter.UserModuleConverter;
import org.eblocker.server.http.model.CredentialsDTO;
import org.eblocker.server.http.model.DeviceDTO;
import org.eblocker.server.http.security.PasswordUtil;
import org.eblocker.server.http.server.SessionContextController;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.MessageCenterService;
import org.eblocker.server.http.service.ParentalControlService;
import org.eblocker.server.http.service.UserService;
import org.eblocker.server.http.utils.ControllerUtils;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.ForbiddenException;
import org.restexpress.exception.NotFoundException;
import org.restexpress.exception.ServiceException;
import org.restexpress.exception.UnauthorizedException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ControlBarControllerImpl extends SessionContextController implements ControlBarController {
    private final BaseURLs baseURLs;
    private final DeviceService deviceService;
    private final ParentalControlService parentalControlService;
    private final UserService userService;
    private final OpenVpnService openVpnService;
    private final MessageCenterService messageCenterService;
    private final String vpnSubnet;
    private final String vpnNetmask;

    @Inject
    public ControlBarControllerImpl(
        BaseURLs baseURLs,
        SessionStore sessionStore,
        PageContextStore pageContextStore,
        DeviceService deviceService,
        ParentalControlService parentalControlService,
        UserService userService,
        OpenVpnService openVpnService,
        MessageCenterService messageCenterService,
        @Named("network.vpn.subnet.ip") String vpnSubnet,
        @Named("network.vpn.subnet.netmask") String vpnNetmask
    ) {
        super(sessionStore, pageContextStore);
        this.baseURLs = baseURLs;
        this.deviceService = deviceService;
        this.parentalControlService = parentalControlService;
        this.userService = userService;
        this.openVpnService = openVpnService;
        this.messageCenterService = messageCenterService;
        this.vpnSubnet = vpnSubnet;
        this.vpnNetmask = vpnNetmask;
    }

    @Override
    public String getConsoleUrl(Request request, Response response) {
        return baseURLs.selectURLForPage(getScheme(request));
    }

    @Override
    public String getConsoleIp(Request request, Response response) {
        IpAddress remoteIp = ControllerUtils.getRequestIPAddress(request);
        if (remoteIp.isIpv4() && IpUtils.isInSubnet(remoteIp.toString(), vpnSubnet, vpnNetmask)) {
            return baseURLs.selectIpForPage(true, getScheme(request));
        }

        return baseURLs.selectIpForPage(false, getScheme(request));
    }

    private String getScheme(Request request) {
        String scheme = request.getHeader("Scheme");
        if (scheme == null) {
            scheme = request.getUrl();
        }
        return scheme;
    }

    @Override
    public UserProfileModule getUserProfile(Request request, Response response) {
        Session session = getSession(request);
        String deviceId = session.getDeviceId();
        Device device = deviceService.getDeviceById(deviceId);
        UserModule user = userService.getUserById(device.getOperatingUser());
        return parentalControlService.getProfile(user.getAssociatedProfileId());
    }

    @Override
    public UserModuleTransport getUser(Request request, Response response) {
        Session session = getSession(request);
        String deviceId = session.getDeviceId();
        Device device = deviceService.getDeviceById(deviceId);
        UserModule user = userService.getUserById(device.getOperatingUser());
        return UserModuleConverter.getUserModuleTransport(user);
    }

    @Override
    public Map<Integer, UserModuleTransport> getUsers(Request request, Response response) {
        Collection<UserModule> users = userService.getUsers(true);
        return users.stream().collect(Collectors.toMap(UserModule::getId, UserModuleConverter::getUserModuleTransport));
    }

    @Override
    public DeviceDTO getDevice(Request request, Response response) {
        Session session = getSession(request);
        String deviceId = session.getDeviceId();
        Device device = deviceService.getDeviceById(deviceId);
        if (device == null) {
            throw new ServiceException("Cannot find current device " + deviceId);
        }
        UserModule operatingUser = userService.getUserById(device.getOperatingUser());
        if (operatingUser == null) {
            throw new ServiceException("Cannot find current operating user " + device.getOperatingUser() + " of device " + deviceId);
        }
        UserProfileModule profile = parentalControlService.getProfile(operatingUser.getAssociatedProfileId());
        return new DeviceDTO(
            deviceId,
            device.getIpAddresses().stream().map(IpAddress::toString).collect(Collectors.toList()),
            device.getAssignedUser(),
            device.getOperatingUser(),
            profile,
            device.getFilterMode(),
            device.isFilterPlugAndPlayAdsEnabled(),
            device.isFilterPlugAndPlayTrackersEnabled(),
            device.isSslEnabled()
        );
    }

    @Override
    public Boolean getDeviceRestrictions(Request request, Response response) {
        response.addHeader("Cache-Control", "private, no-cache, no-store");
        response.addHeader("Access-Control-Allow-Origin", "*");

        Session session = getSession(request);
        String deviceId = session.getDeviceId();
        Device device = deviceService.getDeviceById(deviceId);
        if (device == null) {
            throw new ServiceException("Cannot find current device " + deviceId);
        }
        UserModule operatingUser = userService.getUserById(device.getOperatingUser());
        if (operatingUser == null) {
            throw new ServiceException("Cannot find current operating user " + device.getOperatingUser() + " of device " + deviceId);
        }
        UserProfileModule profile = parentalControlService.getProfile(operatingUser.getAssociatedProfileId());
        return profile.isControlmodeMaxUsage() || profile.isControlmodeTime() || profile.isControlmodeUrls();
    }

    @Override
    public void setOperatingUser(Request request, Response response) {
        Session session = getSession(request);
        String deviceId = session.getDeviceId();
        Device device = deviceService.getDeviceById(deviceId);
        CredentialsDTO credentials = request.getBodyAs(CredentialsDTO.class);

        // Do we have credentials?
        if (credentials == null || credentials.getId() == null) {
            throw new BadRequestException("No or invalid credentials given");
        }
        int userId = credentials.getId();
        String pin = credentials.getPin();

        // Is this a request to lock the device?
        if (userId == 2) {
            doSetOperatingUser(2, device);
            return;
        }

        // Is this a request to return the device? If yes, get actual target user.
        boolean returningDevice;
        if (userId == 0 || userId == device.getAssignedUser()) {
            userId = device.getAssignedUser();
            returningDevice = true;
        } else {
            returningDevice = false;
        }

        //
        // Request to change to another user.
        //

        // Does the target user exist?
        UserModule operatingUser = userService.getUserById(userId);
        if (operatingUser == null) {
            throw new NotFoundException("Operating user '" + userId + "' not found");
        }
        // If it's the return of a device to a user w/o PIN, we can proceed immediately
        if (returningDevice && operatingUser.getPin() == null) {
            doSetOperatingUser(userId, device);
            return;
        }
        // In all other cases, it's an error, if the new user does not have a PIN.
        if (operatingUser.getPin() == null) {
            throw new ForbiddenException("error.user.notPermitted");
        }
        // Does the PIN match?
        if (pin == null || !PasswordUtil.verifyPassword(pin, operatingUser.getPin())) {
            throw new UnauthorizedException("error.pin.invalid");
        }
        doSetOperatingUser(userId, device);
    }

    private void doSetOperatingUser(int userId, Device device) {
        device.setOperatingUser(userId);
        deviceService.updateDevice(device);
    }

    @Override
    public IconState getIconState(Request request, Response response) {
        response.addHeader("Cache-Control", "private, no-cache, no-store");
        response.addHeader("Access-Control-Allow-Origin", "*");
        // First, find out if an anonymization service is used
        Session session = getSession(request);
        Device device = deviceService.getDeviceById(session.getDeviceId());

        if (device == null) {
            return new IconState("", "");
        }
        if (device.isUseAnonymizationService() && device.isRoutedThroughTor()) {
            return new IconState("info", "TOR");
        } else {
            VpnStatus status = openVpnService.getStatusByDevice(device);
            if (status != null && status.isActive()) {
                return new IconState("info", "VPN");
            }
        }

        // If no anonymization is used, consider the messages
        List<MessageCenterMessage> messages = messageCenterService.getMessagesForDevice(device.getId());
        messages = messageCenterService.checkAndReduceSSLExpirationMessage(messages, device.getId(), session.getUserAgent());
        messages = messageCenterService.checkAndReduceSSLUntrustedMessage(messages, device.getId(), session.getUserAgent());

        long numAlerts = messages
            .stream()
            .filter(messageCenterMessage -> messageCenterMessage.getMessageSeverity().equals(MessageSeverity.ALERT))
            .count();
        if (numAlerts > 0) {
            return new IconState("alert", "!");
        }
        return new IconState("", "");
    }

}
