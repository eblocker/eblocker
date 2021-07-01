/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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

import org.eblocker.server.common.EmbeddedRedisTestBase;
import org.junit.Test;
import redis.clients.jedis.Jedis;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JedisUtilsTest extends EmbeddedRedisTestBase {
    @Override
    public void doSetup() {
        super.doSetup();
        try (Jedis jedis = jedisPool.getResource()) {
            for (int i = 0; i < 1000; i++) {
                jedis.set("other_key:" + i, "other value " + i);
            }
            for (int i = 0; i < 100; i++) {
                jedis.set("wanted_key:" + i, "wanted value" + i);
            }
        }
    }

    @Test
    public void scanKeys() {
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> result = JedisUtils.scanKeys(jedis, "wanted_key:*");
            assertEquals(100, result.size());
        }
    }

    @Test
    public void scanKeysEmpty() {
        try (Jedis jedis = jedisPool.getResource()) {
            assertTrue(JedisUtils.scanKeys(jedis, "monkey*").isEmpty());
        }
    }
    @Test
    public void scanKeysPredicate() {
        Predicate<String> evenNumber = key -> Integer.parseInt(key.split(":")[1]) % 2 == 0;
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> result = JedisUtils.scanKeys(jedis, "wanted_key:*", evenNumber);
            assertEquals(50, result.size());
            assertTrue(result.contains("wanted_key:42"));
            assertFalse(result.contains("wanted_key:23"));
        }
    }
}