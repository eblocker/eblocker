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
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.dns.DnsServerConfig;
import org.eblocker.server.common.data.dns.EblockerDnsServerState;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SchemaMigrationVersion15Test {

    private DataSource dataSource;
    private Jedis jedis;
    private JedisPool jedisPool;
    private SchemaMigrationVersion15 migration;

    private Set<Device> devices = new HashSet<>();

    @Before
    public void setup() throws IOException {
        devices.add(createDevice("device:000000000101", "192.168.1.1"));
        devices.add(createDevice("device:000000000102", null));
        devices.add(createDevice("device:000000000103", "192.168.1.3"));

        dataSource = Mockito.mock(DataSource.class);
        Mockito.when(dataSource.getDevices()).thenReturn(devices);


        jedis = Mockito.mock(Jedis.class);
        jedisPool = Mockito.mock(JedisPool.class);
        Mockito.when(jedisPool.getResource()).thenReturn(jedis);


        migration = new SchemaMigrationVersion15(dataSource, jedisPool);
    }

    @Test
    public void getSourceVersion() throws Exception {
        Assert.assertEquals("14", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() throws Exception {
        Assert.assertEquals("15", migration.getTargetVersion());
    }

    @Test
    public void migrateNoConfig() throws Exception {
        migration.migrate();
        Mockito.verify(dataSource).setVersion("15");
        Mockito.verify(dataSource, Mockito.never()).save(Mockito.any(Object.class));
    }

    @Test
    public void migrateEnabled() throws Exception {
        Mockito.when(jedis.get(DefaultEntities.DNS_ENABLED_KEY)).thenReturn("true");

        Map<String, String> resolverByIp = new HashMap<>();
        resolverByIp.put("192.168.1.1", "test-resolver");
        resolverByIp.put("192.168.1.2", "test-resolver");
        resolverByIp.put("192.168.1.3", "test-resolver-2");
        DnsServerConfig config = new DnsServerConfig();
        config.setResolverConfigNameByIp(resolverByIp);
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(config);

        migration.migrate();

        ArgumentCaptor<EblockerDnsServerState> captor = ArgumentCaptor.forClass(EblockerDnsServerState.class);
        Mockito.verify(dataSource).save(captor.capture());

        EblockerDnsServerState savedState = captor.getValue();
        Assert.assertTrue(savedState.isEnabled());
        Assert.assertEquals(2, savedState.getResolverByDeviceId().size());
        Assert.assertEquals("test-resolver", savedState.getResolverByDeviceId().get("device:000000000101"));
        Assert.assertEquals("test-resolver-2", savedState.getResolverByDeviceId().get("device:000000000103"));

        Mockito.verify(dataSource).setVersion("15");
    }

    @Test
    public void migrateDisabled() throws Exception {
        Mockito.when(jedis.get(DefaultEntities.DNS_ENABLED_KEY)).thenReturn("false");

        Map<String, String> resolverByIp = new HashMap<>();
        resolverByIp.put("192.168.1.1", "test-resolver");
        resolverByIp.put("192.168.1.2", "test-resolver");
        resolverByIp.put("192.168.1.3", "test-resolver-2");
        DnsServerConfig config = new DnsServerConfig();
        config.setResolverConfigNameByIp(resolverByIp);
        Mockito.when(dataSource.get(DnsServerConfig.class)).thenReturn(config);

        migration.migrate();

        ArgumentCaptor<EblockerDnsServerState> captor = ArgumentCaptor.forClass(EblockerDnsServerState.class);
        Mockito.verify(dataSource).save(captor.capture());

        EblockerDnsServerState savedState = captor.getValue();
        Assert.assertFalse(savedState.isEnabled());
        Assert.assertEquals(2, savedState.getResolverByDeviceId().size());
        Assert.assertEquals("test-resolver", savedState.getResolverByDeviceId().get("device:000000000101"));
        Assert.assertEquals("test-resolver-2", savedState.getResolverByDeviceId().get("device:000000000103"));

        Mockito.verify(dataSource).setVersion("15");
    }


    private Device createDevice(String id, String ipAddress) {
        Device device = new Device();
        device.setId(id);
        if (ipAddress != null) {
            device.setIpAddresses(Collections.singletonList(IpAddress.parse(ipAddress)));
        }
        return device;
    }

}
