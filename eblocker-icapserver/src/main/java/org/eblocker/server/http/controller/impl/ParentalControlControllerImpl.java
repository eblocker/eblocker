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
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UsageAccount;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.parentalcontrol.SearchEngineConfiguration;
import org.eblocker.server.common.page.PageContextStore;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.http.controller.ParentalControlController;
import org.eblocker.server.http.server.SessionContextController;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.ParentalControlSearchEngineConfigService;
import org.eblocker.server.http.service.ParentalControlService;
import org.eblocker.server.http.service.ParentalControlUsageService;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ParentalControlControllerImpl extends SessionContextController implements ParentalControlController {

    private static final Logger log = LoggerFactory.getLogger(ParentalControlControllerImpl.class);

    private final ParentalControlService parentalControlService;
    private final ParentalControlUsageService parentalControlUsageService;
    private final ParentalControlSearchEngineConfigService searchEngineConfigService;
    private final DeviceService deviceService;

    @Inject
    public ParentalControlControllerImpl(
        SessionStore sessionStore, PageContextStore pageContextStore,
        ParentalControlService parentalControlService, ParentalControlUsageService parentalControlUsageService,
        ParentalControlSearchEngineConfigService searchEngineConfigService,
        DeviceService deviceService) {
        super(sessionStore, pageContextStore);
        this.parentalControlService = parentalControlService;
        this.parentalControlUsageService = parentalControlUsageService;
        this.searchEngineConfigService = searchEngineConfigService;
        this.deviceService = deviceService;
    }

    // Create profile
    @Override
    public UserProfileModule storeNewProfile(Request request, Response response) {
        log.info("storeNewProfile");
        UserProfileModule profile = request.getBodyAs(UserProfileModule.class);
        return parentalControlService.storeNewProfile(profile);
    }

    // Read profiles
    @Override
    public List<UserProfileModule> getProfiles(Request request, Response response) {
        log.info("getProfiles");
        return parentalControlService.getProfiles();
    }

    // Update profile
    @Override
    public UserProfileModule updateProfile(Request request, Response response) {
        log.info("updateProfile");
        UserProfileModule profile = request.getBodyAs(UserProfileModule.class);
        return parentalControlService.updateProfile(profile);
    }

    // Delete profile
    @Override
    public void deleteProfile(Request request, Response response) {
        log.info("deleteProfile");
        String idString = request.getHeader("id", "No profile module ID provided");

        int profileId = Integer.valueOf(idString);
        parentalControlService.deleteProfile(profileId);
    }

    @Override
    public void deleteAllProfiles(Request request, Response response) {
        log.info("deleteAllProfiles");
        List<Integer> ids = request.getBodyAs(List.class);
        ids.forEach(id -> parentalControlService.deleteProfile(id));
    }

    /**
     * REST method - GET /userprofiles/unique
     */
    @Override
    public void isUnique(Request request, Response response) {
        log.debug("GET /userprofiles/unique");
        String idString = request.getHeader("id");
        Integer id = null;
        if (idString != null && !idString.isEmpty()) {
            try {
                id = Integer.valueOf(idString);
            } catch (NumberFormatException e) {
                throw new BadRequestException("Invalid numerical format");
            }
        }
        String name = request.getHeader("name", "No module name provided");

        if (!parentalControlService.isUniqueCustomerCreatedName(id, name)) {
            throw new ConflictException("Name is not unique");
        }
    }

    @Override
    public Set<Integer> getProfilesBeingUpdated(Request request, Response response) {
        return parentalControlService.getProfilesBeingUpdated();
    }

    @Override
    public boolean startUsage(Request request, Response response) {
        Device device = deviceService.getDeviceById(getSession(request).getDeviceId());
        return parentalControlUsageService.startUsage(device);
    }

    @Override
    public void stopUsage(Request request, Response response) {
        Device device = deviceService.getDeviceById(getSession(request).getDeviceId());
        parentalControlUsageService.stopUsage(device);
    }

    @Override
    public UsageAccount getUsage(Request request, Response response) {
        Device device = deviceService.getDeviceById(getSession(request).getDeviceId());
        return parentalControlUsageService.getUsageAccount(device);
    }

    @Override
    public UsageAccount getUsageByUserId(Request request, Response response) {
        Integer userId = Integer.valueOf(request.getHeader("id"));
        return parentalControlUsageService.getUsageAccount(userId);
    }

    @Override
    public Map<String, SearchEngineConfiguration> getSearchEngineConfiguration(Request request, Response response) {
        return searchEngineConfigService.getConfigByLanguage();
    }

    @Override
    public void setMaxUsage(Request request, Response response) {
        Integer profileId = Integer.valueOf(request.getHeader("id"));
        Boolean value = request.getBodyAs(Boolean.class);
        UserProfileModule upm = parentalControlService.getProfile(profileId);
        upm.setControlmodeMaxUsage(value);
        parentalControlService.updateProfile(upm);
    }

    @Override
    public void setContentFilter(Request request, Response response) {
        Integer profileId = Integer.valueOf(request.getHeader("id"));
        Boolean value = request.getBodyAs(Boolean.class);
        UserProfileModule upm = parentalControlService.getProfile(profileId);
        upm.setControlmodeUrls(value);
        parentalControlService.updateProfile(upm);
    }

    @Override
    public void setInternetAccessStatus(Request request, Response response) {
        Integer profileId = Integer.valueOf(request.getHeader("id"));
        Boolean value = request.getBodyAs(Boolean.class);
        UserProfileModule upm = parentalControlService.getProfile(profileId);
        upm.setInternetBlocked(value);
        parentalControlService.updateProfile(upm);
    }

    @Override
    public boolean getInternetAccessStatus(Request request, Response response) {
        Integer profileId = Integer.valueOf(request.getHeader("id"));
        UserProfileModule upm = parentalControlService.getProfile(profileId);
        return upm.isInternetBlocked() != null ? upm.isInternetBlocked() : false;
    }

    @Override
    public void addOnlineTimeForToday(Request request, Response response) {
        Integer profileId = Integer.valueOf(request.getHeader("id"));
        Integer min = request.getBodyAs(Integer.class);
        UserProfileModule upm = parentalControlUsageService.addBonusTimeForToday(profileId, min);
        parentalControlService.updateProfile(upm);
    }

    @Override
    public void resetBonusTimeForToday(Request request, Response response) {
        Integer profileId = Integer.valueOf(request.getHeader("id"));
        UserProfileModule upm = parentalControlService.getProfile(profileId);
        upm.setBonusTimeUsage(null);
        parentalControlService.updateProfile(upm);
    }

}
