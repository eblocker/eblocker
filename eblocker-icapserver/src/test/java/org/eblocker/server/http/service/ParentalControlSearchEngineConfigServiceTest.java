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

import org.eblocker.server.common.data.parentalcontrol.SearchEngineConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ParentalControlSearchEngineConfigServiceTest {

    private ObjectMapper objectMapper = new ObjectMapper();
    private Path configPath;
    private FileSystemWatchService fileSystemWatchService;
    private ParentalControlSearchEngineConfigService configService;

    @Before
    public void setUp() throws IOException {
        configPath = Files.createTempFile("search-engine-config", ".json");
        Files.delete(configPath);
        fileSystemWatchService = Mockito.mock(FileSystemWatchService.class);
        configService = new ParentalControlSearchEngineConfigService(configPath.toString(), fileSystemWatchService, objectMapper);
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(configPath);
    }

    @Test
    public void testInitNoFile() {
        configService.init();
        assertConfig(Collections.emptyMap(), configService.getConfigByLanguage());
    }

    @Test
    public void testInit() throws IOException {
        Map<String, SearchEngineConfiguration> config = new HashMap<>();
        config.put("en", new SearchEngineConfiguration("en-title", "en-text", "en-iframe"));
        config.put("de", new SearchEngineConfiguration("de-title", "de-text", "de-iframe"));
        writeConfig(config);

        configService.init();

        assertConfig(config, configService.getConfigByLanguage());
    }

    @Test
    public void testCheckConfigUpdate() throws IOException {
        Instant now = Instant.now();

        ArgumentCaptor<Consumer<Path>> captor = ArgumentCaptor.forClass(Consumer.class);
        Mockito.verify(fileSystemWatchService).watch(Mockito.eq(configPath), captor.capture());
        Consumer<Path> updateCallback = captor.getValue();

        configService.init();
        Assert.assertEquals(Collections.emptyMap(), configService.getConfigByLanguage());

        Map<String, SearchEngineConfiguration> config = new HashMap<>();
        config.put("en", new SearchEngineConfiguration("en-title", "en-text", "en-iframe"));
        config.put("de", new SearchEngineConfiguration("de-title", "de-text", "de-iframe"));
        writeConfig(config);
        Files.setLastModifiedTime(configPath, FileTime.from(now));

        updateCallback.accept(configPath);
        assertConfig(config, configService.getConfigByLanguage());

        Map<String, SearchEngineConfiguration> config2 = new HashMap<>();
        config.put("en", new SearchEngineConfiguration("en2-title", "en2-text", "en2-iframe"));
        config.put("de", new SearchEngineConfiguration("de2-title", "de2-text", "de2-iframe"));
        writeConfig(config2);
        Files.setLastModifiedTime(configPath, FileTime.from(now.plusSeconds(1)));

        updateCallback.accept(configPath);
        assertConfig(config2, configService.getConfigByLanguage());

        writeConfig(config);
        Files.setLastModifiedTime(configPath, FileTime.from(now));

        updateCallback.accept(configPath);
        assertConfig(config2, configService.getConfigByLanguage());
    }

    private void writeConfig(Map<String, SearchEngineConfiguration> config) throws IOException {
        try (OutputStream out = Files.newOutputStream(configPath)) {
            objectMapper.writeValue(out, config);
        }
    }

    private void assertConfig(Map<String, SearchEngineConfiguration> expected, Map<String, SearchEngineConfiguration> actual) {
        Assert.assertNotNull(actual);
        Assert.assertEquals(expected.size(), actual.size());
        for(Map.Entry<String, SearchEngineConfiguration> e : expected.entrySet()) {
            SearchEngineConfiguration a = actual.get(e.getKey());
            Assert.assertNotNull(a);
            Assert.assertEquals(e.getValue().getIframe(), a.getIframe());
            Assert.assertEquals(e.getValue().getText(), a.getText());
            Assert.assertEquals(e.getValue().getTitle(), a.getTitle());
        }
    }
}
