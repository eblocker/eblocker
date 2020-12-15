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
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserModuleTransport;
import org.eblocker.server.http.controller.UserController;
import org.eblocker.server.http.controller.converter.UserModuleConverter;
import org.eblocker.server.http.service.UserService;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UserControllerImpl implements UserController {
    private static final Logger log = LoggerFactory.getLogger(UserControllerImpl.class);
    private final UserService userService;

    @Inject
    public UserControllerImpl(UserService userService) {
        this.userService = userService;
    }

    // Create
    @Override
    public UserModuleTransport createUser(Request request, Response response) {
        log.info("createUser");
        UserModuleTransport user = request.getBodyAs(UserModuleTransport.class);
        UserModule userModule = userService.createUser(
                user.getAssociatedProfileId(),
                user.getName(),
                user.getNameKey(),
                user.getBirthday(),
                user.getUserRole(),
                user.containsPin() ? user.getNewPin() : null);
        return UserModuleConverter.getUserModuleTransport(userModule);
    }

    // Read
    @Override
    public Collection<UserModuleTransport> getUsers(Request request, Response response) {
        log.info("getUsers");
        Collection<UserModule> users = userService.getUsers(false);
        return users.stream().map(UserModuleConverter::getUserModuleTransport).collect(Collectors.toList());
    }

    @Override
    public UserModuleTransport getUserById(Request request, Response response) {
        log.info("getUsers");
        String userId = request.getHeader("userId", "No user ID provided");
        int prim = Integer.parseInt(userId);
        return UserModuleConverter.getUserModuleTransport(userService.getUserById(prim));
    }

    // Update
    @Override
    public UserModuleTransport updateUser(Request request, Response response) {
        log.info("updateUser");
        UserModuleTransport userTransport = request.getBodyAs(UserModuleTransport.class);
        UserModule userModule = userService.updateUser(
                userTransport.getId(),
                userTransport.getAssociatedProfileId(),
                userTransport.getName(),
                userTransport.getNameKey(),
                userTransport.getBirthday(),
                userTransport.getUserRole(),
                userTransport.getNewPin()
        );
        return UserModuleConverter.getUserModuleTransport(userModule);
    }

    @Override
    public UserModuleTransport updateUserDashboardView(Request request, Response response) {
        log.info("updateUserDashboardView");
        Integer userId = Integer.valueOf(request.getHeader("Id"));
        UserModule userModule = userService.updateUserDashboardView(userId);
        return UserModuleConverter.getUserModuleTransport(userModule);
    }

    @Override
    public void updateDashboardViewOfAllDefaultSystemUsers(Request request, Response response) {
        log.info("updateUserDashboardView");
        userService.updateAllDefaultDashboardView();
    }

    // Delete
    @Override
    public void deleteUser(Request request, Response response) {
        log.info("deleteUser");
        Integer userId = Integer.valueOf(request.getHeader("Id"));
        userService.deleteUser(userId);
    }

    @Override
    public Map<Integer, Boolean> deleteAllUsers(Request request, Response response) {
        List<Integer> ids = request.getBodyAs(List.class);
        Map<Integer, Boolean> ret = new HashMap<>();
        ids.forEach(id -> {
            if (!userService.deleteUser(id)) {
                ret.put(id, false);
            }
        });
        return ret;
    }

    @Override
    public void isUnique(Request request, Response response) {
        log.debug("GET /user/unique");
        String idString = request.getHeader("id");
        Integer id = null;
        if (idString != null && !idString.isEmpty()) {
            try {
                id = Integer.valueOf(idString);
            } catch (NumberFormatException e) {
                throw new BadRequestException("Invalid numerical format");
            }
        }
        String name = request.getHeader("name", "No user name provided");

        if (!userService.isUniqueCustomerCreatedName(id, name)) {
            throw new ConflictException("Name is not unique");
        }
    }

    @Override
    public void setPin(Request request, Response response) {
        log.info("setPin");
        String newPin = request.getBodyAs(UserModuleTransport.class).getNewPin();
        Integer userId = Integer.valueOf(request.getHeader("Id"));
        userService.setPin(userId, newPin);
    }

    @Override
    public void changePin(Request request, Response response) {
        log.info("changePin");
        UserModuleTransport transmittedUserModule = request.getBodyAs(UserModuleTransport.class);
        String newPin = transmittedUserModule.getNewPin();
        String oldPin = transmittedUserModule.getOldPin();
        Integer userId = Integer.valueOf(request.getHeader("Id"));
        userService.changePin(userId, newPin, oldPin);
    }

    @Override
    public void resetPin(Request request, Response response) {
        log.info("resetPin");
        Integer userId = Integer.valueOf(request.getHeader("Id"));
        userService.setPin(userId, null);
    }

}
