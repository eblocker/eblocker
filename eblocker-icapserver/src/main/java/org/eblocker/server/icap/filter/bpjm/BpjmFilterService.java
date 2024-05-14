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
package org.eblocker.server.icap.filter.bpjm;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.http.service.FileSystemWatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Singleton
@SubSystemService(SubSystem.SERVICES)
public class BpjmFilterService {
    private static final Logger log = LoggerFactory.getLogger(BpjmFilterService.class);

    private final String filePath;
    private final BpjmModulSerializer serializer;
    private final FileSystemWatchService fileSystemWatchService;

    private BpjmFilter filter;

    @Inject
    public BpjmFilterService(@Named("parentalcontrol.bpjm.filter.file") String filePath,
                             BpjmModulSerializer serializer,
                             FileSystemWatchService fileSystemWatchService) {
        this.filePath = filePath;
        this.fileSystemWatchService = fileSystemWatchService;
        this.serializer = serializer;
    }

    @SubSystemInit
    public void init() {
        update(Paths.get(filePath));
        try {
            fileSystemWatchService.watch(Paths.get(filePath), this::update);
        } catch (IOException e) {
            log.error("failed to register watch service for {}, bpjm filter will be unavailable.", e);
        }
    }

    public BpjmFilterDecision isBlocked(String url) {
        if (filter == null) {
            return BpjmFilter.NOT_BLOCKED_DECISION;
        }
        return filter.isBlocked(url);
    }

    private void update(Path path) {
        if (!Files.exists(path)) {
            log.warn("Optional BPjM list does not exist");
            return;
        }
        log.info("Loading bpjm filter");
        try (InputStream in = Files.newInputStream(path)) {
            filter = new BpjmFilter(serializer.read(in));
        } catch (IOException e) {
            log.warn("failed to read bpjm filter", e);
        }
    }
}
