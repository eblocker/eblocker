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
import com.google.common.io.ByteStreams;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class SingleFileFilterTest {

    private List<String> blockedDomains;
    private List<String> nonBlockedDomains;

    private Path storagePath;
    private Path storagePathV1;

    @Before
    public void setup() throws IOException {
        blockedDomains = IOUtils.readLines(ClassLoader.getSystemResourceAsStream("test-data/domainblacklists/top-1000"));
        nonBlockedDomains = IOUtils.readLines(ClassLoader.getSystemResourceAsStream("test-data/domainblacklists/bottom-1000"));

        storagePath = Files.createTempFile("unit-test-blacklist", ".v2");
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(storagePath);
        if (storagePathV1 != null) {
            Files.deleteIfExists(storagePathV1);
        }
    }

    @Test
    public void testGetDomains() throws IOException {
        SingleFileFilter filter = new SingleFileFilter(Charsets.UTF_8, storagePath, 0, "unit-test", blockedDomains);
        List<String> domains = filter.getDomains().collect(Collectors.toList());
        // iteration order may be different but both collections must contain the same elements
        Assert.assertEquals(blockedDomains.size(), domains.size());
        Assert.assertTrue(blockedDomains.containsAll(domains));
        Assert.assertTrue(domains.containsAll(blockedDomains));
    }

    @Test
    public void testisBlockedDomain() throws IOException {
        SingleFileFilter filter = new SingleFileFilter(Charsets.UTF_8, storagePath, 0, "unit-test", blockedDomains);
        blockedDomains.forEach(domain -> Assert.assertTrue(filter.isBlocked(domain).isBlocked()));
        nonBlockedDomains.forEach(domain -> Assert.assertFalse(filter.isBlocked(domain).isBlocked()));
    }

    @Test
    public void testGetSize() throws IOException {
        SingleFileFilter filter = new SingleFileFilter(Charsets.UTF_8, storagePath, 0, "unit-test", blockedDomains);
        Assert.assertEquals(blockedDomains.size(), filter.getSize());
    }

    @Test
    public void testDeserialization() throws IOException {
        SingleFileFilter filter = new SingleFileFilter(Charsets.UTF_8, storagePath, 0, "unit-test", blockedDomains);
        SingleFileFilter deserialized = new SingleFileFilter(Charsets.UTF_8, storagePath);

        // check both return the same results
        blockedDomains.forEach(domain -> Assert.assertEquals(filter.isBlocked(domain).isBlocked(), deserialized.isBlocked(domain).isBlocked()));
        nonBlockedDomains.forEach(domain -> Assert.assertEquals(filter.isBlocked(domain).isBlocked(), deserialized.isBlocked(domain).isBlocked()));

        // both must contain the same domains and in exact the same sequence
        Assert.assertEquals(filter.getSize(), deserialized.getSize());
        Assert.assertEquals(filter.getDomains().collect(Collectors.toList()), deserialized.getDomains().collect(Collectors.toList()));
    }

    @Test
    public void testDeserializationV1() throws IOException {
        SingleFileFilter filter = new SingleFileFilter(Charsets.UTF_8, storagePath, 0, "unit-test", blockedDomains);

        // deserialize v1 filter from classpath
        storagePathV1 = Files.createTempFile("unit-test-blacklist", ".v1");
        Files.write(storagePathV1, ByteStreams.toByteArray(ClassLoader.getSystemResourceAsStream("test-data/domainblacklists/top-1000.filter.v1")));
        SingleFileFilter deserialized = new SingleFileFilter(Charsets.UTF_8, storagePathV1);

        // check both return the same results
        blockedDomains.forEach(domain -> Assert.assertEquals(filter.isBlocked(domain).isBlocked(), deserialized.isBlocked(domain).isBlocked()));
        nonBlockedDomains.forEach(domain -> Assert.assertEquals(filter.isBlocked(domain).isBlocked(), deserialized.isBlocked(domain).isBlocked()));

        // both must contain the same domains (order might be different)
        Assert.assertEquals(filter.getSize(), deserialized.getSize());
        Assert.assertEquals(filter.getDomains().collect(Collectors.toSet()), deserialized.getDomains().collect(Collectors.toSet()));
    }

    @Test
    public void testCreationFromDomainStream() throws IOException {
        SingleFileFilter filter = new SingleFileFilter(Charsets.UTF_8, storagePath, 0, "unit-test", blockedDomains::stream);
        blockedDomains.forEach(domain -> Assert.assertTrue(filter.isBlocked(domain).isBlocked()));
        nonBlockedDomains.forEach(domain -> Assert.assertFalse(filter.isBlocked(domain).isBlocked()));
    }
}
