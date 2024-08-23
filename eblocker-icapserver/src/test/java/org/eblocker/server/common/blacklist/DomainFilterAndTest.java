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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainFilterAndTest {

    private QueryCountingCollectionFilter<String> filterA;
    private QueryCountingCollectionFilter<String> filterB;
    private QueryCountingCollectionFilter<String> filterC;
    private DomainFilter<String> domainFilterAnd;

    @BeforeEach
    void setUp() {
        filterA = new QueryCountingCollectionFilter<>(0, Arrays.asList("google.com", "youtube.com"));
        filterB = new QueryCountingCollectionFilter<>(1, List.of("google.com"));
        filterC = new QueryCountingCollectionFilter<>(2, List.of("google.com"));
        domainFilterAnd = new DomainFilterAnd(List.of(filterA, filterB, filterC));
    }

    @Test
    void isBlocked() {
        assertFalse(domainFilterAnd.isBlocked("baidu.com").isBlocked());
        assertFalse(domainFilterAnd.isBlocked("youtube.com").isBlocked());
        assertTrue(domainFilterAnd.isBlocked("google.com").isBlocked());
    }

    @Test
    void evaluationOrder() {
        assertEquals(filterA, domainFilterAnd.isBlocked("duckduckgo.com").getFilter());
        assertEquals(1, filterA.getQueries());
        assertEquals(0, filterB.getQueries());
        assertEquals(0, filterC.getQueries());

        assertEquals(filterB, domainFilterAnd.isBlocked("youtube.com").getFilter());
        assertEquals(2, filterA.getQueries());
        assertEquals(1, filterB.getQueries());
        assertEquals(0, filterC.getQueries());

        assertEquals(filterC, domainFilterAnd.isBlocked("google.com").getFilter());
        assertEquals(3, filterA.getQueries());
        assertEquals(2, filterB.getQueries());
        assertEquals(1, filterC.getQueries());
    }

    @Test
    void getDomains() {
        //Given
        //When
        Set<String> domains = domainFilterAnd.getDomains().collect(Collectors.toSet());

        //Then
        assertEquals(1, domains.size());
        assertTrue(domains.contains("google.com"));
    }

    @Test
    void getSize() {
        //Given
        //When
        int size = domainFilterAnd.getSize();

        //Then
        assertEquals(1, size);
    }

    @Test
    void getName() {
        //Given
        //When
        String name = domainFilterAnd.getName();

        //Then
        assertEquals("(and query-counting-collection-0 query-counting-collection-1 query-counting-collection-2)", name);
    }

    @Test
    void getListId() {
        //Given
        //When
        Integer listId = domainFilterAnd.getListId();
        //Then
        assertNull(listId);
    }

    @Test
    void getChildFilters() {
        //Given
        //When
        List<DomainFilter<?>> childFilters = domainFilterAnd.getChildFilters();

        //Then
        assertEquals(3, childFilters.size());
        assertTrue(childFilters.contains(filterA));
        assertTrue(childFilters.contains(filterB));
        assertTrue(childFilters.contains(filterC));
    }
}
