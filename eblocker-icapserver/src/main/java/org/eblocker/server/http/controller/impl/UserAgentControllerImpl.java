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
import org.eblocker.server.common.data.UserAgent;
import org.eblocker.server.common.data.UserAgentWrapper;
import org.eblocker.server.common.page.PageContextStore;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.http.controller.UserAgentController;
import org.eblocker.server.http.server.SessionContextController;
import org.eblocker.server.http.service.UserAgentService;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.restexpress.Request;
import org.restexpress.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides access to user agent lists, current and outgoing user agent. The predefined user agents are located under the path which is set in configuration.properties (userAgents.predefinedProfiles.file);
 * The structure is: ProfileName#UserAgentString ('#' is used as separator because it should not appear in normal user agent strings)
 */
public class UserAgentControllerImpl extends SessionContextController implements UserAgentController {

    private static final Logger logger = LoggerFactory.getLogger(UserAgentControllerImpl.class);

    private static final String STANDARD_UA = "Off";
    private static final String CUSTOM_UA = "Custom";

    private final UserAgentService userAgentService;

    private final Map<String, UserAgent> userAgents = new HashMap<>();

    @Inject
    public UserAgentControllerImpl(SessionStore sessionStore, PageContextStore pageContextStore, @Named("userAgents.predefinedProfiles.file") String filePath, UserAgentService userAgentService) {
        super(sessionStore, pageContextStore);
        this.userAgentService = userAgentService;
        loadUserAgentsFromFile(filePath);
    }

    private void loadUserAgentsFromFile(String filePath) {
        try (InputStream fileIn = ResourceHandler.getInputStream(new SimpleResource(filePath))) {
            if (fileIn != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(fileIn));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("#");
                    String name = parts[0];
                    String userAgentString = parts[1];
                    userAgents.put(name, new UserAgent(userAgentString));
                }
            }
        } catch (IOException e) {
            logger.warn("failed to load user agents from file", e);
        }
    }

    @Override
    public Object getAgentList(Request request, Response response) throws IOException {
        List<String> userAgentIds = new ArrayList<>();
        userAgentIds.add(STANDARD_UA);
        for (String key : userAgents.keySet()) { // sorting is done in the frontend
            userAgentIds.add(key);
        }
        return userAgentIds;
    }

    private Integer getUserIdFromString(String userId) {
        try {
            return Integer.valueOf(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public Object setCloakedUserAgentByDeviceId(Request request, Response response) {
        @SuppressWarnings("unchecked")
        Map<String, Object> config = request.getBodyAs(Map.class);
        Session session = getSession(request);

        Integer tmpUserId = (Integer) config.get("userId");
        // userId defaults to the user that makes this call.
        Integer userId = tmpUserId != null ? tmpUserId : session.getUserId();

        String tmpDevice = (String) config.get("deviceId");
        // deviceId defaults to the device that makes this call.
        String deviceId = tmpDevice != null ? tmpDevice : session.getDeviceId();

        String cloakedUserAgentName = (String) config.get("userAgentName"); // userAgentName holds the user agent's name, e.g. "PC (Linux)" or "Off"
        String cloakedUserAgentValue = (String) config.get("userAgentValue");//request.getHeader("cloakedUserAgentValue");
        Boolean isCustom = false;

        if (cloakedUserAgentName.equals(STANDARD_UA)) {
            // Off
            cloakedUserAgentValue = null;
        } else if (cloakedUserAgentName.equals(CUSTOM_UA)) {
            // Name is 'Custom' with some custom value <cloakedUserAgentValue>
            isCustom = true;
        } else {
            // Some name (e.g. 'PC (Linux)' or 'Mac') without value, so we set the value here
            cloakedUserAgentValue = userAgents.get(cloakedUserAgentName).getAgentSpec();
        }
        userAgentService.setCloakedUserAgent(userId, deviceId, cloakedUserAgentValue, isCustom);
        session.setOutgoingUserAgent(cloakedUserAgentValue);
        return config;
    }

    @Override
    public Object getCloakedUserAgentByDeviceId(Request request, Response response) {
        // E.g.:
        // Name: 'PC (Linux)'
        // Value: 'Mozilla5.0 (X11, Linux)'
        // isCustom: false
        Session session = getSession(request);
        String deviceId = request.getHeader("deviceId") != null ? request.getHeader("deviceId") : session.getDeviceId();

        Integer tmpUserId = getUserIdFromString(request.getHeader("userId"));
        Integer userId = tmpUserId != null ? tmpUserId : session.getUserId();

        String cloakedUserAgent = userAgentService.getCloakedUserAgent(userId, deviceId);
        Boolean isCustom = userAgentService.isCustom(userId, deviceId);
        String userAgentName = getUserAgentName(cloakedUserAgent, isCustom);

        if (cloakedUserAgent == null) {
            // set some default value; otherwise the custom user agent field is empty if selected
            cloakedUserAgent = session.getUserAgent();
        }

        return new UserAgentWrapper(cloakedUserAgent, userAgentName, isCustom);
    }

    private String getUserAgentName(String currOutUA, Boolean isCustom) {
        if (currOutUA == null) {
            return STANDARD_UA;
        } else if (!isCustom) {
            for (String key : userAgents.keySet()) {
                String userAgentString = userAgents.get(key).getAgentSpec();
                if (userAgentString.equals(currOutUA)) {
                    return key;
                }
            }
        }
        return CUSTOM_UA;
    }

}
