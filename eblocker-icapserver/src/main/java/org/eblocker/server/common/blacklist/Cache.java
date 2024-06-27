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
package org.eblocker.server.common.blacklist;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Cache {
    private static final Logger log = LoggerFactory.getLogger(Cache.class);

    private static final int CACHE_FORMAT = 5;
    static final String LIST_DIR = "lists";
    static final String PROFILES_DIR = "profiles";
    static final String FILTER_FILE_EXTENSION = ".filter";
    static final String BLOOM_FILE_EXTENSION = ".bloom";
    static final String INDEX_JSON = "/index.json";

    @Nonnull
    private final String cachePath;
    @Nonnull
    private final String cacheIndexFile;
    @Nonnull
    private final ObjectMapper objectMapper;

    private CacheIndex index;

    Cache(@Nonnull String cachePath, @Nonnull ObjectMapper objectMapper) throws IOException {
        this.cachePath = cachePath;
        this.objectMapper = objectMapper;

        this.cacheIndexFile = cachePath + INDEX_JSON;

        initCache();
    }

    /**
     * Get latest versions of all file filters
     */
    @Nonnull
    List<CachedFileFilter> getFileFilters() {
        return index.getFilters().values().stream().map(f -> f.get(0)).collect(Collectors.toList());
    }

    /**
     * Get all versions of all file filters
     */
    @Nonnull
    List<CachedFileFilter> getAllFileFilters() {
        return index.getFilters().values().stream().flatMap(Collection::stream).collect(Collectors.toList());
    }

    /**
     * Get latest version of a cached file filter
     */
    @Nullable
    CachedFileFilter getFileFilterById(int id) {
        List<CachedFileFilter> filters = getFileFiltersById(id);
        return filters == null || filters.isEmpty() ? null : filters.get(0);
    }

    /**
     * Get all versions of a cached file filter
     */
    @Nullable
    List<CachedFileFilter> getFileFiltersById(int id) {
        return index.getFilters().get(id);
    }

    CachedFileFilter storeFileFilter(int id, long version, String format, @Nullable String filterFile, @Nullable String bloomFile) throws IOException {
        CachedFilterKey key = new CachedFilterKey(id, version);
        CachedFileFilter importedFilter = new CachedFileFilter();
        importedFilter.setKey(key);
        importedFilter.setFormat(format);

        if (filterFile != null) {
            String cacheFilterFile = LIST_DIR + "/" + key + FILTER_FILE_EXTENSION;
            Files.copy(Paths.get(filterFile), Paths.get(cachePath + "/" + cacheFilterFile),
                    StandardCopyOption.REPLACE_EXISTING);
            importedFilter.setFileFilterFileName(cacheFilterFile);
        }

        if (bloomFile != null) {
            String cacheBloomFile = LIST_DIR + "/" + key + BLOOM_FILE_EXTENSION;
            Files.copy(Paths.get(bloomFile), Paths.get(cachePath + "/" + cacheBloomFile), StandardCopyOption.REPLACE_EXISTING);
            importedFilter.setBloomFilterFileName(cacheBloomFile);
        }

        List<CachedFileFilter> filters = index.getFilters().computeIfAbsent(id, k -> new ArrayList<>());
        int i = 0;
        while (i < filters.size() && version < filters.get(i).getKey().getVersion()) {
            ++i;
        }
        filters.add(i, importedFilter);
        writeIndex();

        return importedFilter;
    }

    void removeFileFilter(int id, long version) throws IOException {
        CachedFilterKey key = new CachedFilterKey(id, version);
        String cacheFilterFile = LIST_DIR + "/" + key + FILTER_FILE_EXTENSION;
        String cacheBloomFile = LIST_DIR + "/" + key + BLOOM_FILE_EXTENSION;

        Files.deleteIfExists(Paths.get(cachePath + "/" + cacheFilterFile));
        Files.deleteIfExists(Paths.get(cachePath + "/" + cacheBloomFile));

        List<CachedFileFilter> remainingFilters = index.getFilters().get(id).stream().filter(f -> !f.getKey().equals(key)).collect(Collectors.toList());
        if (remainingFilters.isEmpty()) {
            index.getFilters().remove(id);
        } else {
            index.getFilters().put(id, remainingFilters);
        }
        writeIndex();
    }

    void markFilterAsDeleted(int id, long version) throws IOException {
        CachedFilterKey key = new CachedFilterKey(id, version);
        CachedFileFilter filter = getAllFileFilters().stream().filter(e -> e.getKey().equals(key)).findFirst().orElse(null);
        if (filter != null) {
            filter.setDeleted(true);
            writeIndex();
        }
    }

    private void initCache() throws IOException {
        if (checkIndexFile()) {
            readIndex();
        } else {
            log.info("creating new cache");
            deleteCachedFilters();
            index = new CacheIndex();
            index.setFormat(CACHE_FORMAT);
            index.setFilters(new HashMap<>());
            writeIndex();
        }
    }

    private void readIndex() throws IOException {
        this.index = objectMapper.readValue(new File(cacheIndexFile), CacheIndex.class);
    }

    private void writeIndex() throws IOException {
        objectMapper.writeValue(new File(cacheIndexFile), index);
    }

    private void deleteCachedFilters() throws IOException {
        deleteFilesInDirectory(LIST_DIR);
        deleteFilesInDirectory(PROFILES_DIR);
    }

    private void deleteFilesInDirectory(@Nonnull String directory) throws IOException {
        try (Stream<Path> fileStream = Files.list(Paths.get(cachePath + "/" + directory))) {
            List<Path> files = fileStream.collect(Collectors.toList());
            for (Path file : files) {
                Files.delete(file);
            }
        }
    }

    private boolean checkIndexFile() throws IOException {
        // check file existence
        if (!Files.exists(Paths.get(cacheIndexFile))) {
            return false;
        }

        // check file format
        JsonNode node = objectMapper.readTree(new File(cacheIndexFile));
        if (node.isMissingNode()) {
            log.error("Could not parse cache index file {}. Root node is missing. Treating as non-existing cache.", cacheIndexFile);
            return false;
        }
        JsonNode cacheFormatField = node.get("format");
        if (cacheFormatField == null || cacheFormatField.intValue() < CACHE_FORMAT) {
            upgradeIndex(node);
        }

        return true;
    }

    private void upgradeIndex(@Nonnull JsonNode node) throws IOException {
        index = new CacheIndex();
        index.setFormat(CACHE_FORMAT);

        // update filter meta data
        ObjectReader filtersReader = objectMapper.readerFor(new TypeReference<Map<Integer, List<CachedFileFilter>>>() {
        });

        Map<Integer, List<CachedFileFilter>> cachedFilters = filtersReader.readValue(node.get("filters"));
        Map<Integer, List<CachedFileFilter>> updatedFilters = cachedFilters.values().stream()
                .flatMap(Collection::stream)
                .map(filter -> {
                    String format = filter.getFileFilterFileName() != null ? "domainblacklist/string" : "domainblacklist/bloom";
                    return new CachedFileFilter(filter.getKey(), filter.getBloomFilterFileName(), filter.getFileFilterFileName(), format, filter.isDeleted());
                })
                .sorted(Comparator.comparing(filter -> filter.getKey().getVersion(), Comparator.reverseOrder()))
                .collect(Collectors.groupingBy(filter -> filter.getKey().getId()));

        // write upgraded index
        index.setFilters(updatedFilters);
        writeIndex();

        // remove obsolete profile filters
        deleteFilesInDirectory(PROFILES_DIR);
    }
}
