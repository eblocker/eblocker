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
package org.eblocker.server.http.controller;

import org.eblocker.server.common.data.UsageAccount;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.parentalcontrol.SearchEngineConfiguration;
import org.restexpress.Request;
import org.restexpress.Response;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ParentalControlController {
    // Create profile
    UserProfileModule storeNewProfile(Request request, Response response);

    // Read profiles
    List<UserProfileModule> getProfiles(Request request, Response response);

    // Update profile
    UserProfileModule updateProfile(Request request, Response response);

    // Delete profile
    void deleteProfile(Request request, Response response);

    void deleteAllProfiles(Request request, Response response);

    void isUnique(Request request, Response response);

    Set<Integer> getProfilesBeingUpdated(Request request, Response response);

    boolean startUsage(Request request, Response response);

    void stopUsage(Request request, Response response);

    UsageAccount getUsage(Request request, Response response);

    UsageAccount getUsageByUserId(Request request, Response response);

    Map<String, SearchEngineConfiguration> getSearchEngineConfiguration(Request request, Response response);

    void setMaxUsage(Request request, Response response);

    void setContentFilter(Request request, Response response);

    void setInternetAccessStatus(Request request, Response response);

    boolean getInternetAccessStatus(Request request, Response response);

    void addOnlineTimeForToday(Request request, Response response);

    void resetBonusTimeForToday(Request request, Response response);
}
