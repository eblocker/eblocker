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
import com.google.common.hash.Funnels;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BlacklistCompilerTest {

    private List<String> blockedDomains;
    private List<String> nonBlockedDomains;

    private Path filterFilePath;
    private Path bloomFilterFilePath;

    private BlacklistCompiler blacklistCompiler;

    @Before
    public void setUp() throws IOException {
        blockedDomains = IOUtils.readLines(ClassLoader.getSystemResourceAsStream("test-data/domainblacklists/top-1000"));
        nonBlockedDomains = IOUtils.readLines(ClassLoader.getSystemResourceAsStream("test-data/domainblacklists/bottom-1000"));

        filterFilePath = Files.createTempFile(BlacklistCompilerTest.class.getName(), ".filter");
        Files.delete(filterFilePath);

        bloomFilterFilePath = Files.createTempFile(BlacklistCompilerTest.class.getName(), ".bloom");
        Files.delete(bloomFilterFilePath);

        blacklistCompiler = new BlacklistCompiler();
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(filterFilePath);
        Files.deleteIfExists(bloomFilterFilePath);
    }

    @Test
    public void compileDomainFilterFromCollection() throws IOException {
        blacklistCompiler.compile(123, "unit-test", blockedDomains, filterFilePath.toString(), bloomFilterFilePath.toString());

        Assert.assertTrue(Files.exists(filterFilePath));
        Assert.assertTrue(Files.exists(bloomFilterFilePath));

        DomainFilter<String> fileFilter = new SingleFileFilter(Charsets.UTF_8, filterFilePath);
        blockedDomains.forEach(domain -> Assert.assertTrue(fileFilter.isBlocked(domain).isBlocked()));
        nonBlockedDomains.forEach(domain -> Assert.assertFalse(fileFilter.isBlocked(domain).isBlocked()));

        BloomDomainFilter<String> bloomDomainFilter = BloomDomainFilter.readFrom(Files.newInputStream(bloomFilterFilePath), new StringFunnel(Charsets.UTF_8), fileFilter);
        blockedDomains.forEach(domain -> Assert.assertTrue(bloomDomainFilter.isBlocked(domain).isBlocked()));
        nonBlockedDomains.forEach(domain -> Assert.assertFalse(bloomDomainFilter.isBlocked(domain).isBlocked()));
    }

    @Test
    public void compileDomainFilterFromStream() throws IOException {
        Supplier<Stream<String>> domainStreamSupplier = () -> {
            BufferedReader br = new BufferedReader(new InputStreamReader(ClassLoader.getSystemResourceAsStream("test-data/domainblacklists/top-1000")));
            return br.lines().onClose(() -> {
                try {
                    br.close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        };
        blacklistCompiler.compile(123, "unit-test", domainStreamSupplier, filterFilePath.toString(), bloomFilterFilePath.toString());

        Assert.assertTrue(Files.exists(filterFilePath));
        Assert.assertTrue(Files.exists(bloomFilterFilePath));

        DomainFilter<String> fileFilter = new SingleFileFilter(Charsets.UTF_8, filterFilePath);
        blockedDomains.forEach(domain -> Assert.assertTrue(fileFilter.isBlocked(domain).isBlocked()));
        nonBlockedDomains.forEach(domain -> Assert.assertFalse(fileFilter.isBlocked(domain).isBlocked()));

        BloomDomainFilter<String> bloomDomainFilter = BloomDomainFilter.readFrom(Files.newInputStream(bloomFilterFilePath), new StringFunnel(Charsets.UTF_8), fileFilter);
        blockedDomains.forEach(domain -> Assert.assertTrue(bloomDomainFilter.isBlocked(domain).isBlocked()));
        nonBlockedDomains.forEach(domain -> Assert.assertFalse(bloomDomainFilter.isBlocked(domain).isBlocked()));
    }

    @Test
    public void compileHashFilter() throws IOException {
        List<byte[]> hashedBlockedDomains = hash(blockedDomains);
        List<byte[]> hashedNonBlockedDomains = hash(nonBlockedDomains);

        blacklistCompiler.compileHashFilter(123, "unit-test", "siphash24", hashedBlockedDomains, filterFilePath.toString(), bloomFilterFilePath.toString());

        Assert.assertTrue(Files.exists(filterFilePath));
        Assert.assertTrue(Files.exists(bloomFilterFilePath));

        DomainFilter<byte[]> fileFilter = new HashFileFilter(filterFilePath);
        hashedBlockedDomains.forEach(domain -> Assert.assertTrue(fileFilter.isBlocked(domain).isBlocked()));
        hashedNonBlockedDomains.forEach(domain -> Assert.assertFalse(fileFilter.isBlocked(domain).isBlocked()));

        BloomDomainFilter<byte[]> bloomDomainFilter = BloomDomainFilter.readFrom(Files.newInputStream(bloomFilterFilePath), Funnels.byteArrayFunnel(), fileFilter);
        hashedBlockedDomains.forEach(domain -> Assert.assertTrue(bloomDomainFilter.isBlocked(domain).isBlocked()));
        hashedNonBlockedDomains.forEach(domain -> Assert.assertFalse(bloomDomainFilter.isBlocked(domain).isBlocked()));
    }

    private List<byte[]> hash(List<String> domains) {
        HashFunction hashFunction = Hashing.sipHash24();
        return domains.stream()
            .map(domain -> hashFunction.hashString(domain, Charsets.UTF_8))
            .map(HashCode::asBytes)
            .collect(Collectors.toList());
    }
}
