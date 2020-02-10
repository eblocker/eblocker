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

import static org.junit.Assert.*;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.InternetAccessContingent;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.UserProfileModule.InternetAccessRestrictionMode;

public class DevicePermissionsServiceTest {
    private DevicePermissionsService service;
    private UserService userService;
    private ParentalControlService parentalControlService;
    private Device device;
    private UserModule operatingUser;
    private UserProfileModule profile;

    @Before
    public void setUp() throws Exception {
        userService = Mockito.mock(UserService.class);
        parentalControlService = Mockito.mock(ParentalControlService.class);
        service = new DevicePermissionsService(userService, parentalControlService);

        device = new Device();
        int userId = 7;
        Integer profileId = 42;
        device.setOperatingUser(userId);
        operatingUser = new UserModule(userId, profileId, null, null, null, null, false, null, null, null, null, null);
        profile = createProfile(profileId);

        // Stubbing:
        Mockito.when(userService.getUserById(userId)).thenReturn(operatingUser);
        Mockito.when(parentalControlService.getProfile(profileId)).thenReturn(profile);
    }

    @Test
    public void permissionToPauseGranted() {
        assertTrue(service.operatingUserMayPause(device));
    }

    @Test
    public void permissionToPauseDenied() {
        profile.setControlmodeMaxUsage(true);
        assertFalse(service.operatingUserMayPause(device));
    }

    private UserProfileModule createProfile(Integer profileId) {
        String name = "My profile";
        String description = "";
        String nameKey = "name key";
        String descriptionKey = "description key";
        Boolean standard = false;
        Boolean hidden = false;
        Set<Integer> accessibleSitesPackages = Collections.emptySet();
        Set<Integer> inaccessibleSitesPackages = Collections.emptySet();
        InternetAccessRestrictionMode internetAccessRestrictionMode = InternetAccessRestrictionMode.NONE;
        Set<InternetAccessContingent> internetAccessContingents = Collections.emptySet();
        Map<DayOfWeek, Integer> maxUsageTimeByDay = Collections.emptyMap();
        return new UserProfileModule(profileId, name, description, nameKey, descriptionKey, standard, hidden, accessibleSitesPackages, inaccessibleSitesPackages, internetAccessRestrictionMode, internetAccessContingents, maxUsageTimeByDay, null, false, null);
    }

}
