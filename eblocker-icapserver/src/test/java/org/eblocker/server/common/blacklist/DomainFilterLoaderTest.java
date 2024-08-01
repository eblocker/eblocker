package org.eblocker.server.common.blacklist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import org.eblocker.server.common.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DomainFilterLoaderTest {
    private String cachePath;
    private DomainFilterLoader sut;

    @BeforeEach
    void setUp() throws IOException {
        cachePath = Files.createTempDirectory("unit-test-DomainFilterLoaderTest").toString();
        Files.createDirectory(Paths.get(cachePath + "/lists"));
        Files.copy(Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("test-data/filter/domain/domainblacklistservicetest-index.json")), Paths.get(cachePath + "/index.json"));
        sut = new DomainFilterLoader(Charsets.UTF_8, cachePath);
    }

    @AfterEach
    void tearDown() throws IOException {
        FileUtils.deleteDirectory(Paths.get(cachePath));
    }

    @Nested
    class loadPossible {

        private Cache cache;

        @BeforeEach
        void setUp() throws IOException {
            ObjectMapper objectMapper = new ObjectMapper();
            cache = new Cache(cachePath, objectMapper);
        }

        @Test
        void loadStoredFilter_domainblacklist() throws IOException {
            //Given
            createStringFilter(5, List.of(".any.com"), cachePath + "/lists/5-v1.filter", cachePath + "/lists/5-v1.bloom");

            List<CachedFileFilter> fileFiltersById = cache.getFileFiltersById(5);
            //Precondition
            assertNotNull(fileFiltersById);

            //When
            DomainFilter<?> domainFilter = sut.loadStoredFilter(fileFiltersById.get(0));

            //Then
            assertNotNull(domainFilter);
        }

        @Test
        void loadStoredFilter_domainblacklist_string() throws IOException {
            //Given
            createStringFilter(0, List.of(".any.com"), cachePath + "/lists/0-v1.filter", cachePath + "/lists/0-v1.bloom");

            List<CachedFileFilter> fileFiltersById = cache.getFileFiltersById(0);
            //Precondition
            assertNotNull(fileFiltersById);

            //When
            DomainFilter<?> domainFilter = sut.loadStoredFilter(fileFiltersById.get(0));

            //Then
            assertNotNull(domainFilter);
        }

        @Test
        void loadStoredFilter_domainblacklist_bloom() throws IOException {
            //Given
            createStringFilter(2, List.of(".any.com"), cachePath + "/lists/2-v0.filter", cachePath + "/lists/2-v0.bloom");

            List<CachedFileFilter> fileFiltersById = cache.getFileFiltersById(2);
            //Precondition
            assertNotNull(fileFiltersById);

            //When
            DomainFilter<?> domainFilter = sut.loadStoredFilter(fileFiltersById.get(0));

            //Then
            assertNotNull(domainFilter);
        }

        @Test
        void loadStoredFilter_domainblacklist_hashMd5() throws IOException {
            //Given
            createHashFilter(List.of(".any.com"), cachePath + "/lists/3-v0.filter", cachePath + "/lists/3-v0.bloom", "md5");

            List<CachedFileFilter> fileFiltersById = cache.getFileFiltersById(3);
            //Precondition
            assertNotNull(fileFiltersById);

            //When
            DomainFilter<?> domainFilter = sut.loadStoredFilter(fileFiltersById.get(0));

            //Then
            assertNotNull(domainFilter);
        }

        @Test
        void loadStoredFilter_domainblacklist_hashSha1() throws IOException {
            //Given
            createHashFilter(List.of(".any.com"), cachePath + "/lists/sha1-4-v0.filter", cachePath + "/lists/sha1-4-v0.bloom", "sha1");
            //Precondition
            List<CachedFileFilter> fileFiltersById = cache.getFileFiltersById(4);
            assertNotNull(fileFiltersById);
            //When
            DomainFilter<?> domainFilter = sut.loadStoredFilter(fileFiltersById.get(0));

            //Then
            assertNotNull(domainFilter);
        }
    }

    @Test
    void loadStoredFilter_wrongFormat() {
        //Given
        CachedFileFilter cachedFileFilter = new CachedFileFilter(new CachedFilterKey(0, 0), "anyName", "anyName", "wrongFormat", false);

        //When
        DomainFilter<?> domainFilter = sut.loadStoredFilter(cachedFileFilter);

        //Then
        assertNull(domainFilter);
    }

    private void createStringFilter(int id, List<String> domains, String fileFilterFileName, String bloomFilterFileName) throws IOException {
        new SingleFileFilter(Charsets.UTF_8, Paths.get(fileFilterFileName), id, "anyName", domains);
        createBloomFilter(new StringFunnel(Charsets.UTF_8), domains, bloomFilterFileName);
    }

    private void createHashFilter(List<String> domains, String fileFilterFileName, String bloomFilterFileName, String hashFunction) throws IOException {
        List<byte[]> hashes = domains.stream()
                .map(domain -> Hashing.md5().hashString(domain, Charsets.UTF_8))
                .map(HashCode::asBytes)
                .collect(Collectors.toList());
        new HashFileFilter(Paths.get(fileFilterFileName), 3, "filter3", hashFunction, hashes);
        createBloomFilter(Funnels.byteArrayFunnel(), hashes, bloomFilterFileName);
    }

    private <T> void createBloomFilter(Funnel<T> funnel, List<T> domains, String bloomFilterFileName) throws IOException {
        BloomDomainFilter<T> bloomDomainFilter = new BloomDomainFilter<>(funnel, 0.01, new CollectionFilter<>(0, domains));
        try (FileOutputStream fos = new FileOutputStream(bloomFilterFileName)) {
            bloomDomainFilter.writeTo(fos);
            fos.flush();
        }
    }
}