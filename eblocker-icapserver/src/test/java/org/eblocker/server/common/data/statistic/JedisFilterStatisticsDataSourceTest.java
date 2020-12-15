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
package org.eblocker.server.common.data.statistic;

import org.eblocker.server.common.TestRedisServer;
import org.eblocker.server.common.data.IpAddress;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class JedisFilterStatisticsDataSourceTest {

    private static TestRedisServer REDIS_SERVER;
    private static JedisPool JEDIS_POOL;

    private JedisFilterStatisticsDataSource filterStatisticsDataSource;

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
        filterStatisticsDataSource = new JedisFilterStatisticsDataSource(JEDIS_POOL);
        try (Jedis jedis = JEDIS_POOL.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            pipeline.set("dns_stats:201804100900:192.168.1.10:blocked_queries:", "1");
            pipeline.set("dns_stats:201804100900:192.168.1.10:blocked_queries:0", "2");
            pipeline.set("dns_stats:201804100900:192.168.1.10:blocked_queries:1", "3");
            pipeline.set("dns_stats:201804100900:192.168.1.10:queries", "10");
            pipeline.set("dns_stats:201804100901:fe80__192_168_1_11:blocked_queries:", "4");
            pipeline.set("dns_stats:201804100901:fe80__192_168_1_11:blocked_queries:0", "5");
            pipeline.set("dns_stats:201804100901:fe80__192_168_1_11:blocked_queries:1", "6");
            pipeline.set("dns_stats:201804100901:fe80__192_168_1_11:queries", "20");
            pipeline.set("dns_stats:201804100905:192.168.1.10:blocked_queries:", "7");
            pipeline.set("dns_stats:201804100905:192.168.1.10:blocked_queries:0", "8");
            pipeline.set("dns_stats:201804100905:192.168.1.10:blocked_queries:1", "9");
            pipeline.set("dns_stats:201804100905:192.168.1.10:queries", "30");
            pipeline.set("pattern_stats:201804100905:192.168.1.12:queries", "45");
            pipeline.set("pattern_stats:201804100905:192.168.1.12:blocked_queries:ADS", "44");
            pipeline.set("stats_total:dns:queries", "60");
            pipeline.set("stats_total:dns:blocked_queries:", "12");
            pipeline.set("stats_total:dns:blocked_queries:0", "15");
            pipeline.set("stats_total:dns:blocked_queries:1", "18");
            pipeline.set("stats_total:pattern:queries", "120");
            pipeline.set("stats_total:pattern:blocked_queries:", "24");
            pipeline.set("stats_total:pattern:blocked_queries:0", "30");
            pipeline.set("stats_total:pattern:blocked_queries:1", "36");
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
    public void getCounters() {
        List<StatisticsCounter> counters = filterStatisticsDataSource.getCounters().collect(Collectors.toList());
        Assert.assertNotNull(counters);
        Assert.assertEquals(14, counters.size());

        Comparator<StatisticsCounter> comparator = Comparator
                .comparing(StatisticsCounter::getInstant)
                .thenComparing(c -> c.getIpAddress().toString())
                .thenComparing(StatisticsCounter::getName)
                .thenComparing(StatisticsCounter::getReason, Comparator.nullsFirst(String::compareTo));
        Collections.sort(counters, comparator);

        assertDnsCounter(ZonedDateTime.of(2018, 4, 10, 9, 0, 0, 0, ZoneId.systemDefault()), "192.168.1.10", "blocked_queries", null, 1, counters.get(0));
        assertDnsCounter(ZonedDateTime.of(2018, 4, 10, 9, 0, 0, 0, ZoneId.systemDefault()), "192.168.1.10", "blocked_queries", "0", 2, counters.get(1));
        assertDnsCounter(ZonedDateTime.of(2018, 4, 10, 9, 0, 0, 0, ZoneId.systemDefault()), "192.168.1.10", "blocked_queries", "1", 3, counters.get(2));
        assertDnsCounter(ZonedDateTime.of(2018, 4, 10, 9, 0, 0, 0, ZoneId.systemDefault()), "192.168.1.10", "queries", null, 10, counters.get(3));
        assertDnsCounter(ZonedDateTime.of(2018, 4, 10, 9, 1, 0, 0, ZoneId.systemDefault()), "fe80::192:168:1:11", "blocked_queries", null, 4, counters.get(4));
        assertDnsCounter(ZonedDateTime.of(2018, 4, 10, 9, 1, 0, 0, ZoneId.systemDefault()), "fe80::192:168:1:11", "blocked_queries", "0", 5, counters.get(5));
        assertDnsCounter(ZonedDateTime.of(2018, 4, 10, 9, 1, 0, 0, ZoneId.systemDefault()), "fe80::192:168:1:11", "blocked_queries", "1", 6, counters.get(6));
        assertDnsCounter(ZonedDateTime.of(2018, 4, 10, 9, 1, 0, 0, ZoneId.systemDefault()), "fe80::192:168:1:11", "queries", null, 20, counters.get(7));
        assertDnsCounter(ZonedDateTime.of(2018, 4, 10, 9, 5, 0, 0, ZoneId.systemDefault()), "192.168.1.10", "blocked_queries", null, 7, counters.get(8));
        assertDnsCounter(ZonedDateTime.of(2018, 4, 10, 9, 5, 0, 0, ZoneId.systemDefault()), "192.168.1.10", "blocked_queries", "0", 8, counters.get(9));
        assertDnsCounter(ZonedDateTime.of(2018, 4, 10, 9, 5, 0, 0, ZoneId.systemDefault()), "192.168.1.10", "blocked_queries", "1", 9, counters.get(10));
        assertDnsCounter(ZonedDateTime.of(2018, 4, 10, 9, 5, 0, 0, ZoneId.systemDefault()), "192.168.1.10", "queries", null, 30, counters.get(11));
        assertDnsCounter(ZonedDateTime.of(2018, 4, 10, 9, 5, 0, 0, ZoneId.systemDefault()), "192.168.1.12", "blocked_queries", "ADS", 44, counters.get(12));
        assertDnsCounter(ZonedDateTime.of(2018, 4, 10, 9, 5, 0, 0, ZoneId.systemDefault()), "192.168.1.12", "queries", null, 45, counters.get(13));
    }

    @Test
    public void getCountersLimited() {
        List<StatisticsCounter> counters = filterStatisticsDataSource.getCounters("dns", IpAddress.parse("fe80::192:168:1:11"), null, null).collect(Collectors.toList());
        Assert.assertNotNull(counters);
        Assert.assertEquals(4, counters.size());

        Comparator<StatisticsCounter> comparator = Comparator
                .comparing(StatisticsCounter::getInstant)
                .thenComparing(c -> c.getIpAddress().toString())
                .thenComparing(StatisticsCounter::getName)
                .thenComparing(StatisticsCounter::getReason, Comparator.nullsFirst(String::compareTo));
        Collections.sort(counters, comparator);

        assertDnsCounter(ZonedDateTime.of(2018, 4, 10, 9, 1, 0, 0, ZoneId.systemDefault()), "fe80::192:168:1:11", "blocked_queries", null, 4, counters.get(0));
        assertDnsCounter(ZonedDateTime.of(2018, 4, 10, 9, 1, 0, 0, ZoneId.systemDefault()), "fe80::192:168:1:11", "blocked_queries", "0", 5, counters.get(1));
        assertDnsCounter(ZonedDateTime.of(2018, 4, 10, 9, 1, 0, 0, ZoneId.systemDefault()), "fe80::192:168:1:11", "blocked_queries", "1", 6, counters.get(2));
        assertDnsCounter(ZonedDateTime.of(2018, 4, 10, 9, 1, 0, 0, ZoneId.systemDefault()), "fe80::192:168:1:11", "queries", null, 20, counters.get(3));
    }

    @Test
    public void incrementCounters() {
        List<StatisticsCounter> counters = Arrays.asList(
                new StatisticsCounter(ZonedDateTime.of(2018, 4, 10, 9, 5, 0, 0, ZoneId.systemDefault()).toInstant(), "dns", IpAddress.parse("192.168.1.15"), "blocked_queries", "ads", 5),
                new StatisticsCounter(ZonedDateTime.of(2018, 4, 10, 9, 5, 10, 0, ZoneId.systemDefault()).toInstant(), "dns", IpAddress.parse("192.168.1.15"), "blocked_queries", "ads", 1),
                new StatisticsCounter(ZonedDateTime.of(2018, 4, 10, 9, 5, 10, 0, ZoneId.systemDefault()).toInstant(), "pattern", IpAddress.parse("192.168.1.15"), "blocked_queries", "ads", 3),
                new StatisticsCounter(ZonedDateTime.of(2018, 4, 10, 9, 6, 0, 0, ZoneId.systemDefault()).toInstant(), "dns", IpAddress.parse("192.168.1.15"), "queries", null, 1),
                new StatisticsCounter(ZonedDateTime.of(2018, 4, 10, 9, 7, 0, 0, ZoneId.systemDefault()).toInstant(), "pattern", IpAddress.parse("192.168.1.15"), "queries", null, 1)
        );
        filterStatisticsDataSource.incrementCounters(counters);

        try (Jedis jedis = JEDIS_POOL.getResource()) {
            Assert.assertEquals("6", jedis.get("dns_stats:201804100905:192.168.1.15:blocked_queries:ads"));
            Assert.assertEquals("3", jedis.get("pattern_stats:201804100905:192.168.1.15:blocked_queries:ads"));
            Assert.assertEquals("1", jedis.get("dns_stats:201804100906:192.168.1.15:queries"));
            Assert.assertEquals("1", jedis.get("pattern_stats:201804100907:192.168.1.15:queries"));
        }
    }

    @Test
    public void deleteCountersBefore() {
        filterStatisticsDataSource.deleteCountersBefore(ZonedDateTime.of(2018, 4, 10, 9, 5, 0, 0, ZoneId.systemDefault()).toInstant());

        List<StatisticsCounter> counters = filterStatisticsDataSource.getCounters().collect(Collectors.toList());
        Assert.assertNotNull(counters);
        Assert.assertEquals(6, counters.size());
        Assert.assertEquals(ZonedDateTime.of(2018, 4, 10, 9, 5, 0, 0, ZoneId.systemDefault()).toInstant(), counters.get(0).getInstant());
        Assert.assertEquals(ZonedDateTime.of(2018, 4, 10, 9, 5, 0, 0, ZoneId.systemDefault()).toInstant(), counters.get(1).getInstant());
        Assert.assertEquals(ZonedDateTime.of(2018, 4, 10, 9, 5, 0, 0, ZoneId.systemDefault()).toInstant(), counters.get(2).getInstant());
        Assert.assertEquals(ZonedDateTime.of(2018, 4, 10, 9, 5, 0, 0, ZoneId.systemDefault()).toInstant(), counters.get(3).getInstant());
        Assert.assertEquals(ZonedDateTime.of(2018, 4, 10, 9, 5, 0, 0, ZoneId.systemDefault()).toInstant(), counters.get(4).getInstant());
        Assert.assertEquals(ZonedDateTime.of(2018, 4, 10, 9, 5, 0, 0, ZoneId.systemDefault()).toInstant(), counters.get(5).getInstant());
    }

    @Test
    public void getTotalCounters() {
        List<TotalCounter> counters = filterStatisticsDataSource.getTotalCounters("dns");
        Assert.assertNotNull(counters);
        Assert.assertEquals(4, counters.size());

        Comparator<TotalCounter> comparator = Comparator
                .comparing(TotalCounter::getType)
                .thenComparing(TotalCounter::getName)
                .thenComparing(TotalCounter::getReason, Comparator.nullsFirst(String::compareTo));
        counters.sort(comparator);

        assertTotalCounter("dns", "blocked_queries", null, 12, counters.get(0));
        assertTotalCounter("dns", "blocked_queries", "0", 15, counters.get(1));
        assertTotalCounter("dns", "blocked_queries", "1", 18, counters.get(2));
        assertTotalCounter("dns", "queries", null, 60, counters.get(3));

        counters = filterStatisticsDataSource.getTotalCounters("pattern");
        Assert.assertNotNull(counters);
        Assert.assertEquals(4, counters.size());

        counters.sort(comparator);

        assertTotalCounter("pattern", "blocked_queries", null, 24, counters.get(0));
        assertTotalCounter("pattern", "blocked_queries", "0", 30, counters.get(1));
        assertTotalCounter("pattern", "blocked_queries", "1", 36, counters.get(2));
        assertTotalCounter("pattern", "queries", null, 120, counters.get(3));

        counters = filterStatisticsDataSource.getTotalCounters(null);
        Assert.assertNotNull(counters);
        Assert.assertEquals(8, counters.size());

        counters.sort(comparator);

        assertTotalCounter("dns", "blocked_queries", null, 12, counters.get(0));
        assertTotalCounter("dns", "blocked_queries", "0", 15, counters.get(1));
        assertTotalCounter("dns", "blocked_queries", "1", 18, counters.get(2));
        assertTotalCounter("dns", "queries", null, 60, counters.get(3));
        assertTotalCounter("pattern", "blocked_queries", null, 24, counters.get(4));
        assertTotalCounter("pattern", "blocked_queries", "0", 30, counters.get(5));
        assertTotalCounter("pattern", "blocked_queries", "1", 36, counters.get(6));
        assertTotalCounter("pattern", "queries", null, 120, counters.get(7));
    }

    @Test
    public void incrementTotalCounters() {
        List<TotalCounter> counters = Arrays.asList(
                new TotalCounter("dns", "blocked_queries", "ads", 5),
                new TotalCounter("dns", "blocked_queries", "ads", 1),
                new TotalCounter("dns", "blocked_queries", "ads", 3),
                new TotalCounter("dns", "queries", null, 1),
                new TotalCounter("dns", "queries", null, 1)
        );
        filterStatisticsDataSource.incrementTotalCounters(counters);

        try (Jedis jedis = JEDIS_POOL.getResource()) {
            Assert.assertEquals("9", jedis.get("stats_total:dns:blocked_queries:ads"));
            Assert.assertEquals("62", jedis.get("stats_total:dns:queries"));
        }
    }

    @Test
    public void getLastResetTotalCounters() {
        Assert.assertNull(filterStatisticsDataSource.getLastResetTotalCounters());
        Instant reset = Instant.now();
        try (Jedis jedis = JEDIS_POOL.getResource()) {
            jedis.set("stats_total_reset", String.valueOf(reset.toEpochMilli()));
        }
        Assert.assertEquals(reset.toEpochMilli(), filterStatisticsDataSource.getLastResetTotalCounters().toEpochMilli());
    }

    @Test
    public void resetTotalCounters() {
        Assert.assertNull(filterStatisticsDataSource.getLastResetTotalCounters());
        List<TotalCounter> counters = filterStatisticsDataSource.getTotalCounters(null);
        Assert.assertNotNull(counters);
        Assert.assertEquals(8, counters.size());

        filterStatisticsDataSource.resetTotalCounters();

        Assert.assertTrue(Duration.between(Instant.now(), filterStatisticsDataSource.getLastResetTotalCounters()).toMinutes() < 1);
        counters = filterStatisticsDataSource.getTotalCounters(null);
        Assert.assertNotNull(counters);
        Assert.assertEquals(0, counters.size());

        try (Jedis jedis = JEDIS_POOL.getResource()) {
            Assert.assertEquals(0, jedis.keys("stats_total:*").size());
            Assert.assertEquals(Long.parseLong(jedis.get("stats_total_reset")), filterStatisticsDataSource.getLastResetTotalCounters().toEpochMilli());
        }
    }

    private void assertDnsCounter(ZonedDateTime zdt, String ipAddress, String name, String reason, int value, StatisticsCounter counter) {
        Assert.assertEquals(zdt.toInstant(), counter.getInstant());
        Assert.assertEquals(IpAddress.parse(ipAddress), counter.getIpAddress());
        Assert.assertEquals(name, counter.getName());
        Assert.assertEquals(reason, counter.getReason());
        Assert.assertEquals(value, counter.getValue());
    }

    private void assertTotalCounter(String type, String name, String reason, int value, TotalCounter counter) {
        Assert.assertEquals(type, counter.getType());
        Assert.assertEquals(name, counter.getName());
        Assert.assertEquals(reason, counter.getReason());
        Assert.assertEquals(value, counter.getValue());
    }

}
