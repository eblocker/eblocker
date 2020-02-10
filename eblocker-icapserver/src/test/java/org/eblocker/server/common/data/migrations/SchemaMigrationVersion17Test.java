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

import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import org.eblocker.server.common.data.DataSource;

public class SchemaMigrationVersion17Test {

    private DataSource dataSource;
    private SchemaMigrationVersion17 migration;

    @Before
    public void setup() {
        dataSource = Mockito.mock(DataSource.class);
        migration = new SchemaMigrationVersion17(dataSource);
    }

    @Test
    public void getSourceVersion() throws Exception {
        Assert.assertEquals("16", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() throws Exception {
        Assert.assertEquals("17", migration.getTargetVersion());
    }

    @Test
    public void migrateConfig() throws Exception {
        Set<String> currentExitNodes = new HashSet<>();
        currentExitNodes.add("Germany");
        currentExitNodes.add("au");
        Set<String> expectedExitNodes = new HashSet<>();
        expectedExitNodes.add("de");
        expectedExitNodes.add("au");
        Mockito.when(dataSource.getCurrentTorExitNodes()).thenReturn(currentExitNodes);


        migration.migrate();

        ArgumentCaptor<Set<String>> captor = ArgumentCaptor.forClass(Set.class);
        Mockito.verify(dataSource).saveCurrentTorExitNodes(captor.capture());
        Assert.assertEquals(expectedExitNodes, captor.getValue());

        Mockito.verify(dataSource).setVersion("17");
    }

    @Test
    public void migrateConfig_nullSafe() throws Exception {
        Set<String> currentExitNodes = null;
        Set<String> expectedExitNodes = new HashSet<>();
        Mockito.when(dataSource.getCurrentTorExitNodes()).thenReturn(currentExitNodes);


        migration.migrate();

        ArgumentCaptor<Set<String>> captor = ArgumentCaptor.forClass(Set.class);
        Mockito.verify(dataSource).saveCurrentTorExitNodes(captor.capture());
        Assert.assertEquals(expectedExitNodes, captor.getValue());

        Mockito.verify(dataSource).setVersion("17");
    }

}
