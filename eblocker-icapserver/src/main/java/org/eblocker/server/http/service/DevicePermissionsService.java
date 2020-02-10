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
package org.eblocker.server.http.service;

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class DevicePermissionsService {
    private static final Logger log = LoggerFactory.getLogger(DevicePermissionsService.class);
    private final UserService userService;
    private final ParentalControlService parentalControlService;

    private enum Action {
        PAUSE, UPDATE
    }

    @Inject
    public DevicePermissionsService(UserService userService, ParentalControlService parentalControlService) {
        this.userService = userService;
        this.parentalControlService = parentalControlService;
    }

    public boolean operatingUserMayPause(Device device) {
        return operatingUserMayPerformAction(Action.PAUSE, device);
    }

    public boolean operatingUserMayUpdate(Device device) {
        return operatingUserMayPerformAction(Action.UPDATE, device);
    }

    private boolean operatingUserMayPerformAction(Action action, Device device) {
        UserModule user = userService.getUserById(device.getOperatingUser());
        int operatingUserId = device.getOperatingUser();
        if (user == null) {
            log.error("Could not determine operating user #{} of device {}. Permission for action {} denied.", operatingUserId, device, action);
            return false;
        }
        Integer profileId = user.getAssociatedProfileId();
        if (profileId == null) {
            log.error("Profile ID of operating user #{} is not set. Permission for action {} of device {} denied", operatingUserId, action, device);
            return false;
        }
        UserProfileModule profile = parentalControlService.getProfile(profileId);
        if (profile == null) {
            log.error("Could not get profile #{} of user #{}. Permission for action {} of device {} denied.", profileId, operatingUserId, action, device);
            return false;
        }

        boolean isRestricted = profile.isControlmodeMaxUsage() || profile.isControlmodeTime() || profile.isControlmodeUrls();
        return !isRestricted;
    }
}
