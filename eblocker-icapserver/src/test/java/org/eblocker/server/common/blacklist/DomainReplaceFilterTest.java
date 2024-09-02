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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainReplaceFilterTest {

    private DomainFilter<String> backingFilter;
    private DomainFilter<String> filter;

    @BeforeEach
    void setup() {
        backingFilter = new CollectionFilter<>(0, Arrays.asList("http://eblocker.com", "https://eblocker.de"));
        filter = new DomainReplaceFilter(backingFilter, "^", "http://");
    }

    @Test
    void filtering() {
        assertDecision(true, "eblocker.com", backingFilter, filter.isBlocked("eblocker.com"));
        assertDecision(false, "eblocker.de", backingFilter, filter.isBlocked("eblocker.de"));
    }

    @Test
    void getDomains() {
        //given
        //when
        UnsupportedOperationException unsupportedOperationException = assertThrows(UnsupportedOperationException.class, () -> filter.getDomains());

        //then
        Assertions.assertNotNull(unsupportedOperationException);
    }

    @Test
    void getSize() {
        assertEquals(2, filter.getSize());
    }

    @Test
    void getChildFilters() {
        List<DomainFilter<?>> childFilters = filter.getChildFilters();
        assertEquals(1, childFilters.size());
        assertEquals(backingFilter, childFilters.get(0));
    }

    @Test
    void getName() {
        assertEquals("(replace collection-filter)", filter.getName());
    }

    @Test
    void getListId() {
        assertEquals(Integer.valueOf(0), filter.getListId());
    }

    private void assertDecision(boolean expectedBlocked, String expectedDomain, DomainFilter<String> expectedFilter, FilterDecision<String> decision) {
        assertEquals(expectedBlocked, decision.isBlocked());
        assertEquals(expectedDomain, decision.getDomain());
        assertEquals(expectedFilter, decision.getFilter());
    }

}
