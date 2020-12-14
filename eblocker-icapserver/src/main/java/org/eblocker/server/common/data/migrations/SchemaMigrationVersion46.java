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
import org.eblocker.server.common.data.UserProfileModule;

import java.util.Collections;

/**
 * Converts custom domain filters to domain filters usable with new blocker api.
 */
public class SchemaMigrationVersion46 implements SchemaMigration {

    private final DataSource dataSource;

    @Inject
    public SchemaMigrationVersion46(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String getSourceVersion() {
        return "45";
    }

    @Override
    public String getTargetVersion() {
        return "46";
    }

    @Override
    public void migrate() {
        resetParentalControlsStandardProfile();
        resetSplashScreen();
        dataSource.setVersion("46");
    }

    private void resetSplashScreen() {
        dataSource.setShowSplashScreen(true);
    }

    /**
     * In eOS 2.2 or before, users had been able to set Parental Controls restrictions
     * on the so called "Standard Profile" #1.
     * <p>
     * This profile is now assigned to all default standard users of all devices.
     * And if there are any restrictions, there will be no dashboard cards for these
     * devices.
     * <p>
     * Unfortunately, the profile itself cannot be edited anymore. So there is no easy
     * way out. Therefore, we reset the "Standard Profile" to its default settings.
     * Just in case.
     */
    private void resetParentalControlsStandardProfile() {
        UserProfileModule standardProfile = new UserProfileModule(
                DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID,
                null,
                null,
                "PARENTAL_CONTROL_DEFAULT_PROFILE_NAME",
                "PARENTAL_CONTROL_DEFAULT_PROFILE_DESCRIPTION",
                true,
                false,
                Collections.emptySet(),
                Collections.emptySet(),
                UserProfileModule.InternetAccessRestrictionMode.BLACKLIST,
                Collections.emptySet(),
                Collections.emptyMap(),
                null,
                false,
                null
        );
        standardProfile.setBuiltin(true);
        standardProfile.setControlmodeTime(false);
        standardProfile.setControlmodeUrls(false);
        standardProfile.setControlmodeMaxUsage(false);
        dataSource.save(standardProfile, DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID);
    }

}
