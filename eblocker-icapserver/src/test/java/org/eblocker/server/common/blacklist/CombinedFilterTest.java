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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CombinedFilterTest {

    private List<String> filteredDomainsA = Arrays.asList(".twitter.com", ".facebook.net");
    private List<String> filteredDomainsB = Arrays.asList(".microsoft.com", ".apple.com", ".eblocker.com");

    private CombinedFilter filter;

    @Before
    public void setup() {
        CollectionFilter filterA = new CollectionFilter(0, filteredDomainsA);
        CollectionFilter filterB = new CollectionFilter(1, filteredDomainsB);
        filter = new CombinedFilter(Arrays.asList(filterA, filterB));
    }

    @Test
    public void test() {
        // check domains from both lists are filtered
        Assert.assertTrue(filter.isBlocked(".twitter.com").isBlocked());
        Assert.assertTrue(filter.isBlocked(".microsoft.com").isBlocked());

        // ensure size is correct
        Assert.assertEquals(filteredDomainsA.size() + filteredDomainsB.size(), filter.getSize());

        // ensure all domains are included
        Set<String> filteredDomains = new HashSet<>();
        filteredDomains.addAll(filteredDomainsA);
        filteredDomains.addAll(filteredDomainsB);
        Assert.assertEquals(filteredDomains, filter.getDomains().collect(Collectors.toSet()));
    }
}