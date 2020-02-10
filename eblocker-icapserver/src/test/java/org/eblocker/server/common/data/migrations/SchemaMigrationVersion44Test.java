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
import org.eblocker.server.icap.filter.FilterStoreConfiguration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class SchemaMigrationVersion44Test {

    private DataSource dataSource;
    private SchemaMigration migration;

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        migration = new SchemaMigrationVersion44(dataSource);
    }

    @Test
    public void getSourceVersion() {
        Assert.assertEquals("43", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() {
        Assert.assertEquals("44", migration.getTargetVersion());
    }

    @Test
    public void migrate() {
        migration.migrate();
        Mockito.verify(dataSource).setIdSequence(FilterStoreConfiguration.class, 1000);
        Mockito.verify(dataSource).setVersion("44");
    }
}
