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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.FilterStats;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.data.statistic.FilterStatisticsDataSource;
import org.eblocker.server.common.data.statistic.StatisticsCounter;
import org.eblocker.server.common.data.statistic.TotalCounter;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.http.service.ParentalControlFilterListsService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

@Singleton
@SubSystemService(SubSystem.SERVICES)
public class FilterStatisticsService {

    private final int maxAgeDays;
    private final Clock clock;
    private final FilterStatisticsDataSource statisticsDataSource;
    private final ParentalControlFilterListsService filterListsService;
    private final Queue<StatisticsCounter> counters = new ConcurrentLinkedQueue<>();

    @Inject
    public FilterStatisticsService(@Named("filter.stats.days") int maxAgeDays,
                                   Clock clock,
                                   FilterStatisticsDataSource statisticsDataSource,
                                   ParentalControlFilterListsService filterListsService) {
        this.maxAgeDays = maxAgeDays;
        this.clock = clock;
        this.filterListsService = filterListsService;
        this.statisticsDataSource = statisticsDataSource;
    }

    @SubSystemInit
    public void init() {
        if (statisticsDataSource.getLastResetTotalCounters() == null) {
            statisticsDataSource.resetTotalCounters();
        }
    }

    public void countQuery(String protocol, IpAddress ipAddress) {
        counters.add(new StatisticsCounter(clock.instant(), protocol, ipAddress, "queries", null, 1));
    }

    public void countBlocked(String protocol, IpAddress ipAddress, String reason) {
        counters.add(new StatisticsCounter(clock.instant(), protocol, ipAddress, "blocked_queries", reason, 1));
    }

    public FilterStats getStatistics(Instant start, Instant end, int binSizeMinutes, String type, List<IpAddress> ipAddresses) {
        Map<String, FilterStats.Category> categoryByReason = createCategoryByReasonsMap();

        Duration duration = Duration.between(start, end);
        int minutes = (int) duration.toMinutes();
        int numberOfBins = minutes / binSizeMinutes + (minutes % binSizeMinutes != 0 ? 1 : 0);

        List<FilterStats.Bin> bins = new ArrayList<>(numberOfBins);
        for (int i = 0; i < numberOfBins; ++i) {
            Instant startBin = start.plus(i * binSizeMinutes, ChronoUnit.MINUTES);
            Instant stopBin = start.plus((i + 1) * binSizeMinutes, ChronoUnit.MINUTES);
            bins.add(new FilterStats.Bin(startBin, stopBin));
        }

        if (ipAddresses == null) {
            updateBins(bins, start, end, binSizeMinutes, categoryByReason, type, null);
        } else {
            for (IpAddress ipAddress : ipAddresses) {
                updateBins(bins, start, end, binSizeMinutes, categoryByReason, type, ipAddress);
            }
        }

        FilterStats.Bin summary = new FilterStats.Bin(start, end);
        for (FilterStats.Bin bin : bins) {
            summary.setQueries(summary.getQueries() + bin.getQueries());
            summary.setBlockedQueries(summary.getBlockedQueries() + bin.getBlockedQueries());
            bin.getBlockedQueriesByReason().forEach((k, v) -> summary.getBlockedQueriesByReason().merge(k, v, (a, b) -> a + b));
        }

        return new FilterStats(start, end, summary, bins);
    }

    private void updateBins(List<FilterStats.Bin> bins, Instant start, Instant end, int binSizeMinutes, Map<String, FilterStats.Category> categoryByReason, String type, IpAddress ipAddress) {
        try (Stream<StatisticsCounter> storedCounters = statisticsDataSource.getCounters(type, ipAddress, start, end)) {
            storedCounters
                    .forEach(counter -> {
                        int i = (int) Duration.between(start, counter.getInstant()).toMinutes() / binSizeMinutes;
                        FilterStats.Bin bin = bins.get(i);
                        if ("queries".equals(counter.getName())) {
                            bin.setQueries(bin.getQueries() + counter.getValue());
                        } else {
                            bin.setBlockedQueries(bin.getBlockedQueries() + counter.getValue());
                            bin.getBlockedQueriesByReason().merge(
                                    categoryByReason.getOrDefault(counter.getReason(), FilterStats.Category.UNKNOWN),
                                    counter.getValue(),
                                    (a, b) -> a + b);
                        }
                    });
        }
    }

    public void deleteOldCounters() {
        Instant t = LocalDate.now(clock).atStartOfDay(ZoneId.systemDefault()).minusDays(maxAgeDays).toInstant();
        statisticsDataSource.deleteCountersBefore(t);
    }

    public void updateCounters() {
        List<StatisticsCounter> updateCounters = new ArrayList<>();
        List<TotalCounter> totalCounters = new ArrayList<>();
        StatisticsCounter counter;
        while ((counter = counters.poll()) != null) {
            updateCounters.add(counter);
            totalCounters.add(new TotalCounter(counter.getType(), counter.getName(), counter.getReason(), counter.getValue()));
        }
        statisticsDataSource.incrementCounters(updateCounters);
        statisticsDataSource.incrementTotalCounters(totalCounters);
    }

    public FilterStats getTotalStatistics(String type) {
        Map<String, FilterStats.Category> categoryByReason = createCategoryByReasonsMap();
        Instant lastReset = statisticsDataSource.getLastResetTotalCounters();
        Instant now = clock.instant();

        FilterStats.Bin summary = new FilterStats.Bin(lastReset, now);
        statisticsDataSource.getTotalCounters(type).forEach(counter -> {
            if ("queries".equals(counter.getName())) {
                summary.setQueries(summary.getQueries() + counter.getValue());
            } else {
                summary.setBlockedQueries(summary.getBlockedQueries() + counter.getValue());
                summary.getBlockedQueriesByReason().merge(
                        categoryByReason.getOrDefault(counter.getReason(), FilterStats.Category.UNKNOWN),
                        counter.getValue(),
                        (a, b) -> a + b);
            }
        });

        return new FilterStats(lastReset, now, summary, Collections.singletonList(summary));
    }

    public void resetTotalStatistics() {
        statisticsDataSource.resetTotalCounters();
    }

    private Map<String, FilterStats.Category> createCategoryByReasonsMap() {
        Map<String, FilterStats.Category> categoryByReason = new HashMap<>();
        filterListsService.getParentalControlFilterMetaData().stream()
                .forEach(metaData -> categoryByReason.put(
                        metaData.getId().toString(),
                        mapFilterlistCategory(metaData.getCategory())));
        for (FilterStats.Category category : FilterStats.Category.values()) {
            categoryByReason.put(category.name(), category);
        }
        categoryByReason.put(null, FilterStats.Category.PARENTAL_CONTROL);
        categoryByReason.put("", FilterStats.Category.PARENTAL_CONTROL);
        return categoryByReason;
    }

    private FilterStats.Category mapFilterlistCategory(Category category) {
        if (category == null) {
            return FilterStats.Category.UNKNOWN;
        }
        switch (category) {
            case ADS:
                return FilterStats.Category.ADS;
            case CUSTOM:
                return FilterStats.Category.CUSTOM;
            case TRACKERS:
                return FilterStats.Category.TRACKERS;
            case PARENTAL_CONTROL:
                return FilterStats.Category.PARENTAL_CONTROL;
            case MALWARE:
                return FilterStats.Category.MALWARE;
            default:
                return FilterStats.Category.UNKNOWN;
        }
    }
}
