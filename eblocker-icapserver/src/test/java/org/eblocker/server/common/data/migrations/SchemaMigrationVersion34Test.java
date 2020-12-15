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

public class SchemaMigrationVersion34Test {

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
        migration = new SchemaMigrationVersion34(dataSource, jedisPool, objectMapper);
    }

    @Test
    public void getSourceVersion() {
        Assert.assertEquals("33", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() {
        Assert.assertEquals("34", migration.getTargetVersion());
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
        ArgumentCaptor<String> captorA = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jedis).set(Mockito.eq("ParentalControlFilterMetaData:3"), captorA.capture());
        JsonNode nodeA = objectMapper.readTree(captorA.getValue());
        Assert.assertNotNull(nodeA.get("category"));
        Assert.assertEquals("PARENTAL_CONTROL", nodeA.get("category").textValue());

        ArgumentCaptor<String> captorB = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jedis).set(Mockito.eq("ParentalControlFilterMetaData:4"), captorB.capture());
        JsonNode nodeB = objectMapper.readTree(captorB.getValue());
        Assert.assertNotNull(nodeB.get("category"));
        Assert.assertEquals("PARENTAL_CONTROL", nodeB.get("category").textValue());

        Mockito.verify(jedis, Mockito.never()).set(Mockito.eq("ParentalControlFilterMetaData:1"), Mockito.anyString());
        Mockito.verify(jedis, Mockito.never()).set(Mockito.eq("ParentalControlFilterMetaData:2"), Mockito.anyString());
    }
}
