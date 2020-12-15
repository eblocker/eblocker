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
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.FilterMode;
import org.eblocker.server.common.data.UserModuleOld;
import org.eblocker.server.common.data.dashboard.DashboardCard;

import java.util.stream.Collectors;

public class SchemaMigrationVersion27 implements SchemaMigration {

    private final DataSource dataSource;
    private final UserMigrationService userMigrationService;

    @Inject
    public SchemaMigrationVersion27(DataSource dataSource, UserMigrationService userMigrationService) {
        this.dataSource = dataSource;
        this.userMigrationService = userMigrationService;
    }

    @Override
    public String getSourceVersion() {
        return "26";
    }

    @Override
    public String getTargetVersion() {
        return "27";
    }

    @Override
    public void migrate() {
        // set default filter mode for devices
        for (Device device : dataSource.getDevices()) {
            FilterMode filterMode = device.isSslEnabled() ? FilterMode.ADVANCED : FilterMode.PLUG_AND_PLAY;
            device.setFilterMode(filterMode);
            dataSource.save(device);
        }

        // create dashboard card for all users
        for (UserModuleOld user : userMigrationService.getAll()) {
            if (user.getDashboardCards().stream().anyMatch(this::isDnsFilterStatisticCard)) {
                user.setDashboardCards(user.getDashboardCards().stream()
                        .map(card -> {
                            if (!isDnsFilterStatisticCard(card)) {
                                return card;
                            }
                            return new DashboardCard(card.getId(),
                                    card.getRequiredFeature(),
                                    card.getTranslateSuffix(),
                                    "<dashboard-filter-statistics></dashboard-filter-statistics>",
                                    card.isVisible(),
                                    card.isAlwaysVisible(),
                                    card.getDefaultPos(),
                                    card.getCustomPos());
                        })
                        .collect(Collectors.toList()));
                userMigrationService.save(user, user.getId());
            }
        }

        dataSource.setVersion("27");
    }

    private boolean isDnsFilterStatisticCard(DashboardCard card) {
        return "<dashboard-dns-statistics></dashboard-dns-statistics>".equals(card.getHtml());
    }

}
