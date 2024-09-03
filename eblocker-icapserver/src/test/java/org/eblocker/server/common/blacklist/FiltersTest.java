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

import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.google.common.hash.HashFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FiltersTest {

    @SuppressWarnings("unchecked")
    @Test
    void not() {
        DomainFilter<String> filter = mock(DomainFilter.class);
        assertInstanceOf(DomainFilterNot.class, Filters.not(filter));

        assertSame(StaticFilter.TRUE, Filters.not(StaticFilter.FALSE));
        assertSame(StaticFilter.FALSE, Filters.not(StaticFilter.TRUE));
    }

    @SuppressWarnings("unchecked")
    @Test
    void or() {
        assertSame(StaticFilter.FALSE, Filters.or());
        assertSame(StaticFilter.FALSE, Filters.or(StaticFilter.FALSE, StaticFilter.FALSE));
        assertSame(StaticFilter.TRUE, Filters.or(StaticFilter.FALSE, StaticFilter.TRUE));
        assertSame(StaticFilter.TRUE, Filters.or(StaticFilter.TRUE, mock(DomainFilter.class)));
        DomainFilter<String> or = Filters.or();
        assertSame(StaticFilter.FALSE, or);

        DomainFilter<String>[] singleFilter = new DomainFilter[]{ mock(DomainFilter.class) };
        assertSame(singleFilter[0], Filters.or(singleFilter));

        DomainFilter<String>[] staticAndNonStaticFilter = new DomainFilter[]{ StaticFilter.FALSE, mock(DomainFilter.class) };
        assertSame(staticAndNonStaticFilter[1], Filters.or(staticAndNonStaticFilter));

        DomainFilter<String>[] filters = new DomainFilter[]{ mock(DomainFilter.class), mock(DomainFilter.class), mock(DomainFilter.class) };
        assertSame(filters[0], Filters.or(filters[0]));

        when(filters[0].isBlocked("test")).thenReturn(new FilterDecision<>("test", false, filters[0]));
        when(filters[1].isBlocked("test")).thenReturn(new FilterDecision<>("test", false, filters[1]));
        when(filters[2].isBlocked("test")).thenReturn(new FilterDecision<>("test", false, filters[2]));
        assertInstanceOf(DomainFilterOr.class, Filters.or(filters));
        assertFalse(Filters.or(filters).isBlocked("test").isBlocked());
        verify(filters[0]).isBlocked("test");
        verify(filters[1]).isBlocked("test");
        verify(filters[2]).isBlocked("test");
    }

    @SuppressWarnings("unchecked")
    @Test
    void and() {
        assertSame(StaticFilter.FALSE, Filters.and());
        assertSame(StaticFilter.FALSE, Filters.and(StaticFilter.FALSE, StaticFilter.FALSE));
        assertSame(StaticFilter.FALSE, Filters.and(StaticFilter.FALSE, StaticFilter.TRUE));
        assertSame(StaticFilter.TRUE, Filters.and(StaticFilter.TRUE, StaticFilter.TRUE));
        assertSame(StaticFilter.FALSE, Filters.and(StaticFilter.FALSE, mock(DomainFilter.class)));
        assertSame(StaticFilter.FALSE, Filters.and());

        DomainFilter<String>[] singleFilter = new DomainFilter[]{ mock(DomainFilter.class) };
        assertSame(singleFilter[0], Filters.and(singleFilter));

        DomainFilter<String>[] staticAndNonStaticFilter = new DomainFilter[]{ StaticFilter.TRUE, mock(DomainFilter.class) };
        assertSame(staticAndNonStaticFilter[1], Filters.and(staticAndNonStaticFilter));

        DomainFilter<String>[] filters = new DomainFilter[]{ mock(DomainFilter.class), mock(DomainFilter.class), mock(DomainFilter.class) };
        assertSame(filters[0], Filters.and(filters[0]));

        when(filters[0].isBlocked("test")).thenReturn(new FilterDecision<>("test", true, filters[0]));
        when(filters[1].isBlocked("test")).thenReturn(new FilterDecision<>("test", true, filters[1]));
        when(filters[2].isBlocked("test")).thenReturn(new FilterDecision<>("test", true, filters[2]));
        assertInstanceOf(DomainFilterAnd.class, Filters.and(filters));
        assertTrue(Filters.and(filters).isBlocked("test").isBlocked());
        verify(filters[0]).isBlocked("test");
        verify(filters[1]).isBlocked("test");
        verify(filters[2]).isBlocked("test");
    }

    @SuppressWarnings("unchecked")
    @Test
    void hostname() {
        assertSame(StaticFilter.FALSE, Filters.hostname(StaticFilter.FALSE));
        assertSame(StaticFilter.TRUE, Filters.hostname(StaticFilter.TRUE));
        assertInstanceOf(HostnameFilter.class, Filters.hostname(mock(DomainFilter.class)));

        HostnameFilter hostnameFilter = mock(HostnameFilter.class);
        assertSame(hostnameFilter, Filters.hostname(hostnameFilter));
    }

    @SuppressWarnings("unchecked")
    @Test
    void bloom() {
        BloomFilter<String> bloomFilter = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 0);
        assertSame(StaticFilter.FALSE, Filters.bloom(bloomFilter, StaticFilter.FALSE));
        assertSame(StaticFilter.TRUE, Filters.bloom(bloomFilter, StaticFilter.TRUE));
        assertInstanceOf(BloomDomainFilter.class, Filters.bloom(bloomFilter, mock(DomainFilter.class)));
    }

    @SuppressWarnings("unchecked")
    @Test
    void cache() {
        assertSame(StaticFilter.FALSE, Filters.cache(128, CachingFilter.CacheMode.ALL, StaticFilter.FALSE));
        assertSame(StaticFilter.TRUE, Filters.cache(128, CachingFilter.CacheMode.ALL, StaticFilter.TRUE));
        assertInstanceOf(CachingFilter.class, Filters.cache(128, CachingFilter.CacheMode.ALL, mock(DomainFilter.class)));
    }

    @SuppressWarnings("unchecked")
    @Test
    void hashing() {
        HashFunction hashFunction = mock(HashFunction.class);
        assertSame(StaticFilter.FALSE, Filters.hashing(hashFunction, StaticFilter.FALSE));
        assertSame(StaticFilter.TRUE, Filters.hashing(hashFunction, StaticFilter.TRUE));
        assertInstanceOf(HashingFilter.class, Filters.hashing(hashFunction, mock(DomainFilter.class)));
    }

    @SuppressWarnings("unchecked")
    @Test
    void replace() {
        assertSame(StaticFilter.FALSE, Filters.replace("regex", "replacement", StaticFilter.FALSE));
        assertSame(StaticFilter.TRUE, Filters.replace("regex", "replacement", StaticFilter.TRUE));
        assertInstanceOf(DomainReplaceFilter.class, Filters.replace("regex", "replacement", mock(DomainFilter.class)));
    }
}
