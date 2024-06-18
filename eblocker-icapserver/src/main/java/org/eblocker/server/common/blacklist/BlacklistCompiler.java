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
import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Singleton
public class BlacklistCompiler {
    private static final Logger log = LoggerFactory.getLogger(BlacklistCompiler.class);

    public void compile(Integer id, String name, @Nonnull List<String> domains, String fileFilterFileName, String bloomFilterFileName) throws IOException {
        log.debug("Compiling filter {} with {} domains", name, domains.size());
        log.debug("Creating file filter for {}", name);
        SingleFileFilter fileFilter = new SingleFileFilter(Charsets.UTF_8, Paths.get(fileFilterFileName), id, name, domains);
        createBloomFilter(bloomFilterFileName, new StringFunnel(Charsets.UTF_8), fileFilter);
    }

    public void compile(Integer id, String name, Supplier<Stream<String>> domainStreamSupplier, String fileFilterFileName, String bloomFilterFileName) throws IOException {
        log.debug("Compiling filter {} from domain stream", name);
        log.debug("Creating file filter for {}", name);
        SingleFileFilter fileFilter = new SingleFileFilter(Charsets.UTF_8, Paths.get(fileFilterFileName), id, name, domainStreamSupplier);
        createBloomFilter(bloomFilterFileName, new StringFunnel(Charsets.UTF_8), fileFilter);
    }

    public void compileHashFilter(Integer id, String name, String hashFunctionName, List<byte[]> hashes, String hashFilterFileName, String bloomFilterFileName) throws IOException {
        log.debug("Compiling filter {} with {} hashes", name, hashes.size());
        log.debug("Creating hash filter for {}", name);
        HashFileFilter hashFileFilter = new HashFileFilter(Paths.get(hashFilterFileName), id, name, hashFunctionName, hashes);
        createBloomFilter(bloomFilterFileName, Funnels.byteArrayFunnel(), hashFileFilter);
    }

    private <T> void createBloomFilter(String bloomFilterFileName, Funnel<T> funnel, DomainFilter<T> fileFilter) throws IOException {
        log.debug("creating bloom filter for {}", fileFilter.getName());
        BloomDomainFilter<T> bloomDomainFilter = new BloomDomainFilter<>(funnel, 0.01, fileFilter);
        try (FileOutputStream fos = new FileOutputStream(bloomFilterFileName)) {
            bloomDomainFilter.writeTo(fos);
        }
    }

}
