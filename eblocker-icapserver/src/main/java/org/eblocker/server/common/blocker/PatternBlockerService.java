/*
 * Copyright 2026 eBlocker Open Source GmbH
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
import org.eblocker.server.common.malware.MalwareFilterService;
import org.eblocker.server.icap.filter.FilterDefinitionFormat;
import org.eblocker.server.icap.filter.FilterLearningMode;
import org.eblocker.server.icap.filter.FilterManager;
import org.eblocker.server.icap.filter.FilterStoreConfiguration;
import org.eblocker.server.icap.filter.content.ContentFilterManager;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Provides a Blocker based API which is mapped to pattern, malware and content filters.
 */
public class PatternBlockerService {
    private final BlockerIdTypeIdCache idCache;
    private final FilterManager filterManager;
    private final MalwareFilterService malwareFilterService;
    private final ContentFilterManager contentFilterManager;

    @Inject
    public PatternBlockerService(BlockerIdTypeIdCache idCache,
                                 FilterManager filterManager,
                                 MalwareFilterService malwareFilterService,
                                 ContentFilterManager contentFilterManager) {
        this.idCache = idCache;
        this.filterManager = filterManager;
        this.malwareFilterService = malwareFilterService;
        this.contentFilterManager = contentFilterManager;
    }


    List<Blocker> getPatternFilters(Map<TypeId, ExternalDefinition> definitionsByTypeReference) {
        return filterManager.getFilterConfigurations()
                .stream()
                .map(c -> mapFilterStoreConfiguration(c, definitionsByTypeReference.get(new TypeId(Type.PATTERN, c.getId()))))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    Blocker enablePatternFilter(int id, ExternalDefinition definition, boolean enabled) {
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

    int newPatternBlocker(Category category, String name, Format format, Path path) {
        FilterDefinitionFormat definitionFormat = selectFilterDefinitionFormat(format);
        FilterLearningMode learningMode = category == Category.MALWARE ? FilterLearningMode.SYNCHRONOUS : FilterLearningMode.ASYNCHRONOUS;
        FilterStoreConfiguration configuration = new FilterStoreConfiguration(
                null,
                name,
                BlockerUtils.mapToPatternFilterCategory(category),
                false,
                System.currentTimeMillis(),
                new String[]{ path.toString() },
                learningMode,
                definitionFormat,
                true,
                new String[0],
                true);
        FilterStoreConfiguration savedConfiguration = filterManager.addFilter(configuration);
        return savedConfiguration.getId();
    }


    private Blocker mapFilterStoreConfiguration(FilterStoreConfiguration configuration, ExternalDefinition definition) {

        Long lastUpdate = filterManager.getFilterStore(configuration.getId()) != null &&
                filterManager.getFilterStore(configuration.getId()).getLastUpdate() != null ?
                filterManager.getFilterStore(configuration.getId()).getLastUpdate().getTime() : new Date().getTime();

        if (definition == null) {
            Category category = BlockerUtils.mapPatternFilterCategory(configuration.getCategory());
            if (category == null) {
                return null;
            }
            return new Blocker(idCache.getId(new TypeId(Type.PATTERN, configuration.getId())),
                    BlockerUtils.localizedMap(configuration.getName()),
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

        return BlockerUtils.mapDefinition(definition, lastUpdate, configuration.isEnabled());
    }


    private FilterDefinitionFormat selectFilterDefinitionFormat(Format format) {
        switch (format) {
            case EASYLIST:
                return FilterDefinitionFormat.EASYLIST;
            case URLS:
                return FilterDefinitionFormat.URL;
            default:
                throw new IllegalArgumentException("format not available for pattern filters: " + format);
        }
    }

    void updatePatternBlocker() {
        filterManager.update();
    }

    void deleteFilter(Integer filterId) {
        filterManager.removeFilter(filterId);
    }
    Blocker getMalwareUrlFilter() {
        return new Blocker(
                idCache.getId(new TypeId(Type.MALWARE_URL, 0)),
                BlockerUtils.localizedMap("Malware"),
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

    Blocker getContentFilter() {
        return new Blocker(
                idCache.getId(new TypeId(Type.CONTENT, 0)),
                BlockerUtils.localizedMap("uBlock Filters"),
                Collections.emptyMap(),
                BlockerType.PATTERN,
                Category.CONTENT,
                contentFilterManager.getLastUpdate(),
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                contentFilterManager.isEnabled(),
                "content"
        );
    }

    Blocker enableMalwareUrlFilter(boolean enabled) {
        malwareFilterService.setEnabled(enabled);
        return getMalwareUrlFilter();
    }

    Blocker enableContentFilter(boolean enabled) {
        contentFilterManager.setEnabled(enabled);
        return getContentFilter();
    }

}
