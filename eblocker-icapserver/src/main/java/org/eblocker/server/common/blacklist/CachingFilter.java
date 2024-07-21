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
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CachingFilter implements DomainFilter<String> {

    @Nonnull
    private final CacheMode cacheMode;
    @Nonnull
    private final Map<String, FilterDecision<String>> cache;
    @Nonnull
    private final DomainFilter<String> filter;
    private final int cacheMaxSize;
    @Nonnull
    private final AtomicInteger requests = new AtomicInteger();
    @Nonnull
    private final AtomicInteger hits = new AtomicInteger();
    @Nonnull
    private final AtomicInteger loads = new AtomicInteger();

    CachingFilter(int size, @Nonnull CacheMode cacheMode, @Nonnull DomainFilter<String> filter) {
        this.cache = new ConcurrentFixedSizeCache<>(size, true);
        this.cacheMode = cacheMode;
        this.filter = filter;
        this.cacheMaxSize = size;
    }

    @Override
    public Integer getListId() {
        return filter.getListId();
    }

    @Nonnull
    @Override
    public String getName() {
        return "(cache " + filter.getName() + ")";
    }

    @Override
    public int getSize() {
        return filter.getSize();
    }

    @Override
    public Stream<String> getDomains() {
        return filter.getDomains();
    }

    @Override
    public FilterDecision<String> isBlocked(String domain) {
        requests.incrementAndGet();

        FilterDecision<String> decision = cache.get(domain);
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

    public Stats getStats(boolean includeDomains) {
        Map<Boolean, List<String>> statsCache = null;
        if (includeDomains) {
            Set<FilterDecision<String>> decisions;
            synchronized (cache) {
                decisions = new HashSet<>(cache.values());
            }

            statsCache = decisions.stream()
                    .collect(Collectors.groupingBy(FilterDecision::isBlocked)).entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> e.getValue().stream().map(FilterDecision::getDomain).collect(Collectors.toList())
                    ));
        }
        return new Stats(getName(), cacheMode, cacheMaxSize, cache.size(), requests.get(), loads.get(), hits.get(), statsCache);
    }

    public void clear() {
        cache.clear();
        requests.set(0);
        hits.set(0);
        loads.set(0);
    }

    public enum CacheMode {NON_BLOCKED, BLOCKED, ALL}

    public static class Stats {
        @Nonnull
        private final String name;
        @Nonnull
        private final CacheMode cacheMode;
        private final int maxSize;
        private final int size;
        private final int requests;
        private final int loads;
        private final int hits;
        @Nullable
        private final Map<Boolean, List<String>> cache;

        Stats(@Nonnull String name, @Nonnull CacheMode cacheMode, int maxSize, int size, int requests, int loads, int hits, @Nullable Map<Boolean, List<String>> cache) {
            this.name = name;
            this.cacheMode = cacheMode;
            this.maxSize = maxSize;
            this.size = size;
            this.requests = requests;
            this.loads = loads;
            this.hits = hits;
            this.cache = cache;
        }

        @Nonnull
        public String getName() {
            return name;
        }

        @Nonnull
        CacheMode getCacheMode() {
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

        public Map<Boolean, List<String>> getCache() {
            return cache;
        }
    }
}
