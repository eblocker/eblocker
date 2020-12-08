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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.common.blocker.Category;
import org.eblocker.server.common.blocker.ExternalDefinition;
import org.eblocker.server.common.blocker.Format;
import org.eblocker.server.common.blocker.Type;
import org.eblocker.server.common.blocker.UpdateInterval;
import org.eblocker.server.common.blocker.UpdateStatus;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Converts custom domain filters to domain filters usable with new blocker api.
 */
public class SchemaMigrationVersion45 implements SchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationVersion45.class);

    private final Path localStoragePath;
    private final Path customFiltersPath;
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    @Inject
    public SchemaMigrationVersion45(@Named("blocker.localStorage.path") String localStoragePath,
                                    @Named("parentalcontrol.filterlists.file.customercreated.path") String customFiltersPath,
                                    DataSource dataSource,
                                    ObjectMapper objectMapper) {
        this.localStoragePath = Paths.get(localStoragePath);
        this.customFiltersPath = Paths.get(customFiltersPath);
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getSourceVersion() {
        return "44";
    }

    @Override
    public String getTargetVersion() {
        return "45";
    }

    @Override
    public void migrate() {
        Map<Integer, ExternalDefinition> definitionsByReferenceId = dataSource.getAll(ExternalDefinition.class)
            .stream()
            .filter(definition -> Type.DOMAIN == definition.getType())
            .filter(definition -> definition.getReferenceId() != null)
            .collect(Collectors.toMap(ExternalDefinition::getReferenceId, Function.identity()));

        // create list of user created domain filters which do not have an external definition yet
        List<ParentalControlFilterMetaData> customMetadata = dataSource
            .getAll(ParentalControlFilterMetaData.class)
            .stream()
            .filter(d -> !d.isBuiltin())
            .filter(d -> !definitionsByReferenceId.containsKey(d.getId()))
            .collect(Collectors.toList());

        for (ParentalControlFilterMetaData metadata : customMetadata) {
            log.info("Creating external definition for custom filter: {}", metadata.getId());
            int id = dataSource.nextId(ExternalDefinition.class);
            Path path = localStoragePath.resolve(id + ":" + Type.DOMAIN);
            ExternalDefinition definition = new ExternalDefinition(
                id,
                metadata.getCustomerCreatedName(),
                metadata.getCustomerCreatedDescription(),
                mapCategory(metadata.getCategory()),
                Type.DOMAIN,
                metadata.getId(),
                Format.DOMAINS,
                null,
                UpdateInterval.NEVER,
                UpdateStatus.READY,
                null,
                path.toString(),
                !metadata.isDisabled(),
                metadata.getFilterType());
            dataSource.save(definition, definition.getId());

            // create source file from copy
            try (InputStream in = Files.newInputStream(customFiltersPath.resolve(Integer.toString(metadata.getId())))) {
                try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(path))) {
                    List<String> lines = objectMapper.readValue(in, new TypeReference<List<String>>() {
                    });
                    lines.forEach(writer::println);
                }
            } catch (IOException e) {
                log.error("failed to create source for custom filter: {}", metadata.getId(), e);
            }
        }

        dataSource.setVersion("45");
    }

    private Category mapCategory(org.eblocker.server.common.data.parentalcontrol.Category category) {
        switch (category) {
            case CUSTOM:
                return Category.CUSTOM;
            case PARENTAL_CONTROL:
                return Category.PARENTAL_CONTROL;
            default:
                log.error("can not correctly map custom filter of category: {}", category);
                return Category.CUSTOM;
        }
    }
}
