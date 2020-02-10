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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class JedisConsistencyCheckTest {

    private JedisPool jedisPool;
    private Jedis jedis;
    private JedisConsistencyCheck check;

    @Before
    public void setup() {
        jedis = Mockito.mock(Jedis.class);
        jedisPool = Mockito.mock(JedisPool.class);
        Mockito.when(jedisPool.getResource()).thenReturn(jedis);

        check = new JedisConsistencyCheck(jedisPool);
    }

    @Test
    public void testIpConflicts() {
        Mockito.when(jedis.keys("device:*")).thenReturn(createSet("device:000000000000", "device:000000000001", "device:000000000002", "device:000000000003", "device:000000000004", "device:000000000005", "device:000000000006", "device:000000000007"));
        Mockito.when(jedis.hget("device:000000000000", "ipAddress")).thenReturn("192.168.1.2");
        Mockito.when(jedis.hget("device:000000000001", "ipAddress")).thenReturn("192.168.1.2");
        Mockito.when(jedis.hget("device:000000000002", "ipAddress")).thenReturn("192.168.1.2");
        Mockito.when(jedis.hget("device:000000000003", "ipAddress")).thenReturn("192.168.1.3");
        Mockito.when(jedis.hget("device:000000000004", "ipAddress")).thenReturn("192.168.1.3");
        Mockito.when(jedis.hget("device:000000000005", "ipAddress")).thenReturn("192.168.1.4");
        Mockito.when(jedis.hget("device:000000000006", "ipAddress")).thenReturn(null);
        Mockito.when(jedis.hget("device:000000000007", "ipAddress")).thenReturn(null);

        Assert.assertFalse(check.run());

        Mockito.verify(jedis).hdel("device:000000000000", "ipAddress");
        Mockito.verify(jedis).hdel("device:000000000001", "ipAddress");
        Mockito.verify(jedis).hdel("device:000000000002", "ipAddress");
        Mockito.verify(jedis).hdel("device:000000000003", "ipAddress");
        Mockito.verify(jedis).hdel("device:000000000004", "ipAddress");
        Mockito.verify(jedis, Mockito.never()).hdel("device:000000000005", "ipAddress");
        Mockito.verify(jedis, Mockito.never()).hdel("device:000000000006", "ipAddress");
        Mockito.verify(jedis, Mockito.never()).hdel("device:000000000007", "ipAddress");
    }

    @Test
    public void testNoIpConflicts() {
        Mockito.when(jedis.keys("device:*")).thenReturn(createSet("device:000000000000", "device:000000000001", "device:000000000006", "device:000000000007"));
        Mockito.when(jedis.hget("device:000000000000", "ipAddress")).thenReturn("192.168.1.2");
        Mockito.when(jedis.hget("device:000000000001", "ipAddress")).thenReturn("192.168.1.3");
        Mockito.when(jedis.hget("device:000000000006", "ipAddress")).thenReturn(null);
        Mockito.when(jedis.hget("device:000000000007", "ipAddress")).thenReturn(null);

        Assert.assertTrue(check.run());

        Mockito.verify(jedis, Mockito.never()).hdel(Mockito.anyString(), Mockito.anyString());
    }

    private Set<String> createSet(String... value) {
        return new HashSet<>(Arrays.asList(value));
    }
}
