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
import org.eblocker.registration.UpsellInfoWrapper;
import org.eblocker.server.http.controller.ProductMigrationController;
import org.eblocker.server.http.service.ProductMigrationService;
import org.restexpress.Request;
import org.restexpress.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProductMigrationControllerImpl implements ProductMigrationController {

    private static final Logger LOG = LoggerFactory.getLogger(ProductMigrationControllerImpl.class);

    private final ProductMigrationService productMigrationService;

    @Inject
    public ProductMigrationControllerImpl(
            ProductMigrationService productMigrationService) {
        this.productMigrationService = productMigrationService;
    }

    @Override
    public UpsellInfoWrapper getUpsellInfo(Request request, Response response) {
        String feature = request.getHeader("feature");
        UpsellInfoWrapper wrapper = productMigrationService.getUpsellInfo(feature);
        return wrapper;
    }
}


