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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eblocker.server.common.data.DataSource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SchemaMigrationVersion30Test {

    private Jedis jedis;
    private JedisPool jedisPool;
    private DataSource dataSource;
    private ObjectMapper objectMapper;
    private SchemaMigration migration;

    @Before
    public void setUp() {
        jedis = Mockito.mock(Jedis.class);
        jedisPool = Mockito.mock(JedisPool.class);
        dataSource = Mockito.mock(DataSource.class);
        Mockito.when(jedisPool.getResource()).thenReturn(jedis);
        objectMapper = new ObjectMapper();
        migration = new SchemaMigrationVersion30(dataSource, jedisPool, objectMapper);
    }

    @Test
    public void getSourceVersion() {
        Assert.assertEquals("29", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() {
        Assert.assertEquals("30", migration.getTargetVersion());
    }

    @Test
    public void testMigrateUsers() throws IOException {
        // setup mock data
        Map<String, String> usersById = new HashMap<>();
        usersById.put("UserModule:1", "{\"id\":1,\"name\":\"standard\",\"plugAndPlayFilterWhitelistId\":1032}");
        usersById.put("UserModule:2", "{\"id\":2,\"name\":\"standard2\",\"plugAndPlayFilterWhitelistId\":null}");
        usersById.put("UserModule:3", "{\"id\":3,\"name\":\"standard3\"}");
        Mockito.when(jedis.keys("UserModule:[0-9]*")).thenReturn(usersById.keySet());
        Mockito.when(jedis.get(Mockito.anyString())).then(im -> usersById.get(im.getArgument(0)));

        // run migration
        migration.migrate();

        // check results
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jedis).set(Mockito.eq("UserModule:1"), captor.capture());
        JsonNode node = objectMapper.readTree(captor.getValue());
        Assert.assertNull(node.get("plugAndPlayFilterWhitelistId"));
        Assert.assertNotNull(node.get("customWhitelistId"));
        Assert.assertEquals(1032, node.get("customWhitelistId").intValue());

        Mockito.verify(jedis).set(Mockito.eq("UserModule:2"), captor.capture());
        node = objectMapper.readTree(captor.getValue());
        Assert.assertNull(node.get("plugAndPlayFilterWhitelistId"));
        Assert.assertNull(node.get("customWhitelistId"));

        Mockito.verify(jedis, Mockito.never()).set(Mockito.eq("UserModule:3"), Mockito.anyString());

        Mockito.verify(dataSource).setVersion("30");
    }

    @Test
    public void testMigrateParentalControlFilterMetaData() throws IOException {
        // setup mock data
        Map<String, String> metaDataById = new HashMap<>();
        metaDataById.put("ParentalControlFilterMetaData:1", "{\"id\":1,\"name\":\"standard\",\"category\":\"ADS_TRACKERS_WHITELIST\"}");
        metaDataById.put("ParentalControlFilterMetaData:2", "{\"id\":2,\"name\":\"standard2\",\"category\":\"ADS\"}");
        metaDataById.put("ParentalControlFilterMetaData:3", "{\"id\":3,\"name\":\"standard3\",\"category\":null}");
        metaDataById.put("ParentalControlFilterMetaData:4", "{\"id\":4,\"name\":\"standard4\"}");
        Mockito.when(jedis.keys("ParentalControlFilterMetaData:[0-9]*")).thenReturn(metaDataById.keySet());
        Mockito.when(jedis.get(Mockito.anyString())).then(im -> metaDataById.get(im.getArgument(0)));

        // run migration
        migration.migrate();

        // check results
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jedis).set(Mockito.eq("ParentalControlFilterMetaData:1"), captor.capture());
        JsonNode node = objectMapper.readTree(captor.getValue());
        Assert.assertNotNull(node.get("category"));
        Assert.assertEquals("CUSTOM", node.get("category").textValue());

        Mockito.verify(jedis, Mockito.never()).set(Mockito.eq("ParentalControlFilterMetaData:2"), Mockito.anyString());
        Mockito.verify(jedis, Mockito.never()).set(Mockito.eq("ParentalControlFilterMetaData:3"), Mockito.anyString());
        Mockito.verify(jedis, Mockito.never()).set(Mockito.eq("ParentalControlFilterMetaData:4"), Mockito.anyString());
    }
}
