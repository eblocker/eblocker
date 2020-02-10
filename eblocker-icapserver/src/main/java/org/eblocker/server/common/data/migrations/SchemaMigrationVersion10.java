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

import org.eblocker.server.common.data.UserModuleOld;
import java.util.Collections;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.UserProfileModule.InternetAccessRestrictionMode;
import com.google.inject.Inject;

public class SchemaMigrationVersion10 implements SchemaMigration {

	private final DataSource dataSource;
	private final UserMigrationService userMigrationService;

    @Inject
    public SchemaMigrationVersion10(DataSource dataSource,
                                    UserMigrationService userMigrationService) {
        this.dataSource = dataSource;
        this.userMigrationService = userMigrationService;
    }

    @Override
    public String getSourceVersion() {
        return "9";
    }

    @Override
    public String getTargetVersion() {
        return "10";
    }

    public void migrate() {
    	createLimboUser();
    	setOperatingUser();
        dataSource.setVersion("10");
    }

    static UserProfileModule createLimboProfile(){
		UserProfileModule limboProfile = new UserProfileModule(
				DefaultEntities.PARENTAL_CONTROL_LIMBO_PROFILE_ID,
				null,
				null,
				DefaultEntities.PARENTAL_CONTROL_LIMBO_PROFILE_NAME_KEY,
				DefaultEntities.PARENTAL_CONTROL_LIMBO_PROFILE_DESCRIPTION_KEY,
				false,
				true,
				Collections.emptySet(),
				Collections.emptySet(),
				InternetAccessRestrictionMode.WHITELIST,
				Collections.emptySet(),
				Collections.emptyMap(),
            null,
            false,
            null
		);
		limboProfile.setBuiltin(true);
		return limboProfile;
    }

    static UserModuleOld createLimboUserModule(){
    	return new UserModuleOld(
    			DefaultEntities.PARENTAL_CONTROL_LIMBO_USER_ID,
				DefaultEntities.PARENTAL_CONTROL_LIMBO_PROFILE_ID,
				null,
				DefaultEntities.PARENTAL_CONTROL_LIMBO_USER_NAME_KEY,
				null,
				null,
				true,

				null,
				Collections.emptyMap(),
            null,
            null,
            null
		);
    }

    private void createLimboUser(){
    	// limbo profile
		UserProfileModule limboProfile = createLimboProfile();
        dataSource.save(limboProfile, limboProfile.getId());
    	// actual user
        UserModuleOld limboUser = createLimboUserModule();
        userMigrationService.save(limboUser, limboUser.getId());
    }

    private void setOperatingUser(){
    	for (Device device : dataSource.getDevices()) {
			device.setOperatingUser(device.getAssignedUser());
			dataSource.save(device);
		}
    }

}
