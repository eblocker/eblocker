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
