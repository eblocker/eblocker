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
import org.eblocker.server.http.ssl.AppWhitelistModule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;

public class SchemaMigrationVersion2Test {

    private DataSource dataSource;

    @Before
    public void setup() {
        dataSource = Mockito.mock(DataSource.class);
    }

    @Test
    public void getSourceVersion() throws Exception {
        SchemaMigrationVersion2 migration = new SchemaMigrationVersion2(dataSource, 0);
        Assert.assertEquals("1", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() throws Exception {
        SchemaMigrationVersion2 migration = new SchemaMigrationVersion2(dataSource, 0);
        Assert.assertEquals("2", migration.getTargetVersion());
    }

    @Test
    public void testInitializeSequenceCustomModules() {
        Mockito.when(dataSource.getAll(AppWhitelistModule.class)).thenReturn(Arrays.asList(mockModule(1), mockModule(2), mockModule(101)));

        SchemaMigrationVersion2 migration = new SchemaMigrationVersion2(dataSource, 100);
        migration.migrate();

        Mockito.verify(dataSource).setVersion("2");
        Mockito.verify(dataSource).setIdSequence(AppWhitelistModule.class, 101);
    }

    @Test
    public void testInitializeSequenceOnlyBuiltIn() {
        Mockito.when(dataSource.getAll(AppWhitelistModule.class)).thenReturn(Arrays.asList(mockModule(1), mockModule(2)));

        SchemaMigrationVersion2 migration = new SchemaMigrationVersion2(dataSource, 100);
        migration.migrate();

        Mockito.verify(dataSource).setVersion("2");
        Mockito.verify(dataSource).setIdSequence(AppWhitelistModule.class, 100);
    }

    private AppWhitelistModule mockModule(int id) {
        return new AppWhitelistModule(id, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}