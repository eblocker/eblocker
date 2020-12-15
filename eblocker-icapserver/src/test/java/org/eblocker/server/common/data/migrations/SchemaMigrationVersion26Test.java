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
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

public class SchemaMigrationVersion26Test {

    private DataSource dataSource;
    private SchemaMigrationVersion26 migration;

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        migration = new SchemaMigrationVersion26(dataSource);
    }

    @Test
    public void getSourceVersion() {
        Assert.assertEquals("25", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() {
        Assert.assertEquals("26", migration.getTargetVersion());
    }

    @Test
    public void migrate() {
        List<ParentalControlFilterMetaData> metaData = Arrays.asList(
                new ParentalControlFilterMetaData(0, null, null, null, null, null, null, "domainblacklist", null, false, false, null, null, null),
                new ParentalControlFilterMetaData(1, null, null, null, null, null, null, "domainblacklist/md5", null, false, false, null, null, null),
                new ParentalControlFilterMetaData(2, null, null, null, null, null, null, "domainblacklist/string", null, false, false, null, null, null),
                new ParentalControlFilterMetaData(3, null, null, null, null, null, null, "domainblacklist/sha1", null, false, false, null, null, null),
                new ParentalControlFilterMetaData(4, null, null, null, null, null, null, "domainblacklist", null, false, false, null, null, null)
        );
        Mockito.when(dataSource.getAll(ParentalControlFilterMetaData.class)).thenReturn(metaData);

        migration.migrate();

        Assert.assertEquals("domainblacklist/string", metaData.get(0).getFormat());
        Mockito.verify(dataSource).save(metaData.get(0), 0);

        Assert.assertEquals("domainblacklist/string", metaData.get(4).getFormat());
        Mockito.verify(dataSource).save(metaData.get(4), 4);

        Mockito.verify(dataSource).setVersion("26");
    }
}
