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
import org.eblocker.server.common.data.openvpn.OpenVpnClientState;
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

public class SchemaMigrationVersion37Test {

    private DataSource dataSource;
    private Jedis jedis;
    private JedisPool jedisPool;
    private ObjectMapper objectMapper;

    private SchemaMigration migration;

    @Before
    public void setUp() {
        jedis = Mockito.mock(Jedis.class);
        jedisPool = Mockito.mock(JedisPool.class);
        dataSource = Mockito.mock(DataSource.class);
        Mockito.when(jedisPool.getResource()).thenReturn(jedis);
        objectMapper = new ObjectMapper();
        migration = new SchemaMigrationVersion37(dataSource, jedisPool, objectMapper);
    }

    @Test
    public void getSourceVersion() {
        Assert.assertEquals("36", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() {
        Assert.assertEquals("37", migration.getTargetVersion());
    }

    @Test
    public void migrate() throws IOException {
        // setup mock data
        Map<String, String> clientStateById = new HashMap<>();
        clientStateById.put("OpenVpnClientState:0",
                "{\"id\":0,\"active\":false,\"virtualInterfaceName\":\"tun0\",\"linkLocalInterfaceAlias\":\"eth0:36\",\"linkLocalIpAddress\":\"169.254.8.2\",\"route\":1,\"routeNetGateway\":\"10.10.10.10\",\"routeVpnGateway\":\"10.8.0.5\",\"trustedIp\":\"10.10.10.102\",\"devices\":[\"device:64200c454900\"],\"nameServers\":[]}");
        clientStateById.put("OpenVpnClientState:1",
                "{\"id\":1,\"active\":true,\"virtualInterfaceName\":\"tun1\",\"linkLocalInterfaceAlias\":\"eth0:37\",\"linkLocalIpAddress\":\"169.254.8.3\",\"route\":2,\"routeNetGateway\":\"10.10.10.10\",\"routeVpnGateway\":\"10.9.0.5\",\"trustedIp\":\"10.10.20.102\",\"devices\":[\"device:64200c454901\"],\"nameServers\":[]}");
        Mockito.when(jedis.keys("OpenVpnClientState:[0-9]*")).thenReturn(clientStateById.keySet());
        Mockito.when(jedis.get(Mockito.anyString())).then(im -> clientStateById.get(im.getArgument(0)));

        // run migration
        migration.migrate();

        // check results
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jedis).set(Mockito.eq("OpenVpnClientState:0"), captor.capture());
        JsonNode node = objectMapper.readTree(captor.getValue());
        Assert.assertNull(node.get("active"));
        Assert.assertNotNull(node.get("state"));
        Assert.assertEquals(OpenVpnClientState.State.INACTIVE.name(), node.get("state").textValue());

        Mockito.verify(jedis).set(Mockito.eq("OpenVpnClientState:1"), captor.capture());
        node = objectMapper.readTree(captor.getValue());
        Assert.assertNull(node.get("active"));
        Assert.assertNotNull(node.get("state"));
        Assert.assertEquals(OpenVpnClientState.State.ACTIVE.name(), node.get("state").textValue());

        Mockito.verify(dataSource).setVersion("37");
    }
}
