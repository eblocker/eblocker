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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class SchemaMigrationVersion1Test {

    private DataSource dataSource;
    private SchemaMigrationVersion1 migration;

    @Before
    public void setup() {
        dataSource = Mockito.mock(DataSource.class);
        migration = new SchemaMigrationVersion1(dataSource);
    }

    @Test
    public void getSourceVersion() throws Exception {
        Assert.assertNull(migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() throws Exception {
        Assert.assertEquals("1", migration.getTargetVersion());
    }

    @Test
    public void testAddAdminUserToEmptyDatabase() {
        migration.migrate();

        User admin = new User();
        admin.setId(User.ID_PREFIX+DefaultEntities.DEFAULT_ADMIN_USER_ID);
        admin.setName("admin");

        Mockito.verify(dataSource).addUser(admin);
        Mockito.verify(dataSource).setVersion("1");
    }
}