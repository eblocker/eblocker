package org.eblocker.server.common.blacklist;

import com.google.common.hash.Funnels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

class DomainFilterLoader {
    private static final Logger log = LoggerFactory.getLogger(DomainFilterLoader.class);

    @Nonnull
    private final Charset charset;
    @Nonnull
    private final String cachePath;

    DomainFilterLoader(@Nonnull Charset charset, @Nonnull String cachePath) {
        this.charset = charset;
        this.cachePath = cachePath;
    }

    @Nullable
    DomainFilter<?> loadStoredFilter(@Nonnull CachedFileFilter storedFilter) {
        switch (storedFilter.getFormat()) {
            case "domainblacklist":
            case "domainblacklist/string":
                return loadFileFilter(storedFilter);
            case "domainblacklist/bloom":
                return loadBloomFilter(storedFilter);
            case "domainblacklist/hash-md5":
            case "domainblacklist/hash-sha1":
                return loadHashFilter(storedFilter);
            default:
                log.error("unknown format {}", storedFilter.getFormat());
                return null;
        }
    }

    @Nullable
    private DomainFilter<String> loadFileFilter(@Nonnull CachedFileFilter storedFilter) {
        try {
            long start = System.currentTimeMillis();

            DomainFilter<String> fileFilter = new SingleFileFilter(charset, Paths.get(cachePath, storedFilter.getFileFilterFileName()));

            BloomDomainFilter<String> bloomFilter;
            try (InputStream in = Files.newInputStream(Paths.get(cachePath, storedFilter.getBloomFilterFileName()))) {
                bloomFilter = BloomDomainFilter.readFrom(in, new StringFunnel(charset), fileFilter);
            }

            long stop = System.currentTimeMillis();
            log.debug("read file filter {} ({}) with {} domains in {}ms.", storedFilter.getKey().getId(), storedFilter.getKey().getVersion(), fileFilter.getSize(), (stop - start));

            return bloomFilter;
        } catch (IOException e) {
            log.error("Failed to load stored file filter {}", storedFilter.getKey(), e);
            return null;
        }
    }

    @Nullable
    private DomainFilter<String> loadBloomFilter(@Nonnull CachedFileFilter storedFilter) {
        try {
            long start = System.currentTimeMillis();
            BloomDomainFilter<String> bloomFilter;

            try (InputStream in = Files.newInputStream(Paths.get(cachePath, storedFilter.getBloomFilterFileName()))) {
                bloomFilter = BloomDomainFilter.readFrom(in, new StringFunnel(charset), StaticFilter.FALSE);
            }

            long stop = System.currentTimeMillis();
            log.debug("read bloom filter {} ({}) in {}ms.", storedFilter.getKey().getId(), storedFilter.getKey().getVersion(), (stop - start));

            return bloomFilter;
        } catch (IOException e) {
            log.error("Failed to load stored bloom filter {}", storedFilter.getKey(), e);
            return null;
        }
    }

    @Nullable
    private DomainFilter<byte[]> loadHashFilter(@Nonnull CachedFileFilter storedFilter) {
        try {
            long start = System.currentTimeMillis();

            HashFileFilter fileFilter = new HashFileFilter(Paths.get(cachePath, storedFilter.getFileFilterFileName()));

            BloomDomainFilter<byte[]> bloomFilter;
            try (InputStream in = Files.newInputStream(Paths.get(cachePath, storedFilter.getBloomFilterFileName()))) {
                bloomFilter = BloomDomainFilter.readFrom(in, Funnels.byteArrayFunnel(), fileFilter);
            }

            long stop = System.currentTimeMillis();
            log.debug("read hash file filter {} ({}) with {} domains in {}ms.", storedFilter.getKey().getId(), storedFilter.getKey().getVersion(), fileFilter.getSize(), (stop - start));

            return bloomFilter;
        } catch (IOException e) {
            log.error("Failed to load stored hash file filter {}", storedFilter.getKey(), e);
            return null;
        }
    }
}
