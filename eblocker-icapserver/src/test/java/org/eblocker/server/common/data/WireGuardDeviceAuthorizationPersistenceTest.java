package org.eblocker.server.common.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class WireGuardDeviceAuthorizationPersistenceTest {

    private Jedis jedis;
    private JedisDataSource dataSource;

    @BeforeEach
    public void setUp() {
        jedis = Mockito.mock(Jedis.class);
        JedisPool jedisPool = Mockito.mock(JedisPool.class);
        Mockito.when(jedisPool.getResource()).thenReturn(jedis);
        dataSource = new JedisDataSource(jedisPool, new ObjectMapper());
    }

    @Test
    public void missingRedisFlagDefaultsToDenied() {
        String deviceId = "device:001122334455";
        Map<String, String> map = new HashMap<>();

        // JedisDataSource treats an empty Redis hash as a non-existing device.
        // Represent an existing legacy device while deliberately omitting the
        // new wireGuardEnabled key.
        map.put("TEST-KEY", "TEST-VALUE");

        Mockito.when(jedis.hgetAll(deviceId)).thenReturn(map);

        Device device = dataSource.getDevice(deviceId);

        assertNotNull(device);
        assertFalse(device.isWireGuardEnabled());
    }

    @Test
    public void redisFlagCanBeLoaded() {
        String deviceId = "device:001122334455";
        Map<String, String> map = new HashMap<>();
        map.put("wireGuardEnabled", "true");
        Mockito.when(jedis.hgetAll(deviceId)).thenReturn(map);

        Device device = dataSource.getDevice(deviceId);

        assertNotNull(device);
        assertTrue(device.isWireGuardEnabled());
    }

    @Test
    public void redisFlagIsSaved() {
        Device device = new Device();
        device.setId("device:001122334455");
        device.setIpAddresses(Arrays.asList(
                IpAddress.parse("10.10.10.10")));
        device.setWireGuardEnabled(true);

        dataSource.save(device);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor =
                ArgumentCaptor.forClass(Map.class);

        Mockito.verify(jedis).hmset(
                Mockito.eq(device.getId()),
                captor.capture());

        assertEquals(
                "true",
                captor.getValue().get("wireGuardEnabled"));
    }
}
