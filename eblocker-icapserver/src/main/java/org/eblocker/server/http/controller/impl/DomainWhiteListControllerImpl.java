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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.WhiteListConfig;
import org.eblocker.server.common.data.WhiteListConfigDto;
import org.eblocker.server.common.page.PageContext;
import org.eblocker.server.common.page.PageContextStore;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.common.util.UrlUtils;
import org.eblocker.server.http.controller.DomainWhiteListController;
import org.eblocker.server.http.server.SessionContextController;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.UserService;
import org.restexpress.Request;
import org.restexpress.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Puts/removes the domain of the current page on/from the whitelist
 */
public class DomainWhiteListControllerImpl extends SessionContextController implements DomainWhiteListController {
    private static final Logger log = LoggerFactory.getLogger(DomainWhiteListControllerImpl.class);
    private final DeviceService deviceService;
    private final UserService userService;

    private final ObjectMapper objectMapper;

    @Inject
    public DomainWhiteListControllerImpl(DeviceService deviceService, PageContextStore pageContextStore, SessionStore sessionStore, UserService userService, ObjectMapper objectMapper) {
        super(sessionStore, pageContextStore);
        this.deviceService = deviceService;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @Override
    public WhiteListConfigDto update(Request request, Response response) {
        PageContext pageContext = getPageContext(request);
        if (pageContext == null) {
            return null;
        }
        String domain = getDomain(request);
        if (domain == null || domain.isEmpty()) {
            return null;
        }

        WhiteListConfigDto dto = request.getBodyAs(WhiteListConfigDto.class);
        UserModule user = getUser(request);
        Map<String, WhiteListConfig> whiteListConfigMap = user.getWhiteListConfigByDomains();
        if (dto.isAds() || dto.isTrackers()) {
            WhiteListConfig whiteListConfig = new WhiteListConfig(dto.isAds(), dto.isTrackers());
            whiteListConfigMap.put(domain, whiteListConfig);
            pageContext.setWhiteListConfig(whiteListConfig);
        } else {
            whiteListConfigMap.remove(domain);
            pageContext.setWhiteListConfig(WhiteListConfig.noWhiteListing());
        }
        userService.updateUser(user.getId(), whiteListConfigMap);

        return mapStatus(domain, pageContext.getWhiteListConfig());
    }

    @Override
    public WhiteListConfigDto getDomainStatus(Request request, Response response) {
        return mapStatus(getDomain(request), getPageContext(request).getWhiteListConfig());
    }

    @Override
    public Map<String, WhiteListConfig> getWhitelist(Request request, Response response) {
        UserModule user = getUser(request);
        return user.getWhiteListConfigByDomains();
    }

    @Override
    public void setWhitelist(Request request, Response response) {
        UserModule user = getUser(request);
        try {
            List<WhiteListConfigDto> list = objectMapper.readValue(request.getBodyAsStream(), new TypeReference<List<WhiteListConfigDto>>() {
            });
            Map<String, WhiteListConfig> whiteMap = new HashMap<>();

            for (WhiteListConfigDto dto : list) {
                if (dto.isAds() || dto.isTrackers()) {
                    whiteMap.put(dto.getDomain(), new WhiteListConfig(dto.isAds(), dto.isTrackers()));
                }
            }

            userService.updateUser(user.getId(), whiteMap);

        } catch (IOException e) {
            log.error("Error while setting whitelist for user " + user.getId(), e);
        }
    }

    /**
     * We have no pageContext on the dashboard, so we need an additional method.
     *
     * @param request
     * @param response
     */
    @Override
    public void updateWhitelistEntry(Request request, Response response) {
        UserModule user = getUser(request);
        WhiteListConfigDto wcd = request.getBodyAs(WhiteListConfigDto.class);
        if (wcd != null) {
            String domain = getDomain(wcd.getDomain());
            Map<String, WhiteListConfig> currentWhitelist = user.getWhiteListConfigByDomains();
            if (wcd.isAds() || wcd.isTrackers()) {
                WhiteListConfig toBeUpdated = new WhiteListConfig(wcd.isAds(), wcd.isTrackers());
                currentWhitelist.put(domain, toBeUpdated);
            } else {
                currentWhitelist.remove(domain);
                currentWhitelist.remove(wcd.getDomain()); // otherwise entries created with older versions can not be deleted
            }
            userService.updateUser(user.getId(), currentWhitelist);
        }
    }

    private WhiteListConfigDto mapStatus(String domain, WhiteListConfig config) {
        return new WhiteListConfigDto(domain, config.isAds(), config.isTrackers());
    }

    private String getDomain(Request request) {
        PageContext pageContext = getPageContext(request);
        if (pageContext == null) {
            return null;
        }
        return UrlUtils.getDomain(UrlUtils.getHostname(pageContext.getUrl()));
    }

    private String getDomain(String hostnameOrUrl) {
        String hostname = UrlUtils.isUrl(hostnameOrUrl) ? UrlUtils.getHostname(hostnameOrUrl) : hostnameOrUrl;
        return UrlUtils.getDomain(hostname);
    }

    private UserModule getUser(Request request) {
        Session session = getSession(request);
        Device device = deviceService.getDeviceById(session.getDeviceId());
        UserModule user = userService.getUserById(device.getOperatingUser());
        if (user == null) {
            user = userService.getUserById(device.getAssignedUser());
        }
        return user;
    }

}
