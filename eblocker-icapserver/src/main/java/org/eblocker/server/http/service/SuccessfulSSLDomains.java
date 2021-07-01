/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.http.service;

import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SuccessfulSSLDomains {
    private static final Logger log = LoggerFactory.getLogger(SuccessfulSSLDomains.class);

    private BloomFilter<String> activeBloomFilter;
    private int sizeOfActive;
    private BloomFilter<String> standbyBloomFilter;
    private int sizeOfStandby;
    private final int maxSize;
    private final int overlap;
    private final double falsePositiveProbability;

    SuccessfulSSLDomains(int maxSize, int overlap, double falsePositiveProbability) {
        if (overlap >= maxSize) {
            throw new RuntimeException("Overlap must be smaller than maxSize");
        }
        this.maxSize = maxSize;
        this.overlap = overlap;
        this.falsePositiveProbability = falsePositiveProbability;
        activeBloomFilter = createBloomFilter();
        standbyBloomFilter = createBloomFilter();
    }

    public boolean isDomainAlreadySucessful(String domain) {
        return activeBloomFilter.mightContain(domain);
    }

    public int size() {
        return sizeOfActive;
    }

    public void recordDomain(String domain) {
        boolean filterChanged = activeBloomFilter.put(domain);
        if (filterChanged) {
            updateForNewDomain(domain);
            log.debug("Recorded new already successful domain " + domain + " Size is now " + sizeOfActive);
        }
    }

    private synchronized void updateForNewDomain(String domain) {
        sizeOfActive += 1;
        if (maxSize - sizeOfActive < overlap) {
            standbyBloomFilter.put(domain);
            sizeOfStandby += 1;
        }
        if (sizeOfActive >= maxSize) {
            activeBloomFilter = standbyBloomFilter;
            sizeOfActive = sizeOfStandby;
            standbyBloomFilter = createBloomFilter();
            sizeOfStandby = 0;
        }
    }

    private BloomFilter<String> createBloomFilter() {
        return BloomFilter.create(Funnels.stringFunnel(Charsets.US_ASCII), maxSize, falsePositiveProbability);
    }
}
