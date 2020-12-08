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
import org.eblocker.registration.CustomerInfo;
import org.eblocker.server.common.page.PageContextStore;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.http.controller.CustomerInfoController;
import org.eblocker.server.http.server.SessionContextController;
import org.eblocker.server.http.service.CustomerInfoService;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class CustomerInfoControllerImpl extends SessionContextController implements CustomerInfoController {
    private static final Logger log = LoggerFactory.getLogger(CustomerInfoControllerImpl.class);

    private final ObjectMapper objectMapper;
    private final CustomerInfoService customerInfoService;

    @Inject
    public CustomerInfoControllerImpl(PageContextStore pageContextStore,
                                      SessionStore sessionStore,
                                      ObjectMapper objectMapper,
                                      CustomerInfoService customerInfoService) {
        super(sessionStore, pageContextStore);
        this.objectMapper = objectMapper;
        this.customerInfoService = customerInfoService;
    }

    @Override
    public CustomerInfo get(Request request, Response response) {
        return customerInfoService.get();
    }

    @Override
    public void save(Request request, Response response) {
        try {
            CustomerInfo customInfo = objectMapper.readValue(request.getBodyAsStream(), new TypeReference<CustomerInfo>() {
            });
            customerInfoService.save(customInfo);
        } catch (IOException e) {
            log.debug("Cannot get customer info from request body.", e);
            throw new BadRequestException("Cannot get customer info from request body." + e);
        }
    }

    @Override
    public void delete(Request request, Response response) {
        customerInfoService.delete();
    }
}
