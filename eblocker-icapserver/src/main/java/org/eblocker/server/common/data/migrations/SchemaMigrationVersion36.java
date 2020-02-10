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

/**
 * "piracy" has been split off from "misc" blacklist so all profiles having "misc" enabled are updated to have the same
 * effective domain filter.
 */
public class SchemaMigrationVersion36 implements SchemaMigration {

    private final DataSource dataSource;

    @Inject
    public SchemaMigrationVersion36(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String getSourceVersion() {
        return "35";
    }

    @Override
    public String getTargetVersion() {
        return "36";
    }

    @Override
    public void migrate() {
        for(UserProfileModule profile : dataSource.getAll(UserProfileModule.class)) {
            if (profile.getInaccessibleSitesPackages().contains(0)) {
                profile.getInaccessibleSitesPackages().add(11);
                dataSource.save(profile, profile.getId());
            }
        }

        dataSource.setVersion("36");
    }
}
