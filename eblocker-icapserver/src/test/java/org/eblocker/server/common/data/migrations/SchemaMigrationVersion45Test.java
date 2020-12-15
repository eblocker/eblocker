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
package org.eblocker.server.common.data.migrations;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eblocker.server.common.blocker.Category;
import org.eblocker.server.common.blocker.ExternalDefinition;
import org.eblocker.server.common.blocker.Format;
import org.eblocker.server.common.blocker.Type;
import org.eblocker.server.common.blocker.UpdateInterval;
import org.eblocker.server.common.blocker.UpdateStatus;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.eblocker.server.common.util.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SchemaMigrationVersion45Test {

    private Path localStoragePath;
    private Path customFiltersPath;
    private DataSource dataSource;
    private ObjectMapper objectMapper;

    private SchemaMigration migration;

    @Before
    public void setUp() throws IOException {
        localStoragePath = Files.createTempDirectory(SchemaMigrationVersion45Test.class.getSimpleName() + "-localStorage");
        customFiltersPath = Files.createTempDirectory(SchemaMigrationVersion45Test.class.getSimpleName() + "-customFilters");
        objectMapper = new ObjectMapper();
        dataSource = Mockito.mock(DataSource.class);
        migration = new SchemaMigrationVersion45(localStoragePath.toString(), customFiltersPath.toString(), dataSource, objectMapper);
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(localStoragePath);
        FileUtils.deleteDirectory(customFiltersPath);
    }

    @Test
    public void getSourceVersion() {
        Assert.assertEquals("44", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() {
        Assert.assertEquals("45", migration.getTargetVersion());
    }

    @Test
    public void migrate() throws IOException {
        AtomicInteger nextId = new AtomicInteger(1000);
        Mockito.when(dataSource.nextId(ExternalDefinition.class)).then(im -> nextId.getAndIncrement());
        Mockito.when(dataSource.getAll(ExternalDefinition.class)).thenReturn(Arrays.asList(
                new ExternalDefinition(0, "assigned-domain-filter", null, Category.ADS, Type.DOMAIN, 1, null, null, null, null, null, null, true, null),
                new ExternalDefinition(1, "assigned-pattern-filter", null, Category.ADS, Type.PATTERN, 1, null, null, null, null, null, null, true, null)));
        Mockito.when(dataSource.getAll(ParentalControlFilterMetaData.class)).thenReturn(Arrays.asList(
                new ParentalControlFilterMetaData(0, null, null, null, null, null, null, null, null, true, false, null, null, null),
                new ParentalControlFilterMetaData(1, null, null, null, null, null, null, null, null, true, false, null, null, null),
                new ParentalControlFilterMetaData(2, null, null, org.eblocker.server.common.data.parentalcontrol.Category.CUSTOM, null, null, null, null, "blacklist", false, false, null, "custom-name", "custom-desc"),
                new ParentalControlFilterMetaData(3, null, null, org.eblocker.server.common.data.parentalcontrol.Category.PARENTAL_CONTROL, null, null, null, null, "whitelist", false, false, null, "parental-name", "parental-desc")
        ));

        List<String> customDomains = Arrays.asList("etracker.com", "google-analytics.com");
        writeCustomFilter(2, customDomains);

        List<String> parentalDomains = Arrays.asList("youtube.com", "facebook.com");
        writeCustomFilter(3, parentalDomains);

        migration.migrate();

        ArgumentCaptor<ExternalDefinition> definitionArgumentCaptor = ArgumentCaptor.forClass(ExternalDefinition.class);
        Mockito.verify(dataSource).save(definitionArgumentCaptor.capture(), Mockito.eq(1000));
        Assert.assertEquals(1000, definitionArgumentCaptor.getValue().getId());
        Assert.assertEquals("custom-name", definitionArgumentCaptor.getValue().getName());
        Assert.assertEquals("custom-desc", definitionArgumentCaptor.getValue().getDescription());
        Assert.assertEquals(Category.CUSTOM, definitionArgumentCaptor.getValue().getCategory());
        Assert.assertEquals(Type.DOMAIN, definitionArgumentCaptor.getValue().getType());
        Assert.assertEquals(Integer.valueOf(2), definitionArgumentCaptor.getValue().getReferenceId());
        Assert.assertEquals(Format.DOMAINS, definitionArgumentCaptor.getValue().getFormat());
        Assert.assertEquals(UpdateInterval.NEVER, definitionArgumentCaptor.getValue().getUpdateInterval());
        Assert.assertNull(definitionArgumentCaptor.getValue().getUpdateError());
        Assert.assertEquals(UpdateStatus.READY, definitionArgumentCaptor.getValue().getUpdateStatus());
        Assert.assertEquals(localStoragePath.resolve("1000:DOMAIN").toString(), definitionArgumentCaptor.getValue().getFile());
        Assert.assertEquals("blacklist", definitionArgumentCaptor.getValue().getFilterType());
        Assert.assertEquals(customDomains, Files.readAllLines(Paths.get(definitionArgumentCaptor.getValue().getFile())));

        Mockito.verify(dataSource).save(definitionArgumentCaptor.capture(), Mockito.eq(1001));
        Assert.assertEquals(1001, definitionArgumentCaptor.getValue().getId());
        Assert.assertEquals("parental-name", definitionArgumentCaptor.getValue().getName());
        Assert.assertEquals("parental-desc", definitionArgumentCaptor.getValue().getDescription());
        Assert.assertEquals(Category.PARENTAL_CONTROL, definitionArgumentCaptor.getValue().getCategory());
        Assert.assertEquals(Type.DOMAIN, definitionArgumentCaptor.getValue().getType());
        Assert.assertEquals(Integer.valueOf(3), definitionArgumentCaptor.getValue().getReferenceId());
        Assert.assertEquals(Format.DOMAINS, definitionArgumentCaptor.getValue().getFormat());
        Assert.assertEquals(UpdateInterval.NEVER, definitionArgumentCaptor.getValue().getUpdateInterval());
        Assert.assertNull(definitionArgumentCaptor.getValue().getUpdateError());
        Assert.assertEquals(UpdateStatus.READY, definitionArgumentCaptor.getValue().getUpdateStatus());
        Assert.assertEquals(localStoragePath.resolve("1001:DOMAIN").toString(), definitionArgumentCaptor.getValue().getFile());
        Assert.assertEquals("whitelist", definitionArgumentCaptor.getValue().getFilterType());
        Assert.assertEquals(parentalDomains, Files.readAllLines(Paths.get(definitionArgumentCaptor.getValue().getFile())));
    }

    private void writeCustomFilter(int id, List<String> domains) throws IOException {
        try (OutputStream out = Files.newOutputStream(customFiltersPath.resolve(Integer.toString(id)))) {
            objectMapper.writeValue(out, domains);
        }
    }

}
