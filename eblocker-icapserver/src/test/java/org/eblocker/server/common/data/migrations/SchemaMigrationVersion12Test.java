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
import org.eblocker.server.common.data.UserModuleOld;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.mockito.Mockito.eq;

public class SchemaMigrationVersion12Test {
    private DataSource dataSource;
    private SchemaMigrationVersion12 migration;
    List<UserModuleOld> userModules = new ArrayList<>();
    private UserMigrationService userMigrationService;
    private UserModuleOld userAlice, userBob;

    @Before
    public void setup() {
        dataSource = Mockito.mock(DataSource.class);
        userMigrationService = Mockito.mock(UserMigrationService.class);

        migration = new SchemaMigrationVersion12(dataSource, userMigrationService);

        // Existing users
        userAlice = new UserModuleOld(1337, 1337, "alice", "alice-key", null, null, false, null, null, null, null, null);

        userBob = new UserModuleOld(4711, 4711, "bob", "bob-key", null, null, false, null, new HashMap<>(), null, null, null);

        userModules.add(userAlice);
        userModules.add(userBob);

        Mockito.when(userMigrationService.getAll()).thenReturn(userModules);
    }

    @Test
    public void getSourceVersion() throws Exception {
        Assert.assertEquals("11", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() throws Exception {
        Assert.assertEquals("12", migration.getTargetVersion());
    }

    @Test
    public void testRemovePin() {
        Mockito.when(userMigrationService.getAll()).thenReturn(userModules);

        migration.migrate();

        Mockito.verify(userMigrationService).save(eq(userAlice), eq(1337));
        Mockito.verify(userMigrationService).save(eq(userBob), eq(4711));
    }

}
