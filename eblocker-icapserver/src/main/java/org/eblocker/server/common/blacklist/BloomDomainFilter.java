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

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class BloomDomainFilter<T> implements DomainFilter<T> {

    private static final byte[] MAGIC_BYTES =  {  0x65, 0x42, 0x6c, 0x6b, 0x42, 0x6c, 0x6d };
    private static final byte FILE_FORMAT_VERSION = 0x01;

    private final DomainFilter<T> filter;
    private final BloomFilter<T> bloomFilter;

    public BloomDomainFilter(Funnel<T> funnel, double probability, DomainFilter<T> filter) {
        this.filter = filter;

        bloomFilter = BloomFilter.create(funnel, filter.getSize(), probability);
        filter.getDomains().forEach(bloomFilter::put);
    }

    public BloomDomainFilter(BloomFilter<T> bloomFilter, DomainFilter<T> filter) {
        this.bloomFilter = bloomFilter;
        this.filter = filter;
    }

    public BloomFilter<T> getBloomFilter() {
        return bloomFilter;
    }

    @Override
    public Integer getListId() {
        return filter.getListId();
    }

    @Override
    public String getName() {
        return "(bloom " + filter.getName() + ")";
    }

    @Override
    public int getSize() {
        return filter.getSize();
    }

    @Override
    public Stream<T> getDomains() {
        return filter.getDomains();
    }

    @Override
    public FilterDecision<T> isBlocked(T domain) {
        if (!bloomFilter.mightContain(domain)) {
            return new FilterDecision<>(domain, false, this);
        }

        return filter.isBlocked(domain);
    }

    @Override
    public List<DomainFilter<?>> getChildFilters() {
        return Collections.singletonList(filter);
    }

    public void writeTo(OutputStream os) throws IOException {
        os.write(MAGIC_BYTES);
        os.write(FILE_FORMAT_VERSION);
        bloomFilter.writeTo(os);
    }

    public static <T> BloomDomainFilter<T> readFrom(InputStream is, Funnel<T> funnel, DomainFilter<T> filter) throws IOException {
        byte[] magicBytes = new byte[7];
        is.read(magicBytes);

        if (!Arrays.equals(MAGIC_BYTES, magicBytes)) {
            throw new IOException("file is not a bloom domain filter");
        }

        byte fileFormatVersion = (byte) is.read();
        if (FILE_FORMAT_VERSION != fileFormatVersion) {
            throw new IOException("expected file format version " + FILE_FORMAT_VERSION + " but found " + fileFormatVersion);
        }

        BloomFilter<T> bloomFilter = BloomFilter.readFrom(is, funnel);
        return new BloomDomainFilter<>(bloomFilter, filter);
    }

}
