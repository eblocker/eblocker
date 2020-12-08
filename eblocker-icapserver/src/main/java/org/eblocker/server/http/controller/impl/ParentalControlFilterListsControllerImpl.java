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
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterSummaryData;
import org.eblocker.server.http.controller.ParentalControlFilterListsController;
import org.eblocker.server.http.service.ParentalControlFilterListsService;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

public class ParentalControlFilterListsControllerImpl implements ParentalControlFilterListsController {
    private static final Logger log = LoggerFactory.getLogger(ParentalControlFilterListsControllerImpl.class);

    private final ParentalControlFilterListsService filterListsService;

    @Inject
    public ParentalControlFilterListsControllerImpl(ParentalControlFilterListsService filterListsService) {
        this.filterListsService = filterListsService;
    }

    @Override
    public Set<ParentalControlFilterSummaryData> getFilterLists(Request request, Response response) {
        log.info("getFilterLists");
        return filterListsService.getParentalControlFilterLists();
    }

    @Override
    public List<ParentalControlFilterMetaData> getFilterMetaData(Request request, Response response) {
        log.info("getFilterLists");
        return filterListsService.getParentalControlFilterMetaData();
    }

    @Override
    public List<String> getFilterListDomains(Request request, Response response) {
        log.info("getFilterListDomains");
        String idString = request.getHeader("id", "No filter list ID provided");

        int filterListId = Integer.valueOf(idString);
        return filterListsService.getFilterListDomains(filterListId);
    }

    @Override
    public ParentalControlFilterSummaryData updateFilterList(Request request, Response response) {
        log.info("updateFilterList");
        String filterType = request.getHeader("filterType");
        return filterListsService.updateFilterList(request
            .getBodyAs(ParentalControlFilterSummaryData.class), filterType);
    }

    @Override
    public synchronized void deleteFilterList(Request request, Response response) {
        log.info("deleteFilterList");
        String idString = request.getHeader("id", "No filter list ID provided");

        int filterListId = Integer.valueOf(idString);
        filterListsService.deleteFilterList(filterListId);
    }

    @Override
    public synchronized ParentalControlFilterSummaryData createFilterList(Request request, Response response) {
        log.info("createFilterList");
        String filterType = request.getHeader("filterType");
        return filterListsService.createFilterList(request
            .getBodyAs(ParentalControlFilterSummaryData.class), filterType);
    }

    /**
     * REST method - GET /filterlists/unique
     */
    @Override
    public void isUnique(Request request, Response response) {
        log.debug("GET /filterlists/unique");
        String idString = request.getHeader("id");
        String filterType = request.getHeader("filterType");
        Integer id = null;
        if (idString != null && !idString.isEmpty()) {
            try {
                id = Integer.valueOf(idString);
            } catch (NumberFormatException e) {
                throw new BadRequestException("Invalid numerical format");
            }
        }
        String name = request.getHeader("name", "No filter list name provided");
        if (!filterListsService.isUniqueCustomerCreatedName(id, name, filterType)) {
            throw new ConflictException("Name is not unique");
        }

    }
}
