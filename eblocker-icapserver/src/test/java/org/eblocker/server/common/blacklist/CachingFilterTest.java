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

import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

public class CachingFilterTest {

    private List<String> blockedDomains = Arrays.asList("blocked.com", "blocked.net", "blocked.org", "blocked.ru");
    private List<String> nonBlockedDomains = Arrays.asList("non-blocked.com", "non-blocked.net", "non-blocked.org");
    private DomainFilter<String> backingFilter;

    @Before
    public void setup() {
        backingFilter = Mockito.mock(DomainFilter.class);
        Mockito.when(backingFilter.isBlocked(Mockito.anyString()))
            .thenAnswer(invocationOnMock -> {
                String domain = invocationOnMock.getArgument(0);
                return new FilterDecision<>(domain, blockedDomains.contains(domain), backingFilter);
            });
    }

    @Test
    public void testBlockedCacheMode() {
        // setup
        InOrder inOrder = Mockito.inOrder(backingFilter);
        CachingFilter<String> filter = new CachingFilter<>(1024, CachingFilter.CacheMode.BLOCKED, backingFilter);

        // first run, any query must hit backing filter
        blockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assert::assertTrue);
        nonBlockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assert::assertFalse);
        inOrder.verify(backingFilter, Mockito.times(blockedDomains.size() + nonBlockedDomains.size())).isBlocked(Mockito.anyString());

        // second run, only non-blocked domains must hit backing filter
        blockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assert::assertTrue);
        nonBlockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assert::assertFalse);
        inOrder.verify(backingFilter, Mockito.times(nonBlockedDomains.size())).isBlocked(Mockito.anyString());
    }

    @Test
    public void testNonBlockedCacheMode() {
        // setup
        InOrder inOrder = Mockito.inOrder(backingFilter);
        CachingFilter<String> filter = new CachingFilter<>(1024, CachingFilter.CacheMode.NON_BLOCKED, backingFilter);

        // first run, any query must hit backing filter
        blockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assert::assertTrue);
        nonBlockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assert::assertFalse);
        inOrder.verify(backingFilter, Mockito.times(blockedDomains.size() + nonBlockedDomains.size())).isBlocked(Mockito.anyString());

        // second run, only blocked domains must hit backing filter
        blockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assert::assertTrue);
        nonBlockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assert::assertFalse);
        inOrder.verify(backingFilter, Mockito.times(blockedDomains.size())).isBlocked(Mockito.anyString());
    }

    @Test
    public void testAllCacheMode() {
        // setup
        InOrder inOrder = Mockito.inOrder(backingFilter);
        CachingFilter<String> filter = new CachingFilter<>(1024, CachingFilter.CacheMode.ALL, backingFilter);

        // first run, any query must hit backing filter
        blockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assert::assertTrue);
        nonBlockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assert::assertFalse);
        inOrder.verify(backingFilter, Mockito.times(blockedDomains.size() + nonBlockedDomains.size())).isBlocked(Mockito.anyString());

        // second run, no query must hit backing filter
        blockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assert::assertTrue);
        nonBlockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assert::assertFalse);
        inOrder.verify(backingFilter, Mockito.times(0)).isBlocked(Mockito.anyString());
    }

    @Test
    public void testLru() {
        // setup
        InOrder inOrder = Mockito.inOrder(backingFilter);
        CachingFilter<String> filter = new CachingFilter<>(blockedDomains.size() - 1, CachingFilter.CacheMode.ALL, backingFilter);

        // first run, any query must hit backing filter
        blockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assert::assertTrue);
        inOrder.verify(backingFilter, Mockito.times(blockedDomains.size())).isBlocked(Mockito.anyString());

        // second run with reversed order, all but original first entry must come from cache
        Lists.reverse(blockedDomains).stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assert::assertTrue);
        inOrder.verify(backingFilter, Mockito.times(1)).isBlocked(blockedDomains.get(0));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testCacheStats() {
        // setup
        CachingFilter<String> filter = new CachingFilter<>(1024, CachingFilter.CacheMode.ALL, backingFilter);

        // fill cache
        blockedDomains.stream().forEach(filter::isBlocked);
        nonBlockedDomains.stream().forEach(filter::isBlocked);

        // check statistics
        int domains = blockedDomains.size() + nonBlockedDomains.size();
        CachingFilter.Stats<String> stats = filter.getStats(false);
        Assert.assertEquals(CachingFilter.CacheMode.ALL, stats.getCacheMode());
        Assert.assertEquals(1024, stats.getMaxSize());
        Assert.assertEquals(domains, stats.getSize());
        Assert.assertEquals(domains, stats.getRequests());
        Assert.assertEquals(0, stats.getHits());
        Assert.assertEquals(domains, stats.getLoads());
        Assert.assertNull(stats.getCache());

        // re-run queries
        blockedDomains.stream().forEach(filter::isBlocked);
        nonBlockedDomains.stream().forEach(filter::isBlocked);

        // check statistics again
        stats = filter.getStats(true);
        Assert.assertEquals(CachingFilter.CacheMode.ALL, stats.getCacheMode());
        Assert.assertEquals(1024, stats.getMaxSize());
        Assert.assertEquals(domains, stats.getSize());
        Assert.assertEquals(2 * domains, stats.getRequests());
        Assert.assertEquals(domains, stats.getHits());
        Assert.assertEquals(domains, stats.getLoads());
        Assert.assertNotNull(stats.getCache());
        Assert.assertEquals(blockedDomains.size(), stats.getCache().get(Boolean.TRUE).size());
        Assert.assertTrue(stats.getCache().get(Boolean.TRUE).containsAll(blockedDomains));
        Assert.assertEquals(nonBlockedDomains.size(), stats.getCache().get(Boolean.FALSE).size());
        Assert.assertTrue(stats.getCache().get(Boolean.FALSE).containsAll(nonBlockedDomains));
    }
}
