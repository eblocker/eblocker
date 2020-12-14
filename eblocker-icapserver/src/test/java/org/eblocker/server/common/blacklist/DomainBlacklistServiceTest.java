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
import com.google.common.base.Charsets;
import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.eblocker.server.common.util.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DomainBlacklistServiceTest {

    private String sourcePath;
    private String cachePath;
    private ObjectMapper objectMapper;
    private ScheduledExecutorService executorService;
    private DomainBlacklistService service;

    @Before
    public void setUp() throws IOException {
        // create and populate source path for imports
        sourcePath = Files.createTempDirectory("unit-test-blacklist-source").toString();
        Files.createDirectory(Paths.get(sourcePath + "/lists"));
        createStringFilter(0, "filter0", Arrays.asList(".filter-0.version-2.com"), sourcePath + "/lists/0-v2.filter", sourcePath + "/lists/0-v2.bloom");
        createStringFilter(0, "filter0", Arrays.asList(".filter-0.version-3.com"), sourcePath + "/lists/0-v3.filter", sourcePath + "/lists/0-v3.bloom");

        // create and populate cache
        cachePath = Files.createTempDirectory("unit-test-blacklist-cache").toString();
        Files.createDirectory(Paths.get(cachePath + "/lists"));
        Files.createDirectory(Paths.get(cachePath + "/profiles"));
        Files.copy(ClassLoader.getSystemResourceAsStream("test-data/filter/domain/domainblacklistservicetest-index.json"), Paths.get(cachePath + "/index.json"));

        // create filters
        createStringFilter(0, "filter0", Arrays.asList(".filter-0.version-0.com"), cachePath + "/lists/0-v0.filter", cachePath + "/lists/0-v0.bloom");
        createStringFilter(0, "filter0", Arrays.asList(".filter-0.version-1.com"), cachePath + "/lists/0-v1.filter", cachePath + "/lists/0-v1.bloom");
        createStringFilter(1, "filter1", Arrays.asList(".filter-1.version-1.com"), cachePath + "/lists/1-v1.filter", cachePath + "/lists/1-v1.bloom");
        createBloomFilter(new StringFunnel(Charsets.UTF_8), Arrays.asList(".filter-2.version-0.com"), cachePath + "/lists/2-v0.bloom");
        createHashFilter(3, "filter3", Arrays.asList(".filter-3.version-0.com"), cachePath + "/lists/3-v0.filter", cachePath + "/lists/3-v0.bloom");

        // create object mapper
        objectMapper = new ObjectMapper();

        // create single thread executor - it is crucial to use a single threaded one here as this is required by the domain blacklist service and allows
        // to check if executions are finished
        executorService = Executors.newSingleThreadScheduledExecutor();
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(Paths.get(sourcePath));
        FileUtils.deleteDirectory(Paths.get(cachePath));
    }

    @Test
    public void testInit() {
        initService();

        // check filters have been loaded
        Assert.assertFalse(service.getFilter(0).isBlocked(".filter-0.version-0.com").isBlocked());
        Assert.assertTrue(service.getFilter(0).isBlocked(".filter-0.version-1.com").isBlocked());
        Assert.assertFalse(service.getFilter(0).isBlocked(".filter-0.version-2.com").isBlocked());
        Assert.assertFalse(service.getFilter(1).isBlocked(".filter-1.version-0.com").isBlocked());
        Assert.assertTrue(service.getFilter(1).isBlocked(".filter-1.version-1.com").isBlocked());
        Assert.assertTrue(service.getFilter(2) instanceof BloomDomainFilter);
        Assert.assertTrue(service.getFilter(3).isBlocked(Hashing.md5().hashString(".filter-3.version-0.com", Charsets.UTF_8).asBytes()).isBlocked());

        // check unused and old filter has been removed
        Assert.assertFalse(Files.exists(Paths.get(cachePath + "/lists/0-v0.filter")));
    }

    @Test
    public void testInitIoErrorStoredFilter() throws Exception {
        Files.write(Paths.get(cachePath + "/lists/0-v1.filter"), new byte[1024], StandardOpenOption.TRUNCATE_EXISTING);
        Files.write(Paths.get(cachePath + "/lists/1-v1.bloom"), new byte[1024], StandardOpenOption.TRUNCATE_EXISTING);
        Files.delete(Paths.get(cachePath + "/lists/3-v0.filter"));
        initService();

        Assert.assertNull(service.getFilter(0));
        Assert.assertNull(service.getFilter(1));
        Assert.assertNull(service.getFilter(3));
    }

    @Test
    public void testFilterUpdate() throws InterruptedException, IOException, ExecutionException {
        initService();

        DomainBlacklistService.Listener listener = Mockito.mock(DomainBlacklistService.Listener.class);
        service.addListener(listener);

        // replace filters with a single updated version of filter 0
        service.setFilters(Collections.singletonList(createFilterMetaData(0, new Date(2))));

        // schedule a dummy job and wait on it to ensure previous task is done
        executorService.submit(() -> {
        }).get();

        // check update is handled correctly
        Assert.assertFalse(service.getFilter(0).isBlocked(".filter-0.version-0.com").isBlocked());
        Assert.assertFalse(service.getFilter(0).isBlocked(".filter-0.version-1.com").isBlocked());
        Assert.assertTrue(service.getFilter(0).isBlocked(".filter-0.version-2.com").isBlocked());
        Assert.assertFalse(service.getFilter(0).isBlocked(".filter-0.version-3.com").isBlocked());
        Assert.assertNull(service.getFilter(1));

        // check last two version of filter 2 are there
        Assert.assertTrue(Files.exists(Paths.get(cachePath + "/lists/0-v2.filter")));
        Assert.assertTrue(Files.exists(Paths.get(cachePath + "/lists/0-v1.filter")));
        Assert.assertFalse(Files.exists(Paths.get(cachePath + "/lists/0-v0.filter")));
        Assert.assertTrue(Files.exists(Paths.get(cachePath + "/lists/1-v1.filter")));

        // check obsolete filter has been marked as deleted
        CacheIndex index = objectMapper.readValue(new File(cachePath + "/index.json"), CacheIndex.class);
        Assert.assertFalse(index.getFilters().get(0).get(0).isDeleted());
        Assert.assertTrue(index.getFilters().get(0).get(1).isDeleted());
        Assert.assertTrue(index.getFilters().get(1).get(0).isDeleted());

        Mockito.verify(listener).onUpdate();

        // run a second update filters with a single updated version of filter 0
        service.setFilters(Collections.singletonList(createFilterMetaData(0, new Date(3))));

        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.MINUTES);

        // check update is handled correctly
        Assert.assertFalse(service.getFilter(0).isBlocked(".filter-0.version-0.com").isBlocked());
        Assert.assertFalse(service.getFilter(0).isBlocked(".filter-0.version-1.com").isBlocked());
        Assert.assertFalse(service.getFilter(0).isBlocked(".filter-0.version-2.com").isBlocked());
        Assert.assertTrue(service.getFilter(0).isBlocked(".filter-0.version-3.com").isBlocked());

        // check only two version of 2 are still there and the rest is gone
        Assert.assertTrue(Files.exists(Paths.get(cachePath + "/lists/0-v3.filter")));
        Assert.assertTrue(Files.exists(Paths.get(cachePath + "/lists/0-v2.filter")));
        Assert.assertFalse(Files.exists(Paths.get(cachePath + "/lists/1-v1.filter")));

        // check obsolete filter has been marked as deleted
        index = objectMapper.readValue(new File(cachePath + "/index.json"), CacheIndex.class);
        Assert.assertFalse(index.getFilters().get(0).get(0).isDeleted());
        Assert.assertTrue(index.getFilters().get(0).get(1).isDeleted());
    }

    private ParentalControlFilterMetaData createFilterMetaData(int id, Date date) {
        String baseName = "lists/" + id + "-v" + date.getTime();
        return new ParentalControlFilterMetaData(id, null, null, Category.PARENTAL_CONTROL, Arrays.asList(baseName + ".filter", baseName + ".bloom"), String.valueOf(date.getTime()), date, "domainblacklist", null, false, false, null, null, null);
    }

    private void createStringFilter(int id, String name, List<String> domains, String fileFilterFileName, String bloomFilterFileName) throws IOException {
        createFileFilter(id, name, domains, fileFilterFileName);
        createBloomFilter(new StringFunnel(Charsets.UTF_8), domains, bloomFilterFileName);
    }

    private void createHashFilter(int id, String name, List<String> domains, String fileFilterFileName, String bloomFilterFileName) throws IOException {
        List<byte[]> hashes = domains.stream()
            .map(domain -> Hashing.md5().hashString(domain, Charsets.UTF_8))
            .map(HashCode::asBytes)
            .collect(Collectors.toList());
        createHashFilter(id, name, "md5", hashes, fileFilterFileName);
        createBloomFilter(Funnels.byteArrayFunnel(), hashes, bloomFilterFileName);
    }

    private void createFileFilter(int id, String name, List<String> domains, String fileFilterFileName) throws IOException {
        new SingleFileFilter(Charsets.UTF_8, Paths.get(fileFilterFileName), id, name, domains);
    }

    private void createHashFilter(int id, String name, String hashFunctionName, List<byte[]> hashes, String fileFilterFileName) throws IOException {
        new HashFileFilter(Paths.get(fileFilterFileName), id, name, hashFunctionName, hashes);
    }

    private <T> void createBloomFilter(Funnel<T> funnel, List<T> domains, String bloomFilterFileName) throws IOException {
        BloomDomainFilter<T> bloomDomainFilter = new BloomDomainFilter<>(funnel, 0.01, new CollectionFilter<>(0, domains));
        try (FileOutputStream fos = new FileOutputStream(bloomFilterFileName)) {
            bloomDomainFilter.writeTo(fos);
            fos.flush();
        }
    }

    private void initService() {
        service = new DomainBlacklistService(Charsets.UTF_8.name(), sourcePath, cachePath, objectMapper, executorService);
    }

}
