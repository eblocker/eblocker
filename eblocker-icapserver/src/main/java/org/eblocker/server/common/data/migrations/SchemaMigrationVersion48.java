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
package org.eblocker.server.common.data.migrations;

import com.google.inject.Inject;
import org.eblocker.registration.ProductFeature;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.UserRole;
import org.eblocker.server.common.data.dashboard.UiCard;
import org.eblocker.server.http.service.DashboardCardService;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class SchemaMigrationVersion48 implements SchemaMigration {
    private final DataSource dataSource;
    private final DashboardCardService dashboardCardService;

    @Inject
    public SchemaMigrationVersion48(DataSource dataSource,
                                    DashboardCardService dashboardCardService) {
        this.dataSource = dataSource;
        this.dashboardCardService = dashboardCardService;
    }

    @Override
    public String getSourceVersion() {
        return "47";
    }

    @Override
    public String getTargetVersion() {
        return "48";
    }

    @Override
    public void migrate() {
        createAndSaveNewCards();
        removeObsoleteCards();
        dataSource.setVersion(getTargetVersion());
    }

    private void createAndSaveNewCards() {
        Stream.of("DEVICE_FIREWALL", "DEVICE_STATUS", "DOMAIN_BLOCKLIST", "DOMAIN_PASSLIST")
                .forEach(name -> {
                    UiCard domainBlocklist = new UiCard(nextId(), name, ProductFeature.PRO.name(), getDefaultRoles(), null);
                    dashboardCardService.saveDashboardCardIfNotExists(domainBlocklist);
                });
    }

    private void removeObsoleteCards() {
        dashboardCardService.removeCardByName("WHITELIST_DNS");
    }

    private int nextId() {
        return dataSource.nextId(UiCard.class);
    }

    private List<UserRole> getDefaultRoles() {
        return Arrays.asList(UserRole.PARENT, UserRole.OTHER);
    }

}
