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
import java.util.stream.Collectors;

public class DomainFilterOrTest {

    private QueryCountingCollectionFilter<String> filterA;
    private QueryCountingCollectionFilter<String> filterB;
    private QueryCountingCollectionFilter<String> filterC;
    private DomainFilter<String> filter;

    @Before
    public void setup() {
        // setup
        filterA = new QueryCountingCollectionFilter<>(0, Arrays.asList("google.com"));
        filterB = new QueryCountingCollectionFilter<>(1, Arrays.asList("google.com", "youtube.com"));
        filterC = new QueryCountingCollectionFilter<>(2, Arrays.asList("google.com", "youtube.com", "duckduckgo.com"));
        filter = new DomainFilterOr<>(filterA, filterB, filterC);
    }

    @Test
    public void testIsBlocked() {
        Assert.assertFalse(filter.isBlocked("baidu.com").isBlocked());
        Assert.assertTrue(filter.isBlocked("youtube.com").isBlocked());
        Assert.assertTrue(filter.isBlocked("google.com").isBlocked());
        Assert.assertTrue(filter.isBlocked("duckduckgo.com").isBlocked());
    }

    @Test
    public void testGetDomains() {
        List<String> domains = filter.getDomains().collect(Collectors.toList());
        Assert.assertEquals(6, domains.size());
        Assert.assertEquals(3, domains.stream().distinct().count());
        Assert.assertTrue(domains.contains("google.com"));
        Assert.assertTrue(domains.contains("youtube.com"));
        Assert.assertTrue(domains.contains("duckduckgo.com"));
    }

    @Test
    public void testEvaluationOrder() {
        Assert.assertEquals(filterA, filter.isBlocked("google.com").getFilter());
        Assert.assertEquals(1, filterA.getQueries());
        Assert.assertEquals(0, filterB.getQueries());
        Assert.assertEquals(0, filterC.getQueries());

        Assert.assertEquals(filterB, filter.isBlocked("youtube.com").getFilter());
        Assert.assertEquals(2, filterA.getQueries());
        Assert.assertEquals(1, filterB.getQueries());
        Assert.assertEquals(0, filterC.getQueries());

        Assert.assertEquals(filterC, filter.isBlocked("duckduckgo.com").getFilter());
        Assert.assertEquals(3, filterA.getQueries());
        Assert.assertEquals(2, filterB.getQueries());
        Assert.assertEquals(1, filterC.getQueries());
    }

    @Test
    public void testGetSize() {
        Assert.assertEquals(6, filter.getSize());
    }

    @Test
    public void testGetChildFilters() {
        List<DomainFilter<?>> childFilters = filter.getChildFilters();
        Assert.assertEquals(3, childFilters.size());
        Assert.assertTrue(childFilters.contains(filterA));
        Assert.assertTrue(childFilters.contains(filterB));
        Assert.assertTrue(childFilters.contains(filterC));
    }

    @Test
    public void testGetName() {
        Assert.assertEquals("(or query-counting-collection-0 query-counting-collection-1 query-counting-collection-2)", filter.getName());
    }

    @Test
    public void testGetListId() {
        Assert.assertNull(filter.getListId());
    }

}
