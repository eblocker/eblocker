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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloomDomainFilterTest {

    private List<String> domains;
    private List<String> nonBlockedDomains;
    private List<String> falsePositives;

    private DomainFilter<String> domainFilter;
    private BloomDomainFilter<String> filter;

    @SuppressWarnings("DataFlowIssue")
    @BeforeEach
    void setUp() throws IOException {
        domains = IOUtils.readLines(ClassLoader.getSystemResourceAsStream("test-data/domainblacklists/top-1000"), Charset.defaultCharset());

        // find some false positives / non blocked sites
        double fpp = 0.25;
        BloomFilter<String> bloomFilter = BloomFilter.create(new StringFunnel(Charsets.UTF_8), domains.size(), fpp);
        domains.forEach(bloomFilter::put);
        Map<Boolean, List<String>> mightContain = IOUtils.readLines(ClassLoader.getSystemResourceAsStream("test-data/domainblacklists/bottom-1000"), Charset.defaultCharset()).stream().collect(Collectors.partitioningBy(bloomFilter::mightContain));

        // check non blocked domains
        nonBlockedDomains = mightContain.get(false);
        assertFalse(nonBlockedDomains.isEmpty(), "test setup does not include non-blocked domains");
        nonBlockedDomains.forEach(domain -> assertFalse(bloomFilter.mightContain(domain)));
        assertTrue(nonBlockedDomains.contains("pockethcm.com"));
        assertFalse(bloomFilter.mightContain("pockethcm.com"));

        // check false positives
        falsePositives = mightContain.get(true);
        assertFalse(falsePositives.isEmpty(), "test setup does not include false positives");
        falsePositives.forEach(domain -> assertTrue(bloomFilter.mightContain(domain)));

        // create test instance
        //noinspection unchecked
        domainFilter = Mockito.mock(DomainFilter.class);
        Mockito.when(domainFilter.getDomains()).thenReturn(domains.stream());
        Mockito.when(domainFilter.getSize()).thenReturn(domains.size());
        Mockito.when(domainFilter.isBlocked(Mockito.anyString())).then(im -> new FilterDecision<>(im.getArgument(0), domains.contains(im.getArgument(0)), domainFilter));
        filter = new BloomDomainFilter<>(new StringFunnel(Charsets.UTF_8), fpp, domainFilter);
    }

    @Test
    void bloomFilteringNonBlockedDomains() {
        // for non-blocked domains the backing-filter must never be called
        nonBlockedDomains.forEach(domain -> assertFalse(filter.isBlocked(domain).isBlocked()));
        Mockito.verify(domainFilter, Mockito.never()).isBlocked(Mockito.anyString());
    }

    @Test
    void bloomFilteringBlockedDomains() {
        // each blocked domain must be checked with backing-filter because it may be a
        // false-positive
        Mockito.when(domainFilter.isBlocked(Mockito.anyString())).then(invocationOnMock -> {
            String domain = invocationOnMock.getArgument(0);
            return new FilterDecision<>(domain, domains.contains(domain), domainFilter);
        });
        domains.forEach(domain -> assertTrue(filter.isBlocked(domain).isBlocked()));
        Mockito.verify(domainFilter, Mockito.times(domains.size())).isBlocked(Mockito.anyString());
    }

    @Test
    void falsePositive() throws IOException {
        int size = 200;

        Mockito.when(domainFilter.isBlocked(Mockito.anyString()))
                .then(invocationOnMock ->
                        new FilterDecision<>(invocationOnMock.getArgument(0), false, domainFilter));

        InOrder inOrder = Mockito.inOrder(domainFilter);
        // each false positive must be checked with backing-filter
        falsePositives.stream().limit(size).forEach(domain -> assertFalse(filter.isBlocked(domain).isBlocked()));
        inOrder.verify(domainFilter, Mockito.times(size)).isBlocked(Mockito.anyString());
    }

    @Test
    void serialization() throws IOException {
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

        domains.forEach(domain -> Assertions.assertEquals(filter.isBlocked(domain).isBlocked(), deserialized.isBlocked(domain).isBlocked()));
        nonBlockedDomains.forEach(domain -> Assertions.assertEquals(filter.isBlocked(domain).isBlocked(), deserialized.isBlocked(domain).isBlocked()));
        falsePositives.forEach(domain -> Assertions.assertEquals(filter.isBlocked(domain).isBlocked(), deserialized.isBlocked(domain).isBlocked()));
    }

    @Test
    void sharedBloomFilter() {
        BloomDomainFilter<String> filter2 = new BloomDomainFilter<>(filter.getBloomFilter(), domainFilter);

        Stream.concat(nonBlockedDomains.stream(), domains.stream()).forEach(domain -> Assertions.assertEquals(filter.isBlocked(domain).isBlocked(), filter2.isBlocked(domain).isBlocked()));
    }

}
