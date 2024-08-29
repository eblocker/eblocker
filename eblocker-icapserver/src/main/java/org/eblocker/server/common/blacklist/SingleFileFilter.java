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

import com.google.common.collect.Sets;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SingleFileFilter implements DomainFilter<String> {
    private static final Logger log = LoggerFactory.getLogger(SingleFileFilter.class);

    private static final byte[] MAGIC_BYTES = { 0x65, 0x42, 0x6c, 0x6b, 0x46, 0x6c, 0x74, 0x72 };
    private static final byte FILE_FORMAT_VERSION = 0x02;

    private static final HashFunction hashFunction = Hashing.sipHash24();

    private final Charset charset;
    private final Path storagePath;

    private Function<String, Integer> domainBucketFn;
    private long fileSize;

    private int listId;
    private String name;
    private int size;
    private int[] buckets;

    /**
     * Create a filter with in-memory loaded domain collection.
     */
    public SingleFileFilter(Charset charset, Path storagePath, int listId, String name, Collection<String> domains) throws IOException {
        this.charset = charset;
        this.storagePath = storagePath;
        this.domainBucketFn = this::mapDomainToBucketV2;
        this.listId = listId;
        this.name = name;
        this.size = domains.size();
        this.buckets = new int[Math.max(size / 256, 1)];
        initStorageFile(domains);
    }

    /**
     * Create a filter from a stream of domains requiring no more than one domain at a time to be in memory.
     * <p>
     * This method takes more time but only a small amount of ram. The stream is iterated three times so a supplier
     * is needed to re-create the stream from start.
     */
    public SingleFileFilter(Charset charset, Path storagePath, int listId, String name, Supplier<Stream<String>> domainStreamSupplier) throws IOException {
        this.charset = charset;
        this.storagePath = storagePath;
        this.domainBucketFn = this::mapDomainToBucketV2;
        this.listId = listId;
        this.name = name;
        initStorageFileFromStream(domainStreamSupplier);
    }

    /**
     * Load a stored filter.
     */
    public SingleFileFilter(Charset charset, Path storagePath) throws IOException {
        this.charset = charset;
        this.storagePath = storagePath;
        initFromFile();
    }

    @Nullable
    @Override
    public Integer getListId() {
        return listId;
    }

    @Nonnull
    @Override
    public String getName() {
        return "(file " + name + ")";
    }

    @Override
    public int getSize() {
        return size;
    }

    @Nonnull
    @Override
    public Stream<String> getDomains() {
        return Stream
                .iterate(0, i -> i + 1)
                .limit(buckets.length)
                .map(this::readDomains)
                .flatMap(Set::stream);
    }

    @Nonnull
    @Override
    public FilterDecision<String> isBlocked(String domain) {
        boolean isBlocked = findBucket(domainBucketFn.apply(domain), domain);
        return new FilterDecision<>(domain, isBlocked, this);
    }

    @Nonnull
    @Override
    public List<DomainFilter<?>> getChildFilters() {
        return Collections.emptyList();
    }

    private Set<String> readDomains(int bucket) {
        byte[] buffer = readBucket(bucket);
        if (buffer.length == 0) {
            return Collections.emptySet();
        }
        return Sets.newHashSet(new String(buffer).split("\\n"));
    }

    private byte[] readBucket(int bucket) {
        log.debug("reading bucket {}", bucket);
        long offset = buckets[bucket];
        long offsetNextBucket = bucket + 1 < buckets.length ? buckets[bucket + 1] : fileSize;
        long length = offsetNextBucket - offset;
        byte[] buffer = new byte[(int) length];

        try (FileInputStream fis = new FileInputStream(storagePath.toFile())) {
            fis.skip(offset);
            IOUtils.readFully(fis, buffer);
            return buffer;
        } catch (IOException e) {
            log.error("failed to read domains", e);
            throw new UncheckedIOException("failed to read domain storage", e);
        }
    }

    private boolean findBucket(int bucket, String domain) {
        byte[] buffer = readBucket(bucket);
        byte[] search = domain.getBytes(charset);
        int matched = 0;

        for (int i = 0; i < buffer.length; ++i) {
            byte c = buffer[i];
            if (c == '\n') {
                if (matched == search.length) {
                    return true;
                }
                matched = 0;
            } else if (matched < search.length && c == search[matched]) {
                ++matched;
            } else {
                matched = 0;
                ++i;
                while (i < buffer.length && buffer[i] != '\n') {
                    ++i;
                }
            }
        }

        return false;
    }

    // Broken: negative hash values are not mapped correctly. Solely kept for
    // backward compatibility with existing v1 filters.
    private int mapDomainToBucketV1(String value) {
        long hash = hashFunction.hashBytes(value.getBytes(charset)).asLong();
        long bucket = buckets.length / 2 + hash % buckets.length / 2;
        return (int) bucket;
    }

    private int mapDomainToBucketV2(String value) {
        long hash = hashFunction.hashBytes(value.getBytes(charset)).asLong();
        long bucket = (hash & 0x7fffffffffffffffL) % buckets.length;
        return (int) bucket;
    }

    private void initStorageFile(Collection<String> domains) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(storagePath.toFile())) {
            writeFilter(fos, domains.stream().collect(Collectors.groupingBy(domainBucketFn)));
        }
        fileSize = Files.size(storagePath);
    }

    private void writeFilter(OutputStream os, Map<Integer, List<String>> domainsByHash) throws IOException {
        ByteBuffer header = createHeader();

        // append bucket offsets
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int offset = baos.size() + header.limit();
        for (int i = 0; i < buckets.length; ++i) {
            header.putInt(offset);
            buckets[i] = offset;
            if (domainsByHash.get(i) != null) {
                offset += writeDomains(baos, charset, domainsByHash.get(i));
            }
        }
        baos.flush();

        os.write(header.array());
        os.write(baos.toByteArray());
    }

    private ByteBuffer createHeader() {
        // header layout:
        // bytes          type
        // 8              bytes  magic bytes
        // 1              byte   file format version
        // 4              int    list id
        // 2              short  name length
        // (length)       byte   name (charset)
        // +4             int    number of domains
        // +4             int    number of buckets
        // +4 * (buckets) int    bucket offsets
        byte[] headerName = name.getBytes(charset);
        ByteBuffer header = ByteBuffer.allocate(8 + 4 + 1 + 2 + headerName.length + 4 + 4 + 4 * buckets.length);

        header.put(MAGIC_BYTES);
        header.put(FILE_FORMAT_VERSION);
        header.putInt(listId);
        header.putShort((short) headerName.length);
        header.put(headerName);
        header.putInt(size);
        header.putInt(buckets.length);

        return header;
    }

    private static int writeDomains(OutputStream os, Charset charset, List<String> domains) throws IOException {
        int writtenBytes = 0;
        for (String domain : domains) {
            byte[] bytes = domain.getBytes(charset);
            os.write(bytes);
            os.write('\n');
            writtenBytes += bytes.length + 1;
        }
        return writtenBytes;
    }

    private void initStorageFileFromStream(Supplier<Stream<String>> domainStreamSupplier) throws IOException {
        initStorageFileFromStreamCountDomains(domainStreamSupplier.get());
        initStorageFileFromStreamCalculateBucketOffsets(domainStreamSupplier.get());
        initStorageFileFromStreamWriteDomains(domainStreamSupplier.get());
    }

    private void initStorageFileFromStreamCountDomains(Stream<String> domains) {
        this.size = (int) domains.count();
        this.buckets = new int[Math.max(size / 256, 1)];
    }

    private void initStorageFileFromStreamCalculateBucketOffsets(Stream<String> domains) {
        // calculate size of each bucket
        int[] bucketsLength = new int[buckets.length];
        domains.forEach(domain -> bucketsLength[domainBucketFn.apply(domain)] += domain.getBytes(charset).length + 1);

        // calculate offsets (based on zero)
        for (int i = 1; i < buckets.length; ++i) {
            buckets[i] = buckets[i - 1] + bucketsLength[i - 1];
        }
    }

    private void initStorageFileFromStreamWriteDomains(Stream<String> domains) throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(storagePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            ByteBuffer headerBuffer = createHeader();
            for (int i = 0; i < buckets.length; ++i) {
                buckets[i] += headerBuffer.limit();
                headerBuffer.putInt(buckets[i]);
            }
            headerBuffer.flip();
            channel.write(headerBuffer);

            ByteBuffer domainBuffer = ByteBuffer.allocate(1024);
            int[] bucketsWriteIndices = new int[buckets.length];
            Iterator<String> it = domains.iterator(); // Stream api not used here to avoid wrapping und un-wrapping of IOExceptions.
            while (it.hasNext()) {
                String domain = it.next();
                domainBuffer.clear();
                domainBuffer.put(domain.getBytes(charset));
                domainBuffer.put((byte) '\n');
                domainBuffer.flip();

                int bucket = domainBucketFn.apply(domain);
                int offset = buckets[bucket] + bucketsWriteIndices[bucket];
                channel.position(offset);
                channel.write(domainBuffer);
                bucketsWriteIndices[bucket] += domainBuffer.limit();
            }
        }
        fileSize = Files.size(storagePath);
    }

    private void initFromFile() throws IOException {
        fileSize = Files.size(storagePath);
        try (FileInputStream fis = new FileInputStream(storagePath.toFile())) {
            DataInputStream dis = new DataInputStream(fis);

            byte[] magicBytes = new byte[8];
            dis.read(magicBytes);
            if (!Arrays.equals(MAGIC_BYTES, magicBytes)) {
                throw new IOException("filter is not a single file filter");
            }

            int fileFormatVersion = dis.readByte();
            if (fileFormatVersion == 0x01) {
                domainBucketFn = this::mapDomainToBucketV1;
            } else if (fileFormatVersion == 0x02) {
                domainBucketFn = this::mapDomainToBucketV2;
            } else {
                throw new IOException("expected file format version " + FILE_FORMAT_VERSION + " but found " + fileFormatVersion);
            }

            listId = dis.readInt();

            int nameLength = dis.readShort();
            byte[] headerName = new byte[nameLength];
            dis.read(headerName);
            name = new String(headerName, charset);

            size = dis.readInt();

            int headerBuckets = dis.readInt();
            buckets = new int[headerBuckets];
            for (int i = 0; i < headerBuckets; ++i) {
                buckets[i] = dis.readInt();
            }
        }
    }
}
