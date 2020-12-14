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
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Collections;

public class SchemaMigrationVersion8Test {
    private DataSource dataSource;
    private JedisPool jedisPool;
    private Jedis jedis;
    private SchemaMigrationVersion8 migration;

    @Before
    public void setup() {
        dataSource = Mockito.mock(DataSource.class);

        jedis = Mockito.mock(Jedis.class);
        Mockito.when(jedis.keys(DefaultEntities.PARENTAL_CONTROL_FILTER_SUMMARY_KEY)).thenReturn(Collections.singleton("test-key"));

        jedisPool = Mockito.mock(JedisPool.class);
        Mockito.when(jedisPool.getResource()).thenReturn(jedis);

        migration = new SchemaMigrationVersion8(dataSource, jedisPool);
    }

    @Test
    public void getSourceVersion() throws Exception {
        Assert.assertEquals("7", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() throws Exception {
        Assert.assertEquals("8", migration.getTargetVersion());
    }

    @Test
    public void migrate() throws Exception {
        // run migration
        migration.migrate();

        // verify migration has been run correctly
        Mockito.verify(jedis).del("test-key");
        Mockito.verify(dataSource).setIdSequence(ParentalControlFilterMetaData.class,
            DefaultEntities.PARENTAL_CONTROL_ID_SEQUENCE_FILTER_METADATA);
        Mockito.verify(dataSource).setVersion("8");
    }
}
