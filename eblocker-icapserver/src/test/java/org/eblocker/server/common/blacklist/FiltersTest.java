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
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class FiltersTest {

    @Test
    public void testNot() {
        DomainFilter filter = Mockito.mock(DomainFilter.class);
        Assert.assertTrue(Filters.not(filter) instanceof DomainFilterNot);

        Assert.assertTrue(StaticFilter.TRUE == Filters.not(StaticFilter.FALSE));
        Assert.assertTrue(StaticFilter.FALSE == Filters.not(StaticFilter.TRUE));
    }

    @Test
    public void testOr() {
        Assert.assertTrue(StaticFilter.FALSE == Filters.or(new DomainFilter[0]));
        Assert.assertTrue(StaticFilter.FALSE == Filters.or(new DomainFilter[] { StaticFilter.FALSE, StaticFilter.FALSE }));
        Assert.assertTrue(StaticFilter.TRUE == Filters.or(new DomainFilter[] { StaticFilter.FALSE, StaticFilter.TRUE }));
        Assert.assertTrue(StaticFilter.TRUE == Filters.or(new DomainFilter[] { StaticFilter.TRUE, Mockito.mock(DomainFilter.class) }));
        Assert.assertTrue(StaticFilter.FALSE == Filters.or());

        DomainFilter[] singleFilter = new DomainFilter[] { Mockito.mock(DomainFilter.class) };
        Assert.assertTrue(singleFilter[0] == Filters.or(singleFilter));

        DomainFilter[] staticAndNonStaticFilter = new DomainFilter[] { StaticFilter.FALSE, Mockito.mock(DomainFilter.class)};
        Assert.assertTrue(staticAndNonStaticFilter[1] == Filters.or(staticAndNonStaticFilter));

        DomainFilter[] filters = new DomainFilter[] { Mockito.mock(DomainFilter.class), Mockito.mock(DomainFilter.class), Mockito.mock(DomainFilter.class)};
        Assert.assertTrue(filters[0] == Filters.or(filters[0]));

        Mockito.when(filters[0].isBlocked("test")).thenReturn(new FilterDecision("test", false, filters[0]));
        Mockito.when(filters[1].isBlocked("test")).thenReturn(new FilterDecision("test", false, filters[1]));
        Mockito.when(filters[2].isBlocked("test")).thenReturn(new FilterDecision("test", false, filters[2]));
        Assert.assertTrue(Filters.or(filters) instanceof DomainFilterOr);
        Assert.assertFalse(Filters.or(filters).isBlocked("test").isBlocked());
        Mockito.verify(filters[0]).isBlocked("test");
        Mockito.verify(filters[1]).isBlocked("test");
        Mockito.verify(filters[2]).isBlocked("test");
    }

    @Test
    public void testAnd() {
        Assert.assertTrue(StaticFilter.FALSE == Filters.and(new DomainFilter[0]));
        Assert.assertTrue(StaticFilter.FALSE == Filters.and(new DomainFilter[] { StaticFilter.FALSE, StaticFilter.FALSE }));
        Assert.assertTrue(StaticFilter.FALSE == Filters.and(new DomainFilter[] { StaticFilter.FALSE, StaticFilter.TRUE }));
        Assert.assertTrue(StaticFilter.TRUE == Filters.and(new DomainFilter[] { StaticFilter.TRUE, StaticFilter.TRUE }));
        Assert.assertTrue(StaticFilter.FALSE == Filters.and(new DomainFilter[] { StaticFilter.FALSE, Mockito.mock(DomainFilter.class) }));
        Assert.assertTrue(StaticFilter.FALSE == Filters.and());

        DomainFilter[] singleFilter = new DomainFilter[] { Mockito.mock(DomainFilter.class) };
        Assert.assertTrue(singleFilter[0] == Filters.and(singleFilter));

        DomainFilter[] staticAndNonStaticFilter = new DomainFilter[] { StaticFilter.TRUE, Mockito.mock(DomainFilter.class)};
        Assert.assertTrue(staticAndNonStaticFilter[1] == Filters.and(staticAndNonStaticFilter));

        DomainFilter[] filters = new DomainFilter[] { Mockito.mock(DomainFilter.class), Mockito.mock(DomainFilter.class), Mockito.mock(DomainFilter.class)};
        Assert.assertTrue(filters[0] == Filters.and(filters[0]));

        Mockito.when(filters[0].isBlocked("test")).thenReturn(new FilterDecision("test", true, filters[0]));
        Mockito.when(filters[1].isBlocked("test")).thenReturn(new FilterDecision("test", true, filters[1]));
        Mockito.when(filters[2].isBlocked("test")).thenReturn(new FilterDecision("test", true, filters[2]));
        Assert.assertTrue(Filters.and(filters) instanceof DomainFilterAnd);
        Assert.assertTrue(Filters.and(filters).isBlocked("test").isBlocked());
        Mockito.verify(filters[0]).isBlocked("test");
        Mockito.verify(filters[1]).isBlocked("test");
        Mockito.verify(filters[2]).isBlocked("test");
    }

    @Test
    public void testHostname() {
        Assert.assertTrue(StaticFilter.FALSE == Filters.hostname(StaticFilter.FALSE));
        Assert.assertTrue(StaticFilter.TRUE == Filters.hostname(StaticFilter.TRUE));
        Assert.assertTrue(Filters.hostname(Mockito.mock(DomainFilter.class)) instanceof HostnameFilter);

        HostnameFilter hostnameFilter = Mockito.mock(HostnameFilter.class);
        Assert.assertTrue(hostnameFilter == Filters.hostname(hostnameFilter));
    }

    @Test
    public void testBloom() {
        BloomFilter<String> bloomFilter = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 0);
        Assert.assertTrue(StaticFilter.FALSE == Filters.bloom(bloomFilter, StaticFilter.FALSE));
        Assert.assertTrue(StaticFilter.TRUE == Filters.bloom(bloomFilter, StaticFilter.TRUE));
        Assert.assertTrue(Filters.bloom(bloomFilter, Mockito.mock(DomainFilter.class)) instanceof BloomDomainFilter);
    }

    @Test
    public void testCache() {
        Assert.assertTrue(StaticFilter.FALSE == Filters.cache(128, CachingFilter.CacheMode.ALL, StaticFilter.FALSE));
        Assert.assertTrue(StaticFilter.TRUE == Filters.cache(128, CachingFilter.CacheMode.ALL, StaticFilter.TRUE));
        Assert.assertTrue(Filters.cache(128, CachingFilter.CacheMode.ALL, Mockito.mock(DomainFilter.class)) instanceof CachingFilter);
    }

    @Test
    public void testHashing() {
        HashFunction hashFunction = Mockito.mock(HashFunction.class);
        Assert.assertTrue(StaticFilter.FALSE == Filters.hashing(hashFunction, StaticFilter.FALSE));
        Assert.assertTrue(StaticFilter.TRUE == Filters.hashing(hashFunction, StaticFilter.TRUE));
        Assert.assertTrue(Filters.hashing(hashFunction, Mockito.mock(DomainFilter.class)) instanceof HashingFilter);
    }

    @Test
    public void testReplace() {
        Assert.assertTrue(StaticFilter.FALSE == Filters.replace("regex", "replacement", StaticFilter.FALSE));
        Assert.assertTrue(StaticFilter.TRUE == Filters.replace("regex", "replacement", StaticFilter.TRUE));
        Assert.assertTrue(Filters.replace("regex", "replacement", Mockito.mock(DomainFilter.class)) instanceof DomainReplaceFilter);
    }
}
