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

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserModuleOld;
import org.eblocker.server.http.service.DashboardService;
import com.google.inject.Inject;
import com.google.inject.name.Named;

public class SchemaMigrationVersion38 implements SchemaMigration {
    private final DataSource dataSource;
    private final DashboardService dashboardService;
    private final UserMigrationService userMigrationService;
    private final String standardUserTranslation;

    @Inject
    public SchemaMigrationVersion38(DataSource dataSource,
                                    DashboardService dashboardService,
                                    UserMigrationService userMigrationService,
                                    @Named("parentalControl.standardUser.translation") String standardUserTranslation) {
        this.dataSource = dataSource;
        this.dashboardService = dashboardService;
        this.standardUserTranslation = standardUserTranslation;
        this.userMigrationService = userMigrationService;
    }

    @Override
    public String getSourceVersion() {
        return "37";
    }

    @Override
    public String getTargetVersion() {
        return "38";
    }

    @Override
    public void migrate() {
        createAndSaveStandardUser();
        dataSource.setVersion("38");
    }

    /**
     *  Create standard system user if it does not exist. Standard user is a proxy user for all
     *  unassigned devices. These devices have own user. But the profile of that user cannot be changed
     *  in the new UI. So we use a standard user to allow the customer to update the standard profile,
     *  which is used by all users of unassigned devices.
     */
    private void createAndSaveStandardUser() {
        if (userMigrationService.getAll().stream().noneMatch(this::isStandardUser)) {
            UserModuleOld standardUser = createStandardUser(DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID, standardUserTranslation, standardUserTranslation);
            userMigrationService.save(standardUser, standardUser.getId());
        }
    }

    private boolean isStandardUser(UserModuleOld user) {
        return user.getNameKey() != null && user.getNameKey().equals(standardUserTranslation);
    }

    private UserModuleOld createStandardUser(Integer associatedProfileId, String name, String nameKey) {
        Integer newId = dataSource.nextId(UserModule.class);
        UserModuleOld newUser = new UserModuleOld(
            newId,
            associatedProfileId,
            name,
            nameKey,
            null,
            null,
            true,
            null,
            null,
            dashboardService.generateDashboardCards(),
            null,
            null);
        return newUser;
    }
}
