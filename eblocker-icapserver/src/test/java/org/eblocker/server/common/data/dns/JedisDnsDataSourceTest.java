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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eblocker.server.common.TestRedisServer;
import org.eblocker.server.common.data.Ip4Address;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class JedisDnsDataSourceTest {

    private static TestRedisServer REDIS_SERVER;
    private static JedisPool JEDIS_POOL;

    private JedisDnsDataSource jedisDnsDataSource;
    private ObjectMapper objectMapper;

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
        objectMapper = new ObjectMapper();
        jedisDnsDataSource = new JedisDnsDataSource(JEDIS_POOL, objectMapper);
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

    @Test
    public void testAddDnsQuery() {
        jedisDnsDataSource.addDnsQueryQueue("1234", "8.8.8.8", Arrays.asList(new DnsQuery(DnsRecordType.A, "xkcd.org"), new DnsQuery(DnsRecordType.AAAA, "eblocker.com")));
        try (Jedis jedis = JEDIS_POOL.getResource()) {
            Assert.assertEquals(Long.valueOf(1L), jedis.llen("dns_query"));
            Assert.assertEquals("1234,8.8.8.8,A:xkcd.org,AAAA:eblocker.com", jedis.lpop("dns_query"));
        }
    }

    @Test
    public void testPopDnsResult() {
        try (Jedis jedis = JEDIS_POOL.getResource()) {
            jedis.rpush("dns_response:2345",
                "{\"responses\":[\"0,A,xkcd.org.,108.168.185.170\",\"0,A,pouet.net.,145.24.145.63\",\"3\",\"\"],\"log\":[\"1511885786.4447541,8.8.8.8,valid,0.059603153\",\"1511885786.743278,8.8.8.8,valid,0.29789187\",\"1517235748.7000978,8.8.8.8,valid,0.032670123\",\"1517236466.824103,8.8.8.8,timeout\"]}");
        }

        DnsDataSourceDnsResponse result = jedisDnsDataSource.popDnsResolutionQueue("2345", 10);

        Assert.assertNotNull(result);

        Assert.assertNotNull(result.getResponses());
        Assert.assertEquals(4, result.getResponses().size());
        Assert.assertEquals(Integer.valueOf(0), result.getResponses().get(0).getStatus());
        Assert.assertEquals("xkcd.org.", result.getResponses().get(0).getName());
        Assert.assertEquals(Ip4Address.parse("108.168.185.170"), result.getResponses().get(0).getIpAddress());

        Assert.assertEquals(Integer.valueOf(0), result.getResponses().get(1).getStatus());
        Assert.assertEquals("pouet.net.", result.getResponses().get(1).getName());
        Assert.assertEquals(Ip4Address.parse("145.24.145.63"), result.getResponses().get(1).getIpAddress());

        Assert.assertEquals(Integer.valueOf(3), result.getResponses().get(2).getStatus());
        Assert.assertNull(result.getResponses().get(2).getName());
        Assert.assertNull(result.getResponses().get(2).getIpAddress());

        Assert.assertNull(result.getResponses().get(3).getStatus());
        Assert.assertNull(result.getResponses().get(3).getName());
        Assert.assertNull(result.getResponses().get(3).getIpAddress());

        Assert.assertNotNull(result.getLog());
        Assert.assertEquals(4, result.getLog().size());
        Assert.assertEquals(1511885786444L, result.getLog().get(0).getInstant().toEpochMilli());
        Assert.assertEquals("valid", result.getLog().get(0).getStatus());
        Assert.assertEquals("8.8.8.8", result.getLog().get(0).getNameServer());
        Assert.assertEquals(59L, (long) result.getLog().get(0).getDuration());

        Assert.assertEquals(1511885786743L, result.getLog().get(1).getInstant().toEpochMilli());
        Assert.assertEquals("valid", result.getLog().get(1).getStatus());
        Assert.assertEquals("8.8.8.8", result.getLog().get(1).getNameServer());
        Assert.assertEquals(297L, (long) result.getLog().get(1).getDuration());

        Assert.assertEquals(1517235748700L, result.getLog().get(2).getInstant().toEpochMilli());
        Assert.assertEquals("valid", result.getLog().get(2).getStatus());
        Assert.assertEquals("8.8.8.8", result.getLog().get(2).getNameServer());
        Assert.assertEquals(32L, (long) result.getLog().get(2).getDuration());

        Assert.assertEquals(1517236466824L, result.getLog().get(3).getInstant().toEpochMilli());
        Assert.assertEquals("timeout", result.getLog().get(3).getStatus());
        Assert.assertEquals("8.8.8.8", result.getLog().get(3).getNameServer());
        Assert.assertNull(result.getLog().get(3).getDuration());
    }

    @Test
    public void testPopDnsResultTimeout() {
        long start = System.currentTimeMillis();
        DnsDataSourceDnsResponse result = jedisDnsDataSource.popDnsResolutionQueue("2345", 1);
        long elapsed = System.currentTimeMillis() - start;

        Assert.assertNull(result);
        Assert.assertTrue("Expected elapsed time to to be between 900 and 10000 ms, got " + elapsed,
            elapsed >= 900 && elapsed < 10000);
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
