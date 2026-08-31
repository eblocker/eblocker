package org.eblocker.server.common.data.wireguard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eblocker.server.common.data.JedisDataSource;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class WireGuardEndpointConfigPersistenceTest {

    private Jedis jedis;
    private JedisDataSource dataSource;

    @Before
    public void setUp() {
        JedisPool jedisPool =
                Mockito.mock(JedisPool.class);

        jedis =
                Mockito.mock(Jedis.class);

        Mockito.when(
                jedisPool.getResource()
        ).thenReturn(jedis);

        dataSource =
                new JedisDataSource(
                        jedisPool,
                        new ObjectMapper()
                );
    }

    @Test
    public void saveUsesSingletonWireGuardEndpointConfigKey() {
        WireGuardEndpointConfig config =
                new WireGuardEndpointConfig(
                        WireGuardEndpointType.DYN_DNS,
                        "vpn.example.org"
                );

        WireGuardEndpointConfig saved =
                dataSource.save(config);

        assertNotNull(saved);
        assertEquals(
                WireGuardEndpointType.DYN_DNS,
                saved.getType()
        );
        assertEquals(
                "vpn.example.org",
                saved.getHost()
        );

        ArgumentCaptor<String> json =
                ArgumentCaptor.forClass(String.class);

        Mockito.verify(jedis).set(
                Mockito.eq("WireGuardEndpointConfig"),
                json.capture()
        );

        String serialized =
                json.getValue();

        assertNotNull(serialized);

        org.junit.Assert.assertTrue(
                serialized.contains(
                        "\"type\":\"DYN_DNS\""
                )
        );

        org.junit.Assert.assertTrue(
                serialized.contains(
                        "\"host\":\"vpn.example.org\""
                )
        );
    }

    @Test
    public void loadRestoresEndpointTypeAndHost() {
        Mockito.when(
                jedis.get("WireGuardEndpointConfig")
        ).thenReturn(
                "{"
                        + "\"type\":\"FIXED_IP\","
                        + "\"host\":\"198.51.100.23\""
                        + "}"
        );

        WireGuardEndpointConfig config =
                dataSource.get(
                        WireGuardEndpointConfig.class
                );

        assertNotNull(config);
        assertEquals(
                WireGuardEndpointType.FIXED_IP,
                config.getType()
        );
        assertEquals(
                "198.51.100.23",
                config.getHost()
        );
    }

    @Test
    public void loadRestoresEblockerDynDnsWithoutHost() {
        Mockito.when(
                jedis.get("WireGuardEndpointConfig")
        ).thenReturn(
                "{"
                        + "\"type\":\"EBLOCKER_DYN_DNS\","
                        + "\"host\":null"
                        + "}"
        );

        WireGuardEndpointConfig config =
                dataSource.get(
                        WireGuardEndpointConfig.class
                );

        assertNotNull(config);
        assertEquals(
                WireGuardEndpointType.EBLOCKER_DYN_DNS,
                config.getType()
        );
        assertNull(config.getHost());
    }
}
