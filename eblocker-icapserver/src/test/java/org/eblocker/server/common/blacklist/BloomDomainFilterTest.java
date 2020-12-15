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

import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BloomDomainFilterTest {

    private double fpp = 0.25;

    private List<String> domains;
    private List<String> nonBlockedDomains;
    private List<String> falsePositives;

    private DomainFilter<String> domainFilter;
    private BloomDomainFilter<String> filter;

    @Before
    public void setup() throws IOException {
        domains = IOUtils.readLines(ClassLoader.getSystemResourceAsStream("test-data/domainblacklists/top-1000"));

        // find some false positives / non blocked sites
        BloomFilter<String> bloomFilter = BloomFilter.create(new StringFunnel(Charsets.UTF_8), domains.size(), fpp);
        domains.forEach(bloomFilter::put);
        Map<Boolean, List<String>> mightContain = IOUtils.readLines(ClassLoader.getSystemResourceAsStream("test-data/domainblacklists/bottom-1000")).stream().collect(Collectors.partitioningBy(bloomFilter::mightContain));

        // check non blocked domains
        nonBlockedDomains = mightContain.get(false);
        Assert.assertFalse("test setup does not include non-blocked domains", nonBlockedDomains.isEmpty());
        nonBlockedDomains.stream().forEach(domain -> Assert.assertFalse(bloomFilter.mightContain(domain)));
        Assert.assertTrue(nonBlockedDomains.contains("pockethcm.com"));
        Assert.assertFalse(bloomFilter.mightContain("pockethcm.com"));

        // check false positives
        falsePositives = mightContain.get(true);
        Assert.assertFalse("test setup does not include false positives", falsePositives.isEmpty());
        falsePositives.stream().forEach(domain -> Assert.assertTrue(bloomFilter.mightContain(domain)));

        // create test instance
        domainFilter = Mockito.mock(DomainFilter.class);
        Mockito.when(domainFilter.getDomains()).thenReturn(domains.stream());
        Mockito.when(domainFilter.getSize()).thenReturn(domains.size());
        Mockito.when(domainFilter.isBlocked(Mockito.anyString())).then(im -> new FilterDecision<>(im.getArgument(0), domains.contains(im.getArgument(0)), domainFilter));
        filter = new BloomDomainFilter<>(new StringFunnel(Charsets.UTF_8), fpp, domainFilter);
    }

    @Test
    public void testBloomFilteringNonBlockedDomains() {
        // for non-blocked domains the backing-filter must never be called
        nonBlockedDomains.forEach(domain -> Assert.assertFalse(filter.isBlocked(domain).isBlocked()));
        Mockito.verify(domainFilter, Mockito.never()).isBlocked(Mockito.anyString());
    }

    @Test
    public void testBloomFilteringBlockedDomains() {
        // each blocked domain must be checked with backing-filter because it may be a
        // false-positive
        Mockito.when(domainFilter.isBlocked(Mockito.anyString())).then(invocationOnMock -> {
            String domain = invocationOnMock.getArgument(0);
            return new FilterDecision<>(domain, domains.contains(domain), domainFilter);
        });
        domains.forEach(domain -> Assert.assertTrue(filter.isBlocked(domain).isBlocked()));
        Mockito.verify(domainFilter, Mockito.times(domains.size())).isBlocked(Mockito.anyString());
    }

    @Test
    public void testFalsePositive() throws IOException {
        int size = 200;

        Mockito.when(domainFilter.isBlocked(Mockito.anyString()))
                .then(invocationOnMock ->
                        new FilterDecision<>(invocationOnMock.getArgument(0), false, domainFilter));

        InOrder inOrder = Mockito.inOrder(domainFilter);
        // each false positive must be checked with backing-filter
        falsePositives.stream().limit(size).forEach(domain -> Assert.assertFalse(filter.isBlocked(domain).isBlocked()));
        inOrder.verify(domainFilter, Mockito.times(size)).isBlocked(Mockito.anyString());
    }

    @Test
    public void testSerialization() throws IOException {
        // ser- and derserialization
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        filter.writeTo(baos);
        baos.flush();
        BloomDomainFilter<String> deserialized = BloomDomainFilter.readFrom(new ByteArrayInputStream(baos.toByteArray()), new StringFunnel(Charsets.UTF_8), domainFilter);

        // check both instances give the same results
        Mockito.when(domainFilter.isBlocked(Mockito.anyString())).then(invocationOnMock -> {
            String domain = invocationOnMock.getArgument(0);
            return new FilterDecision<>(domain, domains.contains(domain), domainFilter);
        });

        domains.stream().forEach(domain -> Assert.assertEquals(filter.isBlocked(domain).isBlocked(), deserialized.isBlocked(domain).isBlocked()));
        nonBlockedDomains.stream().forEach(domain -> Assert.assertEquals(filter.isBlocked(domain).isBlocked(), deserialized.isBlocked(domain).isBlocked()));
        falsePositives.stream().forEach(domain -> Assert.assertEquals(filter.isBlocked(domain).isBlocked(), deserialized.isBlocked(domain).isBlocked()));
    }

    @Test
    public void testSharedBloomFilter() {
        BloomDomainFilter<String> filter2 = new BloomDomainFilter<>(filter.getBloomFilter(), domainFilter);

        Stream.concat(nonBlockedDomains.stream(), domains.stream()).forEach(domain -> Assert.assertEquals(filter.isBlocked(domain).isBlocked(), filter2.isBlocked(domain).isBlocked()));
    }

}
