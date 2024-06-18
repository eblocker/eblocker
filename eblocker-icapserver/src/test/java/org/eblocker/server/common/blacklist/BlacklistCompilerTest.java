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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlacklistCompilerTest {

    private List<String> blockedDomains;
    private List<String> nonBlockedDomains;

    private Path filterFilePath;
    private Path bloomFilterFilePath;

    private BlacklistCompiler blacklistCompiler;

    @SuppressWarnings("DataFlowIssue")
    @BeforeEach
    void setUp() throws IOException {
        blockedDomains = IOUtils.readLines(ClassLoader.getSystemResourceAsStream("test-data/domainblacklists/top-1000"), Charset.defaultCharset());
        nonBlockedDomains = IOUtils.readLines(ClassLoader.getSystemResourceAsStream("test-data/domainblacklists/bottom-1000"), Charset.defaultCharset());

        filterFilePath = Files.createTempFile(BlacklistCompilerTest.class.getName(), ".filter");
        Files.delete(filterFilePath);

        bloomFilterFilePath = Files.createTempFile(BlacklistCompilerTest.class.getName(), ".bloom");
        Files.delete(bloomFilterFilePath);

        blacklistCompiler = new BlacklistCompiler();
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(filterFilePath);
        Files.deleteIfExists(bloomFilterFilePath);
    }

    @Test
    void compileDomainFilterFromCollection() throws IOException {
        //given
        //when
        blacklistCompiler.compile(123, "unit-test", blockedDomains, filterFilePath.toString(), bloomFilterFilePath.toString());

        //then
        assertTrue(Files.exists(filterFilePath));
        assertTrue(Files.exists(bloomFilterFilePath));

        DomainFilter<String> fileFilter = new SingleFileFilter(Charsets.UTF_8, filterFilePath);
        blockedDomains.forEach(domain -> assertTrue(fileFilter.isBlocked(domain).isBlocked()));
        nonBlockedDomains.forEach(domain -> assertFalse(fileFilter.isBlocked(domain).isBlocked()));

        BloomDomainFilter<String> bloomDomainFilter = BloomDomainFilter.readFrom(Files.newInputStream(bloomFilterFilePath), new StringFunnel(Charsets.UTF_8), fileFilter);
        blockedDomains.forEach(domain -> assertTrue(bloomDomainFilter.isBlocked(domain).isBlocked()));
        nonBlockedDomains.forEach(domain -> assertFalse(bloomDomainFilter.isBlocked(domain).isBlocked()));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void compileDomainFilterFromStream() throws IOException {
        //given
        Supplier<Stream<String>> domainStreamSupplier = () -> {
            BufferedReader br = new BufferedReader(new InputStreamReader(ClassLoader.getSystemResourceAsStream("test-data/domainblacklists/top-1000"), Charset.defaultCharset()));
            return br.lines().onClose(() -> {
                try {
                    br.close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        };
        //when
        blacklistCompiler.compile(123, "unit-test", domainStreamSupplier, filterFilePath.toString(), bloomFilterFilePath.toString());

        //then
        assertTrue(Files.exists(filterFilePath));
        assertTrue(Files.exists(bloomFilterFilePath));

        DomainFilter<String> fileFilter = new SingleFileFilter(Charsets.UTF_8, filterFilePath);
        blockedDomains.forEach(domain -> assertTrue(fileFilter.isBlocked(domain).isBlocked()));
        nonBlockedDomains.forEach(domain -> assertFalse(fileFilter.isBlocked(domain).isBlocked()));

        BloomDomainFilter<String> bloomDomainFilter = BloomDomainFilter.readFrom(Files.newInputStream(bloomFilterFilePath), new StringFunnel(Charsets.UTF_8), fileFilter);
        blockedDomains.forEach(domain -> assertTrue(bloomDomainFilter.isBlocked(domain).isBlocked()));
        nonBlockedDomains.forEach(domain -> assertFalse(bloomDomainFilter.isBlocked(domain).isBlocked()));
    }

    @Test
    void compileHashFilter() throws IOException {
        //given
        List<byte[]> hashedBlockedDomains = hash(blockedDomains);
        List<byte[]> hashedNonBlockedDomains = hash(nonBlockedDomains);

        //when
        blacklistCompiler.compileHashFilter(123, "unit-test", "siphash24", hashedBlockedDomains, filterFilePath.toString(), bloomFilterFilePath.toString());

        //then
        assertTrue(Files.exists(filterFilePath));
        assertTrue(Files.exists(bloomFilterFilePath));

        DomainFilter<byte[]> fileFilter = new HashFileFilter(filterFilePath);
        hashedBlockedDomains.forEach(domain -> assertTrue(fileFilter.isBlocked(domain).isBlocked()));
        hashedNonBlockedDomains.forEach(domain -> assertFalse(fileFilter.isBlocked(domain).isBlocked()));

        BloomDomainFilter<byte[]> bloomDomainFilter = BloomDomainFilter.readFrom(Files.newInputStream(bloomFilterFilePath), Funnels.byteArrayFunnel(), fileFilter);
        hashedBlockedDomains.forEach(domain -> assertTrue(bloomDomainFilter.isBlocked(domain).isBlocked()));
        hashedNonBlockedDomains.forEach(domain -> assertFalse(bloomDomainFilter.isBlocked(domain).isBlocked()));
    }

    private List<byte[]> hash(List<String> domains) {
        HashFunction hashFunction = Hashing.sipHash24();
        return domains.stream()
                .map(domain -> hashFunction.hashString(domain, Charsets.UTF_8))
                .map(HashCode::asBytes)
                .collect(Collectors.toList());
    }
}
