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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;

public class SchemaMigrationVersion7Test {

    private DataSource dataSource;
    private SchemaMigrationVersion7 migration;

    @Before
    public void setup() {
        dataSource = Mockito.mock(DataSource.class);
        migration = new SchemaMigrationVersion7(dataSource);
    }

    @Test
    public void getSourceVersion() throws Exception {
        Assert.assertEquals("6", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() throws Exception {
        Assert.assertEquals("7", migration.getTargetVersion());
    }

    @Test
    public void testMigration() {
        Mockito.when(dataSource.keys("message:*")).thenReturn(Collections.singleton("MESSAGE"));
        // run migration
        migration.migrate();

        Mockito.verify(dataSource).keys(DefaultEntities.MESSAGE_DELETION_KEY);
        Mockito.verify(dataSource).delete("MESSAGE");

        // ensure version has been updated
        Mockito.verify(dataSource).setVersion("7");

        // ensure nothing else has been done
        Mockito.verifyNoMoreInteractions(dataSource);
    }
}