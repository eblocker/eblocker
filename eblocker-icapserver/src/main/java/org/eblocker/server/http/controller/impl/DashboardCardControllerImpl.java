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

import org.eblocker.server.common.data.*;
import org.eblocker.server.common.data.dashboard.DashboardColumnsView;
import org.eblocker.server.common.data.dashboard.UiCard;
import org.eblocker.server.common.page.PageContextStore;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.http.controller.DashboardCardController;
import org.eblocker.server.http.server.SessionContextController;
import org.eblocker.server.http.service.DashboardCardService;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.UserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class DashboardCardControllerImpl extends SessionContextController implements DashboardCardController {
    private static final Logger log = LoggerFactory.getLogger(DashboardCardControllerImpl.class);

    private final ObjectMapper objectMapper;

    private final DeviceService deviceService;
    private final UserService userService;
    private final DashboardCardService dashboardCardService;

    @Inject
    public DashboardCardControllerImpl(PageContextStore pageContextStore,
                                       SessionStore sessionStore,
                                       ObjectMapper objectMapper,
                                       DeviceService deviceService,
                                       UserService userService,
                                       DashboardCardService dashboardCardService) {
        super(sessionStore, pageContextStore);
        this.objectMapper = objectMapper;
        this.deviceService = deviceService;
        this.userService = userService;
        this.dashboardCardService = dashboardCardService;
    }

    @Override
    public List<UiCard> getDashboardCards(Request request, Response response) {
        UserModule user = getUser(request);
        return dashboardCardService.getDashboardCards(user.getDashboardColumnsView());
    }

    @Override
    public void setDashboardColumnsView(Request request, Response response) {
        try {
            DashboardColumnsView columns = objectMapper.readValue(request.getBodyAsStream(), new TypeReference<DashboardColumnsView>(){});
            UserModule user = getUser(request);
            user.setDashboardColumnsView(columns);
            userService.updateUser(user.getId(), columns);
        } catch (IOException e) {
            String msg = "Cannot read dashboard card columns from request body";
            log.error(msg, e);
            throw new BadRequestException(msg, e);
        }
    }

    @Override
    public DashboardColumnsView getDashboardColumnsView(Request request, Response response) {
        UserModule user = getUser(request);
        return user.getDashboardColumnsView();
    }

    private UserModule getUser(Request request) {
        Session session = getSession(request);
        Device device = deviceService.getDeviceById(session.getDeviceId());
        UserModule user = userService.getUserById(device.getOperatingUser());
        if (user == null) {
            String msg = "User with id " + device.getOperatingUser() + " not found.";
            log.error(msg);
            throw new NotFoundException(msg);
        }
        return user;
    }
}
