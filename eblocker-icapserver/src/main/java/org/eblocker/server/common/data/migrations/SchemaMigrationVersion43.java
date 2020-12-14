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
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.UserRole;
import org.eblocker.server.common.data.dashboard.AccessRight;
import org.eblocker.server.common.data.dashboard.UiCard;
import org.eblocker.server.http.service.DashboardCardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

public class SchemaMigrationVersion43 implements SchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationVersion43.class);

    private final DataSource dataSource;
    private final DashboardCardService dashboardCardService;

    @Inject
    public SchemaMigrationVersion43(DataSource dataSource,
                                    DashboardCardService dashboardCardService) {
        this.dataSource = dataSource;
        this.dashboardCardService = dashboardCardService;
    }

    @Override
    public String getSourceVersion() {
        return "42";
    }

    @Override
    public String getTargetVersion() {
        return "43";
    }

    @Override
    public void migrate() {
        List<UserModule> users = dataSource.getAll(UserModule.class);

        createDefaultParentIfNoParentExists(users);
        makeDefaultParentEditable(users);

        addAccessRightsToAllCards();

        dataSource.setVersion("43");
    }

    private void createDefaultParentIfNoParentExists(List<UserModule> users) {
        if (users.stream().noneMatch(user -> user != null && user.getUserRole() != null && user.getUserRole().equals(UserRole.PARENT))) {
            int userId = dataSource.nextId(UserModule.class);
            int profileId = dataSource.nextId(UserProfileModule.class);

            UserProfileModule upm = new UserProfileModule(
                    profileId,
                    "PROFILE_FOR_USER_" + userId,
                    "",
                    "",
                    "",
                    false,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    true,
                    false,
                    null
            );
            dataSource.save(upm, upm.getId());

            UserModule defaultParent = new UserModule(
                    userId,
                    profileId,
                    "Parent/Eltern",
                    "SHARED.USER.NAME.DEFAULT_PARENT",
                    null,
                    UserRole.PARENT,
                    false,
                    null,
                    null,
                    dashboardCardService.getNewDashboardCardColumns(UserRole.PARENT),
                    null,
                    null
            );
            dataSource.save(defaultParent, defaultParent.getId());
        }
    }

    private void makeDefaultParentEditable(List<UserModule> users) {
        users.stream().filter(user -> user.getName() != null && user.getName().equals("SHARED.USER.NAME.DEFAULT_PARENT")).forEach(user -> {
            user.setNameKey(user.getName());
            user.setName("Parent/Eltern");
            dataSource.save(user, user.getId());
        });
    }

    private void addAccessRightsToAllCards() {
        List<UiCard> cards = dataSource.getAll(UiCard.class);
        cards.forEach(card -> {
            List<AccessRight> rights = Collections.singletonList(AccessRight.valueOf(card.getName()));

            UiCard newCard = new UiCard(card.getId(), card.getName(), card.getRequiredFeature(), card.getRequiredUserRoles(), rights);

            dataSource.delete(UiCard.class, card.getId());
            dataSource.save(card, card.getId());
            dashboardCardService.saveNewDashboardCard(newCard);
        });
    }

}
