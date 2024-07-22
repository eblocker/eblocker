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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CachingFilterTest {

    private final List<String> blockedDomains = Arrays.asList("blocked.com", "blocked.net", "blocked.org", "blocked.ru");
    private final List<String> nonBlockedDomains = Arrays.asList("non-blocked.com", "non-blocked.net", "non-blocked.org");
    @Mock
    private DomainFilter<String> backingFilter;

    @BeforeEach
    void setup() {
        when(backingFilter.isBlocked(Mockito.anyString()))
                .thenAnswer(invocationOnMock -> {
                    String domain = invocationOnMock.getArgument(0);
                    return new FilterDecision<>(domain, blockedDomains.contains(domain), backingFilter);
                });
    }

    @Test
    void isBlocked_BlockedCacheMode() {
        // setup
        InOrder inOrder = inOrder(backingFilter);
        CachingFilter filter = new CachingFilter(1024, CachingFilter.CacheMode.BLOCKED, backingFilter);

        // first run, any query must hit backing filter
        blockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assertions::assertTrue);
        nonBlockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assertions::assertFalse);
        inOrder.verify(backingFilter, times(blockedDomains.size() + nonBlockedDomains.size())).isBlocked(Mockito.anyString());

        // second run, only non-blocked domains must hit backing filter
        blockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assertions::assertTrue);
        nonBlockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assertions::assertFalse);
        inOrder.verify(backingFilter, times(nonBlockedDomains.size())).isBlocked(Mockito.anyString());
    }

    @Test
    void isBlocked_NonBlockedCacheMode() {
        // setup
        InOrder inOrder = inOrder(backingFilter);
        CachingFilter filter = new CachingFilter(1024, CachingFilter.CacheMode.NON_BLOCKED, backingFilter);

        // first run, any query must hit backing filter
        blockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assertions::assertTrue);
        nonBlockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assertions::assertFalse);
        inOrder.verify(backingFilter, times(blockedDomains.size() + nonBlockedDomains.size())).isBlocked(Mockito.anyString());

        // second run, only blocked domains must hit backing filter
        blockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assertions::assertTrue);
        nonBlockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assertions::assertFalse);
        inOrder.verify(backingFilter, times(blockedDomains.size())).isBlocked(Mockito.anyString());
    }

    @Test
    void isBlocked_AllCacheMode() {
        // setup
        InOrder inOrder = inOrder(backingFilter);
        CachingFilter filter = new CachingFilter(1024, CachingFilter.CacheMode.ALL, backingFilter);

        // first run, any query must hit backing filter
        blockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assertions::assertTrue);
        nonBlockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assertions::assertFalse);
        inOrder.verify(backingFilter, times(blockedDomains.size() + nonBlockedDomains.size())).isBlocked(Mockito.anyString());

        // second run, no query must hit backing filter
        blockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assertions::assertTrue);
        nonBlockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assertions::assertFalse);
        inOrder.verify(backingFilter, times(0)).isBlocked(Mockito.anyString());
    }

    @Test
    void testLru() {
        // setup
        InOrder inOrder = inOrder(backingFilter);
        CachingFilter filter = new CachingFilter(blockedDomains.size() - 1, CachingFilter.CacheMode.ALL, backingFilter);

        // first run, any query must hit backing filter
        blockedDomains.stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assertions::assertTrue);
        inOrder.verify(backingFilter, times(blockedDomains.size())).isBlocked(Mockito.anyString());

        // second run with reversed order, all but original first entry must come from cache
        Lists.reverse(blockedDomains).stream().map(filter::isBlocked).map(FilterDecision::isBlocked).forEach(Assertions::assertTrue);
        inOrder.verify(backingFilter, times(1)).isBlocked(blockedDomains.get(0));
        inOrder.verifyNoMoreInteractions();
    }

}
