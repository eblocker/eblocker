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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.malware.MalwareFilterService;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.icap.filter.content.ContentFilterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Class for managing user-defined blockers. It also enables/disables built-in blockers.
 *
 * User-defined blockers are stored as {@link ExternalDefinition} objects.
 */
@Singleton
@SubSystemService(SubSystem.SERVICES)
public class BlockerService {

    private static final Logger log = LoggerFactory.getLogger(BlockerService.class);

    private final Path localStoragePath;
    private final BlockerIdTypeIdCache idCache;
    private final DataSource dataSource;
    private final DomainBlockerService domainBlockerService;
    private final PatternBlockerService patternBlockerService;
    private final ScheduledExecutorService executorService;
    private final UpdateTaskFactory updateTaskFactory;

    @Inject
    public BlockerService(@Named("blocker.localStorage.path") String localStoragePath,
                          BlockerIdTypeIdCache idCache,
                          DataSource dataSource,
                          DomainBlockerService domainBlockerService,
                          PatternBlockerService patternBlockerService,
                          @Named("lowPrioScheduledExecutor") ScheduledExecutorService executorService,
                          UpdateTaskFactory updateTaskFactory) {
        this.localStoragePath = Paths.get(localStoragePath);
        this.idCache = idCache;
        this.dataSource = dataSource;
        this.domainBlockerService = domainBlockerService;
        this.patternBlockerService = patternBlockerService;
        this.executorService = executorService;
        this.updateTaskFactory = updateTaskFactory;
    }

    @SubSystemInit
    public void init() {
        List<ExternalDefinition> definitions = dataSource.getAll(ExternalDefinition.class);
        for (ExternalDefinition definition : definitions) {
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
        blockers.addAll(domainBlockerService.getDomainFilters(definitionByTypeId));
        blockers.addAll(patternBlockerService.getPatternFilters(definitionByTypeId));
        blockers.add(patternBlockerService.getMalwareUrlFilter());
        blockers.add(patternBlockerService.getContentFilter());
        return blockers;
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
                BlockerUtils.firstValue(blocker.getName()),
                BlockerUtils.firstValue(blocker.getDescription()),
                blocker.getCategory(),
                BlockerUtils.mapBlockerType(blocker.getType()),
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
        return BlockerUtils.mapDefinition(definition, null, true);
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
                    return domainBlockerService.enableDomainFilter(typeId.id, null, blocker.isEnabled());
                case PATTERN:
                    return patternBlockerService.enablePatternFilter(typeId.id, null, blocker.isEnabled());
                case MALWARE_URL:
                    return patternBlockerService.enableMalwareUrlFilter(blocker.isEnabled());
                case CONTENT:
                    return patternBlockerService.enableContentFilter(blocker.isEnabled());
                default:
                    throw new IllegalArgumentException("unknown type " + typeId.type);
            }
        }

        if (BlockerUtils.mapBlockerType(blocker.getType()) != definition.getType()) {
            throw new UnsupportedOperationException("can not change blocker type");
        }

        if (blocker.getCategory() != definition.getCategory()) {
            throw new UnsupportedOperationException("can not change blocker category");
        }

        boolean needsUpdate = blocker.getFormat() != definition.getFormat() || blocker.getContent() != null || !blocker.getUrl().equals(definition.getUrl());
        definition.setName(BlockerUtils.firstValue(blocker.getName()));
        definition.setDescription(BlockerUtils.firstValue(blocker.getDescription()));
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
            return BlockerUtils.mapDefinition(definition, null, definition.isEnabled());
        }

        if (definition.getType() == Type.DOMAIN) {
            return domainBlockerService.enableDomainFilter(definition.getReferenceId(), definition, definition.isEnabled());
        }

        return patternBlockerService.enablePatternFilter(definition.getReferenceId(), definition, definition.isEnabled());
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
                domainBlockerService.deleteFilter(definition.getReferenceId());
            } else {
                patternBlockerService.deleteFilter(definition.getReferenceId());
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
        // tasks are not scheduled individually to avoid blocking the scheduler for other tasks
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

    private List<Blocker> getPendingFilters(List<ExternalDefinition> externalDefinitions) {
        return externalDefinitions.stream()
                .filter(definition -> definition.getReferenceId() == null)
                .map(definition -> BlockerUtils.mapDefinition(definition, null, true))
                .collect(Collectors.toList());
    }


}
