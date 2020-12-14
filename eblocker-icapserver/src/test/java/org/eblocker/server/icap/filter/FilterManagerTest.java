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
package org.eblocker.server.icap.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.CryptoService;
import org.eblocker.crypto.CryptoServiceFactory;
import org.eblocker.crypto.json.JSONCryptoHandler;
import org.eblocker.crypto.keys.SystemKey;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.transaction.TransactionContext;
import org.eblocker.server.common.util.FileUtils;
import org.eblocker.server.icap.filter.learning.AsynchronousLearningFilter;
import org.eblocker.server.icap.filter.learning.NotLearningFilter;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class FilterManagerTest {
    private static final String FILTER_NAME = "test-filter";
    private static final String TRACKER_URL = "http://0tracker.com/";
    private static final String FILE_SUFFIX = ".json.enc";

    private List<FilterStoreConfiguration> defaultConfigurations;
    private Path defaultConfigurationsPath;
    private Path resourceFile;

    private Path keyFile;
    private Path cacheDirectory;
    private DataSource dataSource;
    private ObjectMapper objectMapper;
    private SystemKey systemKey;
    private Path tempDirectory;

    @Before
    public void setUp() throws Exception {
        String tempFilePrefix = FilterManager.class.getSimpleName();

        dataSource = Mockito.mock(DataSource.class);
        Mockito.when(dataSource.getAll(FilterStoreConfiguration.class)).thenReturn(new ArrayList<>());
        AtomicInteger nextId = new AtomicInteger();
        Mockito.when(dataSource.nextId(FilterStoreConfiguration.class)).then(im -> nextId.incrementAndGet());

        objectMapper = new ObjectMapper();
        cacheDirectory = Files.createTempDirectory(tempFilePrefix + "-cache");

        keyFile = Files.createTempFile("system.", ".key");
        Files.deleteIfExists(keyFile);
        systemKey = new SystemKey(keyFile.toString());

        tempDirectory = Files.createTempDirectory(tempFilePrefix + "-tmp");
        resourceFile = Files.createTempFile(tempFilePrefix + "-resource", ".txt");

        defaultConfigurations = new ArrayList<>();
        defaultConfigurations.add(new FilterStoreConfiguration(0, FILTER_NAME, Category.EBLOCKER, true, 0, new String[]{ resourceFile.toString() }, FilterLearningMode.NONE, FilterDefinitionFormat.EASYLIST, false, new String[0], true));
        defaultConfigurationsPath = Files.createTempFile(FilterManager.class.getSimpleName() + "-default", ".json");
        objectMapper.writeValue(defaultConfigurationsPath.toFile(), defaultConfigurations);
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteDirectory(cacheDirectory);
        FileUtils.deleteDirectory(tempDirectory);
        Files.deleteIfExists(keyFile);
        Files.deleteIfExists(defaultConfigurationsPath);
        Files.deleteIfExists(resourceFile);
    }

    @Test
    public void testDefaultFilterStoreCreation() {
        FilterManager manager = createManager();
        Filter filter = manager.getFilter(Category.EBLOCKER);
        Assert.assertNotNull(filter);
        assertEquals(NotLearningFilter.class, filter.getClass());
        Assert.assertTrue(Files.exists(cacheDirectory.resolve("0" + FILE_SUFFIX)));
    }

    @Test
    public void testDefaultFilterStoreUpdate() throws IOException, CryptoException {
        setupStoredConfig(defaultConfigurations);

        // create new default configuration on disk
        List<FilterStoreConfiguration> newDefaultConfigurations = Collections
            .singletonList(new FilterStoreConfiguration(0, FILTER_NAME, Category.EBLOCKER, true, 1, new String[]{ resourceFile.toString() }, FilterLearningMode.ASYNCHRONOUS, FilterDefinitionFormat.EASYLIST, false, new String[0], true));
        objectMapper.writeValue(defaultConfigurationsPath.toFile(), newDefaultConfigurations);

        FilterManager manager = createManager();
        Filter filter = manager.getFilter(Category.EBLOCKER);
        Assert.assertNotNull(filter);
        assertEquals(AsynchronousLearningFilter.class, filter.getClass());
        Assert.assertTrue(Files.exists(cacheDirectory.resolve(0 + FILE_SUFFIX)));
    }

    @Test
    public void testDefaultFilterStoreRemoval() throws IOException, CryptoException {
        setupStoredConfig(defaultConfigurations);

        // create new empty default configuration on disk
        objectMapper.writeValue(defaultConfigurationsPath.toFile(), Collections.emptyList());

        FilterManager manager = createManager();
        Filter filter = manager.getFilter(Category.EBLOCKER);
        Assert.assertNotNull(filter);
        assertEquals(NullFilter.class, filter.getClass());
        Assert.assertFalse(Files.exists(cacheDirectory.resolve("0" + FILE_SUFFIX)));
    }

    // Use bootstrap lists if no cached lists are available:
    @Test
    public void testFilterStoreUpdater() throws Exception {
        FilterManager manager = createManager();
        assertEquals(Decision.NO_DECISION, decisionForURL(manager.getFilter(Category.EBLOCKER), TRACKER_URL)); // Not blocked, since we bootstrapped an empty list

        waitForLastModificationTime();
        writeEasyListToCache();

        manager.getFilterStoreUpdater().run();
        assertEquals(Decision.BLOCK, decisionForURL(manager.getFilter(Category.EBLOCKER), TRACKER_URL)); // Now the list has been updated with easyprivacy.txt
    }

    // Do not use bootstrap lists if cached lists (from the eblocker-lists package) are available:
    @Test
    public void testUseAlreadyAvailableLists() throws Exception {
        writeEasyListToCache();
        waitForLastModificationTime();
        FilterManager manager = createManager();
        assertEquals(Decision.BLOCK, decisionForURL(manager.getFilter(Category.EBLOCKER), TRACKER_URL));
    }

    @Test
    public void testAddFilter() throws IOException {
        FilterManager manager = createManager();
        writeEasyListToCache();
        FilterStoreConfiguration newConfiguration = new FilterStoreConfiguration(null, "test", Category.ADS, false, System.currentTimeMillis(), new String[]{ resourceFile.toString() }, FilterLearningMode.ASYNCHRONOUS, FilterDefinitionFormat.EASYLIST, true,
            new String[0], true);
        FilterStoreConfiguration savedConfiguration = manager.addFilter(newConfiguration);
        Assert.assertNotNull(savedConfiguration.getId());
        assertEquals(newConfiguration.getName(), savedConfiguration.getName());
        assertEquals(newConfiguration.getCategory(), savedConfiguration.getCategory());
        assertEquals(newConfiguration.getLearningMode(), savedConfiguration.getLearningMode());
        assertEquals(newConfiguration.getVersion(), savedConfiguration.getVersion());
        assertEquals(newConfiguration.getFormat(), savedConfiguration.getFormat());
        Assert.assertArrayEquals(newConfiguration.getResources(), savedConfiguration.getResources());
        Assert.assertArrayEquals(newConfiguration.getRuleFilters(), savedConfiguration.getRuleFilters());
        assertEquals(newConfiguration.isEnabled(), savedConfiguration.isEnabled());

        Assert.assertSame(savedConfiguration, manager.getFilterStoreConfigurationById(savedConfiguration.getId()));
        assertEquals(Decision.BLOCK, decisionForURL(manager.getFilter(Category.ADS), TRACKER_URL)); // Now the list has been updated with easyprivacy.txt

        Mockito.verify(dataSource).nextId(FilterStoreConfiguration.class);
        Mockito.verify(dataSource).save(Mockito.any(FilterStoreConfiguration.class), Mockito.eq(savedConfiguration.getId()));
    }

    @Test
    public void testRemoveFilter() throws IOException, CryptoException {
        // create single non-default config
        objectMapper.writeValue(defaultConfigurationsPath.toFile(), Collections.emptyList());
        setupStoredConfig(new ArrayList<>(
            Collections.singletonList(new FilterStoreConfiguration(0, FILTER_NAME, Category.EBLOCKER, false, 0, new String[]{ resourceFile.toString() }, FilterLearningMode.ASYNCHRONOUS, FilterDefinitionFormat.EASYLIST, true, new String[0], true))));

        FilterManager manager = createManager();
        Assert.assertNotNull(manager.getFilterStoreConfigurationById(0));
        assertEquals(NotLearningFilter.class, manager.getFilter(Category.EBLOCKER).getClass());

        manager.removeFilter(0);
        Assert.assertNull(manager.getFilterStoreConfigurationById(0));
        assertEquals(NullFilter.class, manager.getFilter(Category.EBLOCKER).getClass());
        Mockito.verify(dataSource).delete(FilterStoreConfiguration.class, 0);
    }

    @Test
    public void testDisableEnableFilter() throws IOException, CryptoException {
        setupStoredConfig(defaultConfigurations);
        writeEasyListToCache();

        FilterManager manager = createManager();
        Assert.assertNotNull(manager.getFilterStoreConfigurationById(0));
        assertEquals(NotLearningFilter.class, manager.getFilter(Category.EBLOCKER).getClass());
        assertEquals(Decision.BLOCK, decisionForURL(manager.getFilter(Category.EBLOCKER), TRACKER_URL)); // Now the list has been updated with easyprivacy.txt

        manager.updateFilter(new FilterStoreConfiguration(0, null, null, false, 0, null, null, null, false, null, false));
        assertEquals(NullFilter.class, manager.getFilter(Category.EBLOCKER).getClass());

        manager.updateFilter(new FilterStoreConfiguration(0, null, null, false, 0, null, null, null, false, null, true));
        assertEquals(NotLearningFilter.class, manager.getFilter(Category.EBLOCKER).getClass());
        assertEquals(Decision.BLOCK, decisionForURL(manager.getFilter(Category.EBLOCKER), TRACKER_URL)); // Now the list has been updated with easyprivacy.txt
    }

    private void setupStoredConfig(List<FilterStoreConfiguration> configurations) throws IOException, CryptoException {
        Mockito.when(dataSource.getAll(FilterStoreConfiguration.class)).thenReturn(configurations);
        for (FilterStoreConfiguration configuration : configurations) {
            Path cachedPath = Files.createFile(cacheDirectory.resolve(configuration.getId() + FILE_SUFFIX));
            try (OutputStream out = Files.newOutputStream(cachedPath)) {
                CryptoService cryptoService = CryptoServiceFactory.getInstance().setKey(systemKey.get()).build();
                JSONCryptoHandler.encrypt(new FilterStore(new NotLearningFilter()), cryptoService, out);
            }
        }
    }

    private FilterManager createManager() {
        FilterManager manager = new FilterManager(defaultConfigurationsPath.toString(), cacheDirectory.toString(), FILE_SUFFIX, dataSource, objectMapper, systemKey, tempDirectory.toString());
        manager.init();
        manager.getFilterStoreUpdater().run(); // run first update / load
        return manager;
    }

    private Decision decisionForURL(Filter filter, String url) {
        TransactionContext context = new TestContext(url);
        FilterResult result = filter.filter(context);
        return result.getDecision();
    }

    private void waitForLastModificationTime() {
        try {
            Thread.sleep(1000); // Wait a second, because the resolution of the file modification date is only 1000ms, while the FilterStore.getLastUpdate() has 1ms accuracy.
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writeEasyListToCache() throws IOException {
        SimpleResource source = new SimpleResource("classpath:test-data/filter/easyprivacy.txt");
        String list = ResourceHandler.load(source);
        Files.write(resourceFile, list.getBytes(Charset.forName("UTF-8")));
    }
}
