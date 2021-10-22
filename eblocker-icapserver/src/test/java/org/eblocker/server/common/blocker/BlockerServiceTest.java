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
package org.eblocker.server.common.blocker;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterSummaryData;
import org.eblocker.server.common.malware.MalwareFilterService;
import org.eblocker.server.common.util.FileUtils;
import org.eblocker.server.http.service.ParentalControlFilterListsService;
import org.eblocker.server.icap.filter.FilterDefinitionFormat;
import org.eblocker.server.icap.filter.FilterLearningMode;
import org.eblocker.server.icap.filter.FilterManager;
import org.eblocker.server.icap.filter.FilterStore;
import org.eblocker.server.icap.filter.FilterStoreConfiguration;
import org.eblocker.server.icap.filter.content.ContentFilterManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.xml.ws.Holder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BlockerServiceTest {

    private static final Date BUILTIN_UPDATE_DATE = new Date();
    private static final String BLOCKER_SOURCE_CONTENT = "eblocker.com\netracker.com\n";

    private DataSource dataSource;
    private FilterManager filterManager;
    private MalwareFilterService malwareFilterService;
    private ContentFilterManager contentFilterManager;
    private ParentalControlFilterListsService filterListsService;
    private ScheduledExecutorService executorService;
    private UpdateTaskFactory updateTaskFactory;
    private BlockerService blockerService;

    private Path localStoragePath;
    private List<ExternalDefinition> definitions;
    private List<FilterStoreConfiguration> configurations;
    private List<ParentalControlFilterMetaData> metadata;
    private List<Path> blockerFiles;
    private UpdateTask updateTask;

    @Before
    public void setUp() throws IOException {
        localStoragePath = Files.createTempDirectory(BlockerServiceTest.class.getSimpleName());

        blockerFiles = Arrays.asList(
                Files.createTempFile(BlockerServiceTest.class.getSimpleName(), ".1000"),
                Files.createTempFile(BlockerServiceTest.class.getSimpleName(), ".1001"),
                Files.createTempFile(BlockerServiceTest.class.getSimpleName(), ".1002"),
                Files.createTempFile(BlockerServiceTest.class.getSimpleName(), ".1003"),
                Files.createTempFile(BlockerServiceTest.class.getSimpleName(), ".1004"),
                Files.createTempFile(BlockerServiceTest.class.getSimpleName(), ".1005"),
                Files.createTempFile(BlockerServiceTest.class.getSimpleName(), ".1006")
        );

        Files.write(blockerFiles.get(6), BLOCKER_SOURCE_CONTENT.getBytes(StandardCharsets.UTF_8));

        definitions = Arrays.asList(
                new ExternalDefinition(1000, "test-0", "description-0", Category.ADS, Type.DOMAIN, null, Format.DOMAINS, "http://unit.test/domains-ads", UpdateInterval.DAILY, UpdateStatus.NEW, null, blockerFiles.get(0).toString(), true, "blacklist"),
                new ExternalDefinition(1001, "test-1", "description-1", Category.TRACKER, Type.DOMAIN, 100, Format.DOMAINS, "http://unit.test/domains-tracker", UpdateInterval.DAILY, UpdateStatus.READY, null, blockerFiles.get(1).toString(), true,
                        "blacklist"),
                new ExternalDefinition(1002, "test-2", "description-2", Category.ADS, Type.PATTERN, 200, Format.EASYLIST, "http://unit.test/easylist-ads", UpdateInterval.DAILY, UpdateStatus.READY, null, blockerFiles.get(2).toString(), true, "blacklist"),
                new ExternalDefinition(1003, "test-3", "description-3", Category.ADS, Type.PATTERN, null, Format.EASYLIST, "http://unit.test/easylist-ads-404", UpdateInterval.DAILY, UpdateStatus.INITIAL_UPDATE_FAILED, null, blockerFiles.get(3).toString(),
                        true, "blacklist"),
                new ExternalDefinition(1004, "test-4", "description-4", Category.ADS, Type.PATTERN, 206, Format.EASYLIST, "http://unit.test/easylist-ads-4", UpdateInterval.NEVER, UpdateStatus.READY, null, blockerFiles.get(4).toString(), true, "blacklist"),
                new ExternalDefinition(1005, "test-5", "description-5", Category.ADS, Type.PATTERN, 207, Format.EASYLIST, "http://unit.test/easylist-ads-5", UpdateInterval.DAILY, UpdateStatus.UPDATE_FAILED, null, blockerFiles.get(5).toString(), true,
                        "blacklist"),
                new ExternalDefinition(1006, "test-6", "description-6", Category.ADS, Type.DOMAIN, 109, Format.DOMAINS, null, UpdateInterval.NEVER, UpdateStatus.READY, null, blockerFiles.get(6).toString(), true, "blacklist")
        );

        dataSource = Mockito.mock(DataSource.class);
        Mockito.when(dataSource.getAll(ExternalDefinition.class)).thenReturn(definitions);
        Mockito.when(dataSource.get(Mockito.eq(ExternalDefinition.class), Mockito.anyInt())).then(im -> definitions.stream().filter(d -> d.getId() == (int) im.getArgument(1)).findFirst().orElse(null));
        AtomicInteger nextId = new AtomicInteger(1100);
        Mockito.when(dataSource.nextId(ExternalDefinition.class)).then(im -> nextId.getAndIncrement());

        configurations = Arrays.asList(
                new FilterStoreConfiguration(200, "test-2", org.eblocker.server.icap.filter.Category.ADS, false, 0, new String[]{ blockerFiles
                        .get(2).toString() }, FilterLearningMode.ASYNCHRONOUS, FilterDefinitionFormat.EASYLIST, true, new String[0], true),
                new FilterStoreConfiguration(201, "builtin-pattern-ADS", org.eblocker.server.icap.filter.Category.ADS, true, 0, new String[0], FilterLearningMode.ASYNCHRONOUS, FilterDefinitionFormat.EASYLIST, true, new String[0], true),
                new FilterStoreConfiguration(202, "builtin-pattern-CONTENT_SECURITY_POLICIES", org.eblocker.server.icap.filter.Category.CONTENT_SECURITY_POLICIES, true, 0, new String[0], FilterLearningMode.ASYNCHRONOUS, FilterDefinitionFormat.EASYLIST,
                        true,
                        new String[0], true),
                new FilterStoreConfiguration(203, "builtin-pattern-EBLOCKER", org.eblocker.server.icap.filter.Category.EBLOCKER, true, 0, new String[0], FilterLearningMode.ASYNCHRONOUS, FilterDefinitionFormat.EASYLIST, true, new String[0], true),
                new FilterStoreConfiguration(204, "builtin-pattern-TRACKER_BLOCKER", org.eblocker.server.icap.filter.Category.TRACKER_BLOCKER, true, 0, new String[0], FilterLearningMode.ASYNCHRONOUS, FilterDefinitionFormat.EASYLIST, true, new String[0],
                        true),
                new FilterStoreConfiguration(205, "builtin-pattern-TRACKER_REDIRECT", org.eblocker.server.icap.filter.Category.TRACKER_REDIRECT, true, 0, new String[0], FilterLearningMode.ASYNCHRONOUS, FilterDefinitionFormat.EASYLIST, true, new String[0],
                        true),
                new FilterStoreConfiguration(206, "test-4", org.eblocker.server.icap.filter.Category.ADS, false, 0, new String[]{ blockerFiles
                        .get(4).toString() }, FilterLearningMode.ASYNCHRONOUS, FilterDefinitionFormat.EASYLIST, true, new String[0], true),
                new FilterStoreConfiguration(207, "test-5", org.eblocker.server.icap.filter.Category.ADS, false, 0, new String[]{ blockerFiles
                        .get(5).toString() }, FilterLearningMode.ASYNCHRONOUS, FilterDefinitionFormat.EASYLIST, true, new String[0], true)
        );
        filterManager = Mockito.mock(FilterManager.class);
        Mockito.when(filterManager.getFilterConfigurations()).thenReturn(configurations);
        Mockito.when(filterManager.getFilterStoreConfigurationById(Mockito.anyInt())).then(im -> configurations.stream().filter(c -> c.getId() == (int) im.getArgument(0)).findFirst().orElse(null));

        FilterStore store = Mockito.mock(FilterStore.class);
        Mockito.when(store.getLastUpdate()).thenReturn(BUILTIN_UPDATE_DATE);
        Mockito.when(filterManager.getFilterStore(Mockito.anyInt())).thenReturn(store);

        malwareFilterService = Mockito.mock(MalwareFilterService.class);
        Mockito.when(malwareFilterService.isEnabled()).thenReturn(true);
        Mockito.when(malwareFilterService.getLastUpdate()).thenReturn(BUILTIN_UPDATE_DATE.getTime());

        contentFilterManager = Mockito.mock(ContentFilterManager.class);

        filterListsService = Mockito.mock(ParentalControlFilterListsService.class);
        metadata = Arrays.asList(
                new ParentalControlFilterMetaData(100, null, null, org.eblocker.server.common.data.parentalcontrol.Category.ADS, Collections.singletonList("/tmp/file.1001"), null, BUILTIN_UPDATE_DATE, "domainblacklist/string", "blacklist", false, false,
                        Collections.emptyList(), "test-1", null),
                new ParentalControlFilterMetaData(101, map("en", "builtin-domain-ADS", "de", "builtin-domain-ADS"), null, org.eblocker.server.common.data.parentalcontrol.Category.ADS, null, null, BUILTIN_UPDATE_DATE, "domainblacklist/string", "blacklist",
                        true, false, Collections.emptyList(), null, null),
                new ParentalControlFilterMetaData(102, map("en", "builtin-domain-ADS_TRACKERS_BLOOM_FILTER", "de", "builtin-domain-ADS_TRACKERS_BLOOM_FILTER"), null, org.eblocker.server.common.data.parentalcontrol.Category.ADS_TRACKERS_BLOOM_FILTER, null,
                        null, BUILTIN_UPDATE_DATE, "domainblacklist/string", "blacklist", true, false, Collections.emptyList(), null, null),
                new ParentalControlFilterMetaData(103, map("en", "custom-domain-CUSTOM", "de", "custom-domain-CUSTOM"), null, org.eblocker.server.common.data.parentalcontrol.Category.CUSTOM, null, null, BUILTIN_UPDATE_DATE, "domainblacklist/string",
                        "blacklist", false, false, Collections.emptyList(), "custom-domain-CUSTOM", null),
                new ParentalControlFilterMetaData(104, map("en", "builtin-domain-MALWARE", "de", "builtin-domain-MALWARE"), null, org.eblocker.server.common.data.parentalcontrol.Category.MALWARE, null, null, BUILTIN_UPDATE_DATE, "domainblacklist/string",
                        "blacklist", true, false, Collections.emptyList(), null, null),
                new ParentalControlFilterMetaData(105, map("en", "builtin-domain-PARENTAL_CONTROL", "de", "builtin-domain-PARENTAL_CONTROL"), null, org.eblocker.server.common.data.parentalcontrol.Category.PARENTAL_CONTROL, null, null, BUILTIN_UPDATE_DATE,
                        "domainblacklist/string", "blacklist", true, false, Collections.emptyList(), null, null),
                new ParentalControlFilterMetaData(106, map("en", "builtin-domain-PARENTAL_CONTROL_BLOOM_FILTER", "de", "builtin-domain-PARENTAL_CONTROL_BLOOM_FILTER"), null,
                        org.eblocker.server.common.data.parentalcontrol.Category.PARENTAL_CONTROL_BLOOM_FILTER, null, null, BUILTIN_UPDATE_DATE, "domainblacklist/string", "blacklist", true, false, Collections.emptyList(), null, null),
                new ParentalControlFilterMetaData(107, map("en", "builtin-domain-TOP_LEVEL_BLOOM_FILTER", "de", "builtin-domain-TOP_LEVEL_BLOOM_FILTER"), null, org.eblocker.server.common.data.parentalcontrol.Category.TOP_LEVEL_BLOOM_FILTER, null, null,
                        BUILTIN_UPDATE_DATE, "domainblacklist/string", "blacklist", true, false, Collections.emptyList(), null, null),
                new ParentalControlFilterMetaData(108, map("en", "builtin-domain-TRACKERS", "de", "builtin-domain-TRACKERS"), null, org.eblocker.server.common.data.parentalcontrol.Category.TRACKERS, null, null, BUILTIN_UPDATE_DATE,
                        "domainblacklist/string",
                        "blacklist", true, false, Collections.emptyList(), null, null),
                new ParentalControlFilterMetaData(109, map("en", "custom-domain-PARAMETER", "de", "custom-domain-PARAMETER"), null, org.eblocker.server.common.data.parentalcontrol.Category.ADS, null, null, BUILTIN_UPDATE_DATE, "domainblacklist/string",
                        "blacklist", true, false, Collections.emptyList(), null, null)
        );
        Mockito.when(filterListsService.getParentalControlFilterMetaData()).thenReturn(metadata);
        Mockito.when(filterListsService.getParentalControlFilterMetaData(Mockito.anyInt())).then(im -> metadata.stream().filter(m -> m.getId() == im.getArgument(0)).findFirst().orElse(null));
        executorService = Mockito.mock(ScheduledExecutorService.class);

        updateTask = Mockito.mock(UpdateTask.class);
        updateTaskFactory = Mockito.mock(UpdateTaskFactory.class);
        Mockito.when(updateTaskFactory.create(Mockito.anyInt())).thenReturn(updateTask);

        BiMap<TypeId, Integer> idByTypeId = HashBiMap.create();
        idByTypeId.put(new TypeId(Type.PATTERN, 201), 1);
        idByTypeId.put(new TypeId(Type.PATTERN, 202), 2);
        idByTypeId.put(new TypeId(Type.PATTERN, 203), 3);
        idByTypeId.put(new TypeId(Type.PATTERN, 204), 4);
        idByTypeId.put(new TypeId(Type.PATTERN, 205), 5);
        idByTypeId.put(new TypeId(Type.DOMAIN, 101), 11);
        idByTypeId.put(new TypeId(Type.DOMAIN, 102), 12);
        idByTypeId.put(new TypeId(Type.DOMAIN, 103), 13);
        idByTypeId.put(new TypeId(Type.DOMAIN, 104), 14);
        idByTypeId.put(new TypeId(Type.DOMAIN, 105), 15);
        idByTypeId.put(new TypeId(Type.DOMAIN, 106), 16);
        idByTypeId.put(new TypeId(Type.DOMAIN, 107), 17);
        idByTypeId.put(new TypeId(Type.DOMAIN, 108), 18);
        idByTypeId.put(new TypeId(Type.MALWARE_URL, 0), 19);
        BlockerIdTypeIdCache idCache = Mockito.mock(BlockerIdTypeIdCache.class);
        Mockito.when(idCache.getId(Mockito.any(TypeId.class))).then(im -> idByTypeId.get(im.getArgument(0)));
        Mockito.when(idCache.getTypeId(Mockito.anyInt())).then(im -> idByTypeId.inverse().get(im.getArgument(0)));

        blockerService = new BlockerService(localStoragePath.toString(), idCache, dataSource, filterManager, malwareFilterService, contentFilterManager, filterListsService, executorService, updateTaskFactory);
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(localStoragePath);
        for (Path path : blockerFiles) {
            Files.deleteIfExists(path);
        }
    }

    @Test
    public void testGetBlockers() {
        List<Blocker> blockers = blockerService.getBlockers();
        Assert.assertNotNull(blockers);
        Assert.assertEquals(16, blockers.size());

        Map<String, Blocker> blockersByName = blockers.stream().collect(Collectors.toMap(b -> b.getName().get("en"), Function.identity()));
        assertDefinitionMatchesBlocker(definitions.get(0), blockersByName.get("test-0"));
        assertDefinitionMatchesBlocker(definitions.get(1), blockersByName.get("test-1"));
        assertDefinitionMatchesBlocker(definitions.get(2), blockersByName.get("test-2"));
        assertDefinitionMatchesBlocker(definitions.get(3), blockersByName.get("test-3"));
        assertDefinitionMatchesBlocker(definitions.get(4), blockersByName.get("test-4"));
        assertDefinitionMatchesBlocker(definitions.get(5), blockersByName.get("test-5"));

        assertDefinitionMatchesBlocker(definitions.get(6), blockersByName.get("test-6"));
        Assert.assertEquals(BLOCKER_SOURCE_CONTENT, blockersByName.get("test-6").getContent());

        assertBlocker(11, "builtin-domain-ADS", BlockerType.DOMAIN, Category.ADS, true, blockersByName.get("builtin-domain-ADS"));
        assertBlocker(13, "custom-domain-CUSTOM", BlockerType.DOMAIN, Category.CUSTOM, false, blockersByName.get("custom-domain-CUSTOM"));
        assertBlocker(14, "builtin-domain-MALWARE", BlockerType.DOMAIN, Category.MALWARE, true, blockersByName.get("builtin-domain-MALWARE"));
        assertBlocker(15, "builtin-domain-PARENTAL_CONTROL", BlockerType.DOMAIN, Category.PARENTAL_CONTROL, true, blockersByName.get("builtin-domain-PARENTAL_CONTROL"));
        assertBlocker(18, "builtin-domain-TRACKERS", BlockerType.DOMAIN, Category.TRACKER, true, blockersByName.get("builtin-domain-TRACKERS"));
        assertBlocker(1, "builtin-pattern-ADS", BlockerType.PATTERN, Category.ADS, true, blockersByName.get("builtin-pattern-ADS"));
        assertBlocker(4, "builtin-pattern-TRACKER_BLOCKER", BlockerType.PATTERN, Category.TRACKER, true, blockersByName.get("builtin-pattern-TRACKER_BLOCKER"));
    }

    @Test
    public void testGetBlockerById() {
        assertDefinitionMatchesBlocker(definitions.get(0), blockerService.getBlockerById(1000));
        assertDefinitionMatchesBlocker(definitions.get(1), blockerService.getBlockerById(1001));
        assertDefinitionMatchesBlocker(definitions.get(2), blockerService.getBlockerById(1002));
        assertDefinitionMatchesBlocker(definitions.get(3), blockerService.getBlockerById(1003));
        assertDefinitionMatchesBlocker(definitions.get(4), blockerService.getBlockerById(1004));
        assertDefinitionMatchesBlocker(definitions.get(5), blockerService.getBlockerById(1005));

        assertDefinitionMatchesBlocker(definitions.get(6), blockerService.getBlockerById(1006));
        Assert.assertEquals(BLOCKER_SOURCE_CONTENT, blockerService.getBlockerById(1006).getContent());

        Assert.assertNull(blockerService.getBlockerById(1100));
        assertBlocker(11, "builtin-domain-ADS", BlockerType.DOMAIN, Category.ADS, true, blockerService.getBlockerById(11));
        Assert.assertNull(blockerService.getBlockerById(12));
        assertBlocker(13, "custom-domain-CUSTOM", BlockerType.DOMAIN, Category.CUSTOM, false, blockerService.getBlockerById(13));
        assertBlocker(14, "builtin-domain-MALWARE", BlockerType.DOMAIN, Category.MALWARE, true, blockerService.getBlockerById(14));
        assertBlocker(15, "builtin-domain-PARENTAL_CONTROL", BlockerType.DOMAIN, Category.PARENTAL_CONTROL, true, blockerService.getBlockerById(15));
        Assert.assertNull(blockerService.getBlockerById(16));
        Assert.assertNull(blockerService.getBlockerById(17));
        assertBlocker(18, "builtin-domain-TRACKERS", BlockerType.DOMAIN, Category.TRACKER, true, blockerService.getBlockerById(18));
        Assert.assertNull(blockerService.getBlockerById(0));
        assertBlocker(1, "builtin-pattern-ADS", BlockerType.PATTERN, Category.ADS, true, blockerService.getBlockerById(1));
        Assert.assertNull(blockerService.getBlockerById(2));
        Assert.assertNull(blockerService.getBlockerById(3));
        assertBlocker(4, "builtin-pattern-TRACKER_BLOCKER", BlockerType.PATTERN, Category.TRACKER, true, blockerService.getBlockerById(4));
        assertBlocker(19, "Malware", BlockerType.PATTERN, Category.MALWARE, true, blockerService.getBlockerById(19));
    }

    @Test
    public void testDisableBuiltinDomainBlocker() {
        // return value is not tested it as it would require mocking multiple calls and mutating state
        blockerService.updateBlocker(new Blocker(11, null, null, null, null, null, true, null, null, null, null, null, null, false, null));

        ArgumentCaptor<ParentalControlFilterSummaryData> metadataCaptor = ArgumentCaptor.forClass(ParentalControlFilterSummaryData.class);
        Mockito.verify(filterListsService).updateFilterList(metadataCaptor.capture(), Mockito.eq("blacklist"));
        ParentalControlFilterSummaryData data = metadataCaptor.getValue();
        Assert.assertEquals(Integer.valueOf(101), data.getId());
        Assert.assertTrue(data.isDisabled());
    }

    @Test
    public void testDisableCustomDomainBlocker() {
        // return value is not tested it as it would require mocking multiple calls and mutating state
        blockerService.updateBlocker(new Blocker(
                1001,
                Collections.singletonMap("en", definitions.get(1).getName()),
                Collections.singletonMap("de", definitions.get(1).getDescription()),
                mapType(definitions.get(1).getType()),
                definitions.get(1).getCategory(),
                null,
                false,
                definitions.get(1).getUrl(),
                null,
                definitions.get(1).getFormat(),
                null,
                null,
                null,
                false,
                "blacklist"
        ));

        ArgumentCaptor<ParentalControlFilterSummaryData> metadataCaptor = ArgumentCaptor.forClass(ParentalControlFilterSummaryData.class);
        Mockito.verify(filterListsService).updateFilterList(metadataCaptor.capture(), Mockito.eq("blacklist"));
        ParentalControlFilterSummaryData data = metadataCaptor.getValue();
        Assert.assertEquals(Integer.valueOf(100), data.getId());
        Assert.assertTrue(data.isDisabled());

        ArgumentCaptor<ExternalDefinition> definitionCaptor = ArgumentCaptor.forClass(ExternalDefinition.class);
        Mockito.verify(dataSource).save(definitionCaptor.capture(), Mockito.eq(1001));
        ExternalDefinition definition = definitionCaptor.getValue();
        Assert.assertEquals(1001, definition.getId());
        Assert.assertFalse(definition.isEnabled());
        Assert.assertEquals(UpdateStatus.READY, definition.getUpdateStatus());
    }

    @Test
    public void testDisableBuiltinPatternBlocker() {
        Mockito.when(filterManager.updateFilter(Mockito.any(FilterStoreConfiguration.class))).thenReturn(new FilterStoreConfiguration(1, null, org.eblocker.server.icap.filter.Category.ADS, true, 0, null, null, null, true, null, false));
        blockerService.updateBlocker(new Blocker(1, null, null, null, null, null, true, null, null, null, null, null, null, false, "blacklist"));

        ArgumentCaptor<FilterStoreConfiguration> configurationCaptor = ArgumentCaptor.forClass(FilterStoreConfiguration.class);
        Mockito.verify(filterManager).updateFilter(configurationCaptor.capture());
        FilterStoreConfiguration configuration = configurationCaptor.getValue();
        Assert.assertEquals(Integer.valueOf(201), configuration.getId());
        Assert.assertFalse(configuration.isEnabled());
    }

    @Test
    public void testDisableCustomPatternBlocker() {
        Mockito.when(filterManager.updateFilter(Mockito.any(FilterStoreConfiguration.class))).then(im -> im.getArgument(0));
        blockerService.updateBlocker(new Blocker(
                1002,
                Collections.singletonMap("en", definitions.get(2).getName()),
                Collections.singletonMap("en", definitions.get(2).getDescription()),
                mapType(definitions.get(2).getType()),
                definitions.get(2).getCategory(),
                null,
                false,
                definitions.get(2).getUrl(),
                null,
                definitions.get(2).getFormat(),
                null,
                null,
                null,
                false,
                "blacklist"
        ));

        ArgumentCaptor<FilterStoreConfiguration> configurationCaptor = ArgumentCaptor.forClass(FilterStoreConfiguration.class);
        Mockito.verify(filterManager).updateFilter(configurationCaptor.capture());
        FilterStoreConfiguration configuration = configurationCaptor.getValue();
        Assert.assertEquals(Integer.valueOf(200), configuration.getId());
        Assert.assertFalse(configuration.isEnabled());

        ArgumentCaptor<ExternalDefinition> definitionCaptor = ArgumentCaptor.forClass(ExternalDefinition.class);
        Mockito.verify(dataSource).save(definitionCaptor.capture(), Mockito.eq(1002));
        ExternalDefinition definition = definitionCaptor.getValue();
        Assert.assertEquals(1002, definition.getId());
        Assert.assertFalse(definition.isEnabled());
        Assert.assertEquals(UpdateStatus.READY, definition.getUpdateStatus());
    }

    @Test
    public void testDisableNewBlocker() {
        blockerService.updateBlocker(new Blocker(
                1000,
                Collections.singletonMap("en", definitions.get(0).getName()),
                Collections.singletonMap("de", definitions.get(0).getName()),
                mapType(definitions.get(0).getType()),
                definitions.get(0).getCategory(),
                null,
                false,
                definitions.get(0).getUrl(),
                null,
                definitions.get(0).getFormat(),
                null,
                null,
                null,
                false,
                "blacklist"
        ));

        ArgumentCaptor<ExternalDefinition> definitionCaptor = ArgumentCaptor.forClass(ExternalDefinition.class);
        Mockito.verify(dataSource).save(definitionCaptor.capture(), Mockito.eq(1000));
        ExternalDefinition definition = definitionCaptor.getValue();
        Assert.assertEquals(1000, definition.getId());
        Assert.assertFalse(definition.isEnabled());
        Assert.assertEquals(UpdateStatus.NEW, definition.getUpdateStatus());
    }

    @Test
    public void testDisableInitialUpdateFailedBlocker() {
        blockerService.updateBlocker(new Blocker(
                1003,
                Collections.singletonMap("en", definitions.get(3).getName()),
                Collections.singletonMap("en", definitions.get(3).getDescription()),
                mapType(definitions.get(3).getType()),
                definitions.get(3).getCategory(),
                null,
                false,
                definitions.get(3).getUrl(),
                null,
                definitions.get(3).getFormat(),
                null,
                null,
                null,
                false,
                "blacklist"
        ));

        ArgumentCaptor<ExternalDefinition> definitionCaptor = ArgumentCaptor.forClass(ExternalDefinition.class);
        Mockito.verify(dataSource).save(definitionCaptor.capture(), Mockito.eq(1003));
        ExternalDefinition definition = definitionCaptor.getValue();
        Assert.assertEquals(1003, definition.getId());
        Assert.assertFalse(definition.isEnabled());
        Assert.assertEquals(UpdateStatus.INITIAL_UPDATE_FAILED, definition.getUpdateStatus());
    }

    @Test
    public void testDisableMalwareUrlFilter() {
        Holder<Boolean> enabled = new Holder<>(true);
        Mockito.when(malwareFilterService.setEnabled(Mockito.anyBoolean())).then(im -> {
            enabled.value = im.getArgument(0);
            return enabled.value;
        });
        Mockito.when(malwareFilterService.isEnabled()).then(im -> enabled.value);

        Blocker blocker = blockerService.updateBlocker(new Blocker(19, null, null, null, null, null, true, null, null, null, null, null, null, false, null));
        Assert.assertEquals(Integer.valueOf(19), blocker.getId());
        Assert.assertFalse(blocker.isEnabled());
        Mockito.verify(malwareFilterService).setEnabled(false);
    }

    @Test
    public void testCreateDomainBlocker() {
        testCreateBlocker(new Blocker(null, map("en", "test", "de", "test"), null, BlockerType.DOMAIN, Category.ADS, null, false, "https://unit.test/domains.txt", null, Format.DOMAINS, null, UpdateInterval.DAILY, null, true, "blacklist"));
    }

    @Test
    public void testCreatePatternBlocker() {
        testCreateBlocker(new Blocker(null, map("en", "test", "de", "test"), null, BlockerType.PATTERN, Category.ADS, null, false, "https://unit.test/domains.txt", null, Format.EASYLIST, null, UpdateInterval.DAILY, null, true, "blacklist"));
    }

    @Test
    public void testCreateDomainBlockerParameter() throws IOException {
        testCreateBlocker(new Blocker(null, map("en", "test", "de", "test"), null, BlockerType.DOMAIN, Category.ADS, null, false, null, "blocked.com", Format.DOMAINS, null, UpdateInterval.DAILY, null, true, "blacklist"));
        Path path = localStoragePath.resolve("1100:DOMAIN");
        Assert.assertEquals("blocked.com", new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
    }

    @Test
    public void testCreateDomainBlockerPattern() throws IOException {
        testCreateBlocker(new Blocker(null, map("en", "test", "de", "test"), null, BlockerType.PATTERN, Category.ADS, null, false, null, "||blocked.com^", Format.EASYLIST, null, UpdateInterval.DAILY, null, true, "blacklist"));
        Path path = localStoragePath.resolve("1100:PATTERN");
        Assert.assertEquals("||blocked.com^", new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
    }

    private void testCreateBlocker(Blocker blocker) {
        Blocker createdBlocker = blockerService.createBlocker(blocker);
        Assert.assertNotNull(createdBlocker);
        Assert.assertEquals(Integer.valueOf(1100), createdBlocker.getId());
        Assert.assertEquals(blocker.getName(), createdBlocker.getName());
        Assert.assertEquals(blocker.getType(), createdBlocker.getType());
        Assert.assertEquals(blocker.getCategory(), createdBlocker.getCategory());
        Assert.assertFalse(blocker.isProvidedByEblocker());
        Assert.assertEquals(blocker.getUrl(), createdBlocker.getUrl());
        Assert.assertEquals(blocker.getContent(), createdBlocker.getContent());
        Assert.assertEquals(blocker.getFormat(), createdBlocker.getFormat());
        Assert.assertEquals(UpdateStatus.NEW, createdBlocker.getUpdateStatus());

        ArgumentCaptor<ExternalDefinition> definitionCaptor = ArgumentCaptor.forClass(ExternalDefinition.class);
        Mockito.verify(dataSource).save(definitionCaptor.capture(), Mockito.eq(1100));
        ExternalDefinition definition = definitionCaptor.getValue();
        Assert.assertEquals(1100, definition.getId());
        Assert.assertEquals(blocker.getType(), mapType(definition.getType()));
        Assert.assertNull(definition.getReferenceId());
        Assert.assertEquals(blocker.getName().get("en"), definition.getName());
        Assert.assertEquals(blocker.getName().get("de"), definition.getName());
        if (blocker.getDescription() != null) {
            Assert.assertEquals(blocker.getDescription().get("en"), definition.getDescription());
            Assert.assertEquals(blocker.getDescription().get("de"), definition.getDescription());
        } else {
            Assert.assertNull(definition.getDescription());
        }
        Assert.assertEquals(blocker.getCategory(), definition.getCategory());
        Assert.assertEquals(blocker.getUrl(), definition.getUrl());
        Assert.assertEquals(blocker.getFormat(), definition.getFormat());
        Assert.assertEquals(UpdateStatus.NEW, definition.getUpdateStatus());
        Assert.assertNotNull(definition.getFile());
        Assert.assertTrue(definition.getFile().startsWith(localStoragePath.toString()));

        Mockito.verify(updateTaskFactory).create(1100);
        Mockito.verify(executorService).submit(Mockito.any(UpdateTask.class));
    }

    @Test
    public void testUpdateBlockerMetadata() {
        blockerService.updateBlocker(new Blocker(
                1001,
                Collections.singletonMap("en", "TEST-1"),
                Collections.emptyMap(),
                BlockerType.DOMAIN,
                Category.TRACKER,
                null,
                false,
                "http://unit.test/domains-tracker",
                null,
                Format.DOMAINS,
                null,
                UpdateInterval.NEVER,
                null,
                true,
                "blacklist"
        ));

        ArgumentCaptor<ExternalDefinition> definitionCaptor = ArgumentCaptor.forClass(ExternalDefinition.class);
        Mockito.verify(dataSource).save(definitionCaptor.capture(), Mockito.eq(1001));
        ExternalDefinition definition = definitionCaptor.getValue();
        Assert.assertEquals("TEST-1", definition.getName());
        Assert.assertEquals(UpdateInterval.NEVER, definition.getUpdateInterval());
        Mockito.verify(filterListsService, Mockito.never()).updateFilterList(Mockito.any(ParentalControlFilterSummaryData.class), Mockito.anyString());
    }

    @Test
    public void testUpdateBlockerSourceUrl() {
        blockerService.updateBlocker(new Blocker(
                1002,
                Collections.singletonMap("en", "test-1"),
                Collections.emptyMap(),
                BlockerType.PATTERN,
                Category.ADS,
                null,
                false,
                "http://unit.test/easylist-ads-2000",
                null,
                Format.EASYLIST,
                null,
                UpdateInterval.DAILY,
                null,
                true,
                "blacklist"
        ));

        ArgumentCaptor<ExternalDefinition> definitionCaptor = ArgumentCaptor.forClass(ExternalDefinition.class);
        Mockito.verify(dataSource).save(definitionCaptor.capture(), Mockito.eq(1002));
        ExternalDefinition definition = definitionCaptor.getValue();
        Assert.assertEquals("http://unit.test/easylist-ads-2000", definition.getUrl());
        Assert.assertEquals(UpdateStatus.INITIAL_UPDATE, definition.getUpdateStatus());
        Assert.assertNull(definition.getUpdateError());
        Mockito.verify(filterListsService, Mockito.never()).updateFilterList(Mockito.any(ParentalControlFilterSummaryData.class), Mockito.anyString());
        Mockito.verify(updateTaskFactory).create(1002);
        Mockito.verify(executorService).submit(Mockito.any(UpdateTask.class));
    }

    @Test
    public void testUpdateBlockerSourceParameter() throws IOException {
        blockerService.updateBlocker(new Blocker(
                1006,
                Collections.singletonMap("en", "test-6"),
                Collections.emptyMap(),
                BlockerType.DOMAIN,
                Category.ADS,
                null,
                false,
                null,
                "fancy-ads.com",
                Format.EASYLIST,
                null,
                UpdateInterval.DAILY,
                null,
                true,
                "blacklist"
        ));

        ArgumentCaptor<ExternalDefinition> definitionCaptor = ArgumentCaptor.forClass(ExternalDefinition.class);
        Mockito.verify(dataSource).save(definitionCaptor.capture(), Mockito.eq(1006));
        ExternalDefinition definition = definitionCaptor.getValue();
        Assert.assertNull(definition.getUrl());
        Assert.assertEquals(UpdateStatus.INITIAL_UPDATE, definition.getUpdateStatus());
        Assert.assertNull(definition.getUpdateError());
        Mockito.verify(filterListsService, Mockito.never()).updateFilterList(Mockito.any(ParentalControlFilterSummaryData.class), Mockito.anyString());
        Mockito.verify(updateTaskFactory).create(1006);
        Mockito.verify(executorService).submit(Mockito.any(UpdateTask.class));

        Assert.assertEquals("fancy-ads.com", new String(Files.readAllBytes(blockerFiles.get(6)), StandardCharsets.UTF_8));
    }

    @Test
    public void testRemoveBuiltinBlocker() {
        blockerService.deleteBlocker(1);
        Mockito.verifyZeroInteractions(filterManager);

        blockerService.deleteBlocker(11);
        Mockito.verifyZeroInteractions(filterListsService);
    }

    @Test
    public void testRemoveDomainBlocker() {
        blockerService.deleteBlocker(1001);
        Mockito.verify(dataSource).delete(ExternalDefinition.class, 1001);
        Mockito.verify(filterListsService).deleteFilterList(100);
        Assert.assertFalse(Files.exists(blockerFiles.get(1)));
    }

    @Test
    public void testRemovePatternBlocker() {
        blockerService.deleteBlocker(1002);
        Mockito.verify(dataSource).delete(ExternalDefinition.class, 1002);
        Mockito.verify(filterManager).removeFilter(200);
        Assert.assertFalse(Files.exists(blockerFiles.get(2)));
    }

    @Test
    public void testRemoveFailedBlocker() {
        blockerService.deleteBlocker(1003);
        Mockito.verify(dataSource).delete(ExternalDefinition.class, 1003);
        Assert.assertFalse(Files.exists(blockerFiles.get(3)));
        Mockito.verifyZeroInteractions(filterManager);
    }

    @Test
    public void testDeleteWithExceptions() {
        Mockito.doThrow(new RuntimeException()).when(filterListsService).deleteFilterList(100);
        try {
            blockerService.deleteBlocker(1001);
            Assert.fail("expected RuntimeException on deletion");
        } catch (RuntimeException e) {
            // expected
        }

        Mockito.verify(dataSource, Mockito.never()).delete(ExternalDefinition.class, 1001);
        Assert.assertTrue(Files.exists(blockerFiles.get(1)));
    }

    @Test
    public void testUpdate() {
        blockerService.update();

        Mockito.verify(updateTaskFactory).create(1001);
        Mockito.verify(updateTaskFactory).create(1002);
        Mockito.verify(updateTaskFactory).create(1005);
        Mockito.verify(updateTask, Mockito.times(3)).run();
    }

    @Test
    public void testInit() {
        Mockito.when(dataSource.getAll(ExternalDefinition.class)).thenReturn(Arrays.asList(
                new ExternalDefinition(1000, "test-0", null, Category.ADS, Type.DOMAIN, null, Format.DOMAINS, "http://unit.test/domains-ads", UpdateInterval.DAILY, UpdateStatus.INITIAL_UPDATE, null, blockerFiles.get(0).toString(), true, "blacklist"),
                new ExternalDefinition(1001, "test-1", null, Category.TRACKER, Type.DOMAIN, 100, Format.DOMAINS, "http://unit.test/domains-tracker", UpdateInterval.DAILY, UpdateStatus.UPDATE, null, blockerFiles.get(1).toString(), true, "blacklist")
        ));

        blockerService.init();

        ArgumentCaptor<ExternalDefinition> definitionCaptor = ArgumentCaptor.forClass(ExternalDefinition.class);
        Mockito.verify(dataSource).save(definitionCaptor.capture(), Mockito.eq(1000));
        Assert.assertEquals(1000, definitionCaptor.getValue().getId());
        Assert.assertEquals(UpdateStatus.INITIAL_UPDATE_FAILED, definitionCaptor.getValue().getUpdateStatus());
        Assert.assertNotNull(definitionCaptor.getValue().getUpdateError());

        Mockito.verify(dataSource).save(definitionCaptor.capture(), Mockito.eq(1001));
        Assert.assertEquals(1001, definitionCaptor.getValue().getId());
        Assert.assertEquals(UpdateStatus.UPDATE_FAILED, definitionCaptor.getValue().getUpdateStatus());
        Assert.assertNotNull(definitionCaptor.getValue().getUpdateError());
    }

    private void assertDefinitionMatchesBlocker(ExternalDefinition definition, Blocker blocker) {
        Assert.assertNotNull(blocker);
        Assert.assertEquals(Integer.valueOf(definition.getId()), blocker.getId());
        Assert.assertEquals(definition.getName(), blocker.getName().get("en"));
        Assert.assertEquals(definition.getName(), blocker.getName().get("de"));
        Assert.assertEquals(definition.getDescription(), blocker.getDescription().get("en"));
        Assert.assertEquals(definition.getDescription(), blocker.getDescription().get("de"));
        Assert.assertEquals(mapType(definition.getType()), blocker.getType());
        Assert.assertEquals(definition.getCategory(), blocker.getCategory());
        Assert.assertEquals(definition.getFormat(), blocker.getFormat());
        Assert.assertEquals(definition.getUrl(), blocker.getUrl());
        Assert.assertEquals(definition.getUpdateStatus(), blocker.getUpdateStatus());
        Assert.assertEquals(definition.getUpdateError(), blocker.getError());
        Assert.assertFalse(blocker.isProvidedByEblocker());
    }

    private void assertBlocker(int id, String name, BlockerType type, Category category, boolean providedByEblocker, Blocker blocker) {
        Assert.assertNotNull(blocker);
        Assert.assertEquals(Integer.valueOf(id), blocker.getId());
        Assert.assertNotNull(blocker.getName());
        Assert.assertEquals(name, blocker.getName().get("en"));
        Assert.assertEquals(name, blocker.getName().get("de"));
        Assert.assertEquals(type, blocker.getType());
        Assert.assertEquals(category, blocker.getCategory());
        Assert.assertEquals(providedByEblocker, blocker.isProvidedByEblocker());
        Assert.assertNull(blocker.getFormat());
        Assert.assertNull(blocker.getUrl());
        Assert.assertNull(blocker.getUpdateInterval());
        Assert.assertNull(blocker.getUpdateStatus());
        Assert.assertNull(blocker.getError());
        Assert.assertNotNull(blocker.getLastUpdate());
        Assert.assertEquals(BUILTIN_UPDATE_DATE.getTime(), (long) blocker.getLastUpdate());
    }

    private <U, V> Map<U, V> map(U key0, V value0, U key1, V value1) {
        Map<U, V> map = new HashMap<>();
        map.put(key0, value0);
        map.put(key1, value1);
        return map;
    }

    private BlockerType mapType(Type type) {
        switch (type) {
            case DOMAIN:
                return BlockerType.DOMAIN;
            case MALWARE_URL:
            case PATTERN:
                return BlockerType.PATTERN;
            default:
                Assert.fail("no blocker type: " + type);
                return null;
        }
    }

}
