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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class DomainReplaceFilterTest {

    private DomainFilter<String> backingFilter;
    private DomainFilter<String> filter;

    @Before
    public void setup() {
        backingFilter = new CollectionFilter<>(0, Arrays.asList("http://eblocker.com", "https://eblocker.de"));
        filter = new DomainReplaceFilter(backingFilter, "^", "http://");
    }

    @Test
    public void testFiltering() {
        assertDecision(true, "eblocker.com", backingFilter, filter.isBlocked("eblocker.com"));
        assertDecision(false, "eblocker.de", backingFilter, filter.isBlocked("eblocker.de"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetDomains() {
        filter.getDomains();
    }

    @Test
    public void testGetSize() {
        Assert.assertEquals(2, filter.getSize());
    }

    @Test
    public void testGetChildFilters() {
        List<DomainFilter<?>> childFilters = filter.getChildFilters();
        Assert.assertEquals(1, childFilters.size());
        Assert.assertEquals(backingFilter, childFilters.get(0));
    }

    @Test
    public void testGetName() {
        Assert.assertEquals("(replace collection-filter)", filter.getName());
    }

    @Test
    public void testGetListId() {
        Assert.assertEquals(Integer.valueOf(0), filter.getListId());
    }

    private void assertDecision(boolean expectedBlocked, String expectedDomain, DomainFilter<String> expectedFilter, FilterDecision<String> decision) {
        Assert.assertEquals(expectedBlocked, decision.isBlocked());
        Assert.assertEquals(expectedDomain, decision.getDomain());
        Assert.assertEquals(expectedFilter, decision.getFilter());
    }


}
