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

import org.eblocker.server.common.data.dashboard.DashboardCard;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.UserModuleOld;
import org.eblocker.server.http.service.DashboardService;
import com.google.inject.Inject;

import java.util.List;

public class SchemaMigrationVersion25 implements SchemaMigration {

    private final DataSource dataSource;
    private final DashboardService dashboardService;
    private final UserMigrationService userMigrationService;

    @Inject
    public SchemaMigrationVersion25(DataSource dataSource,
                                    DashboardService dashboardService,
                                    UserMigrationService userMigrationService) {
        this.dataSource = dataSource;
        this.dashboardService = dashboardService;
        this.userMigrationService = userMigrationService;
    }

    @Override
    public String getSourceVersion() {
        return "24";
    }

    @Override
    public String getTargetVersion() {
        return "25";
    }

    @Override
    public void migrate(){
        List<UserModuleOld> users = userMigrationService.getAll();

        DashboardCard dashboardCard = dashboardService.generateDnsStatisticsCard();
        DashboardCard filterCard = dashboardService.generateFilterCard();
        DashboardCard dnsWhitelistCard = dashboardService.generateWhitelistDnsCard();
        DashboardCard eblockerMobileCard = dashboardService.generateEblockerMobileCard();
        for (UserModuleOld user : users) {
            boolean updated = false;
            if (!containsCardWithId(user.getDashboardCards(), dashboardCard.getId())) {
                user.addDashboardCard(dashboardService.normalizeCustomPosition(user.getDashboardCards(), dashboardCard));
                updated = true;
            }

            if (!containsCardWithId(user.getDashboardCards(), filterCard.getId())) {
                user.addDashboardCard(dashboardService.normalizeCustomPosition(user.getDashboardCards(), filterCard));
                updated = true;
            }

            if (!containsCardWithId(user.getDashboardCards(), dnsWhitelistCard.getId())) {
                user.addDashboardCard(dashboardService.normalizeCustomPosition(user.getDashboardCards(), dnsWhitelistCard));
                updated = true;
            }

            if (!containsCardWithId(user.getDashboardCards(), eblockerMobileCard.getId())) {
                user.addDashboardCard(dashboardService.normalizeCustomPosition(user.getDashboardCards(), eblockerMobileCard));
                updated = true;
            }
            if (updated) {
                userMigrationService.save(user, user.getId());
            }
        }

        dataSource.setVersion("25");
    }

    private boolean containsCardWithId(List<DashboardCard> cards, int cardId) {
        return cards.stream().anyMatch(card -> card.getId() == cardId);
    }

}
