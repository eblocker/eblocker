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

import org.eblocker.server.common.blocker.parser.DomainParser;
import org.eblocker.server.common.blocker.parser.EtcHostsParser;
import org.eblocker.server.common.blocker.parser.SquidAclParser;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterSummaryData;
import org.eblocker.server.common.util.HttpClient;
import org.eblocker.server.http.service.ParentalControlFilterListsService;
import org.eblocker.server.icap.filter.FilterDefinitionFormat;
import org.eblocker.server.icap.filter.FilterLearningMode;
import org.eblocker.server.icap.filter.FilterManager;
import org.eblocker.server.icap.filter.FilterStoreConfiguration;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class UpdateTask implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(UpdateTask.class);

    private final Clock clock;
    private final DataSource dataSource;
    private final FilterManager filterManager;
    private final ParentalControlFilterListsService filterListsService;
    private final HttpClient httpClient;
    private final int id;

    @Inject
    public UpdateTask(Clock clock,
                      DataSource dataSource,
                      FilterManager filterManager,
                      ParentalControlFilterListsService filterListsService,
                      HttpClient httpClient,
                      @Assisted int id) {
        this.clock = clock;
        this.dataSource = dataSource;
        this.filterManager = filterManager;
        this.filterListsService = filterListsService;
        this.httpClient = httpClient;
        this.id = id;
    }

    @Override
    public synchronized void run() {
        log.info("updating {}", id);
        ExternalDefinition definition = dataSource.get(ExternalDefinition.class, id);
        if (definition == null) {
            log.warn("definition {} not found", id);
            return;
        }

        boolean newBlocker = definition.getReferenceId() == null;
        UpdateStatus updateStatus = newBlocker ? UpdateStatus.INITIAL_UPDATE : UpdateStatus.UPDATE;
        definition.setUpdateStatus(updateStatus);
        dataSource.save(definition, definition.getId());

        Path path = Paths.get(definition.getFile());
        try {
            if (definition.getUrl() != null) {
                download(definition.getUrl(), path);
            }
            if (newBlocker) {
                int referenceId;
                if (definition.getType() == Type.DOMAIN) {
                    referenceId = newDomainBlocker(definition.getCategory(), definition.getName(), definition.getDescription(), definition.getFormat(), definition.getFilterType(), path);
                } else {
                    referenceId = newPatternBlocker(definition.getCategory(), definition.getName(), definition.getFormat(), path);
                }
                definition.setReferenceId(referenceId);
            } else {
                if (definition.getType() == Type.DOMAIN) {
                    updateDomainBlocker(definition.getReferenceId(), definition.getFormat(), path);
                } else {
                    updatePatternBlocker();
                }
            }
            definition.setUpdateStatus(UpdateStatus.READY);
            definition.setUpdateError(null);
            dataSource.save(definition, definition.getId());
        } catch (IOException e) {
            log.error("failed to update {}", id, e);
            definition.setUpdateStatus(newBlocker ? UpdateStatus.INITIAL_UPDATE_FAILED : UpdateStatus.UPDATE_FAILED);
            definition.setUpdateError(e.getMessage());
            dataSource.save(definition, definition.getId());
        }
    }

    private void download(String url, Path path) throws IOException {
        Path tempPath = Files.createTempFile(BlockerService.class.getSimpleName(), ".tmp");
        try (InputStream in = httpClient.download(url)) {
            try (OutputStream out = Files.newOutputStream(tempPath)) {
                ByteStreams.copy(in, out);
            }
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    private int newDomainBlocker(Category category, String name, String description, Format format, String filterType, Path path) {
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
            mapToDomainFilterCategory(category));
        data.setDomainsStreamSupplier(new DomainStreamSupplier(path, format));
        ParentalControlFilterSummaryData savedData = filterListsService.createFilterList(data, "blacklist");
        return savedData.getId();
    }

    private org.eblocker.server.common.data.parentalcontrol.Category mapToDomainFilterCategory(Category category) {
        switch (category) {
            case ADS:
                return org.eblocker.server.common.data.parentalcontrol.Category.ADS;
            case CUSTOM:
                return org.eblocker.server.common.data.parentalcontrol.Category.CUSTOM;
            case MALWARE:
                return org.eblocker.server.common.data.parentalcontrol.Category.MALWARE;
            case PARENTAL_CONTROL:
                return org.eblocker.server.common.data.parentalcontrol.Category.PARENTAL_CONTROL;
            case TRACKER:
                return org.eblocker.server.common.data.parentalcontrol.Category.TRACKERS;
            default:
                throw new IllegalArgumentException("no domain filter category available for " + category);
        }
    }

    private void updateDomainBlocker(int id, Format format, Path path) {
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

    private int newPatternBlocker(Category category, String name, Format format, Path path) {
        FilterDefinitionFormat definitionFormat = selectFilterDefinitionFormat(format);
        FilterLearningMode learningMode = category == Category.MALWARE ? FilterLearningMode.SYNCHRONOUS : FilterLearningMode.ASYNCHRONOUS;
        FilterStoreConfiguration configuration = new FilterStoreConfiguration(
            null,
            name,
            mapToPatternFilterCategory(category),
            false,
            System.currentTimeMillis(),
            new String[] { path.toString() },
            learningMode,
            definitionFormat,
            true,
            new String[0],
            true);
        FilterStoreConfiguration savedConfiguration = filterManager.addFilter(configuration);
        return savedConfiguration.getId();
    }

    private org.eblocker.server.icap.filter.Category mapToPatternFilterCategory(Category category) {
        switch (category) {
            case ADS:
                return org.eblocker.server.icap.filter.Category.ADS;
            case TRACKER:
                return org.eblocker.server.icap.filter.Category.TRACKER_BLOCKER;
            case MALWARE:
                return org.eblocker.server.icap.filter.Category.MALWARE;
            default:
                throw new IllegalArgumentException("pattern filters not available for " + category);
        }
    }

    private void updatePatternBlocker() {
        filterManager.update();
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
