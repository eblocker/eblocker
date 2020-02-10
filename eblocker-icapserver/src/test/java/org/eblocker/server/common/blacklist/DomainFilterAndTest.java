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
import java.util.Set;
import java.util.stream.Collectors;

public class DomainFilterAndTest {

    private QueryCountingCollectionFilter<String> filterA;
    private QueryCountingCollectionFilter<String> filterB;
    private QueryCountingCollectionFilter<String> filterC;
    private DomainFilter<String> filter;

    @Before
    public void setup() {
        filterA = new QueryCountingCollectionFilter<>(0, Arrays.asList("google.com", "youtube.com"));
        filterB = new QueryCountingCollectionFilter<>(1, Arrays.asList("google.com"));
        filterC = new QueryCountingCollectionFilter<>(2, Arrays.asList("google.com"));
        filter = new DomainFilterAnd<>(filterA, filterB, filterC);
    }

    @Test
    public void testIsBlocked() {
        // test
        Assert.assertFalse(filter.isBlocked("baidu.com").isBlocked());
        Assert.assertFalse(filter.isBlocked("youtube.com").isBlocked());
        Assert.assertTrue(filter.isBlocked("google.com").isBlocked());
    }

    @Test
    public void testEvaluationOrder() {
        Assert.assertEquals(filterA, filter.isBlocked("duckduckgo.com").getFilter());
        Assert.assertEquals(1, filterA.getQueries());
        Assert.assertEquals(0, filterB.getQueries());
        Assert.assertEquals(0, filterC.getQueries());

        Assert.assertEquals(filterB, filter.isBlocked("youtube.com").getFilter());
        Assert.assertEquals(2, filterA.getQueries());
        Assert.assertEquals(1, filterB.getQueries());
        Assert.assertEquals(0, filterC.getQueries());

        Assert.assertEquals(filterC, filter.isBlocked("google.com").getFilter());
        Assert.assertEquals(3, filterA.getQueries());
        Assert.assertEquals(2, filterB.getQueries());
        Assert.assertEquals(1, filterC.getQueries());
    }

    @Test
    public void testGetDomains() {
        Set<String> domains = filter.getDomains().collect(Collectors.toSet());
        Assert.assertEquals(1, domains.size());
        Assert.assertTrue(domains.contains("google.com"));
    }

    @Test
    public void testGetSize() {
        Assert.assertEquals(1, filter.getSize());
    }

    @Test
    public void testGetName() {
        Assert.assertEquals("(and query-counting-collection-0 query-counting-collection-1 query-counting-collection-2)", filter.getName());
    }

    @Test
    public void testGetListId() {
        Assert.assertNull(filter.getListId());
    }

}
