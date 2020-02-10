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
package org.eblocker.server.common.data;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class JedisDataSourceTest {

    private JedisPool jedisPool;
    private Jedis jedis;
    private ObjectMapper objectMapper;

    private JedisDataSource dataSource;

    @Before
    public void setup() {
        jedis = Mockito.mock(Jedis.class);
        jedisPool = Mockito.mock(JedisPool.class);
        Mockito.when(jedisPool.getResource()).thenReturn(jedis);

        objectMapper = new ObjectMapper();

        dataSource = new JedisDataSource(jedisPool, objectMapper);
    }


    @Test
    public void testSaveSingularEntity() {
        dataSource.save(new Entity());
        Mockito.verify(jedis).set(Mockito.eq("Entity"), Mockito.anyString());
    }

    @Test
    public void testSaveEntity() {
        dataSource.save(new Entity(), 5);
        Mockito.verify(jedis).set(Mockito.eq("Entity:5"), Mockito.anyString());
    }

    @Test
    public void testNextId() {
        Mockito.when(jedis.incr("Entity:sequence")).thenReturn(5L);
        Assert.assertEquals(5L, dataSource.nextId(Entity.class));
    }

    public static class Entity {
        private int id;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }
    }

    @Test
    public void testDeviceWithoutPauseFlagNotPaused(){
        String deviceId = "device:112233445566";

        // Need to set at least one parameter
        Map<String, String> map = new HashMap<>();
        map.put("TEST-KEY", "TEST-VALUE");

        // Needed to set the gateway
        Mockito.when(jedis.hgetAll(Mockito.eq(deviceId))).thenReturn(map);

        Device device = dataSource.getDevice(deviceId);

        Assert.assertFalse(device.isPaused());
    }

    @Test
    public void testDeviceSave() {
        Device device = new Device();
        device.setId("device:112233445566");
        device.setIpAddresses(Arrays.asList(IpAddress.parse("10.10.10.10"), IpAddress.parse("10.10.10.11")));

        dataSource.save(device);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(jedis).hmset(Mockito.eq("device:112233445566"), captor.capture());

        Map<String, String> map = captor.getValue();
        Assert.assertEquals("10.10.10.10,10.10.10.11", map.get("ipAddress"));
    }

    @Test
    public void testDeviceLoad() {
        String deviceId = "device:112233445566";

        Map<String, String> map = new HashMap<>();
        map.put("ipAddress", "10.10.10.10,10.10.10.11");
        Mockito.when(jedis.hgetAll(deviceId)).thenReturn(map);

        Device device = dataSource.getDevice(deviceId);
        Assert.assertNotNull(device);
        Assert.assertNotNull(device.getIpAddresses());
        Assert.assertEquals(2, device.getIpAddresses().size());
        Assert.assertTrue(device.getIpAddresses().contains(IpAddress.parse("10.10.10.10")));
        Assert.assertTrue(device.getIpAddresses().contains(IpAddress.parse("10.10.10.11")));
    }

    @Test
    public void testDeviceGateway() {
        String deviceId = "device:fb1122334455";

        Map<String, String> map = new HashMap<>();
        map.put("ipAddress", "192.168.1.1,fe80::2665:11ff:fe58:d32b");
        Mockito.when(jedis.hgetAll(deviceId)).thenReturn(map);
        Mockito.when(jedis.get("gateway")).thenReturn("192.168.1.1");

        Device device = dataSource.getDevice(deviceId);
        Assert.assertTrue(device.isGateway());
    }

    @Test
    public void testGetDeviceScanningInterval() {
        // good value:
        Mockito.when(jedis.get(JedisDataSource.KEY_DEVICE_SCANNING_INTERVAL)).thenReturn("42");
        Assert.assertEquals(Long.valueOf(42), dataSource.getDeviceScanningInterval());

        // empty value:
        Mockito.when(jedis.get(JedisDataSource.KEY_DEVICE_SCANNING_INTERVAL)).thenReturn(null);
        Assert.assertNull(dataSource.getDeviceScanningInterval());
    }

    @Test(expected = NumberFormatException.class)
    public void testGetDeviceScanningIntervalBadValue() {
        // bad value:
        Mockito.when(jedis.get(JedisDataSource.KEY_DEVICE_SCANNING_INTERVAL)).thenReturn("not an integer");
        dataSource.getDeviceScanningInterval();
    }

    @Test
    public void testSetDeviceScanningInterval() {
        dataSource.setDeviceScanningInterval(42L);
        Mockito.verify(jedis).set(JedisDataSource.KEY_DEVICE_SCANNING_INTERVAL, "42");
    }
}
