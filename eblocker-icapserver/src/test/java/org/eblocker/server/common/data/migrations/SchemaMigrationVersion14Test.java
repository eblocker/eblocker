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
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SchemaMigrationVersion14Test {

    private DataSource dataSource;
    private Jedis jedis;
    private JedisPool jedisPool;
    private SchemaMigrationVersion14 migration;

    private Path flagFilePath;

    @Before
    public void setup() throws IOException {
        dataSource = Mockito.mock(DataSource.class);
        jedis = Mockito.mock(Jedis.class);
        jedisPool = Mockito.mock(JedisPool.class);
        Mockito.when(jedisPool.getResource()).thenReturn(jedis);

        flagFilePath = Files.createTempFile("dns", ".flag");
        Files.delete(flagFilePath);

        migration = new SchemaMigrationVersion14(dataSource, jedisPool, flagFilePath.toString());
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(flagFilePath);
    }

    @Test
    public void getSourceVersion() throws Exception {
        Assert.assertEquals("13", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() throws Exception {
        Assert.assertEquals("14", migration.getTargetVersion());
    }

    @Test
    public void migrateDnsEnabled() throws Exception {
        Mockito.when(jedis.get(DefaultEntities.DNS_ENABLED_KEY)).thenReturn("true");
        migration.migrate();
        Assert.assertTrue(Files.exists(flagFilePath));
    }

    @Test
    public void migrateDnsDisabled() throws Exception {
        Mockito.when(jedis.get(DefaultEntities.DNS_ENABLED_KEY)).thenReturn("false");
        migration.migrate();
        Assert.assertFalse(Files.exists(flagFilePath));
    }
}
