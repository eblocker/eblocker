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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.UserModuleOld;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Arrays;
import java.util.List;

public class SchemaMigrationVersion35Test {

    private DataSource dataSource;
    private SchemaMigrationVersion35 migration;

    private UserMigrationService userMigrationService;
    private JedisPool jedisPool;
    private Jedis jedis;
    private ObjectMapper objectMapper;

    private List<UserModuleOld> users;

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);

        jedis = Mockito.mock(Jedis.class);
        jedisPool = Mockito.mock(JedisPool.class);
        objectMapper = new ObjectMapper();
        userMigrationService = Mockito.mock(UserMigrationService.class); // new UserMigrationService(jedisPool, objectMapper);
        Mockito.when(jedisPool.getResource()).thenReturn(jedis);

        users = Arrays.asList(
            new UserModuleOld(0, null, null, null, null, null, false, null, null, null, null, null),
            new UserModuleOld(1, null, null, null, null, null, false, null, null, null, null, 1),
            new UserModuleOld(2, null, null, null, null, null, false, null, null, null, 2, null),
            new UserModuleOld(3, null, null, null, null, null, false, null, null, null, 3, 4),
            new UserModuleOld(4, null, null, null, null, null, false, null, null, null, null, 100),
            new UserModuleOld(5, null, null, null, null, null, false, null, null, null, 5, 100),
            new UserModuleOld(6, null, null, null, null, null, false, null, null, null, 200, null),
            new UserModuleOld(7, null, null, null, null, null, false, null, null, null, 200, 6),
            new UserModuleOld(8, null, null, null, null, null, false, null, null, null, 200, 100)
        );
        Mockito.when(userMigrationService.getAll()).thenReturn(users);

        migration = new SchemaMigrationVersion35(dataSource, userMigrationService);
    }

    @Test
    public void getSourceVersion() {
        Assert.assertEquals("34", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() {
        Assert.assertEquals("35", migration.getTargetVersion());
    }

    @Test
    public void migrate() {

        migration.migrate();

        Mockito.verify(userMigrationService, Mockito.times(5)).save(Mockito.any(UserModuleOld.class), Mockito.anyInt());
        assertSave(4, null, null);
        assertSave(5, 5, null);
        assertSave(6, null, null);
        assertSave(7, null, 6);
        assertSave(8, null, null);

        Mockito.verify(dataSource).setVersion("35");
    }

    private void assertSave(int id, Integer blacklistId, Integer whitelistId) {
        ArgumentCaptor<UserModuleOld> captor = ArgumentCaptor.forClass(UserModuleOld.class);
        Mockito.verify(userMigrationService).save(captor.capture(), Mockito.eq(id));
        Assert.assertEquals(blacklistId, captor.getValue().getCustomBlacklistId());
        Assert.assertEquals(whitelistId, captor.getValue().getCustomWhitelistId());
    }
}
