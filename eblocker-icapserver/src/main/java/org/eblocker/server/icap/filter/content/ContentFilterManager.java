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

import com.google.common.net.InternetDomainName;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.blocker.Category;
import org.eblocker.server.common.blocker.ExternalDefinition;
import org.eblocker.server.common.blocker.Type;
import org.eblocker.server.common.blocker.UpdateStatus;
import org.eblocker.server.common.data.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Loads bundled, additional, and custom content filters. */
@Singleton
public class ContentFilterManager {
    private static final Logger log = LoggerFactory.getLogger(ContentFilterManager.class);
    private static final int MAX_CUSTOM_RULES = 500;
    private static final int MAX_RULE_LENGTH = 512;

    private final ContentFilterService contentFilterService;
    private final DataSource dataSource;
    private final Path contentFilterFile;
    private final Path customFilterFile;
    private long lastUpdate;
    private long lastCustomUpdate;

    @Inject
    public ContentFilterManager(@Named("contentFilter.file.path") String contentFilterFile,
                                @Named("blocker.localStorage.path") String customFilterDirectory,
                                ContentFilterService contentFilterService,
                                DataSource dataSource) {
        this.dataSource = dataSource;
        this.contentFilterFile = Path.of(contentFilterFile);
        this.customFilterFile = Path.of(customFilterDirectory).resolve("element-hiding-rules.txt");
        this.contentFilterService = contentFilterService;
        updateFilters();
    }

    private void updateFilters() {
        try {
            ContentFilterList filterList = loadFilters();
            log.info("Updating content filter with {} filters.", filterList.size());
            contentFilterService.setFilterList(filterList);
        } catch (IOException | IllegalArgumentException e) {
            log.error("Could not update content filters.", e);
        }
    }

    private ContentFilterList loadFilters() throws IOException {
        ContentFilterParser parser = new ContentFilterParser();
        List<ContentFilter> filters = new ArrayList<>();
        if (isEnabled()) {
            try (BufferedReader reader = Files.newBufferedReader(contentFilterFile)) {
                filters.addAll(parser.parse(reader.lines()));
            }
            lastUpdate = Files.getLastModifiedTime(contentFilterFile).toMillis();
        }
        if (Files.exists(customFilterFile)) {
            if (isCustomElementHidingEnabled()) {
                List<String> rules = normalizeRules(Files.readAllLines(customFilterFile, StandardCharsets.UTF_8));
                filters.addAll(parser.parse(rules.stream()));
            }
            lastCustomUpdate = Files.getLastModifiedTime(customFilterFile).toMillis();
        } else {
            lastCustomUpdate = 0;
        }
        List<ExternalDefinition> definitions = dataSource.getAll(ExternalDefinition.class);
        if (definitions != null) {
            for (ExternalDefinition definition : definitions) {
                if (definition.getType() != Type.CONTENT || definition.getCategory() != Category.CONTENT ||
                        !definition.isEnabled() || definition.getReferenceId() == null ||
                        (definition.getUpdateStatus() != UpdateStatus.READY &&
                         definition.getUpdateStatus() != UpdateStatus.UPDATE_FAILED)) {
                    continue;
                }
                Path path = Path.of(definition.getFile());
                if (Files.isRegularFile(path)) {
                    try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
                        filters.addAll(new ContentFilterParser().parse(lines));
                    }
                } else {
                    log.warn("Content list source is missing: {}", definition.getId());
                }
            }
        }
        return new ContentFilterList(filters);
    }

    public synchronized void refresh() {
        updateFilters();
    }

    public synchronized List<String> getCustomRules() throws IOException {
        if (!Files.exists(customFilterFile)) return List.of();
        return normalizeRules(Files.readAllLines(customFilterFile, StandardCharsets.UTF_8));
    }

    public List<String> validateCustomRules(List<String> rules) {
        return normalizeRules(rules);
    }

    public synchronized List<String> setCustomRules(List<String> rules) throws IOException {
        List<String> normalized = normalizeRules(rules);
        Files.createDirectories(customFilterFile.getParent());
        Path temporary = Files.createTempFile(customFilterFile.getParent(), "element-hiding-", ".tmp");
        try {
            Files.write(temporary, normalized, StandardCharsets.UTF_8);
            Files.move(temporary, customFilterFile, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        updateFilters();
        return normalized;
    }

    public synchronized boolean appendCustomRule(String rule) throws IOException {
        List<String> candidate = normalizeRules(List.of(rule));
        List<String> current = getCustomRules();
        if (current.contains(candidate.get(0))) return false;
        List<String> updated = new ArrayList<>(current);
        updated.add(candidate.get(0));
        setCustomRules(updated);
        return true;
    }

    private List<String> normalizeRules(List<String> rules) {
        if (rules == null || rules.size() > MAX_CUSTOM_RULES) {
            throw new IllegalArgumentException("Too many element hiding rules");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String rule : rules) {
            if (rule == null || rule.length() > MAX_RULE_LENGTH) {
                throw new IllegalArgumentException("Invalid element hiding rule length");
            }
            String value = rule.trim();
            int separator = value.indexOf("##");
            if (separator < 1 || value.substring(separator + 2).contains("##")) {
                throw new IllegalArgumentException("Expected domain##selector");
            }
            String domain = value.substring(0, separator);
            String selector = value.substring(separator + 2);
            if (!InternetDomainName.isValid(domain) || domain.contains("..") || selector.isBlank()) {
                throw new IllegalArgumentException("Invalid domain or CSS selector");
            }
            for (String part : selector.split(",", -1)) {
                String trimmed = part.trim();
                boolean simpleSelector = trimmed.matches("[A-Za-z0-9._# >:()\\-]+");
                boolean idPrefixSelector = trimmed.matches("\\[id\\^=\"[A-Za-z0-9_-]{8,}\"\\]");
                if (!simpleSelector && !idPrefixSelector) {
                    throw new IllegalArgumentException("Invalid CSS selector");
                }
            }
            normalized.add(domain.toLowerCase() + "##" + selector);
        }
        return List.copyOf(normalized);
    }

    public synchronized void updateIfModified() {
        try {
            long bundledModified = isEnabled() ? Files.getLastModifiedTime(contentFilterFile).toMillis() : lastUpdate;
            long customModified = Files.exists(customFilterFile)
                    ? Files.getLastModifiedTime(customFilterFile).toMillis() : 0;
            if (bundledModified != lastUpdate || customModified != lastCustomUpdate) {
                updateFilters();
            }
        } catch (IOException e) {
            log.error("Could not check whether content filters need to be updated", e);
        }
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public boolean isEnabled() {
        return dataSource.isContentFilterEnabled();
    }

    public boolean isCustomElementHidingEnabled() {
        return dataSource.isCustomElementHidingEnabled();
    }

    public synchronized void setCustomElementHidingEnabled(boolean enabled) {
        dataSource.setCustomElementHidingEnabled(enabled);
        updateFilters();
    }

    public synchronized void setEnabled(boolean enabled) {
        dataSource.setContentFilterEnabled(enabled);
        updateFilters();
    }
}
