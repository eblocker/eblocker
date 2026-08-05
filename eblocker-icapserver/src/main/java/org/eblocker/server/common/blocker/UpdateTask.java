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

import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
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

/**
 * An UpdateTask can create or update a domain or pattern filter
 * for a user-defined blocker (read from an ExternalDefinition).
 */
public class UpdateTask implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(UpdateTask.class);

    private final DataSource dataSource;
    private final DomainBlockerService domainBlockerService;
    private final PatternBlockerService patternBlockerService;
    private final HttpClient httpClient;
    private final int id;

    @Inject
    public UpdateTask(DataSource dataSource,
                      DomainBlockerService domainBlockerService,
                      PatternBlockerService patternBlockerService,
                      HttpClient httpClient,
                      @Assisted int id) {
        this.dataSource = dataSource;
        this.domainBlockerService = domainBlockerService;
        this.patternBlockerService = patternBlockerService;
        this.httpClient = httpClient;
        this.id = id;
    }

    /**
     * UpdateTasks are usually scheduled to run in the background.
     * The processing of large filter lists (e.g. lists of millions of domains)
     * can take a few minutes.
     */
    @Override
    public void run() {
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
                    referenceId = domainBlockerService.newDomainBlocker(definition.getCategory(), definition.getName(), definition.getDescription(), definition.getFormat(), definition.getFilterType(), path);
                } else {
                    referenceId = patternBlockerService.newPatternBlocker(definition.getCategory(), definition.getName(), definition.getFormat(), path);
                }
                definition.setReferenceId(referenceId);
            } else {
                if (definition.getType() == Type.DOMAIN) {
                    domainBlockerService.updateDomainBlocker(definition.getReferenceId(), definition.getFormat(), path);
                } else {
                    patternBlockerService.updatePatternBlocker();
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

}
