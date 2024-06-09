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
package org.eblocker.server.common.blacklist;

import org.eblocker.server.common.collections.ConcurrentFixedSizeCache;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CachingFilter<T> implements DomainFilter<T> {

    private final CacheMode cacheMode;
    private final Map<T, FilterDecision<T>> cache;
    private final DomainFilter<T> filter;
    private final int cacheMaxSize;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicInteger loads = new AtomicInteger();

    public CachingFilter(int size, CacheMode cacheMode, DomainFilter<T> filter) {
        this.cache = new ConcurrentFixedSizeCache<>(size, true);
        this.cacheMode = cacheMode;
        this.filter = filter;
        this.cacheMaxSize = size;
    }

    @Override
    public Integer getListId() {
        return filter.getListId();
    }

    @Override
    public String getName() {
        return "(cache " + filter.getName() + ")";
    }

    @Override
    public int getSize() {
        return filter.getSize();
    }

    @Override
    public Stream<T> getDomains() {
        return filter.getDomains();
    }

    @Override
    public FilterDecision<T> isBlocked(T domain) {
        requests.incrementAndGet();

        FilterDecision<T> decision = cache.get(domain);
        if (decision == null) {
            decision = filter.isBlocked(domain);
            if (cacheMode == CacheMode.ALL
                    || !decision.isBlocked() && cacheMode == CacheMode.NON_BLOCKED
                    || decision.isBlocked() && cacheMode == CacheMode.BLOCKED) {
                loads.incrementAndGet();
                cache.put(domain, decision);
            }
        } else {
            hits.incrementAndGet();
        }

        return decision;
    }

    @Nonnull
    @Override
    public List<DomainFilter<?>> getChildFilters() {
        return Collections.singletonList(filter);
    }

    public Stats<T> getStats(boolean includeDomains) {
        Stats<T> stats = new Stats<>();
        stats.name = getName();
        stats.cacheMode = cacheMode;
        stats.maxSize = cacheMaxSize;
        stats.size = cache.size();
        stats.requests = requests.get();
        stats.hits = hits.get();
        stats.loads = loads.get();

        if (includeDomains) {
            Set<FilterDecision<T>> decisions;
            synchronized (cache) {
                decisions = new HashSet<>(cache.values());
            }

            stats.cache = decisions.stream()
                    .collect(Collectors.groupingBy(FilterDecision::isBlocked)).entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> e.getValue().stream().map(FilterDecision::getDomain).collect(Collectors.toList())
                    ));
        }

        return stats;
    }

    public void clear() {
        cache.clear();
        requests.set(0);
        hits.set(0);
        loads.set(0);
    }

    public enum CacheMode {NON_BLOCKED, BLOCKED, ALL}

    public static class Stats<T> {
        private String name;
        private CacheMode cacheMode;
        private int maxSize;
        private int size;
        private int requests;
        private int loads;
        private int hits;
        private Map<Boolean, List<T>> cache;

        public String getName() {
            return name;
        }

        public CacheMode getCacheMode() {
            return cacheMode;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public int getSize() {
            return size;
        }

        public int getRequests() {
            return requests;
        }

        public int getLoads() {
            return loads;
        }

        public int getHits() {
            return hits;
        }

        public Map<Boolean, List<T>> getCache() {
            return cache;
        }
    }
}
