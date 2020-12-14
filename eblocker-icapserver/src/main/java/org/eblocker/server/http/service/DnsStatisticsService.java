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

import com.google.common.collect.SortedMultiset;
import com.google.common.collect.TreeMultiset;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.dns.DnsDataSource;
import org.eblocker.server.common.data.dns.DnsDataSourceDnsResponse;
import org.eblocker.server.common.data.dns.DnsQuery;
import org.eblocker.server.common.data.dns.DnsRating;
import org.eblocker.server.common.data.dns.DnsRecordType;
import org.eblocker.server.common.data.dns.DnsReliabilityRating;
import org.eblocker.server.common.data.dns.DnsResponseTimeRating;
import org.eblocker.server.common.data.dns.NameServerStats;
import org.eblocker.server.common.data.dns.ResolverEvent;
import org.eblocker.server.common.data.dns.ResolverStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.stream.Collectors;

@Singleton
public class DnsStatisticsService {
    private static final Logger log = LoggerFactory.getLogger(DnsStatisticsService.class);

    private final int days;
    private final Clock clock;
    private final DnsDataSource dnsDataSource;

    @Inject
    public DnsStatisticsService(@Named("dns.server.stats.days") int days,
                                Clock clock,
                                DnsDataSource dnsDataSource) {
        this.days = days;
        this.clock = clock;
        this.dnsDataSource = dnsDataSource;
    }

    public void update() {
        Instant t = LocalDate.now(clock).atStartOfDay(ZoneId.systemDefault()).minusDays(days).toInstant();
        dnsDataSource.deleteEventsBefore(t);
    }

    public ResolverStats getResolverStatistics(String resolver, Instant start) {
        Queue<ResolverEvent> events = new LinkedList<>(dnsDataSource.getEventsByResolver(resolver));

        // drop all times before start
        eventsBefore(events, start);

        List<NameServerStats> nameServerStats = aggregateEvents(events);
        return new ResolverStats(start, ZonedDateTime.now(clock).toInstant(), nameServerStats);
    }

    public List<ResolverStats> getResolverStatistics(String resolver, Instant start, long interval, ChronoUnit unit) {
        Queue<ResolverEvent> events = new LinkedList<>(dnsDataSource.getEventsByResolver(resolver));

        // drop all times before start
        eventsBefore(events, start);

        List<ResolverStats> resolverStats = new ArrayList<>();

        // step through intervals
        Instant last = Instant.now(clock);
        Instant from = start;
        while (from.isBefore(last)) {
            Instant to = from.plus(interval, unit);
            List<ResolverEvent> eventsInInterval = eventsBefore(events, to);
            List<NameServerStats> nameServerStats = aggregateEvents(eventsInInterval);
            resolverStats.add(new ResolverStats(from, to, nameServerStats));
            from = to;
        }

        return resolverStats;
    }

    public NameServerStats testNameServer(String nameServer, List<String> names) {
        String id = UUID.randomUUID().toString();
        List<DnsQuery> queries = names.stream().map(name -> new DnsQuery(DnsRecordType.A, name)).collect(Collectors.toList());
        ;
        dnsDataSource.addDnsQueryQueue(id, nameServer, queries);

        DnsDataSourceDnsResponse result = dnsDataSource.popDnsResolutionQueue(id, 10 * names.size());
        if (result.getLog().size() != names.size()) {
            return null;
        }

        return aggregateSingleNameServerEvents(result.getLog());
    }

    private List<ResolverEvent> eventsBefore(Queue<ResolverEvent> events, Instant instant) {
        List<ResolverEvent> eventsBefore = new ArrayList<>();
        while (!events.isEmpty() && events.peek().getInstant().isBefore(instant)) {
            eventsBefore.add(events.poll());
        }

        return eventsBefore;
    }

    private List<NameServerStats> aggregateEvents(Collection<ResolverEvent> events) {
        return events.stream()
                .collect(Collectors.groupingBy(ResolverEvent::getNameServer))
                .values().stream()
                .map(this::aggregateSingleNameServerEvents)
                .collect(Collectors.toList());
    }

    private NameServerStats aggregateSingleNameServerEvents(List<ResolverEvent> events) {
        int valid = 0;
        int invalid = 0;
        int timeout = 0;
        int error = 0;
        long total = 0;
        SortedMultiset<Long> durations = TreeMultiset.create();
        for (ResolverEvent event : events) {
            switch (event.getStatus()) {
                case "valid":
                    ++valid;
                    break;
                case "invalid":
                    ++invalid;
                    break;
                case "timeout":
                    ++timeout;
                    break;
                case "error":
                    ++error;
                    break;
                default:
                    log.error("unknown resolver event status: {}", event.getStatus());
                    break;
            }
            if (event.getDuration() != null) {
                durations.add(event.getDuration());
                total += event.getDuration();
            }
        }

        DnsReliabilityRating reliabilityRating;
        DnsResponseTimeRating responseTimeRating;

        long average = 0;
        long median = 0;
        long min = 0;
        long max = 0;
        if (!durations.isEmpty()) {
            average = total / durations.size();
            median = median(durations);
            min = durations.firstEntry().getElement();
            max = durations.lastEntry().getElement();

            reliabilityRating = rateReliability(valid, invalid, timeout, error);
            responseTimeRating = rateResponseTime(median);
        } else {
            reliabilityRating = DnsReliabilityRating.UNAVAILABLE;
            responseTimeRating = DnsResponseTimeRating.UNAVAILABLE;
        }

        DnsRating rating = rate(reliabilityRating, responseTimeRating);

        return new NameServerStats(
                events.get(0).getNameServer(),
                valid,
                invalid,
                error,
                timeout,
                average,
                median,
                min,
                max,
                rating,
                reliabilityRating,
                responseTimeRating
        );
    }

    private Long median(SortedMultiset<Long> values) {
        int size = values.size();
        if (size == 0) {
            return null;
        }

        boolean odd = size % 2 == 1;
        Iterator<Long> i = values.iterator();
        for (int left = size / 2; left > 1; --left) {
            i.next();
        }
        return odd ? i.next() : (i.next() + i.next()) / 2;
    }

    private DnsResponseTimeRating rateResponseTime(long t) {
        for (DnsResponseTimeRating time : DnsResponseTimeRating.values()) {
            if (t <= time.getLowerBound()) {
                return time;
            }
        }
        return DnsResponseTimeRating.SLOW;
    }

    private DnsReliabilityRating rateReliability(int valid, int invalid, int timeout, int error) {
        int unusable = invalid + timeout + error;
        if (unusable == 0) {
            return DnsReliabilityRating.HIGH;
        }
        double ratio = (double) valid / (valid + unusable);
        for (DnsReliabilityRating dnsReliabilityRating : DnsReliabilityRating.values()) {
            if (ratio <= dnsReliabilityRating.getLowerBound()) {
                return dnsReliabilityRating;
            }
        }
        return DnsReliabilityRating.LOW;
    }

    private DnsRating rate(DnsReliabilityRating reliabilityRating, DnsResponseTimeRating responseTimeRating) {
        if (reliabilityRating == DnsReliabilityRating.UNAVAILABLE || responseTimeRating == DnsResponseTimeRating.UNAVAILABLE) {
            return DnsRating.BAD;
        }

        if (reliabilityRating == DnsReliabilityRating.LOW) {
            return DnsRating.BAD;
        }

        if (reliabilityRating == DnsReliabilityRating.MEDIUM && responseTimeRating == DnsResponseTimeRating.SLOW) {
            return DnsRating.BAD;
        }

        if (reliabilityRating == DnsReliabilityRating.HIGH && responseTimeRating == DnsResponseTimeRating.MEDIUM || responseTimeRating == DnsResponseTimeRating.FAST) {
            return DnsRating.GOOD;
        }

        return DnsRating.MEDIUM;
    }
}

