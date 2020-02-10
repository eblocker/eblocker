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
import org.eblocker.server.common.data.User;
import com.google.inject.Inject;

public class SchemaMigrationVersion1 implements SchemaMigration {

    private DataSource dataSource;

    @Inject
    public SchemaMigrationVersion1(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String getSourceVersion() {
        return null;
    }

    @Override
    public String getTargetVersion() {
        return "1";
    }

    /**
     * Migrate from an empty database to schema version 1.
     * Adds a default admin user.
     */
    @Override
    public void migrate() {
        User defaultUser = new User();
        defaultUser.setId(User.ID_PREFIX + DefaultEntities.DEFAULT_ADMIN_USER_ID);
        defaultUser.setName(DefaultEntities.DEFAULT_ADMIN_USER_NAME);
        dataSource.addUser(defaultUser);
        dataSource.setVersion("1");
    }
}
