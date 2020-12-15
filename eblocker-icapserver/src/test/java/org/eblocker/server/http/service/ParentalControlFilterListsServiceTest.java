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
package org.eblocker.server.http.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eblocker.server.common.blacklist.BlacklistCompiler;
import org.eblocker.server.common.blacklist.DomainBlacklistService;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterSummaryData;
import org.eblocker.server.common.data.parentalcontrol.QueryTransformation;
import org.eblocker.server.common.util.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ParentalControlFilterListsServiceTest {

    private Path metaDataPath;
    private Path customFiltersPath;
    private BlacklistCompiler blacklistCompiler;
    private DataSource dataSource;
    private DomainBlacklistService domainBlacklistService;
    private FileSystemWatchService fileSystemWatchService;
    private ObjectMapper objectMapper;
    private ParentalControlService parentalControlService;

    @Before
    public void setUp() throws IOException {
        dataSource = Mockito.mock(DataSource.class);
        Mockito.when(dataSource.nextId(ParentalControlFilterMetaData.class)).thenReturn(1);
        Mockito.when(dataSource.save(Mockito.any(), Mockito.anyInt())).then(im -> im.getArgument(0));

        objectMapper = new ObjectMapper();
        metaDataPath = Files.createTempFile("parental-control-lists-service-test-config", ".json");
        setupMetadata();

        customFiltersPath = Files.createTempDirectory("parental-control-lists-service-test-custom");

        blacklistCompiler = Mockito.mock(BlacklistCompiler.class);
        domainBlacklistService = Mockito.mock(DomainBlacklistService.class);
        parentalControlService = Mockito.mock(ParentalControlService.class);
        fileSystemWatchService = Mockito.mock(FileSystemWatchService.class);
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(metaDataPath);
        FileUtils.deleteDirectory(customFiltersPath);
    }

    @Test
    public void testEnableDisableCustomFilter() throws IOException {
        setupMetadata(new ParentalControlFilterMetaData(0, null, null, null, null, null, new Date(), null, "blacklist", false, false, null, null, null));
        ParentalControlFilterListsService service = createService();

        // disable
        ParentalControlFilterSummaryData savedMetaData = service.updateFilterList(new ParentalControlFilterSummaryData(0, null, null, null, null, "blacklist", false, true, null, null, null, null), null);
        Assert.assertTrue(savedMetaData.isDisabled());
        ArgumentCaptor<ParentalControlFilterMetaData> metadataCaptor = ArgumentCaptor.forClass(ParentalControlFilterMetaData.class);
        Mockito.verify(dataSource, Mockito.times(2)).save(metadataCaptor.capture(), Mockito.eq(Integer.valueOf(0))); //already called once at service creation
        Assert.assertFalse(metadataCaptor.getValue().isBuiltin());
        Assert.assertTrue(metadataCaptor.getValue().isDisabled());

        // enable
        savedMetaData = service.updateFilterList(new ParentalControlFilterSummaryData(0, null, null, null, null, "blacklist", false, false, null, null, null, null), null);
        Assert.assertFalse(savedMetaData.isDisabled());
        Mockito.verify(dataSource, Mockito.times(3)).save(metadataCaptor.capture(), Mockito.eq(Integer.valueOf(0)));
        Assert.assertFalse(metadataCaptor.getValue().isBuiltin());
        Assert.assertFalse(metadataCaptor.getValue().isDisabled());
    }

    @Test
    public void testEnableDisableBuiltinFilter() throws IOException {
        ParentalControlFilterMetaData metadata = new ParentalControlFilterMetaData(
                0,
                Collections.singletonMap("en", "name"),
                Collections.singletonMap("en", "desc"),
                Category.ADS,
                Collections.singletonList("domains.txt"),
                "123",
                new Date(),
                "domainblacklist/string",
                "blacklist",
                true,
                false,
                Collections.singletonList(new QueryTransformation("x", "y")),
                null,
                null
        );

        setupMetadata(metadata);
        ParentalControlFilterListsService service = createService();

        // disable
        ParentalControlFilterSummaryData savedMetaData = service.updateFilterList(new ParentalControlFilterSummaryData(0, null, null, null, null, "blacklist", true, true, null, null, null, null), null);
        Assert.assertTrue(savedMetaData.isDisabled());
        ArgumentCaptor<ParentalControlFilterMetaData> metadataCaptor = ArgumentCaptor.forClass(ParentalControlFilterMetaData.class);
        Mockito.verify(dataSource, Mockito.times(2)).save(metadataCaptor.capture(), Mockito.eq(Integer.valueOf(0))); //already called once at service creation
        Assert.assertEquals(metadata.getId(), metadataCaptor.getValue().getId());
        Assert.assertEquals(metadata.getName(), metadataCaptor.getValue().getName());
        Assert.assertEquals(metadata.getDescription(), metadataCaptor.getValue().getDescription());
        Assert.assertEquals(metadata.getCategory(), metadataCaptor.getValue().getCategory());
        Assert.assertEquals(metadata.getFilenames(), metadataCaptor.getValue().getFilenames());
        Assert.assertEquals(metadata.getVersion(), metadataCaptor.getValue().getVersion());
        Assert.assertEquals(metadata.getDate(), metadataCaptor.getValue().getDate());
        Assert.assertEquals(metadata.getFormat(), metadataCaptor.getValue().getFormat());
        Assert.assertEquals(metadata.isBuiltin(), metadataCaptor.getValue().isBuiltin());
        Assert.assertTrue(metadataCaptor.getValue().isDisabled());
        Assert.assertEquals(metadata.getQueryTransformations(), metadataCaptor.getValue().getQueryTransformations());
        Assert.assertEquals(metadata.getCustomerCreatedName(), metadataCaptor.getValue().getCustomerCreatedName());
        Assert.assertEquals(metadata.getCustomerCreatedDescription(), metadataCaptor.getValue().getCustomerCreatedDescription());

        // enable
        savedMetaData = service.updateFilterList(new ParentalControlFilterSummaryData(0, null, null, null, null, "blacklist", true, false, null, null, null, null), null);
        Assert.assertFalse(savedMetaData.isDisabled());
        Mockito.verify(dataSource, Mockito.times(3)).save(metadataCaptor.capture(), Mockito.eq(Integer.valueOf(0)));
        Assert.assertEquals(metadata.getId(), metadataCaptor.getValue().getId());
        Assert.assertEquals(metadata.getName(), metadataCaptor.getValue().getName());
        Assert.assertEquals(metadata.getDescription(), metadataCaptor.getValue().getDescription());
        Assert.assertEquals(metadata.getCategory(), metadataCaptor.getValue().getCategory());
        Assert.assertEquals(metadata.getFilenames(), metadataCaptor.getValue().getFilenames());
        Assert.assertEquals(metadata.getVersion(), metadataCaptor.getValue().getVersion());
        Assert.assertEquals(metadata.getDate(), metadataCaptor.getValue().getDate());
        Assert.assertEquals(metadata.getFormat(), metadataCaptor.getValue().getFormat());
        Assert.assertEquals(metadata.isBuiltin(), metadataCaptor.getValue().isBuiltin());
        Assert.assertFalse(metadataCaptor.getValue().isDisabled());
        Assert.assertEquals(metadata.getQueryTransformations(), metadataCaptor.getValue().getQueryTransformations());
        Assert.assertEquals(metadata.getCustomerCreatedName(), metadataCaptor.getValue().getCustomerCreatedName());
        Assert.assertEquals(metadata.getCustomerCreatedDescription(), metadataCaptor.getValue().getCustomerCreatedDescription());
    }

    @Test
    @Ignore
    public void testMetaDataFileUpdate() throws IOException {
        ArgumentCaptor<Consumer<Path>> callbackCaptor = ArgumentCaptor.forClass(Consumer.class);
        Mockito.verify(fileSystemWatchService).watch(Mockito.eq(metaDataPath), callbackCaptor.capture());
        Consumer<Path> callback = callbackCaptor.getValue();

        setupMetadata(new ParentalControlFilterMetaData(
                0,
                Collections.singletonMap("en", "name"),
                Collections.singletonMap("en", "description"),
                Category.ADS,
                Arrays.asList("file0.filter", "file0.bloom"),
                "20180731",
                Date.from(Instant.now()),
                "domainblacklist/string",
                "blacklist",
                true,
                false,
                null,
                null,
                null));

        callback.accept(metaDataPath);

        Mockito.verify(dataSource).save(Mockito.any(ParentalControlFilterMetaData.class), Mockito.eq(0));
        Mockito.verify(domainBlacklistService, Mockito.times(2)).setFilters(Mockito.anyCollection());
        Mockito.verify(parentalControlService, Mockito.times(2)).updateFilters(Mockito.anyCollection());
    }

    @Test
    public void testCreateFilterFromDomainCollection() throws IOException {
        ParentalControlFilterListsService service = createService();

        ParentalControlFilterSummaryData data = new ParentalControlFilterSummaryData(
                null,
                Collections.singletonMap("en", "test-name"),
                Collections.singletonMap("en", "test-description"),
                null,
                null,
                "blacklist",
                false,
                false,
                Arrays.asList(".eblocker.com", ".etracker.com", "xkcd.org"),
                "test-customer-name",
                "test-customer-description",
                Category.ADS);
        ParentalControlFilterSummaryData savedData = service.createFilterList(data, "blacklist");

        Assert.assertEquals(Integer.valueOf(1), savedData.getId());
        Assert.assertNull(savedData.getName());
        Assert.assertNull(savedData.getDescription());
        Assert.assertEquals(data.getVersion(), savedData.getVersion());
        Assert.assertNotNull(savedData.getLastUpdate());
        Assert.assertEquals(data.getFilterType(), savedData.getFilterType());
        Assert.assertNull(savedData.getDomains());
        Assert.assertNull(savedData.getDomainsStreamSupplier());
        Assert.assertEquals(data.getCustomerCreatedName(), savedData.getCustomerCreatedName());
        Assert.assertEquals(data.getCustomerCreatedDescription(), savedData.getCustomerCreatedDescription());
        Assert.assertEquals(data.getCategory(), savedData.getCategory());

        ArgumentCaptor<ParentalControlFilterMetaData> savedMetadataCaptor = ArgumentCaptor.forClass(ParentalControlFilterMetaData.class);
        Mockito.verify(dataSource).save(savedMetadataCaptor.capture(), Mockito.eq(1));
        Assert.assertEquals(Integer.valueOf(1), savedMetadataCaptor.getValue().getId());
        Assert.assertNull(savedMetadataCaptor.getValue().getName());
        Assert.assertNull(savedMetadataCaptor.getValue().getDescription());
        Assert.assertFalse(savedMetadataCaptor.getValue().isBuiltin());
        Assert.assertFalse(savedMetadataCaptor.getValue().isDisabled());
        Assert.assertEquals("domainblacklist/string", savedMetadataCaptor.getValue().getFormat());
        Assert.assertNull(savedMetadataCaptor.getValue().getVersion());
        Assert.assertEquals("blacklist", savedMetadataCaptor.getValue().getFilterType());
        Assert.assertNull(savedMetadataCaptor.getValue().getQueryTransformations());
        Assert.assertEquals(data.getCustomerCreatedName(), savedMetadataCaptor.getValue().getCustomerCreatedName());
        Assert.assertEquals(data.getCustomerCreatedDescription(), savedMetadataCaptor.getValue().getCustomerCreatedDescription());

        Mockito.verify(blacklistCompiler).compile(Mockito.eq(1),
                Mockito.eq("test-customer-name"),
                Mockito.eq(Arrays.asList(".eblocker.com", ".etracker.com", ".xkcd.org")),
                Mockito.anyString(),
                Mockito.anyString());
    }

    @Test
    public void testCreateFilterFromDomainStream() throws IOException {
        ParentalControlFilterListsService service = createService();

        ParentalControlFilterSummaryData data = new ParentalControlFilterSummaryData(
                null,
                Collections.singletonMap("en", "test-name"),
                Collections.singletonMap("en", "test-description"),
                null,
                null,
                "blacklist",
                false,
                false,
                null,
                "test-customer-name",
                "test-customer-description",
                Category.ADS);
        List<String> domains = Arrays.asList(".eblocker.com", ".etracker.com", "xkcd.org");
        data.setDomainsStreamSupplier(domains::stream);

        ParentalControlFilterSummaryData savedData = service.createFilterList(data, "blacklist");

        Assert.assertEquals(Integer.valueOf(1), savedData.getId());
        Assert.assertNull(savedData.getName());
        Assert.assertNull(savedData.getDescription());
        Assert.assertEquals(data.getVersion(), savedData.getVersion());
        Assert.assertNotNull(savedData.getLastUpdate());
        Assert.assertEquals(data.getFilterType(), savedData.getFilterType());
        Assert.assertNull(savedData.getDomains());
        Assert.assertNull(savedData.getDomainsStreamSupplier());
        Assert.assertEquals(data.getCustomerCreatedName(), savedData.getCustomerCreatedName());
        Assert.assertEquals(data.getCustomerCreatedDescription(), savedData.getCustomerCreatedDescription());
        Assert.assertEquals(data.getCategory(), savedData.getCategory());

        ArgumentCaptor<ParentalControlFilterMetaData> savedMetadataCaptor = ArgumentCaptor.forClass(ParentalControlFilterMetaData.class);
        Mockito.verify(dataSource).save(savedMetadataCaptor.capture(), Mockito.eq(1));
        Assert.assertEquals(Integer.valueOf(1), savedMetadataCaptor.getValue().getId());
        Assert.assertNull(savedMetadataCaptor.getValue().getName());
        Assert.assertNull(savedMetadataCaptor.getValue().getDescription());
        Assert.assertFalse(savedMetadataCaptor.getValue().isBuiltin());
        Assert.assertFalse(savedMetadataCaptor.getValue().isDisabled());
        Assert.assertEquals("domainblacklist/string", savedMetadataCaptor.getValue().getFormat());
        Assert.assertNull(savedMetadataCaptor.getValue().getVersion());
        Assert.assertEquals("blacklist", savedMetadataCaptor.getValue().getFilterType());
        Assert.assertNull(savedMetadataCaptor.getValue().getQueryTransformations());
        Assert.assertEquals(data.getCustomerCreatedName(), savedMetadataCaptor.getValue().getCustomerCreatedName());
        Assert.assertEquals(data.getCustomerCreatedDescription(), savedMetadataCaptor.getValue().getCustomerCreatedDescription());

        Mockito.verify(blacklistCompiler).compile(Mockito.eq(1), Mockito.eq("test-customer-name"), Mockito.any(Supplier.class), Mockito.anyString(), Mockito.anyString());
    }

    private void setupMetadata(ParentalControlFilterMetaData... metaData) throws IOException {
        if (System.getProperty("os.name").startsWith("Mac OS")) {
            try {
                // On MacOS: Sleep at least one second, to ensure that the time stamp of the file has changed.
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                //
            }
        }

        List<ParentalControlFilterMetaData> metadataList = metaData != null ? Arrays.asList(metaData) : Collections.emptyList();
        try (OutputStream out = Files.newOutputStream(metaDataPath)) {
            objectMapper.writeValue(out, metadataList);
        }

        Mockito.when(dataSource.getAll(ParentalControlFilterMetaData.class)).thenReturn(metadataList);
        for (ParentalControlFilterMetaData d : metadataList) {
            Mockito.when(dataSource.get(ParentalControlFilterMetaData.class, d.getId())).thenReturn(d);
        }
    }

    private ParentalControlFilterListsService createService() throws IOException {
        return new ParentalControlFilterListsService(metaDataPath.toString(), customFiltersPath.toString(), dataSource, fileSystemWatchService, objectMapper, parentalControlService, domainBlacklistService, blacklistCompiler);
    }

}
