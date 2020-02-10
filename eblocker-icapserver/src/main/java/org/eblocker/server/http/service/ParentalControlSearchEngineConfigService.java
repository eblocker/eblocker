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
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

@Singleton
@SubSystemService(value = SubSystem.SERVICES)
public class ParentalControlSearchEngineConfigService {

    private static final Logger log = LoggerFactory.getLogger(ParentalControlSearchEngineConfigService.class);

    private final Path configPath;
    private final ObjectMapper objectMapper;

    private Instant lastUpdate = Instant.MIN;
    private Map<String, SearchEngineConfiguration> configByLanguage = Collections.emptyMap();

    @Inject
    public ParentalControlSearchEngineConfigService(@Named("parentalControl.searchEngineConfig.path") String configPath,
                                                    FileSystemWatchService fileSystemWatchService,
                                                    ObjectMapper objectMapper) throws IOException {
        this.configPath = Paths.get(configPath);
        this.objectMapper = objectMapper;
        fileSystemWatchService.watch(Paths.get(configPath), p -> updateConfig());
    }

    @SubSystemInit
    public void init() {
        updateConfig();
    }

    public Map<String, SearchEngineConfiguration> getConfigByLanguage() {
        return configByLanguage;
    }

    private void updateConfig() {
        if (!Files.exists(configPath)) {
            log.warn("missing configuration file: {}", configPath);
            return;
        }
        try {
            Instant lastModification = Files.getLastModifiedTime(configPath).toInstant();
            if (lastModification.isAfter(lastUpdate)) {
                try (InputStream in = Files.newInputStream(configPath)) {
                    configByLanguage = objectMapper.readValue(in, new TypeReference<Map<String, SearchEngineConfiguration>>() {});
                    lastUpdate = lastModification;
                }
                log.debug("engine configuration updated");
            } else {
                log.debug("engine configuration not modified");
            }
        } catch (IOException e) {
            log.error("failed to get last modification time of {}", configPath, e);
        }
    }
}
