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

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterSummaryData;
import org.eblocker.server.common.util.HttpClient;
import org.eblocker.server.http.service.ParentalControlFilterListsService;
import org.eblocker.server.common.TestClock;
import org.eblocker.server.icap.filter.FilterDefinitionFormat;
import org.eblocker.server.icap.filter.FilterLearningMode;
import org.eblocker.server.icap.filter.FilterManager;
import org.eblocker.server.icap.filter.FilterStoreConfiguration;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class UpdateTaskTest {

    private TestClock clock;
    private DataSource dataSource;
    private FilterManager filterManager;
    private ParentalControlFilterListsService filterListsService;
    private HttpClient httpClient;
    private UpdateTask updateTask;

    private List<ExternalDefinition> savedDefinitions;
    private Path sourceFile;

    @Before
    public void setUp() throws IOException {
        clock = new TestClock(ZonedDateTime.now());

        dataSource = Mockito.mock(DataSource.class);
        // not using argument captor as object is mutated
        savedDefinitions = new ArrayList<>();
        Mockito.when(dataSource.save(Mockito.any(ExternalDefinition.class), Mockito.eq(0))).then(im -> {
            ExternalDefinition saved = im.getArgument(0);
            savedDefinitions.add(new ExternalDefinition(saved.getId(), saved.getName(), saved.getDescription(), saved.getCategory(), saved.getType(), saved.getReferenceId(), saved.getFormat(), saved.getUrl(), saved.getUpdateInterval(),
                    saved.getUpdateStatus(), saved.getUpdateError(), saved.getFile(), saved.isEnabled(), "blacklist"));
            return saved;
        });

        filterManager = Mockito.mock(FilterManager.class);
        filterListsService = Mockito.mock(ParentalControlFilterListsService.class);
        httpClient = Mockito.mock(HttpClient.class);

        sourceFile = Files.createTempFile(UpdateTaskTest.class.getSimpleName(), ".test");

        updateTask = new UpdateTask(clock, dataSource, filterManager, filterListsService, httpClient, 0);
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(sourceFile);
    }

    @Test
    public void testCreateDomainBlocker() throws IOException {
        Mockito.when(httpClient.download("http://filter.org/domains.txt")).thenReturn(new ByteArrayInputStream("eblocker.com\netracker.com\n".getBytes()));

        ExternalDefinition definition = new ExternalDefinition(0, "test", "description", Category.ADS, Type.DOMAIN, null, Format.DOMAINS, "http://filter.org/domains.txt", UpdateInterval.DAILY, UpdateStatus.NEW, null, sourceFile.toString(), true,
                "blacklist");
        Mockito.when(dataSource.get(ExternalDefinition.class, 0)).thenReturn(definition);
        Mockito.when(filterListsService.createFilterList(Mockito.any(ParentalControlFilterSummaryData.class), Mockito.anyString())).thenReturn(new ParentalControlFilterSummaryData(123, null, null, null, null, null, false, false, null, null, null, null));

        updateTask.run();

        ArgumentCaptor<ParentalControlFilterSummaryData> dataCaptor = ArgumentCaptor.forClass(ParentalControlFilterSummaryData.class);
        Mockito.verify(filterListsService).createFilterList(dataCaptor.capture(), Mockito.eq("blacklist"));
        ParentalControlFilterSummaryData data = dataCaptor.getValue();
        Assert.assertNull(data.getId());
        Assert.assertEquals("test", data.getCustomerCreatedName());
        Assert.assertEquals("description", data.getCustomerCreatedDescription());
        Assert.assertEquals("blacklist", data.getFilterType());
        Assert.assertEquals(org.eblocker.server.common.data.parentalcontrol.Category.ADS, data.getCategory());
        Assert.assertFalse(data.isDisabled());
        Assert.assertNull(data.getDomains());
        Assert.assertEquals(Date.from(clock.instant()), data.getLastUpdate());
        Assert.assertEquals(Arrays.asList("eblocker.com", "etracker.com"), data.getDomainsStreamSupplier().get().collect(Collectors.toList()));

        Assert.assertEquals(2, savedDefinitions.size());
        Assert.assertEquals(UpdateStatus.INITIAL_UPDATE, savedDefinitions.get(0).getUpdateStatus());
        Assert.assertEquals(UpdateStatus.READY, savedDefinitions.get(1).getUpdateStatus());
        Assert.assertEquals(Integer.valueOf(123), savedDefinitions.get(1).getReferenceId());
    }

    @Test
    public void testUpdateDomainBlocker() throws IOException {
        Mockito.when(httpClient.download("http://filter.org/domains.txt")).thenReturn(new ByteArrayInputStream("eblocker.com\netracker.com\n".getBytes()));

        ExternalDefinition definition = new ExternalDefinition(0, "test", "description", Category.ADS, Type.DOMAIN, 123, Format.DOMAINS, "http://filter.org/domains.txt", UpdateInterval.DAILY, UpdateStatus.READY, null, sourceFile.toString(), true,
                "blacklist");
        Mockito.when(dataSource.get(ExternalDefinition.class, 0)).thenReturn(definition);
        Mockito.when(filterListsService.updateFilterList(Mockito.any(ParentalControlFilterSummaryData.class), Mockito.anyString())).thenReturn(new ParentalControlFilterSummaryData(0, null, null, null, null, null, false, false, null, null, null, null));
        Mockito.when(filterListsService.getParentalControlFilterMetaData(123)).thenReturn(new ParentalControlFilterMetaData(
                123,
                Collections.singletonMap("en", "test"),
                null,
                org.eblocker.server.common.data.parentalcontrol.Category.ADS,
                Collections.singletonList(sourceFile.toString()),
                null,
                Date.from(clock.instant()),
                "domainblacklist/string",
                "blacklist",
                false,
                false,
                Collections.emptyList(),
                "test",
                null));

        updateTask.run();

        ArgumentCaptor<ParentalControlFilterSummaryData> dataCaptor = ArgumentCaptor.forClass(ParentalControlFilterSummaryData.class);
        Mockito.verify(filterListsService).updateFilterList(dataCaptor.capture(), Mockito.eq("blacklist"));
        ParentalControlFilterSummaryData data = dataCaptor.getValue();
        Assert.assertEquals(Integer.valueOf(123), data.getId());
        Assert.assertEquals("test", data.getCustomerCreatedName());
        Assert.assertEquals("blacklist", data.getFilterType());
        Assert.assertEquals(org.eblocker.server.common.data.parentalcontrol.Category.ADS, data.getCategory());
        Assert.assertFalse(data.isDisabled());
        Assert.assertNull(data.getDomains());
        Assert.assertEquals(Date.from(clock.instant()), data.getLastUpdate());
        Assert.assertEquals(Arrays.asList("eblocker.com", "etracker.com"), data.getDomainsStreamSupplier().get().collect(Collectors.toList()));

        Assert.assertEquals(2, savedDefinitions.size());
        Assert.assertEquals(UpdateStatus.UPDATE, savedDefinitions.get(0).getUpdateStatus());
        Assert.assertEquals(UpdateStatus.READY, savedDefinitions.get(1).getUpdateStatus());
    }

    @Test
    public void testCreatePatternBlocker() throws IOException {
        Mockito.when(httpClient.download("http://filter.org/easylist.txt")).thenReturn(new ByteArrayInputStream("eblocker.com\netracker.com\n".getBytes()));

        ExternalDefinition definition = new ExternalDefinition(0, "test", "description", Category.ADS, Type.PATTERN, null, Format.EASYLIST, "http://filter.org/easylist.txt", UpdateInterval.DAILY, UpdateStatus.NEW, null, sourceFile.toString(), true,
                "blacklist");
        Mockito.when(dataSource.get(ExternalDefinition.class, 0)).thenReturn(definition);
        Mockito.when(filterManager.addFilter(Mockito.any(FilterStoreConfiguration.class))).thenReturn(new FilterStoreConfiguration(235, null, null, false, 0, null, null, null, false, null, true));
        Mockito.when(filterManager.getFilterStoreConfigurationById(235)).thenReturn(new FilterStoreConfiguration(
                235,
                "test",
                org.eblocker.server.icap.filter.Category.ADS,
                false,
                0,
                new String[]{ sourceFile.toString() },
                FilterLearningMode.ASYNCHRONOUS,
                FilterDefinitionFormat.EASYLIST,
                true,
                new String[0],
                true));

        updateTask.run();

        ArgumentCaptor<FilterStoreConfiguration> dataCaptor = ArgumentCaptor.forClass(FilterStoreConfiguration.class);
        Mockito.verify(filterManager).addFilter(dataCaptor.capture());
        FilterStoreConfiguration configuration = dataCaptor.getValue();
        Assert.assertNull(configuration.getId());
        Assert.assertEquals("test", configuration.getName());
        Assert.assertEquals(org.eblocker.server.icap.filter.Category.ADS, configuration.getCategory());
        Assert.assertEquals(FilterLearningMode.ASYNCHRONOUS, configuration.getLearningMode());
        Assert.assertEquals(FilterDefinitionFormat.EASYLIST, configuration.getFormat());
        Assert.assertArrayEquals(new String[]{ sourceFile.toString() }, configuration.getResources());
        Assert.assertArrayEquals(new String[0], configuration.getRuleFilters());

        Assert.assertEquals(2, savedDefinitions.size());
        Assert.assertEquals(UpdateStatus.INITIAL_UPDATE, savedDefinitions.get(0).getUpdateStatus());
        Assert.assertEquals(UpdateStatus.READY, savedDefinitions.get(1).getUpdateStatus());
        Assert.assertEquals(Integer.valueOf(235), savedDefinitions.get(1).getReferenceId());
    }

    @Test
    public void testCreateMalwarePatternBlocker() throws IOException {
        Mockito.when(httpClient.download("http://filter.org/easylist.txt")).thenReturn(new ByteArrayInputStream("eblocker.com\netracker.com\n".getBytes()));

        ExternalDefinition definition = new ExternalDefinition(0, "test", null, Category.ADS, Type.PATTERN, null, Format.EASYLIST, "http://filter.org/easylist.txt", UpdateInterval.DAILY, UpdateStatus.NEW, null, sourceFile.toString(), true, "blacklist");
        Mockito.when(dataSource.get(ExternalDefinition.class, 0)).thenReturn(definition);
        Mockito.when(filterManager.addFilter(Mockito.any(FilterStoreConfiguration.class))).thenReturn(new FilterStoreConfiguration(235, null, null, false, 0, null, null, null, false, null, true));
        Mockito.when(filterManager.getFilterStoreConfigurationById(235)).thenReturn(new FilterStoreConfiguration(
                235,
                "test",
                org.eblocker.server.icap.filter.Category.MALWARE,
                false,
                0,
                new String[]{ sourceFile.toString() },
                FilterLearningMode.SYNCHRONOUS,
                FilterDefinitionFormat.EASYLIST,
                true,
                new String[0],
                true));

        updateTask.run();

        ArgumentCaptor<FilterStoreConfiguration> dataCaptor = ArgumentCaptor.forClass(FilterStoreConfiguration.class);
        Mockito.verify(filterManager).addFilter(dataCaptor.capture());
        FilterStoreConfiguration configuration = dataCaptor.getValue();
        Assert.assertNull(configuration.getId());
        Assert.assertEquals("test", configuration.getName());
        Assert.assertEquals(org.eblocker.server.icap.filter.Category.ADS, configuration.getCategory());
        Assert.assertEquals(FilterLearningMode.ASYNCHRONOUS, configuration.getLearningMode());
        Assert.assertEquals(FilterDefinitionFormat.EASYLIST, configuration.getFormat());
        Assert.assertArrayEquals(new String[]{ sourceFile.toString() }, configuration.getResources());
        Assert.assertArrayEquals(new String[0], configuration.getRuleFilters());

        Assert.assertEquals(2, savedDefinitions.size());
        Assert.assertEquals(UpdateStatus.INITIAL_UPDATE, savedDefinitions.get(0).getUpdateStatus());
        Assert.assertEquals(UpdateStatus.READY, savedDefinitions.get(1).getUpdateStatus());
        Assert.assertEquals(Integer.valueOf(235), savedDefinitions.get(1).getReferenceId());
    }

    @Test
    public void testUpdatePatternBlocker() throws IOException {
        Mockito.when(httpClient.download("http://filter.org/easylist.txt")).thenReturn(new ByteArrayInputStream("eblocker.com\netracker.com\n".getBytes()));

        ExternalDefinition definition = new ExternalDefinition(0, "test", "description", Category.ADS, Type.PATTERN, 235, Format.EASYLIST, "http://filter.org/easylist.txt", UpdateInterval.DAILY, UpdateStatus.READY, null, sourceFile.toString(), true,
                "blacklist");
        Mockito.when(dataSource.get(ExternalDefinition.class, 0)).thenReturn(definition);
        Mockito.when(filterManager.addFilter(Mockito.any(FilterStoreConfiguration.class))).thenReturn(new FilterStoreConfiguration(235, null, null, false, 0, null, null, null, false, null, true));

        updateTask.run();

        Mockito.verify(filterManager).update();

        Assert.assertEquals(2, savedDefinitions.size());
        Assert.assertEquals(UpdateStatus.UPDATE, savedDefinitions.get(0).getUpdateStatus());
        Assert.assertEquals(UpdateStatus.READY, savedDefinitions.get(1).getUpdateStatus());
        Assert.assertEquals(Integer.valueOf(235), savedDefinitions.get(1).getReferenceId());
    }

    @Test
    public void testUpdateFailedBlocker() throws IOException {
        Mockito.when(httpClient.download("http://filter.org/easylist.txt")).thenThrow(new IOException("download failed"));

        ExternalDefinition definition = new ExternalDefinition(0, "test", "description", Category.ADS, Type.PATTERN, 235, Format.EASYLIST, "http://filter.org/easylist.txt", UpdateInterval.DAILY, UpdateStatus.READY, null, sourceFile.toString(), true,
                "blacklist");
        Mockito.when(dataSource.get(ExternalDefinition.class, 0)).thenReturn(definition);
        Mockito.when(filterManager.addFilter(Mockito.any(FilterStoreConfiguration.class))).thenReturn(new FilterStoreConfiguration(235, null, null, false, 0, null, null, null, false, null, true));

        updateTask.run();

        Mockito.verify(filterManager, Mockito.never()).update();

        Assert.assertEquals(2, savedDefinitions.size());
        Assert.assertEquals(UpdateStatus.UPDATE, savedDefinitions.get(0).getUpdateStatus());
        Assert.assertEquals(UpdateStatus.UPDATE_FAILED, savedDefinitions.get(1).getUpdateStatus());
        Assert.assertEquals("download failed", savedDefinitions.get(1).getUpdateError());
    }

    @Test
    public void testCreateBlockerNoDownload() throws IOException {
        Files.write(sourceFile, "eblocker.com\netracker.com\n".getBytes(StandardCharsets.UTF_8));

        ExternalDefinition definition = new ExternalDefinition(0, "test", "description", Category.ADS, Type.DOMAIN, null, Format.DOMAINS, null, UpdateInterval.NEVER, UpdateStatus.NEW, null, sourceFile.toString(), true, "blacklist");
        Mockito.when(dataSource.get(ExternalDefinition.class, 0)).thenReturn(definition);
        Mockito.when(filterListsService.createFilterList(Mockito.any(ParentalControlFilterSummaryData.class), Mockito.anyString())).thenReturn(new ParentalControlFilterSummaryData(123, null, null, null, null, null, false, false, null, null, null, null));

        updateTask.run();

        Mockito.verifyNoInteractions(httpClient);

        ArgumentCaptor<ParentalControlFilterSummaryData> dataCaptor = ArgumentCaptor.forClass(ParentalControlFilterSummaryData.class);
        Mockito.verify(filterListsService).createFilterList(dataCaptor.capture(), Mockito.eq("blacklist"));
        ParentalControlFilterSummaryData data = dataCaptor.getValue();
        Assert.assertNull(data.getId());
        Assert.assertEquals("test", data.getCustomerCreatedName());
        Assert.assertEquals("blacklist", data.getFilterType());
        Assert.assertEquals(org.eblocker.server.common.data.parentalcontrol.Category.ADS, data.getCategory());
        Assert.assertFalse(data.isDisabled());
        Assert.assertNull(data.getDomains());
        Assert.assertEquals(Date.from(clock.instant()), data.getLastUpdate());
        Assert.assertEquals(Arrays.asList("eblocker.com", "etracker.com"), data.getDomainsStreamSupplier().get().collect(Collectors.toList()));

        Assert.assertEquals(2, savedDefinitions.size());
        Assert.assertEquals(UpdateStatus.INITIAL_UPDATE, savedDefinitions.get(0).getUpdateStatus());
        Assert.assertEquals(UpdateStatus.READY, savedDefinitions.get(1).getUpdateStatus());
        Assert.assertEquals(Integer.valueOf(123), savedDefinitions.get(1).getReferenceId());
    }

    @Test
    public void testUpdateBlockerNoDownload() throws IOException {
        Files.write(sourceFile, "eblocker.com\netracker.com\n".getBytes(StandardCharsets.UTF_8));

        ExternalDefinition definition = new ExternalDefinition(0, "test", "description", Category.ADS, Type.DOMAIN, 123, Format.DOMAINS, null, UpdateInterval.DAILY, UpdateStatus.READY, null, sourceFile.toString(), true, "blacklist");
        Mockito.when(dataSource.get(ExternalDefinition.class, 0)).thenReturn(definition);
        Mockito.when(filterListsService.updateFilterList(Mockito.any(ParentalControlFilterSummaryData.class), Mockito.anyString())).thenReturn(new ParentalControlFilterSummaryData(0, null, null, null, null, null, false, false, null, null, null, null));
        Mockito.when(filterListsService.getParentalControlFilterMetaData(123)).thenReturn(new ParentalControlFilterMetaData(
                123,
                Collections.singletonMap("en", "test"),
                null,
                org.eblocker.server.common.data.parentalcontrol.Category.ADS,
                Collections.singletonList(sourceFile.toString()),
                null,
                Date.from(clock.instant()),
                "domainblacklist/string",
                "blacklist",
                false,
                false,
                Collections.emptyList(),
                "test",
                null));

        updateTask.run();

        Mockito.verifyNoInteractions(httpClient);

        ArgumentCaptor<ParentalControlFilterSummaryData> dataCaptor = ArgumentCaptor.forClass(ParentalControlFilterSummaryData.class);
        Mockito.verify(filterListsService).updateFilterList(dataCaptor.capture(), Mockito.eq("blacklist"));
        ParentalControlFilterSummaryData data = dataCaptor.getValue();
        Assert.assertEquals(Integer.valueOf(123), data.getId());
        Assert.assertEquals("test", data.getCustomerCreatedName());
        Assert.assertEquals("blacklist", data.getFilterType());
        Assert.assertEquals(org.eblocker.server.common.data.parentalcontrol.Category.ADS, data.getCategory());
        Assert.assertFalse(data.isDisabled());
        Assert.assertNull(data.getDomains());
        Assert.assertEquals(Date.from(clock.instant()), data.getLastUpdate());
        Assert.assertEquals(Arrays.asList("eblocker.com", "etracker.com"), data.getDomainsStreamSupplier().get().collect(Collectors.toList()));

        Assert.assertEquals(2, savedDefinitions.size());
        Assert.assertEquals(UpdateStatus.UPDATE, savedDefinitions.get(0).getUpdateStatus());
        Assert.assertEquals(UpdateStatus.READY, savedDefinitions.get(1).getUpdateStatus());
    }

    @Test
    public void testUnknownBlocker() {
        Mockito.when(dataSource.get(ExternalDefinition.class, 0)).thenReturn(null);
        updateTask.run();

        Mockito.verify(filterManager, Mockito.never()).update();
    }

    // EB1-2460
    @Test
    public void testBlockerNonUtf8Encoding() throws IOException {
        String hosts = "127.0.0.1\tbireysel-ziraat-bankasi-giris.com\r\n" +
                "127.0.0.1\tbireysel-zîraat.com\r\n" +
                "127.0.0.1\tbireysel.halkwebsubesi.com\r\n";

        Mockito.when(httpClient.download("http://filter.org/domains.txt")).thenReturn(new ByteArrayInputStream(hosts.getBytes(StandardCharsets.ISO_8859_1)));

        ExternalDefinition definition = new ExternalDefinition(0, "test", "description", Category.ADS, Type.DOMAIN, null, Format.ETC_HOSTS, "http://filter.org/domains.txt", UpdateInterval.DAILY, UpdateStatus.NEW, null, sourceFile.toString(), true,
                "blacklist");
        Mockito.when(dataSource.get(ExternalDefinition.class, 0)).thenReturn(definition);
        Mockito.when(filterListsService.createFilterList(Mockito.any(ParentalControlFilterSummaryData.class), Mockito.anyString())).thenReturn(new ParentalControlFilterSummaryData(123, null, null, null, null, null, false, false, null, null, null, null));

        updateTask.run();

        ArgumentCaptor<ParentalControlFilterSummaryData> dataCaptor = ArgumentCaptor.forClass(ParentalControlFilterSummaryData.class);
        Mockito.verify(filterListsService).createFilterList(dataCaptor.capture(), Mockito.eq("blacklist"));
        ParentalControlFilterSummaryData data = dataCaptor.getValue();
        Assert.assertNull(data.getId());
        Assert.assertEquals("test", data.getCustomerCreatedName());
        Assert.assertEquals("description", data.getCustomerCreatedDescription());
        Assert.assertEquals("blacklist", data.getFilterType());
        Assert.assertEquals(org.eblocker.server.common.data.parentalcontrol.Category.ADS, data.getCategory());
        Assert.assertFalse(data.isDisabled());
        Assert.assertNull(data.getDomains());
        Assert.assertEquals(Date.from(clock.instant()), data.getLastUpdate());
        Assert.assertEquals(Arrays.asList("bireysel-ziraat-bankasi-giris.com", "bireysel-zîraat.com", "bireysel.halkwebsubesi.com"), data.getDomainsStreamSupplier().get().collect(Collectors.toList()));

        Assert.assertEquals(2, savedDefinitions.size());
        Assert.assertEquals(UpdateStatus.INITIAL_UPDATE, savedDefinitions.get(0).getUpdateStatus());
        Assert.assertEquals(UpdateStatus.READY, savedDefinitions.get(1).getUpdateStatus());
        Assert.assertEquals(Integer.valueOf(123), savedDefinitions.get(1).getReferenceId());

    }
}
