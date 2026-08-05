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
import com.google.inject.Singleton;
import org.eblocker.server.common.blocker.parser.DomainParser;
import org.eblocker.server.common.blocker.parser.EtcHostsParser;
import org.eblocker.server.common.blocker.parser.SquidAclParser;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterSummaryData;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.http.service.ParentalControlFilterListsService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides a Blocker based API which is mapped to domain filters.
 */
@Singleton
@SubSystemService(SubSystem.SERVICES)
public class DomainBlockerService {
    private final BlockerIdTypeIdCache idCache;
    private final ParentalControlFilterListsService filterListsService;
    private final Clock clock;

    @Inject
    public DomainBlockerService(Clock clock,
                                BlockerIdTypeIdCache idCache,
                                ParentalControlFilterListsService filterListsService) {
        this.clock = clock;
        this.idCache = idCache;
        this.filterListsService = filterListsService;
    }

    void deleteFilter(Integer filterId) {
        filterListsService.deleteFilterList(filterId);
    }

    private Blocker getDomainBlockerById(int id, ExternalDefinition externalDefinition) {
        ParentalControlFilterMetaData metadata = filterListsService.getParentalControlFilterMetaData(id);
        if (metadata == null) {
            return null;
        }
        return mapParentControlFilterMetadata(metadata, externalDefinition);
    }

    Blocker enableDomainFilter(int id, ExternalDefinition definition, boolean enabled) {
        ParentalControlFilterMetaData metadata = filterListsService.getParentalControlFilterMetaData(id);
        if (metadata.isDisabled() == enabled) {
            metadata.setDisabled(!enabled);
            filterListsService.updateFilterList(new ParentalControlFilterSummaryData(metadata), metadata.getFilterType());
        }
        return getDomainBlockerById(id, definition);
    }

    List<Blocker> getDomainFilters(Map<TypeId, ExternalDefinition> definitionsByTypeReference) {
        return filterListsService.getParentalControlFilterMetaData()
                .stream()
                .map(m -> mapParentControlFilterMetadata(m, definitionsByTypeReference.get(new TypeId(Type.DOMAIN, m.getId()))))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    int newDomainBlocker(Category category, String name, String description, Format format, String filterType, Path path) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        ParentalControlFilterSummaryData data = new ParentalControlFilterSummaryData(
                null,
                null,
                null,
                now.format(DateTimeFormatter.BASIC_ISO_DATE),
                Date.from(now.toInstant()),
                filterType,
                false,
                false,
                null,
                name,
                description,
                BlockerUtils.mapToDomainFilterCategory(category));
        data.setDomainsStreamSupplier(new DomainStreamSupplier(path, format));
        ParentalControlFilterSummaryData savedData = filterListsService.createFilterList(data, "blacklist");
        return savedData.getId();
    }

    void updateDomainBlocker(int id, Format format, Path path) {
        ZonedDateTime now = ZonedDateTime.now(clock);

        ParentalControlFilterMetaData metadata = filterListsService.getParentalControlFilterMetaData(id);
        ParentalControlFilterSummaryData updatedData = new ParentalControlFilterSummaryData(
                metadata.getId(),
                metadata.getName(),
                metadata.getDescription(),
                now.format(DateTimeFormatter.BASIC_ISO_DATE),
                Date.from(now.toInstant()),
                metadata.getFilterType(),
                metadata.isBuiltin(),
                metadata.isDisabled(),
                null,
                metadata.getCustomerCreatedName(),
                metadata.getCustomerCreatedDescription(),
                metadata.getCategory());
        updatedData.setDomainsStreamSupplier(new DomainStreamSupplier(path, format));
        filterListsService.updateFilterList(updatedData, metadata.getFilterType());
    }

    private Blocker mapParentControlFilterMetadata(ParentalControlFilterMetaData metadata, ExternalDefinition definition) {
        if (definition == null) {
            Category category = BlockerUtils.mapDomainFilterCategory(metadata.getCategory());
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

        return BlockerUtils.mapDefinition(definition, metadata.getDate().getTime(), !metadata.isDisabled());
    }

    private class DomainStreamSupplier implements Supplier<Stream<String>> {
        private final Path path;
        private final Format format;

        DomainStreamSupplier(Path path, Format format) {
            this.path = path;
            this.format = format;
        }

        @Override
        public Stream<String> get() {
            try {
                DomainParser parser = createParser(format);
                return parser.parse(Files.lines(path, StandardCharsets.ISO_8859_1));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private DomainParser createParser(Format format) {
            switch (format) {
                case ETC_HOSTS:
                    return new EtcHostsParser();
                case DOMAINS:
                case SQUID_ACL:
                    return new SquidAclParser();
                default:
                    throw new IllegalArgumentException("unsupported format: " + format);
            }
        }
    }

}
