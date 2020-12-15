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
import org.eblocker.server.common.data.InternetAccessContingent;
import org.eblocker.server.common.data.UserProfileModule;

import java.util.HashSet;
import java.util.Set;

public class SchemaMigrationVersion6 implements SchemaMigration {

    private final DataSource dataSource;

    @Inject
    public SchemaMigrationVersion6(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String getSourceVersion() {
        return "5";
    }

    @Override
    public String getTargetVersion() {
        return "6";
    }

    public void migrate() {
        modifyMediumProfile();
        dataSource.setVersion("6");
    }

    /**
     * Removes facebook filter list {@link SchemaMigrationVersion3#createFullProfile()}
     */
    private void modifyMediumProfile() {
        UserProfileModule profile = dataSource.get(UserProfileModule.class, DefaultEntities.PARENTAL_CONTROL_MEDIUM_PROFILE_ID);

        Set<InternetAccessContingent> medContingent = new HashSet<>();
        medContingent.add(new InternetAccessContingent(8, 16 * 60, 17 * 60, 60)); // Every Weekday, 16-17
        medContingent.add(new InternetAccessContingent(9, 10 * 60, 11 * 60, 60)); // Every Weekend, 10-11
        medContingent.add(new InternetAccessContingent(9, 16 * 60, 18 * 60, 120)); // Every Weekend, 16-18
        profile.setInternetAccessContingents(medContingent);
        dataSource.save(profile, profile.getId());
    }
}

