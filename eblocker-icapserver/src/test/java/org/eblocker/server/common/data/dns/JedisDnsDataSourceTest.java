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
package org.eblocker.server.common.data.dns;

import org.eblocker.server.common.TestRedisServer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class JedisDnsDataSourceTest {

    private static TestRedisServer REDIS_SERVER;
    private static JedisPool JEDIS_POOL;

    private JedisDnsDataSource jedisDnsDataSource;

    @BeforeClass
    public static void beforeClass() {
        REDIS_SERVER = new TestRedisServer();
        REDIS_SERVER.start();
        JEDIS_POOL = REDIS_SERVER.getPool();
    }

    @AfterClass
    public static void afterClass() {
        REDIS_SERVER.stop();
    }

    @Before
    public void setup() {
        jedisDnsDataSource = new JedisDnsDataSource(JEDIS_POOL);
        try (Jedis jedis = JEDIS_POOL.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            pipeline.rpush("dns_stats:test",
                    entry(ZonedDateTime.of(2017, 11, 20, 12, 0, 0, 0, ZoneId.systemDefault()), "8.8.8.8", "valid", 10),
                    entry(ZonedDateTime.of(2017, 11, 20, 12, 0, 1, 0, ZoneId.systemDefault()), "8.8.8.8", "valid", 20),
                    entry(ZonedDateTime.of(2017, 11, 20, 12, 0, 2, 0, ZoneId.systemDefault()), "8.8.8.8", "valid", 30),
                    entry(ZonedDateTime.of(2017, 11, 20, 12, 0, 3, 0, ZoneId.systemDefault()), "8.8.8.8", "valid", 40),
                    entry(ZonedDateTime.of(2017, 11, 20, 12, 0, 4, 0, ZoneId.systemDefault()), "8.8.8.8", "invalid"),
                    entry(ZonedDateTime.of(2017, 11, 20, 12, 0, 5, 0, ZoneId.systemDefault()), "8.8.8.8", "invalid"),
                    entry(ZonedDateTime.of(2017, 11, 20, 12, 0, 6, 0, ZoneId.systemDefault()), "8.8.8.8", "invalid"),
                    entry(ZonedDateTime.of(2017, 11, 20, 12, 0, 7, 0, ZoneId.systemDefault()), "8.8.8.8", "timeout"),
                    entry(ZonedDateTime.of(2017, 11, 20, 12, 0, 8, 0, ZoneId.systemDefault()), "8.8.8.8", "timeout"),
                    entry(ZonedDateTime.of(2017, 11, 20, 12, 0, 9, 0, ZoneId.systemDefault()), "8.8.8.8", "error"),
                    entry(ZonedDateTime.of(2017, 11, 20, 12, 0, 10, 0, ZoneId.systemDefault()), "8.8.4.4", "valid", 20),
                    entry(ZonedDateTime.of(2017, 11, 20, 12, 0, 11, 0, ZoneId.systemDefault()), "9.9.9.9", "valid", 30));
            pipeline.sync();
        }
    }

    @After
    public void tearDown() {
        try (Jedis jedis = JEDIS_POOL.getResource()) {
            jedis.flushAll();
        }
    }

    @Test
    public void getEventsByResolver() {
        List<ResolverEvent> stats = jedisDnsDataSource.getEventsByResolver("test");
        Assert.assertNotNull(stats);
        Assert.assertEquals(12, stats.size());
        Assert.assertEquals(ZonedDateTime.of(2017, 11, 20, 12, 0, 0, 0, ZoneId.systemDefault()).toInstant(), stats.get(0).getInstant());
    }

    @Test
    public void getEventsByUnknownResolver() {
        List<ResolverEvent> stats = jedisDnsDataSource.getEventsByResolver("unknown");
        Assert.assertEquals(Collections.emptyList(), stats);
    }

    @Test
    public void deleteEventsBefore() {
        jedisDnsDataSource.deleteEventsBefore(ZonedDateTime.of(2017, 11, 20, 12, 0, 4, 0, ZoneId.systemDefault()).toInstant());
        try (Jedis jedis = JEDIS_POOL.getResource()) {
            Assert.assertEquals(Long.valueOf(8), jedis.llen("dns_stats:test"));
        }
    }

    @Test
    public void deleteEventsBeforeNoDeletions() {
        jedisDnsDataSource.deleteEventsBefore(ZonedDateTime.of(2017, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant());
        try (Jedis jedis = JEDIS_POOL.getResource()) {
            Assert.assertEquals(Long.valueOf(12), jedis.llen("dns_stats:test"));
        }
    }

    private String entry(ZonedDateTime dt, String nameServer, String status) {
        return entry(dt, nameServer, status, null);
    }

    private String entry(ZonedDateTime dt, String nameServer, String status, Integer duration) {
        Instant i = dt.toInstant();
        double t = i.getEpochSecond() + i.getNano() / 1000000000.0;
        if (duration != null) {
            return String.format(Locale.US, "%f,%s,%s,%s", t, nameServer, status, duration);
        }
        return String.format(Locale.US, "%f,%s,%s", t, nameServer, status);
    }

}
