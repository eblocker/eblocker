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
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class HashFileFilterTest {

    private static Charset CHARSET = Charsets.UTF_8;
    private static HashFunction HASH_FUNCTION = Hashing.md5();

    private List<byte[]> blockedDomains;
    private List<byte[]> nonBlockedDomains;

    private Path storagePath;
    private HashFileFilter filter;

    @Before
    public void setup() throws IOException {
        blockedDomains = readAndHash(ClassLoader.getSystemResourceAsStream("test-data/domainblacklists/top-1000"));
        nonBlockedDomains = readAndHash(ClassLoader.getSystemResourceAsStream("test-data/domainblacklists/bottom-1000"));

        storagePath = Files.createTempFile("unit-test-blacklist", "");
        filter = new HashFileFilter(storagePath, 0, "unit-test", "md5", blockedDomains);
    }

    @After
    public void tearDown() throws IOException {
        if (Files.exists(storagePath)) {
            Files.delete(storagePath);
        }
    }

    @Test
    public void testGetDomains() {
        List<byte[]> domains = filter.getDomains().collect(Collectors.toList());
        // iteration order may be different but both collections must contain the same elements
        Assert.assertEquals(blockedDomains.size(), domains.size());
        blockedDomains.forEach(blockedHash -> Assert.assertTrue(domains.stream().anyMatch(hash -> Arrays.equals(blockedHash, hash))));
        domains.forEach(hash -> Assert.assertTrue(domains.stream().anyMatch(blockedHash -> Arrays.equals(hash, blockedHash))));
    }

    @Test
    public void testisBlockedDomain() {
        blockedDomains.forEach(domain -> Assert.assertTrue(filter.isBlocked(domain).isBlocked()));
        nonBlockedDomains.forEach(domain -> Assert.assertFalse(filter.isBlocked(domain).isBlocked()));
    }

    @Test
    public void testGetSize() {
        Assert.assertEquals(blockedDomains.size(), filter.getSize());
    }

    @Test
    public void testDeserialization() throws IOException {
        // file filter is always stored at storage path so there's no need to serialize first
        HashFileFilter deserialized = new HashFileFilter(storagePath);

        // check both return the same results
        blockedDomains.forEach(domain -> Assert.assertEquals(filter.isBlocked(domain).isBlocked(), deserialized.isBlocked(domain).isBlocked()));
        nonBlockedDomains.forEach(domain -> Assert.assertEquals(filter.isBlocked(domain).isBlocked(), deserialized.isBlocked(domain).isBlocked()));

        // both must contain the same domains and in exact the same sequence
        Assert.assertEquals(filter.getSize(), deserialized.getSize());
        List<byte[]> domains = filter.getDomains().collect(Collectors.toList());
        List<byte[]> deserializedDomains = deserialized.getDomains().collect(Collectors.toList());
        for (int i = 0; i < domains.size(); ++i) {
            Assert.assertArrayEquals(domains.get(i), deserializedDomains.get(i));
        }
    }

    private List<byte[]> readAndHash(InputStream in) throws IOException {
        return IOUtils.readLines(in).stream()
                .map(domain -> HASH_FUNCTION.hashString(domain, CHARSET))
                .map(HashCode::asBytes).collect(Collectors.toList());
    }
}
