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
import com.google.inject.Singleton;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.User;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.util.UrlUtils;
import org.eblocker.server.http.controller.CustomDomainFilterConfigController;
import org.eblocker.server.http.model.CustomDomainFilterConfig;
import org.eblocker.server.http.security.DashboardAuthorizationProcessor;
import org.eblocker.server.http.service.CustomDomainFilterConfigService;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.utils.ControllerUtils;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.NotFoundException;

import java.util.stream.Collectors;

@Singleton
public class CustomDomainFilterConfigControllerImpl implements CustomDomainFilterConfigController {

    private final CustomDomainFilterConfigService customDomainFilterConfigService;

    @Inject
    public CustomDomainFilterConfigControllerImpl(CustomDomainFilterConfigService customDomainFilterConfigService) {
        this.customDomainFilterConfigService = customDomainFilterConfigService;
    }

    @Override
    public CustomDomainFilterConfig getFilter(Request request, Response response) {
        return customDomainFilterConfigService.getCustomDomainFilterConfig(getUserId(request));
    }

    @Override
    public CustomDomainFilterConfig setFilter(Request request, Response response) {
        CustomDomainFilterConfig customDomainFilterConfig = request.getBodyAs(CustomDomainFilterConfig.class);
        customDomainFilterConfig.setBlacklistedDomains(customDomainFilterConfig.getBlacklistedDomains().stream().map(this::mapToHostname).collect(Collectors.toSet()));
        customDomainFilterConfig.setWhitelistedDomains(customDomainFilterConfig.getWhitelistedDomains().stream().map(this::mapToHostname).collect(Collectors.toSet()));
        return customDomainFilterConfigService.setCustomDomainFilterConfig(getUserId(request), customDomainFilterConfig);
    }

    private String mapToHostname(String hostnameOrUrl) {
        return UrlUtils.isUrl(hostnameOrUrl) ? UrlUtils.getHostname(hostnameOrUrl) : hostnameOrUrl;
    }

    private int getUserId(Request request) {
        String userIdStr = request.getHeader(DashboardAuthorizationProcessor.USER_ID_KEY);
        if (userIdStr == null) {
            throw new NotFoundException("Could not get required parameter 'userId' from request");
        }
        return Integer.parseInt(userIdStr);
    }
}
