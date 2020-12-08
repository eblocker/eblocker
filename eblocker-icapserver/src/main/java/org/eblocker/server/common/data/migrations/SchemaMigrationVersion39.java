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
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserModuleOld;
import org.eblocker.server.common.data.UserRole;
import org.eblocker.server.common.data.dashboard.DashboardCard;
import org.eblocker.server.common.data.dashboard.DashboardCardPosition;
import org.eblocker.server.common.data.dashboard.DashboardColumnsView;
import org.eblocker.server.common.data.dashboard.UiCard;
import org.eblocker.server.common.data.dashboard.UiCardColumnPosition;
import org.eblocker.server.http.service.DashboardCardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SchemaMigrationVersion39 implements SchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationVersion39.class);

    private final DataSource dataSource;
    private final DashboardCardService dashboardCardService;
    private final UserMigrationService userMigrationService;

    @Inject
    public SchemaMigrationVersion39(DataSource dataSource,
                                    DashboardCardService dashboardCardService,
                                    UserMigrationService userMigrationService) {
        this.dataSource = dataSource;
        this.dashboardCardService = dashboardCardService;
        this.userMigrationService = userMigrationService;
    }

    @Override
    public String getSourceVersion() {
        return "38";
    }

    @Override
    public String getTargetVersion() {
        return "39";
    }

    @Override
    public void migrate() {
        createAndSaveDashboardCardsToDataStore();

        updateOldUsersIntoNewStructure();

        dataSource.setVersion("39");
    }

    private Map<Integer, UiCardColumnPosition[]> getIdPositionMapping(List<DashboardCard> oldUserCards, List<UiCard> newUserCards) {
        Map<Integer, UiCardColumnPosition[]> mapping = new HashMap<>();

        newUserCards.forEach((card) -> {
            DashboardCard oldCard = getCardByName(oldUserCards, card.getName());
            if (oldCard != null) {
                DashboardCardPosition positionsOne = oldCard.getCustomPos() != null && oldCard.getCustomPos().length > 0 && oldCard.getCustomPos()[0] != null ?
                    oldCard.getCustomPos()[0] : oldCard.getDefaultPos()[0];
                DashboardCardPosition positionsTwo = oldCard.getCustomPos() != null && oldCard.getCustomPos().length > 1 && oldCard.getCustomPos()[1] != null ?
                    oldCard.getCustomPos()[1] : oldCard.getDefaultPos()[1];
                DashboardCardPosition positionsThree = oldCard.getCustomPos() != null && oldCard.getCustomPos().length > 2 && oldCard.getCustomPos()[2] != null ?
                    oldCard.getCustomPos()[2] : oldCard.getDefaultPos()[2];

                UiCardColumnPosition[] positions = new UiCardColumnPosition[3];
                positions[0] = new UiCardColumnPosition(card.getId(), positionsOne.getColumn(), positionsOne.getOrder(), oldCard.isVisible(), false);
                positions[1] = new UiCardColumnPosition(card.getId(), positionsTwo.getColumn(), positionsTwo.getOrder(), oldCard.isVisible(), true);
                positions[2] = new UiCardColumnPosition(card.getId(), positionsThree.getColumn(), positionsThree.getOrder(), oldCard.isVisible(), true);
                mapping.put(card.getId(), positions);
            }
        });
        return mapping;
    }

    private void updateOldUsersIntoNewStructure() {
        List<UserModuleOld> users = userMigrationService.getAll();

        users.forEach(user -> {
            // ** recycle ID, so that we do not have to updated assignedUser of devices and to keep IDs of system users.
            int id = user.getId();

            List<UiCard> newCards = dataSource.getAll(UiCard.class);

            Map<Integer, UiCardColumnPosition[]> idPositionMapping = getIdPositionMapping(user.getDashboardCards(), newCards);

            DashboardColumnsView dashboardColumnsView = new DashboardColumnsView(idPositionMapping);
            UserModule newUser = new UserModule(
                id,
                user.getAssociatedProfileId(),
                user.getName(),
                user.getNameKey(),
                user.getBirthday(),
                user.getUserRole(),
                user.isSystem(),
                user.getPin(),
                user.getWhiteListConfigByDomains(),
                dashboardColumnsView,
                user.getCustomBlacklistId(),
                user.getCustomWhitelistId()
            );

            // delete old user, so that we can save new user with same ID
            dataSource.delete(UserModule.class, id);
            dataSource.save(newUser, id);
        });
    }

    private DashboardCard getCardByName(List<DashboardCard> list, String name) {
        // Avoid NPE for already migrated databases
        if (list == null || list.isEmpty() || name == null) {
            return null;
        }
        // add _CARD to name, so that old names matches new name (removed _CARD suffix from name)
        // rename "ANON" card to "ANONYMIZATION", so add special case for that card
        return list.stream().filter(c -> c.getTranslateSuffix().equals(name + "_CARD") || (c.getTranslateSuffix().equals("ANON_CARD") && name.equals("ANONYMIZATION"))).findFirst().orElse(null);
    }

    private void createAndSaveDashboardCardsToDataStore() {
        List<UiCard> cards = dataSource.getAll(UiCard.class);

        generateDashboardCards().stream().
            filter(card -> !containsCard(cards, card)).
            forEach(card -> dashboardCardService.saveNewDashboardCard(card));
    }

    private boolean containsCard(List<UiCard> source, UiCard contains) {
        return source.stream().anyMatch(card -> contains.getName().equals(card.getName()));
    }

    private List<UiCard> generateDashboardCards() {
        return Arrays.asList(
            new UiCard(nextId(), "MESSAGE", ProductFeature.WOL.name(), getDefaultRoles(), null),
            new UiCard(nextId(), "CONSOLE", ProductFeature.WOL.name(), getDefaultRoles(), null),
            new UiCard(nextId(), "ICON", ProductFeature.WOL.name(), getDefaultRoles(), null),
            new UiCard(nextId(), "ANONYMIZATION", ProductFeature.BAS.name(), getDefaultRoles(), null),
            new UiCard(nextId(), "PAUSE", ProductFeature.BAS.name(), getDefaultRoles(), null),
            new UiCard(nextId(), "MOBILE", ProductFeature.BAS.name(), getDefaultRoles(), null),
            new UiCard(nextId(), "SSL", ProductFeature.PRO.name(), getDefaultRoles(), null),
            new UiCard(nextId(), "WHITELIST", ProductFeature.PRO.name(), getDefaultRoles(), null),
            new UiCard(nextId(), "DNS_STATISTICS", ProductFeature.PRO.name(), getDefaultRoles(), null),
            new UiCard(nextId(), "FILTER", ProductFeature.PRO.name(), getDefaultRoles(), null), // This card is currently not visible in the UI. Only the admin is allowed to make the change settings that can be changed with this card.
            new UiCard(nextId(), "WHITELIST_DNS", ProductFeature.PRO.name(), getDefaultRoles(), null),
            new UiCard(nextId(), "BLOCKER_STATISTICS_TOTAL", ProductFeature.PRO.name(), getDefaultRoles(), null),
            new UiCard(nextId(), "ONLINE_TIME", ProductFeature.FAM.name(), getAllRoles(), null),
            new UiCard(nextId(), "USER", ProductFeature.FAM.name(), getAllRoles(), null)
        );
    }

    private int nextId() {
        return dataSource.nextId(UiCard.class);
    }

    private List<UserRole> getAllRoles() {
        return Arrays.asList(UserRole.PARENT, UserRole.OTHER, UserRole.CHILD);
    }

    private List<UserRole> getDefaultRoles() {
        return Arrays.asList(UserRole.PARENT, UserRole.OTHER);
    }

}
