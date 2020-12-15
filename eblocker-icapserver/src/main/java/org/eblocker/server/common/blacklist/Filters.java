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

import com.google.common.hash.BloomFilter;
import com.google.common.hash.HashFunction;

import java.util.ArrayList;
import java.util.List;

/**
 * Static utility methods to combine {@link DomainFilter}s. Returned terms will be simplified if possible.
 */
public class Filters {

    private Filters() {
    }

    public static <T> DomainFilter<T> not(DomainFilter<T> filter) {
        if (StaticFilter.FALSE == filter) {
            return staticTrue();
        }

        if (StaticFilter.TRUE == filter) {
            return staticFalse();
        }

        return new DomainFilterNot<>(filter);
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static <T> DomainFilter<T> and(DomainFilter<T>... filters) {
        if (filters.length == 0) {
            return staticFalse();
        }

        if (filters.length == 1) {
            return filters[0];
        }

        List<DomainFilter<T>> nonStaticFilters = new ArrayList<>(filters.length);
        for (DomainFilter filter : filters) {
            if (StaticFilter.FALSE == filter) {
                return staticFalse();
            } else if (StaticFilter.TRUE != filter) {
                nonStaticFilters.add(filter);
            }
        }

        if (nonStaticFilters.isEmpty()) {
            return staticTrue();
        }

        if (nonStaticFilters.size() == 1) {
            return nonStaticFilters.get(0);
        }

        return new DomainFilterAnd<>(nonStaticFilters.toArray(new DomainFilter[0]));
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static <T> DomainFilter<T> or(DomainFilter<T>... filters) {
        if (filters.length == 0) {
            return staticFalse();
        }

        if (filters.length == 1) {
            return filters[0];
        }

        List<DomainFilter<T>> nonStaticFilters = new ArrayList<>(filters.length);
        for (DomainFilter<T> filter : filters) {
            if (StaticFilter.TRUE == filter) {
                return filter;
            } else if (StaticFilter.FALSE != filter) {
                nonStaticFilters.add(filter);
            }
        }

        if (nonStaticFilters.isEmpty()) {
            return Filters.staticFalse();
        }

        if (nonStaticFilters.size() == 1) {
            return nonStaticFilters.get(0);
        }

        return new DomainFilterOr<>(nonStaticFilters.toArray(new DomainFilter[0]));
    }

    public static DomainFilter<String> hostname(DomainFilter<String> filter) {
        if (filter instanceof StaticFilter) {
            return filter;
        }

        if (filter instanceof HostnameFilter) {
            return filter;
        }

        return new HostnameFilter(filter);
    }

    public static DomainFilter<String> bloom(BloomFilter<String> bloomFilter, DomainFilter<String> filter) {
        if (filter instanceof StaticFilter) {
            return filter;
        }

        return new BloomDomainFilter<>(bloomFilter, filter);
    }

    public static <T> DomainFilter<T> cache(int size, CachingFilter.CacheMode cacheMode, DomainFilter<T> filter) {
        if (filter instanceof StaticFilter) {
            return filter;
        }

        return new CachingFilter<>(size, cacheMode, filter);
    }

    @SuppressWarnings("unchecked")
    public static DomainFilter<String> hashing(HashFunction hashFunction, DomainFilter<byte[]> filter) {
        if (filter instanceof StaticFilter) {
            return (DomainFilter<String>) (StaticFilter) filter;
        }

        return new HashingFilter(hashFunction, filter);
    }

    public static DomainFilter<String> replace(String regex, String replacement, DomainFilter<String> filter) {
        if (filter instanceof StaticFilter) {
            return filter;
        }

        return new DomainReplaceFilter(filter, regex, replacement);
    }

    @SuppressWarnings("unchecked")
    public static <T> StaticFilter<T> staticFalse() {
        return (StaticFilter<T>) StaticFilter.FALSE;
    }

    @SuppressWarnings("unchecked")
    public static <T> StaticFilter<T> staticTrue() {
        return (StaticFilter<T>) StaticFilter.TRUE;
    }
}
