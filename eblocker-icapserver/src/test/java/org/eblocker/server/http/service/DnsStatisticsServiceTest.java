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
package org.eblocker.server.http.service;

import org.eblocker.server.common.TestClock;
import org.eblocker.server.common.data.dns.DnsDataSource;
import org.eblocker.server.common.data.dns.DnsDataSourceDnsResponse;
import org.eblocker.server.common.data.dns.DnsRating;
import org.eblocker.server.common.data.dns.DnsReliabilityRating;
import org.eblocker.server.common.data.dns.DnsResponse;
import org.eblocker.server.common.data.dns.DnsResponseTimeRating;
import org.eblocker.server.common.data.dns.NameServerStats;
import org.eblocker.server.common.data.dns.ResolverEvent;
import org.eblocker.server.common.data.dns.ResolverStats;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class DnsStatisticsServiceTest {

    private static final int DAYS = 3;

    private TestClock testClock;
    private DnsDataSource dnsDataSource;
    private DnsStatisticsService dnsStatisticsService;

    @Before
    public void setUp() {
        testClock = new TestClock(ZonedDateTime.now());
        dnsDataSource = Mockito.mock(DnsDataSource.class);
        dnsStatisticsService = new DnsStatisticsService(DAYS, testClock, dnsDataSource);
    }

    @Test
    public void testDeletingOldEvents() {
        dnsStatisticsService.update();

        Instant t = LocalDate.now(testClock).atStartOfDay(ZoneId.systemDefault()).minus(DAYS, ChronoUnit.DAYS).toInstant();
        Mockito.verify(dnsDataSource).deleteEventsBefore(t);
    }

    @Test
    public void testResolverStatistics() {
        // setup events
        List<ResolverEvent> events = new ArrayList<>();
        events.add(new ResolverEvent(instantOf(2017, 11, 20, 12, 0, 0, 0), "8.8.8.8", "valid", 10L));
        events.add(new ResolverEvent(instantOf(2017, 11, 20, 12, 1, 0, 0), "8.8.8.8", "valid", 20L));
        events.add(new ResolverEvent(instantOf(2017, 11, 20, 12, 2, 0, 0), "8.8.8.8", "valid", 30L));
        events.add(new ResolverEvent(instantOf(2017, 11, 20, 12, 3, 0, 0), "8.8.8.8", "valid", 60L));
        events.add(new ResolverEvent(instantOf(2017, 11, 20, 12, 4, 0, 0), "8.8.8.8", "invalid", null));
        events.add(new ResolverEvent(instantOf(2017, 11, 20, 12, 5, 0, 0), "8.8.8.8", "invalid", null));
        events.add(new ResolverEvent(instantOf(2017, 11, 20, 12, 6, 0, 0), "8.8.8.8", "invalid", null));
        events.add(new ResolverEvent(instantOf(2017, 11, 20, 12, 7, 0, 0), "8.8.8.8", "timeout", null));
        events.add(new ResolverEvent(instantOf(2017, 11, 20, 12, 8, 0, 0), "8.8.8.8", "timeout", null));
        events.add(new ResolverEvent(instantOf(2017, 11, 20, 12, 9, 0, 0), "8.8.8.8", "error", null));
        events.add(new ResolverEvent(instantOf(2017, 11, 20, 12, 10, 0, 0), "8.8.4.4", "valid", 20L));
        events.add(new ResolverEvent(instantOf(2017, 11, 20, 12, 11, 0, 0), "9.9.9.9", "valid", 30L));
        events.add(new ResolverEvent(instantOf(2017, 11, 20, 12, 12, 0, 0), "10.10.10.10", "timeout", null));
        events.add(new ResolverEvent(instantOf(2017, 11, 20, 12, 13, 0, 0), "10.10.10.10", "timeout", null));
        Mockito.when(dnsDataSource.getEventsByResolver("test")).thenReturn(events);

        testClock.setInstant(instantOf(2017, 11, 20, 12, 12, 0, 0));

        // retrieve stats with 5-minute interval
        List<ResolverStats> stats = dnsStatisticsService.getResolverStatistics("test", instantOf(2017, 11, 20, 12, 0, 0, 0).minus(DAYS, ChronoUnit.DAYS), 5, ChronoUnit.MINUTES);

        // check stats size
        Assert.assertEquals(DAYS * 24 * 12 + 3, stats.size());

        // check first interval
        Assert.assertEquals(instantOf(2017, 11, 17, 12, 0, 0, 0), stats.get(0).getFrom());
        Assert.assertEquals(instantOf(2017, 11, 17, 12, 5, 0, 0), stats.get(0).getTo());
        Assert.assertTrue(stats.get(0).getNameServerStats().isEmpty());

        // check last interval
        int len = stats.size();
        Assert.assertEquals(instantOf(2017, 11, 20, 12, 10, 0, 0), stats.get(len - 1).getFrom());
        Assert.assertEquals(instantOf(2017, 11, 20, 12, 15, 0, 0), stats.get(len - 1).getTo());

        // check created stats (first interval with data)
        ResolverStats resolverStats = stats.get(len - 3);
        Assert.assertEquals(instantOf(2017, 11, 20, 12, 0, 0, 0), resolverStats.getFrom());
        Assert.assertEquals(instantOf(2017, 11, 20, 12, 5, 0, 0), resolverStats.getTo());
        Assert.assertEquals(1, resolverStats.getNameServerStats().size());
        NameServerStats nameServerStats = resolverStats.getNameServerStats().get(0);
        assertNameServerStat(4, 1, 0, 0, nameServerStats);
        Assert.assertEquals(10L, nameServerStats.getResponseTimeMin());
        Assert.assertEquals(60L, nameServerStats.getResponseTimeMax());
        Assert.assertEquals(30L, nameServerStats.getResponseTimeAverage());
        Assert.assertEquals(25L, nameServerStats.getResponseTimeMedian());

        // check created stats (second interval with data)
        resolverStats = stats.get(len - 2);
        Assert.assertEquals(instantOf(2017, 11, 20, 12, 5, 0, 0), resolverStats.getFrom());
        Assert.assertEquals(instantOf(2017, 11, 20, 12, 10, 0, 0), resolverStats.getTo());
        Assert.assertEquals(1, resolverStats.getNameServerStats().size());
        nameServerStats = resolverStats.getNameServerStats().get(0);
        assertNameServerStat(0, 2, 2, 1, nameServerStats);
        Assert.assertEquals(0, nameServerStats.getResponseTimeMin());
        Assert.assertEquals(0, nameServerStats.getResponseTimeMax());
        Assert.assertEquals(0, nameServerStats.getResponseTimeAverage());
        Assert.assertEquals(0, nameServerStats.getResponseTimeMedian());

        // check created stats (third interval with data)
        resolverStats = stats.get(len - 1);
        Assert.assertEquals(instantOf(2017, 11, 20, 12, 10, 0, 0), resolverStats.getFrom());
        Assert.assertEquals(instantOf(2017, 11, 20, 12, 15, 0, 0), resolverStats.getTo());
        Assert.assertEquals(3, resolverStats.getNameServerStats().size());

        Map<String, NameServerStats> nameServerStatsByNameServer = resolverStats.getNameServerStats().stream().collect(Collectors.toMap(NameServerStats::getNameServer, Function.identity()));
        nameServerStats = nameServerStatsByNameServer.get("8.8.4.4");
        assertNameServerStat(1, 0, 0, 0, nameServerStats);
        Assert.assertEquals(20, nameServerStats.getResponseTimeMin());
        Assert.assertEquals(20, nameServerStats.getResponseTimeMax());
        Assert.assertEquals(20, nameServerStats.getResponseTimeAverage());
        Assert.assertEquals(20, nameServerStats.getResponseTimeMedian());
        nameServerStats = nameServerStatsByNameServer.get("9.9.9.9");
        assertNameServerStat(1, 0, 0, 0, nameServerStats);
        Assert.assertEquals(30, nameServerStats.getResponseTimeMin());
        Assert.assertEquals(30, nameServerStats.getResponseTimeMax());
        Assert.assertEquals(30, nameServerStats.getResponseTimeAverage());
        Assert.assertEquals(30, nameServerStats.getResponseTimeMedian());

        //Test unavailable name server
        nameServerStats = nameServerStatsByNameServer.get("10.10.10.10");
        assertEquals(DnsRating.BAD, nameServerStats.getRating());
        assertEquals(DnsReliabilityRating.UNAVAILABLE, nameServerStats.getReliabilityRating());
        assertEquals(DnsResponseTimeRating.UNAVAILABLE, nameServerStats.getResponseTimeRating());
        assertNameServerStat(0, 0, 2, 0, nameServerStats);

    }

    @Test
    public void testTestNameServer() {
        DnsDataSourceDnsResponse response = new DnsDataSourceDnsResponse();
        response.setResponses(List.of(new DnsResponse("0,A,eblocker.org.,1.2.3.4")));
        response.setLog(List.of(new ResolverEvent("1706542836.8161058,192.168.0.1,valid,0.123")));
        Mockito.when(dnsDataSource.popDnsResolutionQueue(Mockito.anyString(), Mockito.eq(10))).thenReturn(response);

        NameServerStats stats = dnsStatisticsService.testNameServer("1.1.1.1", List.of("eblocker.org"));
        Assert.assertEquals(0, stats.getError());
        Assert.assertEquals(123, stats.getResponseTimeMedian());
    }

    @Test
    public void testTestNameServerNotRunning() {
        // DNS not running or timing out:
        Mockito.when(dnsDataSource.popDnsResolutionQueue(Mockito.anyString(), Mockito.eq(10))).thenReturn(null);

        Assert.assertNull(dnsStatisticsService.testNameServer("1.1.1.1", List.of("eblocker.org")));
    }

    private void assertNameServerStat(int valid, int invalid, int timeout, int error, NameServerStats stats) {
        Assert.assertEquals(valid, stats.getValid());
        Assert.assertEquals(invalid, stats.getInvalid());
        Assert.assertEquals(timeout, stats.getTimeout());
        Assert.assertEquals(error, stats.getError());
    }

    private Instant instantOf(int year, int month, int dayOfMonth, int hour, int minute, int second, int nanoOfSecond) {
        return ZonedDateTime.of(year, month, dayOfMonth, hour, minute, second, nanoOfSecond, ZoneId.systemDefault()).toInstant();
    }

}
