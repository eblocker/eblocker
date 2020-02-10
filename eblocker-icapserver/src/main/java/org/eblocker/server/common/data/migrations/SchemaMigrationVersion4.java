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
import org.eblocker.server.common.data.UserProfileModule;
import com.google.inject.Inject;

public class SchemaMigrationVersion4 implements SchemaMigration {

    private final DataSource dataSource;

    @Inject
    public SchemaMigrationVersion4(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String getSourceVersion() {
        return "3";
    }

    @Override
    public String getTargetVersion() {
        return "4";
    }

    public void migrate() {
        deleteLowProfile();
        modifyFullProfile();
        dataSource.setVersion("4");
    }

    private void deleteLowProfile() {
        dataSource.delete(UserProfileModule.class, DefaultEntities.PARENTAL_CONTROL_LOW_PROFILE_ID);
    }

    /**
     * Removes facebook filter list {@link SchemaMigrationVersion3#createFullProfile()}
     */
    private void modifyFullProfile() {
        UserProfileModule profile = dataSource.get(UserProfileModule.class, DefaultEntities.PARENTAL_CONTROL_FULL_PROFILE_ID);
        profile.getInaccessibleSitesPackages().remove(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_FACEBOOK);
        dataSource.save(profile, profile.getId());
    }
}

