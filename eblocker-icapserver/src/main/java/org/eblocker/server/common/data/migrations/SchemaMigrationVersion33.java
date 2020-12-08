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
import org.eblocker.server.common.data.UserModuleOld;
import org.eblocker.server.common.data.dashboard.DashboardCard;
import org.eblocker.server.common.data.dashboard.DashboardCardPosition;
import org.eblocker.server.http.service.DashboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SchemaMigrationVersion33 implements SchemaMigration {
    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationVersion33.class);

    private final DataSource dataSource;
    private final DashboardService dashboardService;
    private final UserMigrationService userMigrationService;

    @Inject
    public SchemaMigrationVersion33(DataSource dataSource, DashboardService dashboardService,
                                    UserMigrationService userMigrationService) {
        this.dataSource = dataSource;
        this.dashboardService = dashboardService;
        this.userMigrationService = userMigrationService;
    }

    @Override
    public String getSourceVersion() {
        return "32";
    }

    @Override
    public String getTargetVersion() {
        return "33";
    }

    @Override
    public void migrate() {
        List<UserModuleOld> users = userMigrationService.getAll();
        int eblStatisticsCardId = dashboardService.generateDnsStatisticsCard().getId();

        for (UserModuleOld user : users) {
            // Get the DNS Statistics-card
            Optional<DashboardCard> eblStatisticsCard = user.getDashboardCards().stream()
                .filter(c -> c.getId() == eblStatisticsCardId).findFirst();
            if (!eblStatisticsCard.isPresent()) {
                // error - the user should have such a card
                // But the DashbordService will make sure that she gets one!
            } else {
                DashboardCard card = eblStatisticsCard.get();
                if (!card.getRequiredFeature().equalsIgnoreCase(ProductFeature.PRO.name())) {
                    // Must change required feature

                    // New card with required feature updated
                    DashboardCard newCard = new DashboardCard(card.getId(), ProductFeature.PRO.name(),
                        card.getTranslateSuffix(), card.getHtml(), card.isVisible(), card.isAlwaysVisible(),
                        card.getDefaultPos(), card.getCustomPos());

                    // Keep every card but replace the DNS Statistics Card
                    user.setDashboardCards(user.getDashboardCards().stream()
                        .map(c -> c.getId() == newCard.getId() ? newCard : c).collect(Collectors.toList()));
                    userMigrationService.save(user, user.getId());
                }
            }

            fixOrderOfStatisticsCards(user);

        }
        dataSource.setVersion("33");
    }

    private void fixOrderOfStatisticsCards(UserModuleOld user) {
        int eblStatisticsCardId = dashboardService.generateDnsStatisticsCard().getId();
        int eblStatisticsTotalCardId = dashboardService.generateBlockerStatisticsTotalCard().getId();

        Optional<DashboardCard> eblStatisticsCard = user.getDashboardCards().stream()
            .filter(c -> c.getId() == eblStatisticsCardId).findFirst();
        Optional<DashboardCard> eblStatisticsTotalCard = user.getDashboardCards().stream()
            .filter(c -> c.getId() == eblStatisticsTotalCardId).findFirst();

        DashboardCardPosition[] posTotal = dashboardService.generateBlockerStatisticsTotalCard().getDefaultPos();
        DashboardCardPosition[] pos = dashboardService.generateDnsStatisticsCard().getDefaultPos();
        fixOrderOfCard(eblStatisticsCard, user, pos);
        fixOrderOfCard(eblStatisticsTotalCard, user, posTotal);
    }

    private void fixOrderOfCard(Optional<DashboardCard> cardOption, UserModuleOld user, DashboardCardPosition[] position) {
        if (!cardOption.isPresent()) {
            // error - the user should have such a card
            // But the DashbordService will make sure that she gets one!
        } else {
            DashboardCard card = cardOption.get();
            DashboardCard newCard;
            if (card.getCustomPos() == null) {
                // New card with required feature updated
                newCard = new DashboardCard(card.getId(), card.getRequiredFeature(),
                    card.getTranslateSuffix(), card.getHtml(), card.isVisible(), card.isAlwaysVisible(),
                    position, card.getCustomPos());
            } else {
                // New card with required feature updated
                newCard = new DashboardCard(card.getId(), card.getRequiredFeature(),
                    card.getTranslateSuffix(), card.getHtml(), card.isVisible(), card.isAlwaysVisible(),
                    card.getDefaultPos(), position);
            }
            // Keep every card but replace the new Card
            user.setDashboardCards(user.getDashboardCards().stream()
                .map(c -> c.getId() == newCard.getId() ? newCard : c).collect(Collectors.toList()));
            userMigrationService.save(user, user.getId());
        }
    }

}
