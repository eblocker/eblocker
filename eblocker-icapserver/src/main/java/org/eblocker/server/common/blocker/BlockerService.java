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
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.common.malware.MalwareFilterService;
import org.eblocker.server.http.service.ParentalControlFilterListsService;
import org.eblocker.server.icap.filter.FilterManager;
import org.eblocker.server.icap.filter.FilterStoreConfiguration;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

@SubSystemService(SubSystem.SERVICES)
public class BlockerService {

    private static final Logger log = LoggerFactory.getLogger(BlockerService.class);

    private final Path localStoragePath;
    private final BlockerIdTypeIdCache idCache;
    private final DataSource dataSource;
    private final FilterManager filterManager;
    private final MalwareFilterService malwareFilterService;
    private final ParentalControlFilterListsService filterListsService;
    private final ScheduledExecutorService executorService;
    private final UpdateTaskFactory updateTaskFactory;

    @Inject
    public BlockerService(@Named("blocker.localStorage.path") String localStoragePath,
                          BlockerIdTypeIdCache idCache,
                          DataSource dataSource,
                          FilterManager filterManager,
                          MalwareFilterService malwareFilterService,
                          ParentalControlFilterListsService filterListsService,
                          @Named("lowPrioScheduledExecutor") ScheduledExecutorService executorService,
                          UpdateTaskFactory updateTaskFactory) {
        this.localStoragePath = Paths.get(localStoragePath);
        this.idCache = idCache;
        this.dataSource = dataSource;
        this.filterManager = filterManager;
        this.malwareFilterService = malwareFilterService;
        this.filterListsService = filterListsService;
        this.executorService = executorService;
        this.updateTaskFactory = updateTaskFactory;
    }

    @SubSystemInit
    public void init() {
        List<ExternalDefinition> definitions = dataSource.getAll(ExternalDefinition.class);
        for(ExternalDefinition definition : definitions) {
            if (definition.getUpdateStatus() == UpdateStatus.INITIAL_UPDATE) {
                log.warn("interrupted update: {}", definition.getId());
                definition.setUpdateStatus(UpdateStatus.INITIAL_UPDATE_FAILED);
                definition.setUpdateError("initial update interrupted");
                dataSource.save(definition, definition.getId());
            } else if (definition.getUpdateStatus() == UpdateStatus.UPDATE) {
                log.warn("interrupted update: {}", definition.getId());
                definition.setUpdateStatus(UpdateStatus.UPDATE_FAILED);
                definition.setUpdateError("update interrupted");
                dataSource.save(definition, definition.getId());
            }
        }
    }

    public List<Blocker> getBlockers() {
        List<ExternalDefinition> definitions = dataSource.getAll(ExternalDefinition.class);
        Map<TypeId, ExternalDefinition> definitionByTypeId = definitions.stream()
            .filter(definition -> definition.getReferenceId() != null)
            .collect(Collectors.toMap(definition -> new TypeId(definition.getType(), definition.getReferenceId()), Function.identity()));

        List<Blocker> blockers = new ArrayList<>();
        blockers.addAll(getPendingFilters(definitions));
        blockers.addAll(getDomainFilters(definitionByTypeId));
        blockers.addAll(getPatternFilters(definitionByTypeId));
        blockers.add(getMalwareUrlFilter());
        return blockers;
    }

    public Blocker getBlockerById(int id) {
        ExternalDefinition definition = dataSource.get(ExternalDefinition.class, id);
        Type type;
        Integer referenceId;
        if (definition != null) {
            type = definition.getType();
            referenceId = definition.getReferenceId();
            if (referenceId == null) {
                return mapDefinition(definition, null, true);
            }
        } else {
            TypeId typeId = idCache.getTypeId(id);
            if (typeId == null) {
                return null;
            }
            type = typeId.type;
            referenceId = typeId.id;
        }

        switch (type) {
            case DOMAIN:
                return getDomainBlockerById(referenceId, definition);
            case PATTERN:
                return getPatternBlockerById(referenceId, definition);
            case MALWARE_URL:
                return getMalwareUrlFilter();
            default:
                throw new IllegalArgumentException("unknown type " + type);
        }
    }

    public Blocker createBlocker(Blocker blocker) {
        int id = dataSource.nextId(ExternalDefinition.class);

        UpdateStatus updateStatus = UpdateStatus.NEW;
        String error = null;
        Path path = localStoragePath.resolve(id + ":" + blocker.getType());
        if (blocker.getContent() != null) {
            try {
                Files.write(path, blocker.getContent().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                log.error("Failed to write blocker {} content", id, e);
                error = e.getMessage();
                updateStatus = UpdateStatus.INITIAL_UPDATE_FAILED;
            }
        }

        ExternalDefinition definition = new ExternalDefinition(id,
            firstValue(blocker.getName()),
            firstValue(blocker.getDescription()),
            blocker.getCategory(),
            mapBlockerType(blocker.getType()),
            null,
            blocker.getFormat(),
            blocker.getUrl(),
            blocker.getUpdateInterval(),
            updateStatus,
            error,
            path.toString(),
            true,
            blocker.getFilterType());
        dataSource.save(definition, id);

        executorService.submit(updateTaskFactory.create(id));
        return mapDefinition(definition, null, true);
    }

    public Blocker updateBlocker(Blocker blocker) {
        ExternalDefinition definition = dataSource.get(ExternalDefinition.class, blocker.getId());
        if (definition == null) {
            TypeId typeId = idCache.getTypeId(blocker.getId());
            if (typeId == null) {
                return null;
            }

            switch (typeId.type) {
                case DOMAIN:
                    return enableDomainFilter(typeId.id, null, blocker.isEnabled());
                case PATTERN:
                    return enablePatternFilter(typeId.id, null, blocker.isEnabled());
                case MALWARE_URL:
                    return enableMalwareUrlFilter(blocker.isEnabled());
                default:
                    throw new IllegalArgumentException("unknown type " + typeId.type);
            }
        }

        if (mapBlockerType(blocker.getType()) != definition.getType()) {
            throw new UnsupportedOperationException("can not change blocker type");
        }

        if (blocker.getCategory() != definition.getCategory()) {
            throw new UnsupportedOperationException("can not change blocker category");
        }

        boolean needsUpdate = blocker.getFormat() != definition.getFormat() || blocker.getContent() != null || !blocker.getUrl().equals(definition.getUrl());
        definition.setName(firstValue(blocker.getName()));
        definition.setDescription(firstValue(blocker.getDescription()));
        definition.setFormat(blocker.getFormat());
        definition.setUrl(blocker.getUrl());
        definition.setUpdateInterval(blocker.getUpdateInterval());
        definition.setEnabled(blocker.isEnabled());

        if (blocker.getContent() != null) {
            try {
                Files.write(Paths.get(definition.getFile()), blocker.getContent().getBytes());
            } catch (IOException e) {
                log.error("Failed to write blocker {} content", definition.getId(), e);
                definition.setUpdateStatus(UpdateStatus.INITIAL_UPDATE_FAILED);
                definition.setUpdateError(e.getMessage());
                needsUpdate = false;
            }
        }

        if (needsUpdate) {
            definition.setUpdateStatus(UpdateStatus.INITIAL_UPDATE); // TODO: not quite right, it is not already updating
            definition.setUpdateError(null);
        }
        dataSource.save(definition, definition.getId());

        if (needsUpdate) {
            executorService.submit(updateTaskFactory.create(definition.getId()));
        }

        if (definition.getReferenceId() == null) {
            return mapDefinition(definition, null, definition.isEnabled());
        }

        if (definition.getType() == Type.DOMAIN) {
            return enableDomainFilter(definition.getReferenceId(), definition, definition.isEnabled());
        }

        return enablePatternFilter(definition.getReferenceId(), definition, definition.isEnabled());
    }

    public void deleteBlocker(int id) {
        ExternalDefinition definition = dataSource.get(ExternalDefinition.class, id);
        if (definition == null) {
            return;
        }

        // First delete the actual filter and afterwards the definition.
        // This at least ensures users see the deletion has no effect and the
        // filter does not stay activated unseen.
        if (definition.getReferenceId() != null) {
            if (definition.getType() == Type.DOMAIN) {
                filterListsService.deleteFilterList(definition.getReferenceId());
            } else {
                filterManager.removeFilter(definition.getReferenceId());
            }
        }

        dataSource.delete(ExternalDefinition.class, id);
        try {
            Files.deleteIfExists(Paths.get(definition.getFile()));
        } catch (IOException e) {
            log.warn("failed to delete filter {} source: {}", definition.getId(), definition.getFile(), e);
        }
    }

    public void update() {
        // tasks are not scheduled individually to avoid blocking the scheduler for other tasks (UpdateTask::run is synchronized)
        List<UpdateTask> tasks = dataSource.getAll(ExternalDefinition.class)
            .stream()
            .filter(definition -> definition.getReferenceId() != null)
            .filter(definition -> definition.getUpdateInterval() == UpdateInterval.DAILY)
            .filter(definition -> definition.getUpdateStatus() == UpdateStatus.READY || definition.getUpdateStatus() == UpdateStatus.UPDATE_FAILED)
            .map(ExternalDefinition::getId)
            .map(updateTaskFactory::create)
            .collect(Collectors.toList());

        long start = System.currentTimeMillis();
        log.info("Updating {} custom blockers", tasks.size());
        tasks.forEach(Runnable::run);
        long elapsed = System.currentTimeMillis() - start;
        log.info("Updating {} blockers finished in {}ms", tasks.size(), elapsed);
    }

    private Blocker getDomainBlockerById(int id, ExternalDefinition externalDefinition) {
        ParentalControlFilterMetaData metadata = filterListsService.getParentalControlFilterMetaData(id);
        if (metadata == null) {
            return null;
        }
        return mapParentControlFilterMetadata(metadata, externalDefinition);
    }

    private Blocker getPatternBlockerById(int id, ExternalDefinition externalDefinition) {
        FilterStoreConfiguration configuration = filterManager.getFilterStoreConfigurationById(id);
        if (configuration == null) {
            return null;
        }
        return mapFilterStoreConfiguration(configuration, externalDefinition);
    }

    private List<Blocker> getPendingFilters(List<ExternalDefinition> externalDefinitions) {
        return externalDefinitions.stream()
            .filter(definition -> definition.getReferenceId() == null)
            .map(definition -> mapDefinition(definition, null, true))
            .collect(Collectors.toList());
    }

    private Blocker mapDefinition(ExternalDefinition definition, Long lastUpdate, boolean enabled) {
        return new Blocker(definition.getId(),
            localizedMap(definition.getName()),
            localizedMap(definition.getDescription()),
            mapType(definition.getType()),
            definition.getCategory(),
            lastUpdate,
            false,
            definition.getUrl(),
            readContent(definition),
            definition.getFormat(),
            null,
            definition.getUpdateInterval(),
            definition.getUpdateStatus(),
            enabled,
            definition.getFilterType());
    }

    private List<Blocker> getDomainFilters(Map<TypeId, ExternalDefinition> definitionsByTypeReference) {
        return filterListsService.getParentalControlFilterMetaData()
            .stream()
            .map(m -> mapParentControlFilterMetadata(m, definitionsByTypeReference.get(new TypeId(Type.DOMAIN, m.getId()))))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private List<Blocker> getPatternFilters(Map<TypeId, ExternalDefinition> definitionsByTypeReference) {
        return filterManager.getFilterConfigurations()
            .stream()
            .map(c -> mapFilterStoreConfiguration(c, definitionsByTypeReference.get(new TypeId(Type.PATTERN, c.getId()))))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private Blocker getMalwareUrlFilter() {
        return new Blocker(
            idCache.getId(new TypeId(Type.MALWARE_URL, 0)),
            localizedMap("Malware"),
            Collections.emptyMap(),
            BlockerType.PATTERN,
            Category.MALWARE,
            malwareFilterService.getLastUpdate(),
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            malwareFilterService.isEnabled(),
            "blacklist"
        );
    }

    private Blocker mapParentControlFilterMetadata(ParentalControlFilterMetaData metadata, ExternalDefinition definition) {
        if (definition == null) {
            Category category = mapDomainFilterCategory(metadata.getCategory());
            if (category == null) {
                return null;
            }
            return new Blocker(idCache.getId(new TypeId(Type.DOMAIN, metadata.getId())),
                metadata.getName(),
                metadata.getDescription(),
                BlockerType.DOMAIN,
                category,
                metadata.getDate().getTime(),
                metadata.isBuiltin(),
                null,
                null,
                null,
                null,
                null,
                null,
                !metadata.isDisabled(),
                metadata.getFilterType());
        }

        return mapDefinition(definition, metadata.getDate().getTime(), !metadata.isDisabled());
    }

    private Category mapDomainFilterCategory(org.eblocker.server.common.data.parentalcontrol.Category category) {
        switch (category) {
            case ADS:
                return Category.ADS;
            case CUSTOM:
                return Category.CUSTOM;
            case MALWARE:
                return Category.MALWARE;
            case PARENTAL_CONTROL:
                return Category.PARENTAL_CONTROL;
            case TRACKERS:
                return Category.TRACKER;
            default:
                return null;
        }
    }

    private Blocker mapFilterStoreConfiguration(FilterStoreConfiguration configuration, ExternalDefinition definition) {

        Long lastUpdate = filterManager.getFilterStore(configuration.getId()) != null &&
            filterManager.getFilterStore(configuration.getId()).getLastUpdate() != null ?
            filterManager.getFilterStore(configuration.getId()).getLastUpdate().getTime() : new Date().getTime();

        if (definition == null) {
            Category category = mapPatternFilterCategory(configuration.getCategory());
            if (category == null) {
                return null;
            }
            return new Blocker(idCache.getId(new TypeId(Type.PATTERN, configuration.getId())),
                localizedMap(configuration.getName()),
                Collections.emptyMap(),
                BlockerType.PATTERN,
                category,
                lastUpdate,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                configuration.isEnabled(),
                "blacklist"); // TODO ??
        }

        return mapDefinition(definition, lastUpdate, configuration.isEnabled());
    }

    private Category mapPatternFilterCategory(org.eblocker.server.icap.filter.Category category) {
        switch (category) {
            case ADS:
                return Category.ADS;
            case TRACKER_BLOCKER:
                return Category.TRACKER;
            default:
                return null;
        }
    }

    private Blocker enableDomainFilter(int id, ExternalDefinition definition, boolean enabled) {
        ParentalControlFilterMetaData metadata = filterListsService.getParentalControlFilterMetaData(id);
        if (metadata.isDisabled() == enabled) {
            metadata.setDisabled(!enabled);
            filterListsService.updateFilterList(new ParentalControlFilterSummaryData(metadata), metadata.getFilterType());
        }
        return getDomainBlockerById(id, definition);
    }

    private Blocker enablePatternFilter(int id, ExternalDefinition definition, boolean enabled) {
        FilterStoreConfiguration configuration = filterManager.getFilterStoreConfigurationById(id);
        if (configuration.isEnabled() != enabled) {
            FilterStoreConfiguration updatedConfiguration = filterManager.updateFilter(new FilterStoreConfiguration(
                configuration.getId(),
                configuration.getName(),
                configuration.getCategory(),
                configuration.isBuiltin(),
                configuration.getVersion(),
                configuration.getResources(),
                configuration.getLearningMode(),
                configuration.getFormat(),
                configuration.isLearnForAllDomains(),
                configuration.getRuleFilters(),
                enabled
            ));
            return mapFilterStoreConfiguration(updatedConfiguration, definition);
        }
        return mapFilterStoreConfiguration(configuration, definition);
    }

    private String readContent(ExternalDefinition definition) {
        if (definition.getUrl() != null) {
            return null;
        }
        try {
            return new String(Files.readAllBytes(Paths.get(definition.getFile())), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read content of blocker " + definition.getId(), e);
            return null;
        }
    }

    private <U, V> V firstValue(Map<U, V> map) {
        if (map == null) {
            return null;
        }

        Iterator<V> it = map.values().iterator();
        return it.hasNext() ? it.next() : null;
    }

    private Map<String, String> localizedMap(String value) {
        Map<String, String> languageMap = new HashMap<>();
        languageMap.put("en", value);
        languageMap.put("de", value);
        return languageMap;
    }

    private Blocker enableMalwareUrlFilter(boolean enabled) {
        malwareFilterService.setEnabled(enabled);
        return getMalwareUrlFilter();
    }

    private BlockerType mapType(Type type) {
        switch (type) {
            case DOMAIN:
                return BlockerType.DOMAIN;
            case MALWARE_URL:
            case PATTERN:
                return BlockerType.PATTERN;
            default:
                throw new IllegalArgumentException("can not map blocker " + type + " to type");
        }
    }

    private Type mapBlockerType(BlockerType type) {
        switch (type) {
            case DOMAIN:
                return Type.DOMAIN;
            case PATTERN:
                return Type.PATTERN;
            default:
                throw new IllegalArgumentException("can not map " + type + " to blocker type");
        }
    }

}
