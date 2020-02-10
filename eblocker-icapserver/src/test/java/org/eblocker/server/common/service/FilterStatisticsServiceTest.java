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
package org.eblocker.server.common.service;

import org.eblocker.server.common.data.FilterStats;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.eblocker.server.common.data.statistic.FilterStatisticsDataSource;
import org.eblocker.server.common.data.statistic.StatisticsCounter;
import org.eblocker.server.common.data.statistic.TotalCounter;
import org.eblocker.server.http.service.ParentalControlFilterListsService;
import org.eblocker.server.http.service.TestClock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FilterStatisticsServiceTest {

    private static final int MAX_AGE_DAYS = 3;

    private TestClock clock;
    private ParentalControlFilterListsService filterListsService;
    private FilterStatisticsDataSource filterStatisticsDataSource;
    private FilterStatisticsService filterStatisticsService;

    @Before
    public void setUp() {
        clock = new TestClock(ZonedDateTime.now());

        filterListsService = Mockito.mock(ParentalControlFilterListsService.class);
        Mockito.when(filterListsService.getParentalControlFilterMetaData()).thenReturn(Arrays.asList(
            createFilterMetaData(1, Category.ADS),
            createFilterMetaData(2, Category.TRACKERS)
        ));

        filterStatisticsDataSource = Mockito.mock(FilterStatisticsDataSource.class);
        filterStatisticsService = new FilterStatisticsService(MAX_AGE_DAYS, clock, filterStatisticsDataSource, filterListsService);
        filterStatisticsService.init();
    }

    @Test
    public void testStatistics() {
        Instant start = instantOf(2018, 4, 10, 8, 0, 0, 0);
        Instant end = instantOf(2018, 4, 10, 10, 0, 0, 0);

        // setup counters
        List<StatisticsCounter> counters = new ArrayList<>();
        counters.add(new StatisticsCounter(instantOf(2018, 4, 10, 9, 0, 0, 0), "dns", IpAddress.parse("192.168.1.3"), "queries", null, 10));
        counters.add(new StatisticsCounter(instantOf(2018, 4, 10, 9, 0, 0, 0), "dns", IpAddress.parse("192.168.1.3"), "blocked_queries", null, 1));
        counters.add(new StatisticsCounter(instantOf(2018, 4, 10, 9, 0, 0, 0), "dns", IpAddress.parse("192.168.1.3"), "blocked_queries", "1", 2));
        counters.add(new StatisticsCounter(instantOf(2018, 4, 10, 9, 0, 0, 0), "dns", IpAddress.parse("192.168.1.3"), "blocked_queries", "2", 3));
        counters.add(new StatisticsCounter(instantOf(2018, 4, 10, 9, 1, 0, 0), "dns", IpAddress.parse("192.168.1.4"), "queries", null, 20));
        counters.add(new StatisticsCounter(instantOf(2018, 4, 10, 9, 1, 0, 0), "dns", IpAddress.parse("192.168.1.4"), "blocked_queries", null, 4));
        counters.add(new StatisticsCounter(instantOf(2018, 4, 10, 9, 1, 0, 0), "dns", IpAddress.parse("192.168.1.4"), "blocked_queries", "1", 5));
        counters.add(new StatisticsCounter(instantOf(2018, 4, 10, 9, 1, 0, 0), "dns", IpAddress.parse("192.168.1.4"), "blocked_queries", "2", 6));
        counters.add(new StatisticsCounter(instantOf(2018, 4, 10, 9, 5, 0, 0), "dns", IpAddress.parse("192.168.1.3"), "queries", null, 30));
        counters.add(new StatisticsCounter(instantOf(2018, 4, 10, 9, 5, 0, 0), "dns", IpAddress.parse("192.168.1.3"), "blocked_queries", null, 7));
        counters.add(new StatisticsCounter(instantOf(2018, 4, 10, 9, 5, 0, 0), "dns", IpAddress.parse("192.168.1.3"), "blocked_queries", "1", 8));
        counters.add(new StatisticsCounter(instantOf(2018, 4, 10, 9, 5, 0, 0), "dns", IpAddress.parse("192.168.1.3"), "blocked_queries", "2", 9));
        Mockito.when(filterStatisticsDataSource.getCounters("dns", null, start, end)).thenReturn(counters.stream());

        FilterStats stats = filterStatisticsService.getStatistics(start, end, 5, "dns", null);

        Assert.assertEquals(instantOf(2018, 4, 10, 8, 0, 0, 0), stats.getBegin());
        Assert.assertEquals(instantOf(2018, 4, 10, 10, 0, 0, 0), stats.getEnd());
        Assert.assertNotNull(stats.getSummary());
        Assert.assertEquals(60, stats.getSummary().getQueries());
        Assert.assertEquals(45, stats.getSummary().getBlockedQueries());
        Assert.assertEquals(Integer.valueOf(12), stats.getSummary().getBlockedQueriesByReason().get(FilterStats.Category.PARENTAL_CONTROL));
        Assert.assertEquals(Integer.valueOf(15), stats.getSummary().getBlockedQueriesByReason().get(FilterStats.Category.ADS));
        Assert.assertEquals(Integer.valueOf(18), stats.getSummary().getBlockedQueriesByReason().get(FilterStats.Category.TRACKERS));

        Assert.assertNotNull(stats.getBins());
        Assert.assertEquals(24, stats.getBins().size());
        Assert.assertEquals(instantOf(2018, 4, 10, 8, 0, 0, 0), stats.getBins().get(0).getBegin());
        Assert.assertEquals(instantOf(2018, 4, 10, 10, 0, 0, 0), stats.getBins().get(23).getEnd());

        Assert.assertEquals(instantOf(2018, 4, 10, 9, 0, 0, 0), stats.getBins().get(12).getBegin());
        Assert.assertEquals(instantOf(2018, 4, 10, 9, 5, 0, 0), stats.getBins().get(12).getEnd());
        Assert.assertEquals(30, stats.getBins().get(12).getQueries());
        Assert.assertEquals(21, stats.getBins().get(12).getBlockedQueries());
        Assert.assertEquals(Integer.valueOf(5), stats.getBins().get(12).getBlockedQueriesByReason().get(FilterStats.Category.PARENTAL_CONTROL));
        Assert.assertEquals(Integer.valueOf(7), stats.getBins().get(12).getBlockedQueriesByReason().get(FilterStats.Category.ADS));
        Assert.assertEquals(Integer.valueOf(9), stats.getBins().get(12).getBlockedQueriesByReason().get(FilterStats.Category.TRACKERS));

        Assert.assertEquals(instantOf(2018, 4, 10, 9, 5, 0, 0), stats.getBins().get(13).getBegin());
        Assert.assertEquals(instantOf(2018, 4, 10, 9, 10, 0, 0), stats.getBins().get(13).getEnd());
        Assert.assertEquals(30, stats.getBins().get(13).getQueries());
        Assert.assertEquals(24, stats.getBins().get(13).getBlockedQueries());
        Assert.assertEquals(Integer.valueOf(7), stats.getBins().get(13).getBlockedQueriesByReason().get(FilterStats.Category.PARENTAL_CONTROL));
        Assert.assertEquals(Integer.valueOf(8), stats.getBins().get(13).getBlockedQueriesByReason().get(FilterStats.Category.ADS));
        Assert.assertEquals(Integer.valueOf(9), stats.getBins().get(13).getBlockedQueriesByReason().get(FilterStats.Category.TRACKERS));
    }

    @Test
    public void testCountingAndUpdate() {
        clock.setInstant(instantOf(2018, 5, 22, 13, 30, 0, 0));
        filterStatisticsService.countQuery("dns", IpAddress.parse("10.10.10.99"));
        clock.setInstant(instantOf(2018, 5, 22, 13, 31, 0, 0));
        filterStatisticsService.countQuery("dns", IpAddress.parse("10.10.10.99"));
        filterStatisticsService.countBlocked("dns", IpAddress.parse("10.10.10.99"), "ADS");

        filterStatisticsService.updateCounters();

        ArgumentCaptor<List<StatisticsCounter>> statsCounter = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<TotalCounter>> totalCounter = ArgumentCaptor.forClass(List.class);
        Mockito.verify(filterStatisticsDataSource).incrementCounters(statsCounter.capture());
        Mockito.verify(filterStatisticsDataSource).incrementTotalCounters(totalCounter.capture());
        Assert.assertEquals(3, statsCounter.getValue().size());
        Assert.assertEquals(instantOf(2018, 5, 22, 13, 30, 0, 0), statsCounter.getValue().get(0).getInstant());
        Assert.assertEquals("dns", statsCounter.getValue().get(0).getType());
        Assert.assertEquals(IpAddress.parse("10.10.10.99"), statsCounter.getValue().get(0).getIpAddress());
        Assert.assertEquals("queries", statsCounter.getValue().get(0).getName());
        Assert.assertNull(statsCounter.getValue().get(0).getReason());
        Assert.assertEquals(1, statsCounter.getValue().get(0).getValue());
        Assert.assertEquals(3, totalCounter.getValue().size());
        Assert.assertEquals("queries", totalCounter.getValue().get(0).getName());
        Assert.assertNull(totalCounter.getValue().get(0).getReason());
        Assert.assertEquals(1, totalCounter.getValue().get(0).getValue());

        Assert.assertEquals(instantOf(2018, 5, 22, 13, 31, 0, 0), statsCounter.getValue().get(1).getInstant());
        Assert.assertEquals("dns", statsCounter.getValue().get(1).getType());
        Assert.assertEquals(IpAddress.parse("10.10.10.99"), statsCounter.getValue().get(1).getIpAddress());
        Assert.assertEquals("queries", statsCounter.getValue().get(1).getName());
        Assert.assertNull(statsCounter.getValue().get(1).getReason());
        Assert.assertEquals(1, statsCounter.getValue().get(1).getValue());
        Assert.assertEquals(3, totalCounter.getValue().size());
        Assert.assertEquals("queries", totalCounter.getValue().get(1).getName());
        Assert.assertNull(totalCounter.getValue().get(1).getReason());
        Assert.assertEquals(1, totalCounter.getValue().get(1).getValue());


        Assert.assertEquals(instantOf(2018, 5, 22, 13, 31, 0, 0), statsCounter.getValue().get(2).getInstant());
        Assert.assertEquals("dns", statsCounter.getValue().get(2).getType());
        Assert.assertEquals(IpAddress.parse("10.10.10.99"), statsCounter.getValue().get(2).getIpAddress());
        Assert.assertEquals("blocked_queries", statsCounter.getValue().get(2).getName());
        Assert.assertEquals("ADS", statsCounter.getValue().get(2).getReason());
        Assert.assertEquals(1, statsCounter.getValue().get(2).getValue());
        Assert.assertEquals("blocked_queries", totalCounter.getValue().get(2).getName());
        Assert.assertEquals("ADS", totalCounter.getValue().get(2).getReason());
        Assert.assertEquals(1, totalCounter.getValue().get(2).getValue());
    }

    @Test
    public void testDeleteOldCounters() {
        clock.setInstant(instantOf(2018, 5, 22, 13, 0, 0, 0));
        filterStatisticsService.deleteOldCounters();
        filterStatisticsDataSource.deleteCountersBefore(instantOf(2018, 5, 19, 0, 0, 0, 0));
    }

    @Test
    public void testGetTotalStatisticsSingleType() {
        // setup counters
        List<TotalCounter> counters = new ArrayList<>();
        counters.add(new TotalCounter("dns", "queries", null, 10));
        counters.add(new TotalCounter("dns", "blocked_queries", null, 1));
        counters.add(new TotalCounter("dns", "blocked_queries", "1", 2));
        counters.add(new TotalCounter("dns", "blocked_queries", "2", 3));
        Mockito.when(filterStatisticsDataSource.getTotalCounters("dns")).thenReturn(counters);

        Instant lastReset = instantOf(2018, 6, 1, 0, 0, 0, 0);
        Mockito.when(filterStatisticsDataSource.getLastResetTotalCounters()).thenReturn(lastReset);

        FilterStats stats = filterStatisticsService.getTotalStatistics("dns");
        Assert.assertNotNull(stats);
        Assert.assertEquals(lastReset, stats.getBegin());
        Assert.assertEquals(clock.instant(), stats.getEnd());
        Assert.assertNotNull(stats.getSummary());
        Assert.assertNotNull(stats.getSummary().getBlockedQueriesByReason());
        Assert.assertEquals(10, stats.getSummary().getQueries());
        Assert.assertEquals(6, stats.getSummary().getBlockedQueries());
        Assert.assertEquals(Integer.valueOf(1), stats.getSummary().getBlockedQueriesByReason().get(FilterStats.Category.PARENTAL_CONTROL));
        Assert.assertEquals(Integer.valueOf(2), stats.getSummary().getBlockedQueriesByReason().get(FilterStats.Category.ADS));
        Assert.assertEquals(Integer.valueOf(3), stats.getSummary().getBlockedQueriesByReason().get(FilterStats.Category.TRACKERS));
    }

    @Test
    public void testGetTotalStatisticsMultipleTypes() {
        // setup counters
        List<TotalCounter> counters = new ArrayList<>();
        counters.add(new TotalCounter("dns", "queries", null, 10));
        counters.add(new TotalCounter("dns", "blocked_queries", null, 1));
        counters.add(new TotalCounter("dns", "blocked_queries", "1", 2));
        counters.add(new TotalCounter("dns", "blocked_queries", "2", 3));
        counters.add(new TotalCounter("pattern", "queries", null, 20));
        counters.add(new TotalCounter("pattern", "blocked_queries", null, 2));
        counters.add(new TotalCounter("pattern", "blocked_queries", "1", 4));
        counters.add(new TotalCounter("pattern", "blocked_queries", "2", 6));
        Mockito.when(filterStatisticsDataSource.getTotalCounters(null)).thenReturn(counters);

        Instant lastReset = instantOf(2018, 6, 1, 0, 0, 0, 0);
        Mockito.when(filterStatisticsDataSource.getLastResetTotalCounters()).thenReturn(lastReset);

        FilterStats stats = filterStatisticsService.getTotalStatistics(null);
        Assert.assertNotNull(stats);
        Assert.assertEquals(lastReset, stats.getBegin());
        Assert.assertEquals(clock.instant(), stats.getEnd());
        Assert.assertNotNull(stats.getSummary());
        Assert.assertNotNull(stats.getSummary().getBlockedQueriesByReason());
        Assert.assertEquals(30, stats.getSummary().getQueries());
        Assert.assertEquals(18, stats.getSummary().getBlockedQueries());
        Assert.assertEquals(Integer.valueOf(3), stats.getSummary().getBlockedQueriesByReason().get(FilterStats.Category.PARENTAL_CONTROL));
        Assert.assertEquals(Integer.valueOf(6), stats.getSummary().getBlockedQueriesByReason().get(FilterStats.Category.ADS));
        Assert.assertEquals(Integer.valueOf(9), stats.getSummary().getBlockedQueriesByReason().get(FilterStats.Category.TRACKERS));
    }

    @Test
    public void testResetTotalStatistics() {
        filterStatisticsService.resetTotalStatistics();
        Mockito.verify(filterStatisticsDataSource, Mockito.times(2)).resetTotalCounters(); // including reset on init
    }

    @Test
    public void testInitTotalStatisticsReset() {
        // init is already called in setup
        Mockito.verify(filterStatisticsDataSource).resetTotalCounters();

        // re-init and check reset has not been executed again
        Mockito.when(filterStatisticsDataSource.getLastResetTotalCounters()).thenReturn(Instant.now());
        filterStatisticsService.init();
        Mockito.verify(filterStatisticsDataSource, Mockito.times(1)).resetTotalCounters();
    }

    private Instant instantOf(int year, int month, int dayOfMonth, int hour, int minute, int second, int nanoOfSecond) {
        return ZonedDateTime.of(year, month, dayOfMonth, hour, minute, second, nanoOfSecond, ZoneId.systemDefault()).toInstant();
    }

    private ParentalControlFilterMetaData createFilterMetaData(int id, Category category) {
        return new ParentalControlFilterMetaData(id, null, null, category, null, null, null, null, null, false, false, null, null, null);
    }

}
