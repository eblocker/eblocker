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

import com.google.common.collect.Sets;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.UserProfileModule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

public class SchemaMigrationVersion36Test {

    private DataSource dataSource;
    private SchemaMigration migration;

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        migration = new SchemaMigrationVersion36(dataSource);
    }

    @Test
    public void getSourceVersion() {
        Assert.assertEquals("35", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() {
        Assert.assertEquals("36", migration.getTargetVersion());
    }

    @Test
    public void migrate() {
        List<UserProfileModule> profiles = Arrays.asList(
            createMockProfile(0),
            createMockProfile(1, 0),
            createMockProfile(2, 0, 1),
            createMockProfile(3, 1)
        );
        Mockito.when(dataSource.getAll(UserProfileModule.class)).thenReturn(profiles);

        migration.migrate();

        Mockito.verify(dataSource).getAll(UserProfileModule.class);
        Mockito.verify(dataSource).save(profiles.get(1), 1);
        Assert.assertEquals(Sets.newHashSet(0, 11), profiles.get(1).getInaccessibleSitesPackages());
        Mockito.verify(dataSource).save(profiles.get(2), 2);
        Assert.assertEquals(Sets.newHashSet(0, 1, 11), profiles.get(2).getInaccessibleSitesPackages());

        Mockito.verify(dataSource).setVersion("36");
        Mockito.verifyNoMoreInteractions(dataSource);
    }

    private UserProfileModule createMockProfile(int id, Integer... prohibitedListIds) {
        return new UserProfileModule(id, null, null, null, null, null, null, null, Sets.newHashSet(prohibitedListIds), null, null, null, null, false, null);
    }
}
