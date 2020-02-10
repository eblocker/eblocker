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


public class SchemaMigrationVersion23 implements SchemaMigration {

    private final DataSource dataSource;
    private final DashboardService dashboardService;
    private final UserMigrationService userMigrationService;

    @Inject
    public SchemaMigrationVersion23(
        DataSource dataSource,
        DashboardService dashboardService,
        UserMigrationService userMigrationService) {
        this.dataSource = dataSource;
        this.dashboardService = dashboardService;
        this.userMigrationService = userMigrationService;
    }

    @Override
    public String getSourceVersion() {
        return "22";
    }

    @Override
    public String getTargetVersion() {
        return "23";
    }

    @Override
    public void migrate() {

        List<UserModuleOld> users = userMigrationService.getAll();
        int dashboardCardId = dashboardService.generateIconCard().getId();
        for (UserModuleOld user : users) {
            if (!containsCardWithId(user.getDashboardCards(), dashboardCardId)) {
                DashboardCard iconPositionCard = dashboardService.generateIconCard();
                user.addDashboardCard(dashboardService.normalizeCustomPosition(user.getDashboardCards(), iconPositionCard));
                userMigrationService.save(user, user.getId());
            }
        }

        dataSource.setVersion("23");
    }

    private boolean containsCardWithId(List<DashboardCard> cards, int cardId){
        return cards.stream().anyMatch(card -> card.getId()==cardId);
    }

}
