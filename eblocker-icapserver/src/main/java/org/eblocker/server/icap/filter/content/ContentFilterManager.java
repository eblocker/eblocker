/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.icap.filter.content;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

/**
 * Responsible for loading the content filter from disk and
 * enabling/disabling it.
 */
@Singleton
public class ContentFilterManager {
    private static final Logger log = LoggerFactory.getLogger(ContentFilterManager.class);

    private final ContentFilterService contentFilterService;
    private final DataSource dataSource;
    private final Path contentFilterFile;

    private long lastUpdate;

    @Inject
    public ContentFilterManager(@Named("contentFilter.file.path") String contentFilterFile,
                                ContentFilterService contentFilterService,
                                DataSource dataSource) {
        this.dataSource = dataSource;
        this.contentFilterFile = Path.of(contentFilterFile);
        this.contentFilterService = contentFilterService;
        updateFilters();
    }

    private void updateFilters() {
        try {
            ContentFilterList filterList = isEnabled() ? loadFilters() : ContentFilterList.emptyList();
            log.info("Updating content filter with {} filters.", filterList.size());
            contentFilterService.setFilterList(filterList);
        } catch (IOException e) {
            log.error("Could not update content filters from {}.", contentFilterFile, e);
        }
    }

    private ContentFilterList loadFilters() throws IOException {
        ContentFilterParser parser = new ContentFilterParser();
        FileTime lastModified = Files.getLastModifiedTime(contentFilterFile);
        BufferedReader reader = Files.newBufferedReader(contentFilterFile);
        List<ContentFilter> filters = parser.parse(reader.lines());
        lastUpdate = lastModified.toMillis();
        return new ContentFilterList(filters);
    }

    public void updateIfModified() {
        try {
            if (Files.getLastModifiedTime(contentFilterFile).toMillis() > lastUpdate) {
                updateFilters();
            }
        } catch (IOException e) {
            log.error("Could not check whether filters at '{}' need to be updated", contentFilterFile, e);
        }
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public boolean isEnabled() {
        return dataSource.isContentFilterEnabled();
    }

    public void setEnabled(boolean enabled) {
        dataSource.setContentFilterEnabled(enabled);
        updateFilters();
    }
}
