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
import org.eblocker.server.common.util.UrlUtils;
import org.eblocker.server.http.controller.CustomDomainFilterConfigController;
import org.eblocker.server.http.model.CustomDomainFilterConfig;
import org.eblocker.server.http.service.CustomDomainFilterConfigService;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.utils.ControllerUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.NotFoundException;

import java.util.stream.Collectors;

@Singleton
public class CustomDomainFilterConfigControllerImpl implements CustomDomainFilterConfigController {

    private final CustomDomainFilterConfigService customDomainFilterConfigService;
    private final DeviceService deviceService;

    @Inject
    public CustomDomainFilterConfigControllerImpl(CustomDomainFilterConfigService customDomainFilterConfigService,
                                                  DeviceService deviceService) {
        this.deviceService = deviceService;
        this.customDomainFilterConfigService = customDomainFilterConfigService;
    }

    @Override
    public CustomDomainFilterConfig getFilter(Request request, Response response) {
        Device device = deviceService.getDeviceByIp(ControllerUtils.getRequestIPAddress(request));
        if (device == null) {
            throw new NotFoundException();
        }
        return customDomainFilterConfigService.getCustomDomainFilterConfig(device.getOperatingUser());
    }

    @Override
    public CustomDomainFilterConfig setFilter(Request request, Response response) {
        Device device = deviceService.getDeviceByIp(ControllerUtils.getRequestIPAddress(request));
        CustomDomainFilterConfig customDomainFilterConfig = request.getBodyAs(CustomDomainFilterConfig.class);
        customDomainFilterConfig.setBlacklistedDomains(customDomainFilterConfig.getBlacklistedDomains().stream().map(this::mapToHostname).collect(Collectors.toSet()));
        customDomainFilterConfig.setWhitelistedDomains(customDomainFilterConfig.getWhitelistedDomains().stream().map(this::mapToHostname).collect(Collectors.toSet()));
        return customDomainFilterConfigService
            .setCustomDomainFilterConfig(device.getOperatingUser(), customDomainFilterConfig);
    }

    private String mapToHostname(String hostnameOrUrl) {
        return UrlUtils.isUrl(hostnameOrUrl) ? UrlUtils.getHostname(hostnameOrUrl) : hostnameOrUrl;
    }
}
