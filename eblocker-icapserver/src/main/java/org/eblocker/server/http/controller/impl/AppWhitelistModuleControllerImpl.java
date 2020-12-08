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
import org.eblocker.server.http.controller.AppWhitelistModuleController;
import org.eblocker.server.http.service.AppModuleService;
import org.eblocker.server.http.ssl.AppWhitelistModule;
import org.eblocker.server.http.ssl.AppWhitelistModuleDisplay;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.ConflictException;
import org.restexpress.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class handles all the configurations of which AppWhitelistModules are enabled
 * and usable in the system. It also handles the user generated modules.
 */
public class AppWhitelistModuleControllerImpl implements AppWhitelistModuleController {

    private static final Logger LOG = LoggerFactory.getLogger(AppWhitelistModuleControllerImpl.class);

    private AppModuleService appModuleService;

    @Inject
    public AppWhitelistModuleControllerImpl(
        AppModuleService appModuleService
    ) {
        this.appModuleService = appModuleService;
    }

    /**
     * REST method - GET /appmodules/id/{id}
     * <p>
     * Get one AppWhitelistModule by id
     */
    @Override
    public AppWhitelistModuleDisplay read(Request request, Response response) {
        LOG.debug("GET /appmodules/id/{id}");
        String idString = request.getHeader("id", "No app module ID provided");
        int id;
        try {
            id = Integer.parseInt(idString);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid numerical format");
        }
        AppWhitelistModule module = appModuleService.get(id);
        if (module == null) {
            throw new NotFoundException();
        }
        return new AppWhitelistModuleDisplay(appModuleService.get(id));
    }

    /**
     * REST method - DELETE /appmodules/id/{id}
     * <p>
     * Delete one AppWhitelistModule by id
     */
    @Override
    public void delete(Request request, Response response) {
        LOG.debug("DELETE /appmodules/id/{id}");
        String idString = request.getHeader("id", "No app module ID provided");
        int id;
        try {
            id = Integer.parseInt(idString);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid numerical format");
        }
        appModuleService.delete(id);
    }

    /**
     * REST method - POST /appmodules/id
     * <p>
     * Create one AppWhitelistModule
     */
    @Override
    public AppWhitelistModuleDisplay create(Request request, Response response) {
        LOG.debug("POST /appmodules/id");
        AppWhitelistModuleDisplay tmpl = request.getBodyAs(AppWhitelistModuleDisplay.class);
        AppWhitelistModule module = new AppWhitelistModule(tmpl);
        return new AppWhitelistModuleDisplay(appModuleService.save(module));
    }

    /**
     * REST method - PUT /appmodules/id/{id}
     * <p>
     * Update one AppWhitelistModule by id
     */
    @Override
    public AppWhitelistModuleDisplay update(Request request, Response response) {
        LOG.debug("PUT /appmodules/id/{id}");
        String idString = request.getHeader("id", "No app module ID provided");
        int id;
        try {
            id = Integer.parseInt(idString);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid numerical format");
        }
        AppWhitelistModule module = new AppWhitelistModule(request.getBodyAs(AppWhitelistModuleDisplay.class));
        return new AppWhitelistModuleDisplay(appModuleService.update(module, id));
    }

    /**
     * REST method - GET /appmodules/{id}
     * <p>
     * Get all AppWhitelistModules
     */
    @Override
    public List<AppWhitelistModuleDisplay> getAppWhitelistModules(Request request, Response response) {
        LOG.debug("GET /appmodules/all");
        return appModuleService.getAll().stream().map(module -> new AppWhitelistModuleDisplay(module)).collect(Collectors.toList());
    }

    /**
     * REST method - GET /appmodules/onlyenabled
     * <p>
     * Get a set of only the enabled modules
     */
    @Override
    public List<AppWhitelistModuleDisplay> getOnlyEnabledAppWhitelistModules(
        Request request, Response response) {
        LOG.debug("GET /appmodules/onlyenabled");

        List<AppWhitelistModuleDisplay> res = appModuleService.getAll().stream()
            .filter(AppWhitelistModule::isEnabled)
            .map(module -> new AppWhitelistModuleDisplay(module))
            .collect(Collectors.toList());
        return res;
    }

    /**
     * REST method - PUT /appmodules/enable
     * <p>
     * User enabled an AppWhitelistModule and therefore adds it to the SSLWhite
     */
    @Override
    public void enableAppWhitelistModule(Request request, Response response) {
        LOG.debug("PUT /appmodules/enable");
        Map<String, String> map = request.getBodyAs(Map.class);

        Integer id = Integer.parseInt(map.get("id"));
        boolean enabled = Boolean.parseBoolean(map.get("setEnabled"));

        appModuleService.storeAndActivateEnabledState(id, enabled);
    }

    /**
     * REST method - GET /appmodules/unique
     */
    @Override
    public void isUnique(Request request, Response response) {
        LOG.debug("GET /appmodules/unique");
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

        if (!appModuleService.isUniqueCustomerCreatedName(id, name)) {
            throw new ConflictException("Name is not unique");
        }
    }

}
