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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SchemaMigrationVersion4Test {

    private DataSource dataSource;
    private SchemaMigrationVersion4 migration;

    @Before
    public void setup() {
        dataSource = Mockito.mock(DataSource.class);
        migration = new SchemaMigrationVersion4(dataSource);
    }


    @Test
    public void getSourceVersion() throws Exception {
        Assert.assertEquals("3", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() throws Exception {
        Assert.assertEquals("4", migration.getTargetVersion());
    }

    @Test
    public void migrate() throws Exception {
        // setup mock full profile
        Set<Integer> filterLists = new HashSet<>(Arrays.asList(0, 1, 2));
        UserProfileModule profile = new UserProfileModule(DefaultEntities.PARENTAL_CONTROL_FULL_PROFILE_ID, null, null, null, null, false, false,null, filterLists, null, null, null, null, false, null);
        Mockito.when(dataSource.get(UserProfileModule.class, DefaultEntities.PARENTAL_CONTROL_FULL_PROFILE_ID)).thenReturn(profile);

        // run migration
        migration.migrate();

        // ensure low profile has been deleted
        Mockito.verify(dataSource).delete(UserProfileModule.class, DefaultEntities.PARENTAL_CONTROL_LOW_PROFILE_ID);

        // ensure facebook filter list has been removed and saves
        Assert.assertFalse(filterLists.contains("1"));
        Mockito.verify(dataSource).save(profile, DefaultEntities.PARENTAL_CONTROL_FULL_PROFILE_ID);

        // ensure version has been set
        Mockito.verify(dataSource).setVersion("4");
    }

}
