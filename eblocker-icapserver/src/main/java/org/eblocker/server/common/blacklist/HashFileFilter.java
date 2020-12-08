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
import com.google.common.math.IntMath;
import org.apache.commons.io.IOUtils;
import org.eblocker.server.common.util.ByteArrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HashFileFilter implements DomainFilter<byte[]> {
    private static final Logger log = LoggerFactory.getLogger(HashFileFilter.class);

    private static final byte[] MAGIC_BYTES = {0x65, 0x42, 0x6c, 0x6b, 0x46, 0x6c, 0x74, 0x48};
    private static final byte FILE_FORMAT_VERSION = 0x01;

    private final Path storagePath;

    private long fileSize;

    private int listId;
    private String name;
    private int size;
    private String hashFunctionName;
    private int hashLength;
    private int[] buckets;

    public HashFileFilter(Path storagePath, int listId, String name,
                          String hashFunctionName, Collection<byte[]> domains) throws IOException {
        this.storagePath = storagePath;
        this.listId = listId;
        this.name = name;
        this.size = domains.size();
        this.hashFunctionName = hashFunctionName;

        this.hashLength = domains.isEmpty() ? 0 : domains.iterator().next().length;
        if (domains.stream().map(b -> b.length).anyMatch(i -> i != hashLength)) {
            throw new IllegalArgumentException("hashes with different size");
        }

        int bits = size >= 256 ? IntMath.log2(size / 256, RoundingMode.CEILING) : 0;
        this.buckets = new int[IntMath.pow(2, bits)];

        initStorageFile(domains);
    }

    public HashFileFilter(Path storagePath) throws IOException {
        this.storagePath = storagePath;
        initFromFile();
    }

    @Override
    public Integer getListId() {
        return listId;
    }

    @Override
    public String getName() {
        return "(byte-filter-file " + name + ")";
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public Stream<byte[]> getDomains() {
        return Stream
            .iterate(0, i -> i + 1)
            .limit(buckets.length)
            .map(this::readDomains)
            .flatMap(List::stream);
    }

    @Override
    public FilterDecision<byte[]> isBlocked(byte[] domain) {
        boolean isBlocked = findBucket(getBucket(domain), domain);
        return new FilterDecision<>(domain, isBlocked, this);
    }

    @Override
    public List<DomainFilter<?>> getChildFilters() {
        return Collections.emptyList();
    }

    private List<byte[]> readDomains(int bucket) {
        byte[] buffer = readBucket(bucket);

        List<byte[]> domains = new ArrayList<>();
        for (int i = 0; i < buffer.length; i += hashLength) {
            domains.add(Arrays.copyOfRange(buffer, i, i + hashLength));
        }
        return domains;
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
            log.error("failed to read hashes", e);
            throw new UncheckedIOException("failed to read hash storage", e);
        }
    }

    private boolean findBucket(int bucket, byte[] search) {
        byte[] buffer = readBucket(bucket);

        for (int i = 0; i < buffer.length; i += hashLength) {
            int cmp = ByteArrays.compare(hashLength, i, buffer, search);
            if (cmp == 0) {
                return true;
            } else if (cmp > 1) {
                break;
            }
        }

        return false;
    }

    private int getBucket(byte[] hash) {
        int bits = IntMath.log2(buckets.length, RoundingMode.UNNECESSARY);

        int bucket = 0;
        for (int i = 0; i < bits; ++i) {
            bucket <<= 1;
            bucket |= (hash[i / 8] >> 7 - i % 8) & 0x01;
        }

        return bucket;
    }

    private void initStorageFile(Collection<byte[]> domains) throws IOException {
        Map<Integer, List<byte[]>> hashes = domains.stream()
            .sorted(ByteArrays::compare)
            .collect(Collectors.groupingBy(this::getBucket));

        try (OutputStream out = Files.newOutputStream(storagePath)) {
            writeFilter(out, name, hashes);
            out.flush();
            out.close();
            fileSize = Files.size(storagePath);
        }
    }

    private void writeFilter(OutputStream os, String name, Map<Integer, List<byte[]>> hashesByBucket) throws IOException {
        // header layout:
        // bytes          type
        // 8              bytes  magic bytes
        // 1              byte   file format version
        // 4              int    list id
        // 2              short  name length
        // 2              short  hash function name length
        // 4              int    number of domains
        // 2              short  hash length in bytes
        // 4              int    number of buckets
        // (name length)  byte   name (UTF-8)
        // (hname length) byte   hash function name (UTF-8)
        // 4 * (buckets)  int    bucket offsets
        byte[] headerName = name.getBytes(Charsets.UTF_8);
        byte[] headerHashFunctionName = hashFunctionName.getBytes(Charsets.UTF_8);
        ByteBuffer header = ByteBuffer.allocate(8 + 1 + 4 + 2 + 2 + 4 + 4 + 2 + 4 * buckets.length + headerName.length + headerHashFunctionName.length);

        header.put(MAGIC_BYTES);
        header.put(FILE_FORMAT_VERSION);
        header.putInt(listId);
        header.putShort((short) headerName.length);
        header.putShort((short) headerHashFunctionName.length);
        header.putInt(size);
        header.putShort((short) hashLength);
        header.putInt(buckets.length);
        header.put(headerName);
        header.put(headerHashFunctionName);

        // append bucket offsets
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int offset = header.limit();
        for (int i = 0; i < buckets.length; ++i) {
            header.putInt(offset);
            buckets[i] = offset;
            if (hashesByBucket.get(i) != null) {
                offset += writeHashes(baos, hashesByBucket.get(i));
            }
        }
        baos.flush();

        os.write(header.array());
        os.write(baos.toByteArray());
    }

    private static int writeHashes(OutputStream os, List<byte[]> hashes) throws IOException {
        int writtenBytes = 0;
        for (byte[] hash : hashes) {
            os.write(hash);
            writtenBytes += hash.length;
        }
        return writtenBytes;
    }

    private void initFromFile() throws IOException {
        fileSize = Files.size(storagePath);
        try (FileInputStream fis = new FileInputStream(storagePath.toFile())) {
            DataInputStream dis = new DataInputStream(fis);

            byte[] magicBytes = new byte[8];
            dis.read(magicBytes);
            if (!Arrays.equals(MAGIC_BYTES, magicBytes)) {
                throw new IOException("filter is not a hash file filter");
            }

            int fileFormatVersion = dis.readByte();
            if (FILE_FORMAT_VERSION != fileFormatVersion) {
                throw new IOException("expected file format version " + FILE_FORMAT_VERSION + " but found " + fileFormatVersion);
            }

            listId = dis.readInt();

            int nameLength = dis.readShort();
            int hashFunctionNameLength = dis.readShort();

            size = dis.readInt();
            hashLength = dis.readShort();
            int headerBuckets = dis.readInt();

            byte[] headerName = new byte[nameLength];
            dis.read(headerName);
            name = new String(headerName, Charsets.UTF_8);

            byte[] headerHashFunctionName = new byte[hashFunctionNameLength];
            dis.read(headerHashFunctionName);
            hashFunctionName = new String(headerHashFunctionName, Charsets.UTF_8);

            buckets = new int[headerBuckets];
            for (int i = 0; i < headerBuckets; ++i) {
                buckets[i] = dis.readInt();
            }
        }
    }
}
