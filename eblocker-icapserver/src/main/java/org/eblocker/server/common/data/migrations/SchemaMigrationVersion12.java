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
import org.eblocker.server.common.data.UserModuleOld;

import java.util.List;

public class SchemaMigrationVersion12 implements SchemaMigration {

    private final DataSource dataSource;
    private final UserMigrationService userMigrationService;

    @Inject
    public SchemaMigrationVersion12(DataSource dataSource, UserMigrationService userMigrationService) {
        this.dataSource = dataSource;
        this.userMigrationService = userMigrationService;
    }

    @Override
    public String getSourceVersion() {
        return "11";
    }

    @Override
    public String getTargetVersion() {
        return "12";
    }

    @Override
    public void migrate() {
        // Decided not to do this in SchemaMigration13, but to change SM12:
        // Migration from v1.0 or earlier will now have users w/o PIN.
        // Migration from v1.3.0 or 1.3.1 (only internal users) will keep the PINs.
        // To reset the PIN in a local upgrade, set DB version to 11.
        removePin();
        dataSource.setVersion("12");
    }

    private void removePin() {
        List<UserModuleOld> users = userMigrationService.getAll();
        for (UserModuleOld user : users) {
            user.setPin(null);
            userMigrationService.save(user, user.getId());
        }
    }

}
