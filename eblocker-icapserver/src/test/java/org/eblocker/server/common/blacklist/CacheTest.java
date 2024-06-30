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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eblocker.server.common.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.eblocker.server.common.blacklist.Cache.INDEX_JSON;
import static org.eblocker.server.common.blacklist.Cache.LIST_DIR;
import static org.eblocker.server.common.blacklist.Cache.PROFILES_DIR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class CacheTest {

    private String cachePath;
    private String indexFilePath;
    private ObjectMapper objectMapper;

    private final List<Path> createdTestFiles = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        cachePath = Files.createTempDirectory("unit-cache-test").toString();
        indexFilePath = cachePath + INDEX_JSON;

        Files.createDirectory(Paths.get(cachePath + "/" + LIST_DIR));
        Files.createDirectory(Paths.get(cachePath + "/" + PROFILES_DIR));

        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws IOException {
        FileUtils.deleteDirectory(Paths.get(cachePath));
        createdTestFiles.forEach(p -> {
            try {
                Files.delete(p);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Test
    void getFileFilterById_noCachedFileFilter_nullValueReturned() throws IOException {
        //Given
        populateCache();
        Cache cache = new Cache(cachePath, objectMapper);

        //When
        CachedFileFilter fileFilterById = cache.getFileFilterById(2);

        //Then
        assertNull(fileFilterById);
    }

    @Test
    void getFileFilterById_anyCachedFileFilter_anyValueReturned() throws IOException {
        //Given
        populateCache();
        Cache cache = new Cache(cachePath, objectMapper);

        //When
        CachedFileFilter fileFilterById = cache.getFileFilterById(1);

        //Then
        assertNotNull(fileFilterById);
    }

    @Test
    void getFileFilterById_anyEmptyCachedFileFilter_nullValueReturned() throws IOException {
        //Given
        Cache cache = spy(new Cache(cachePath, objectMapper));
        doReturn(new ArrayList<>()).when(cache).getFileFiltersById(anyInt());

        //When
        CachedFileFilter fileFilterById = cache.getFileFilterById(1);

        //Then
        assertNull(fileFilterById);
    }

    @Test
    void emptyCacheInitialization() throws IOException {
        //Given
        //When
        Cache cache = new Cache(cachePath, objectMapper);

        //Then
        // Check index (empty) has been generated
        Files.exists(Paths.get(indexFilePath));

        // Check loaded and stored index are the same (empty)
        CacheIndex loadedIndex = objectMapper.readValue(new File(indexFilePath), CacheIndex.class);
        assertTrue(loadedIndex.getFilters().isEmpty());
        assertTrue(cache.getFileFilters().isEmpty());
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void populatedCacheInitialization() throws IOException {
        //Given
        populateCache();
        //When
        Cache cache = new Cache(cachePath, objectMapper);

        //Then
        // check file filters
        assertNotNull(cache.getFileFilters());
        assertEquals(2, cache.getFileFilters().size());
        assertEquals(3, cache.getAllFileFilters().size());
        assertNotNull(cache.getFileFiltersById(0));
        assertEquals(2, cache.getFileFiltersById(0).size());
        assertNotNull(cache.getFileFiltersById(1));
        assertEquals(1, cache.getFileFiltersById(1).size());
    }

    @Test
    void markAsDeleted() throws IOException {
        //Given
        populateCache();
        Cache cache = new Cache(cachePath, objectMapper);

        //When
        cache.markFilterAsDeleted(1, 1);

        //Then
        CacheIndex index = objectMapper.readValue(new File(indexFilePath), CacheIndex.class);
        assertTrue(index.getFilters().get(1).get(0).isDeleted());
    }

    @Test
    void markAsDeleted_noFilterFileFound_noException() throws IOException {
        //given
        Cache cache = new Cache(cachePath, objectMapper);

        //when
        //then
        Assertions.assertDoesNotThrow(() -> cache.markFilterAsDeleted(1, 1));
    }

    @Test
    void cacheUpgradeFromVersion0() throws IOException {
        //Given
        // populate old cache
        Files.copy(Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("test-data/filter/domain/cachetest-index-format0.json")), Paths.get(indexFilePath));
        Files.createFile(Paths.get(cachePath + "/" + PROFILES_DIR + "/0.bloom"));

        //When
        Cache cache = new Cache(cachePath, objectMapper);

        //Then
        assertEquals(3, cache.getAllFileFilters().size());
        assertEquals(0, pathCount("/" + PROFILES_DIR));

        CacheIndex index = objectMapper.readValue(new File(indexFilePath), CacheIndex.class);
        assertEquals(5, index.getFormat());
        index.getFilters().values().stream()
                .flatMap(Collection::stream)
                .forEach(cachedFileFilter -> assertEquals("domainblacklist/string", cachedFileFilter.getFormat()));
    }

    @Test
    void cacheUpgradeFromVersion1() throws IOException {
        //Given
        // populate old cache
        Files.copy(Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("test-data/filter/domain/cachetest-index-format1.json")), Paths.get(indexFilePath));
        Files.createFile(Paths.get(cachePath + "/" + PROFILES_DIR + "/0.bloom"));

        //When
        Cache cache = new Cache(cachePath, objectMapper);

        //Then
        assertEquals(3, cache.getAllFileFilters().size());
        assertEquals(0, pathCount("/" + PROFILES_DIR));

        CacheIndex index = objectMapper.readValue(new File(indexFilePath), CacheIndex.class);
        assertEquals(5, index.getFormat());
        index.getFilters().values().stream()
                .flatMap(Collection::stream)
                .forEach(cachedFileFilter -> assertEquals("domainblacklist/string", cachedFileFilter.getFormat()));
    }

    @Test
    void cacheUpgradeFromVersion2() throws IOException {
        //Given
        // populate old cache
        Files.copy(Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("test-data/filter/domain/cachetest-index-format2.json")), Paths.get(indexFilePath));
        Files.createFile(Paths.get(cachePath + "/" + PROFILES_DIR + "/0.bloom"));

        //When
        Cache cache = new Cache(cachePath, objectMapper);

        //Then
        assertEquals(3, cache.getAllFileFilters().size());
        assertEquals(0, pathCount("/" + PROFILES_DIR));

        CacheIndex index = objectMapper.readValue(new File(indexFilePath), CacheIndex.class);
        assertEquals(5, index.getFormat());
        index.getFilters().values().stream()
                .flatMap(Collection::stream)
                .forEach(cachedFileFilter -> assertEquals("domainblacklist/string", cachedFileFilter.getFormat()));
    }

    @Test
    void cacheUpgradeFromVersion3() throws IOException {
        //Given
        // populate old cache
        Files.copy(Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("test-data/filter/domain/cachetest-index-format3.json")), Paths.get(indexFilePath));
        Files.createFile(Paths.get(cachePath + "/" + PROFILES_DIR + "/0.bloom"));

        //When
        Cache cache = new Cache(cachePath, objectMapper);

        //Then
        assertEquals(4, cache.getAllFileFilters().size());
        assertEquals(0, pathCount("/" + PROFILES_DIR));

        CacheIndex index = objectMapper.readValue(new File(indexFilePath), CacheIndex.class);
        assertEquals(5, index.getFormat());

        index.getFilters().get(0).forEach(cachedFileFilter -> assertEquals("domainblacklist/string", cachedFileFilter.getFormat()));
        index.getFilters().get(1).forEach(cachedFileFilter -> assertEquals("domainblacklist/string", cachedFileFilter.getFormat()));
        index.getFilters().get(2).forEach(cachedFileFilter -> assertEquals("domainblacklist/bloom", cachedFileFilter.getFormat()));
    }

    @Test
    void cacheUpgradeFromVersion4() throws IOException {
        //Given
        // populate old cache
        Files.copy(Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("test-data/filter/domain/cachetest-index-format4.json")), Paths.get(indexFilePath));
        Files.createFile(Paths.get(cachePath + "/" + PROFILES_DIR + "/0.bloom"));

        //When
        Cache cache = new Cache(cachePath, objectMapper);

        //Then
        assertEquals(4, cache.getAllFileFilters().size());
        assertEquals(0, pathCount("/" + PROFILES_DIR));

        CacheIndex index = objectMapper.readValue(new File(indexFilePath), CacheIndex.class);
        assertEquals(5, index.getFormat());

        index.getFilters().get(0).forEach(cachedFileFilter -> assertEquals("domainblacklist/string", cachedFileFilter.getFormat()));
        index.getFilters().get(1).forEach(cachedFileFilter -> assertEquals("domainblacklist/string", cachedFileFilter.getFormat()));
        index.getFilters().get(2).forEach(cachedFileFilter -> assertEquals("domainblacklist/bloom", cachedFileFilter.getFormat()));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void storeRemoveFileFilter() throws IOException {
        //Given
        populateCache();
        Cache cache = new Cache(cachePath, objectMapper);

        // create empty file filter files
        Path filterPath = createTestFile(0);
        Path bloomPath = createTestFile(1);

        //When
        // import filter
        CachedFileFilter cachedFileFilter = cache.storeFileFilter(0, 1, "domainblacklist/string", filterPath.toString(), bloomPath.toString());

        //Then
        assertNotNull(cachedFileFilter.getKey());
        assertEquals(0, cachedFileFilter.getKey().getId());
        assertEquals(1, cachedFileFilter.getKey().getVersion());
        assertTrue(Files.exists(Paths.get(cachePath + "/" + cachedFileFilter.getFileFilterFileName())));
        assertEquals(0, readFileContent(cachePath + "/" + cachedFileFilter.getFileFilterFileName()));
        assertTrue(Files.exists(Paths.get(cachePath + "/" + cachedFileFilter.getBloomFilterFileName())));
        assertEquals(1, readFileContent(cachePath + "/" + cachedFileFilter.getBloomFilterFileName()));

        // check cache in-memory content has been updated correctly (inserted between version 0 and 2)
        assertEquals(3, cache.getFileFiltersById(0).size());
        assertEquals(1, cache.getFileFiltersById(0).get(1).getKey().getVersion());

        // check disk cache has been updated too
        CacheIndex loadedIndex = objectMapper.readValue(new File(indexFilePath), CacheIndex.class);
        assertEquals(3, loadedIndex.getFilters().get(0).size());
        assertEquals(1, loadedIndex.getFilters().get(0).get(1).getKey().getVersion());

        // remove filter again
        cache.removeFileFilter(0, 1);

        // check files have been removed
        assertFalse(Files.exists(Paths.get(cachePath + "/" + cachedFileFilter.getFileFilterFileName())));
        assertFalse(Files.exists(Paths.get(cachePath + "/" + cachedFileFilter.getBloomFilterFileName())));

        // check cache in-memory content has been updated correctly
        assertEquals(2, cache.getFileFiltersById(0).size());
        assertEquals(2, cache.getFileFiltersById(0).get(0).getKey().getVersion());
        assertEquals(0, cache.getFileFiltersById(0).get(1).getKey().getVersion());

        // check disk cache has been updated too
        loadedIndex = objectMapper.readValue(new File(indexFilePath), CacheIndex.class);
        assertEquals(2, loadedIndex.getFilters().get(0).size());
        assertEquals(2, loadedIndex.getFilters().get(0).get(0).getKey().getVersion());
        assertEquals(0, loadedIndex.getFilters().get(0).get(1).getKey().getVersion());
    }

    @Test
    void storeRemoveBloomOnlyFilter() throws IOException {
        //Given
        Path filePath = createTestFile(0);

        Cache cache = new Cache(cachePath, objectMapper);

        //When
        CachedFileFilter cachedFilter = cache.storeFileFilter(0, 0, "domainblacklist/bloom", null, filePath.toString());

        //Then
        assertNull(cachedFilter.getFileFilterFileName());
        assertTrue(Files.exists(Paths.get(cachePath + "/" + cachedFilter.getBloomFilterFileName())));
        assertEquals(1, pathCount("/" + LIST_DIR));

        cache.removeFileFilter(0, 0);
        assertFalse(Files.exists(Paths.get(cachePath + "/" + cachedFilter.getBloomFilterFileName())));
        assertEquals(0, pathCount("/" + LIST_DIR));
    }

    private void populateCache() throws IOException {
        Files.copy(Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("test-data/filter/domain/cachetest-index.json")), Paths.get(indexFilePath));
    }

    private long pathCount(String subPath) throws IOException {
        try (Stream<Path> lists = Files.list(Paths.get(cachePath + subPath))) {
            return lists.count();
        }
    }

    private Path createTestFile(int content) throws IOException {
        Path filterPath = Files.createTempFile("unit-cache-test", "filter");
        FileOutputStream fos = new FileOutputStream(filterPath.toFile());
        fos.write(content);
        fos.flush();
        fos.close();
        createdTestFiles.add(filterPath);
        return filterPath;
    }

    private int readFileContent(String name) throws IOException {
        FileInputStream fis = new FileInputStream(name);
        int content = fis.read();
        fis.close();
        return content;
    }

}
