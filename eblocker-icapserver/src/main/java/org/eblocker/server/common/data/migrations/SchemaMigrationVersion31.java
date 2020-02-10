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
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserModuleOld;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;


public class SchemaMigrationVersion31 implements SchemaMigration {

    private final DataSource dataSource;
    private final UserMigrationService userMigrationService;

    @Inject
    public SchemaMigrationVersion31(DataSource dataSource,
                                    UserMigrationService userMigrationService) {
        this.dataSource = dataSource;
        this.userMigrationService = userMigrationService;
    }

    @Override
    public String getSourceVersion() {
        return "30";
    }

    @Override
    public String getTargetVersion() {
        return "31";
    }

    @Override
    public void migrate() {
        int oldStandardUserId = DefaultEntities.PARENTAL_CONTROL_DEFAULT_USER_ID;
        UserModuleOld oldStandardUser = userMigrationService.get(UserModuleOld.class, oldStandardUserId);
        for(Device device : dataSource.getDevices()) {
            /*
             * Create and set new, device specific system user for all devices.
             * Copy all relevant settings from "old" standard user.
             */
            boolean deviceChanged = false;
            if (device.getDefaultSystemUser() == oldStandardUserId || userMigrationService.get(UserModuleOld.class, device.getDefaultSystemUser()) == null) {
                UserModuleOld defaultSystemUser = createDefaultSystemUser(device.getId(), oldStandardUser);
                device.setDefaultSystemUser(defaultSystemUser.getId());
                deviceChanged = true;
            }
            if (device.getAssignedUser() == oldStandardUserId) {
                device.setAssignedUser(device.getDefaultSystemUser());
                deviceChanged = true;
            }
            if (device.getOperatingUser() == oldStandardUserId) {
                device.setOperatingUser(device.getDefaultSystemUser());
                deviceChanged = true;
            }
            if (deviceChanged) {
                dataSource.save(device);
            }
        }
        /*
         * Old standard user should not be referenced anymore. So we can delete it.
         */
        if (oldStandardUser != null) {
            userMigrationService.delete(UserModuleOld.class, oldStandardUser.getId());
        }
        dataSource.setVersion("31");
    }

    private UserModuleOld createDefaultSystemUser(String name, UserModuleOld oldStandardUser) {
        UserModuleOld user = new UserModuleOld(
            dataSource.nextId(UserModule.class),
            DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID,
            name,
            DefaultEntities.USER_SYSTEM_DEFAULT_NAME_KEY,
            null,
            null,
            true,
            null,
            oldStandardUser == null ? new HashMap<>() : oldStandardUser.getWhiteListConfigByDomains(),
            oldStandardUser == null ? new ArrayList<>() : oldStandardUser.getDashboardCards(),
            oldStandardUser == null ? null : oldStandardUser.getCustomBlacklistId(),
            oldStandardUser == null ? null : oldStandardUser.getCustomWhitelistId()
        );
        return userMigrationService.save(user, user.getId());
    }

}
