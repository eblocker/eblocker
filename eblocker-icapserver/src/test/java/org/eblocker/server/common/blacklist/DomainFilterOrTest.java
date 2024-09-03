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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainFilterOrTest {

    private QueryCountingCollectionFilter<String> filterA;
    private QueryCountingCollectionFilter<String> filterB;
    private QueryCountingCollectionFilter<String> filterC;
    private DomainFilter<String> filter;

    @BeforeEach
    void setup() {
        // setup
        filterA = new QueryCountingCollectionFilter<>(0, List.of("google.com"));
        filterB = new QueryCountingCollectionFilter<>(1, Arrays.asList("google.com", "youtube.com"));
        filterC = new QueryCountingCollectionFilter<>(2, Arrays.asList("google.com", "youtube.com", "duckduckgo.com"));
        filter = new DomainFilterOr<>(List.of(filterA, filterB, filterC));
    }

    @Test
    void isBlocked() {
        assertFalse(filter.isBlocked("baidu.com").isBlocked());
        assertTrue(filter.isBlocked("youtube.com").isBlocked());
        assertTrue(filter.isBlocked("google.com").isBlocked());
        assertTrue(filter.isBlocked("duckduckgo.com").isBlocked());
    }

    @Test
    void getDomains() {
        List<String> domains = filter.getDomains().collect(Collectors.toList());
        assertEquals(6, domains.size());
        assertEquals(3, domains.stream().distinct().count());
        assertTrue(domains.contains("google.com"));
        assertTrue(domains.contains("youtube.com"));
        assertTrue(domains.contains("duckduckgo.com"));
    }

    @Test
    void evaluationOrder() {
        assertEquals(filterA, filter.isBlocked("google.com").getFilter());
        assertEquals(1, filterA.getQueries());
        assertEquals(0, filterB.getQueries());
        assertEquals(0, filterC.getQueries());

        assertEquals(filterB, filter.isBlocked("youtube.com").getFilter());
        assertEquals(2, filterA.getQueries());
        assertEquals(1, filterB.getQueries());
        assertEquals(0, filterC.getQueries());

        assertEquals(filterC, filter.isBlocked("duckduckgo.com").getFilter());
        assertEquals(3, filterA.getQueries());
        assertEquals(2, filterB.getQueries());
        assertEquals(1, filterC.getQueries());
    }

    @Test
    void getSize() {
        assertEquals(6, filter.getSize());
    }

    @Test
    void getChildFilters() {
        List<DomainFilter<?>> childFilters = filter.getChildFilters();
        assertEquals(3, childFilters.size());
        assertTrue(childFilters.contains(filterA));
        assertTrue(childFilters.contains(filterB));
        assertTrue(childFilters.contains(filterC));
    }

    @Test
    void getName() {
        assertEquals("(or query-counting-collection-0 query-counting-collection-1 query-counting-collection-2)", filter.getName());
    }

    @Test
    void getListId() {
        Assertions.assertNull(filter.getListId());
    }

}
