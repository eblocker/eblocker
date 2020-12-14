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
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

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

public class CacheTest {

    private String cachePath;
    private String indexFilePath;
    private ObjectMapper objectMapper;

    private List<Path> createdTestFiles = new ArrayList<>();

    @Before
    public void setUp() throws IOException {
        cachePath = Files.createTempDirectory("unit-cache-test").toString();
        indexFilePath = cachePath + "/index.json";

        Files.createDirectory(Paths.get(cachePath + "/lists"));
        Files.createDirectory(Paths.get(cachePath + "/profiles"));

        objectMapper = new ObjectMapper();
    }

    @After
    public void tearDown() throws IOException {
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
    public void testEmptyCacheInitialization() throws IOException {
        Cache cache = new Cache(cachePath, objectMapper);

        // Check index (empty) has been generated
        Files.exists(Paths.get(indexFilePath));

        // Check loaded and stored index are the same (empty)
        CacheIndex loadedIndex = objectMapper.readValue(new File(indexFilePath), CacheIndex.class);
        Assert.assertTrue(loadedIndex.getFilters().isEmpty());
        Assert.assertTrue(cache.getFileFilters().isEmpty());
    }

    @Test
    public void testPopulatedCacheInitialization() throws IOException {
        populateCache();
        Cache cache = new Cache(cachePath, objectMapper);

        // check file filters
        Assert.assertNotNull(cache.getFileFilters());
        Assert.assertEquals(2, cache.getFileFilters().size());
        Assert.assertEquals(3, cache.getAllFileFilters().size());
        Assert.assertNotNull(cache.getFileFiltersById(0));
        Assert.assertEquals(2, cache.getFileFiltersById(0).size());
        Assert.assertNotNull(cache.getFileFiltersById(1));
        Assert.assertEquals(1, cache.getFileFiltersById(1).size());
    }

    @Test
    public void testMarkAsDeleted() throws IOException {
        populateCache();
        Cache cache = new Cache(cachePath, objectMapper);

        cache.markFilterAsDeleted(1, 1);

        CacheIndex index = objectMapper.readValue(new File(indexFilePath), CacheIndex.class);
        Assert.assertTrue(index.getFilters().get(1).get(0).isDeleted());
    }

    @Test
    public void testCacheUpgradeFromVersion0() throws IOException {
        // populate old cache
        Files.copy(ClassLoader.getSystemResourceAsStream("test-data/filter/domain/cachetest-index-format0.json"), Paths.get(indexFilePath));
        Files.createFile(Paths.get(cachePath + "/profiles/0.bloom"));

        // run upgrade
        Cache cache = new Cache(cachePath, objectMapper);

        // check results
        Assert.assertEquals(3, cache.getAllFileFilters().size());
        Assert.assertEquals(0, Files.list(Paths.get(cachePath + "/profiles")).count());

        CacheIndex index = objectMapper.readValue(new File(indexFilePath), CacheIndex.class);
        Assert.assertEquals(5, index.getFormat());
        index.getFilters().values().stream()
            .flatMap(Collection::stream)
            .forEach(cachedFileFilter -> Assert.assertEquals("domainblacklist/string", cachedFileFilter.getFormat()));
    }

    @Test
    public void testCacheUpgradeFromVersion1() throws IOException {
        // populate old cache
        Files.copy(ClassLoader.getSystemResourceAsStream("test-data/filter/domain/cachetest-index-format1.json"), Paths.get(indexFilePath));
        Files.createFile(Paths.get(cachePath + "/profiles/0.bloom"));

        // run upgrade
        Cache cache = new Cache(cachePath, objectMapper);

        // check results
        Assert.assertEquals(3, cache.getAllFileFilters().size());
        Assert.assertEquals(0, Files.list(Paths.get(cachePath + "/profiles")).count());

        CacheIndex index = objectMapper.readValue(new File(indexFilePath), CacheIndex.class);
        Assert.assertEquals(5, index.getFormat());
        index.getFilters().values().stream()
            .flatMap(Collection::stream)
            .forEach(cachedFileFilter -> Assert.assertEquals("domainblacklist/string", cachedFileFilter.getFormat()));
    }

    @Test
    public void testCacheUpgradeFromVersion2() throws IOException {
        // populate old cache
        Files.copy(ClassLoader.getSystemResourceAsStream("test-data/filter/domain/cachetest-index-format2.json"), Paths.get(indexFilePath));
        Files.createFile(Paths.get(cachePath + "/profiles/0.bloom"));

        // run upgrade
        Cache cache = new Cache(cachePath, objectMapper);

        // check results
        Assert.assertEquals(3, cache.getAllFileFilters().size());
        Assert.assertEquals(0, Files.list(Paths.get(cachePath + "/profiles")).count());

        CacheIndex index = objectMapper.readValue(new File(indexFilePath), CacheIndex.class);
        Assert.assertEquals(5, index.getFormat());
        index.getFilters().values().stream()
            .flatMap(Collection::stream)
            .forEach(cachedFileFilter -> Assert.assertEquals("domainblacklist/string", cachedFileFilter.getFormat()));
    }

    @Test
    public void testCacheUpgradeFromVersion3() throws IOException {
        // populate old cache
        Files.copy(ClassLoader.getSystemResourceAsStream("test-data/filter/domain/cachetest-index-format3.json"), Paths.get(indexFilePath));
        Files.createFile(Paths.get(cachePath + "/profiles/0.bloom"));

        // run upgrade
        Cache cache = new Cache(cachePath, objectMapper);

        // check results
        Assert.assertEquals(4, cache.getAllFileFilters().size());
        Assert.assertEquals(0, Files.list(Paths.get(cachePath + "/profiles")).count());

        CacheIndex index = objectMapper.readValue(new File(indexFilePath), CacheIndex.class);
        Assert.assertEquals(5, index.getFormat());

        index.getFilters().get(0).stream().forEach(cachedFileFilter -> Assert.assertEquals("domainblacklist/string", cachedFileFilter.getFormat()));
        index.getFilters().get(1).stream().forEach(cachedFileFilter -> Assert.assertEquals("domainblacklist/string", cachedFileFilter.getFormat()));
        index.getFilters().get(2).stream().forEach(cachedFileFilter -> Assert.assertEquals("domainblacklist/bloom", cachedFileFilter.getFormat()));
    }

    @Test
    public void testCacheUpgradeFromVersion4() throws IOException {
        // populate old cache
        Files.copy(ClassLoader.getSystemResourceAsStream("test-data/filter/domain/cachetest-index-format4.json"), Paths.get(indexFilePath));
        Files.createFile(Paths.get(cachePath + "/profiles/0.bloom"));

        // run upgrade
        Cache cache = new Cache(cachePath, objectMapper);

        // check results
        Assert.assertEquals(4, cache.getAllFileFilters().size());
        Assert.assertEquals(0, Files.list(Paths.get(cachePath + "/profiles")).count());

        CacheIndex index = objectMapper.readValue(new File(indexFilePath), CacheIndex.class);
        Assert.assertEquals(5, index.getFormat());

        index.getFilters().get(0).stream().forEach(cachedFileFilter -> Assert.assertEquals("domainblacklist/string", cachedFileFilter.getFormat()));
        index.getFilters().get(1).stream().forEach(cachedFileFilter -> Assert.assertEquals("domainblacklist/string", cachedFileFilter.getFormat()));
        index.getFilters().get(2).stream().forEach(cachedFileFilter -> Assert.assertEquals("domainblacklist/bloom", cachedFileFilter.getFormat()));
    }

    @Test
    public void testStoreRemoveFileFilter() throws IOException {
        populateCache();
        Cache cache = new Cache(cachePath, objectMapper);

        // create empty file filter files
        Path filterPath = createTestFile(0);
        Path bloomPath = createTestFile(1);

        // import filter
        CachedFileFilter cachedFileFilter = cache.storeFileFilter(0, 1, "domainblacklist/string", filterPath.toString(), bloomPath.toString());

        // check filter has been imported correctly
        Assert.assertNotNull(cachedFileFilter.getKey());
        Assert.assertEquals(0, cachedFileFilter.getKey().getId());
        Assert.assertEquals(1, cachedFileFilter.getKey().getVersion());
        Assert.assertTrue(Files.exists(Paths.get(cachePath + "/" + cachedFileFilter.getFileFilterFileName())));
        Assert.assertEquals(0, readFileContent(cachePath + "/" + cachedFileFilter.getFileFilterFileName()));
        Assert.assertTrue(Files.exists(Paths.get(cachePath + "/" + cachedFileFilter.getBloomFilterFileName())));
        Assert.assertEquals(1, readFileContent(cachePath + "/" + cachedFileFilter.getBloomFilterFileName()));

        // check cache in-memory content has been updated correctly (inserted between version 0 and 2)
        Assert.assertEquals(3, cache.getFileFiltersById(0).size());
        Assert.assertEquals(1, cache.getFileFiltersById(0).get(1).getKey().getVersion());

        // check disk cache has been updated too
        CacheIndex loadedIndex = objectMapper.readValue(new File(indexFilePath), CacheIndex.class);
        Assert.assertEquals(3, loadedIndex.getFilters().get(0).size());
        Assert.assertEquals(1, loadedIndex.getFilters().get(0).get(1).getKey().getVersion());

        // remove filter again
        cache.removeFileFilter(0, 1);

        // check files have been removed
        Assert.assertFalse(Files.exists(Paths.get(cachePath + "/" + cachedFileFilter.getFileFilterFileName())));
        Assert.assertFalse(Files.exists(Paths.get(cachePath + "/" + cachedFileFilter.getBloomFilterFileName())));

        // check cache in-memory content has been updated correctly
        Assert.assertEquals(2, cache.getFileFiltersById(0).size());
        Assert.assertEquals(2, cache.getFileFiltersById(0).get(0).getKey().getVersion());
        Assert.assertEquals(0, cache.getFileFiltersById(0).get(1).getKey().getVersion());

        // check disk cache has been updated too
        loadedIndex = objectMapper.readValue(new File(indexFilePath), CacheIndex.class);
        Assert.assertEquals(2, loadedIndex.getFilters().get(0).size());
        Assert.assertEquals(2, loadedIndex.getFilters().get(0).get(0).getKey().getVersion());
        Assert.assertEquals(0, loadedIndex.getFilters().get(0).get(1).getKey().getVersion());
    }

    @Test
    public void testStoreRemoveBloomOnlyFilter() throws IOException {
        Path filePath = createTestFile(0);

        Cache cache = new Cache(cachePath, objectMapper);
        CachedFileFilter cachedFilter = cache.storeFileFilter(0, 0, "domainblacklist/bloom", null, filePath.toString());

        Assert.assertNull(cachedFilter.getFileFilterFileName());
        Assert.assertTrue(Files.exists(Paths.get(cachePath + "/" + cachedFilter.getBloomFilterFileName())));
        Assert.assertEquals(1, Files.list(Paths.get(cachePath + "/lists")).count());

        cache.removeFileFilter(0, 0);
        Assert.assertFalse(Files.exists(Paths.get(cachePath + "/" + cachedFilter.getBloomFilterFileName())));
        Assert.assertEquals(0, Files.list(Paths.get(cachePath + "/lists")).count());
    }

    @Test
    public void testStoreRemoveFileOnlyFilter() throws IOException {
        Path filePath = createTestFile(0);

        Cache cache = new Cache(cachePath, objectMapper);
        CachedFileFilter cachedFilter = cache.storeFileFilter(0, 0, "domainblacklist/string", filePath.toString(), null);

        Assert.assertNull(cachedFilter.getBloomFilterFileName());
        Assert.assertTrue(Files.exists(Paths.get(cachePath + "/" + cachedFilter.getFileFilterFileName())));
        Assert.assertEquals(1, Files.list(Paths.get(cachePath + "/lists")).count());

        cache.removeFileFilter(0, 0);
        Assert.assertFalse(Files.exists(Paths.get(cachePath + "/" + cachedFilter.getFileFilterFileName())));
        Assert.assertEquals(0, Files.list(Paths.get(cachePath + "/lists")).count());
    }

    private void populateCache() throws IOException {
        Files.copy(ClassLoader.getSystemResourceAsStream("test-data/filter/domain/cachetest-index.json"), Paths.get(indexFilePath));
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
