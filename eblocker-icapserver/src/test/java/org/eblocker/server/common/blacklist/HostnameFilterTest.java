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

public class HostnameFilterTest {

    private List<String> filteredDomains = Arrays.asList(".twitter.com", "fancy.api.facebook.net", ".first-level-domain", "first-level-host");

    private CollectionFilter collectionFilter;
    private HostnameFilter filter;

    @Before
    public void setup() {
        collectionFilter = new CollectionFilter(0, filteredDomains);
        filter = new HostnameFilter(collectionFilter);
    }

    @Test
    public void test() {
        // blocked domain
        Assert.assertTrue(filter.isBlocked("twitter.com").isBlocked());
        Assert.assertTrue(filter.isBlocked("www.twitter.com").isBlocked());

        // blocked hostname
        Assert.assertFalse(filter.isBlocked("dumb.api.facebook.net").isBlocked());
        Assert.assertTrue(filter.isBlocked("fancy.api.facebook.net").isBlocked());
        Assert.assertFalse(filter.isBlocked("really.fancy.api.facebook.net").isBlocked());

        // non-exsiting entry
        Assert.assertFalse(filter.isBlocked("hello.com").isBlocked());

        // top level host inblacklist has no effect
        Assert.assertFalse(filter.isBlocked("first-level-host").isBlocked());
        Assert.assertFalse(filter.isBlocked("www.first-level-host").isBlocked());

        // top level domain has no effect
        Assert.assertFalse(filter.isBlocked("first-level-domain").isBlocked());
        Assert.assertFalse(filter.isBlocked("www.first-level-domain").isBlocked());
    }

    @Test
    public void testDelegation() {
        Assert.assertEquals(filteredDomains.size(), filter.getSize());
        Assert.assertEquals(filteredDomains, filter.getDomains().collect(Collectors.toList()));
    }

}