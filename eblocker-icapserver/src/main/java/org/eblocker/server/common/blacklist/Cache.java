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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Cache {
    private static final Logger log = LoggerFactory.getLogger(Cache.class);

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
    @Nonnull
    private final CacheIndex index;

    private final CacheIndexRepository cacheIndexRepository;

    Cache(@Nonnull String cachePath, @Nonnull ObjectMapper objectMapper) throws IOException {
        this.cachePath = cachePath;
        this.objectMapper = objectMapper;

        this.cacheIndexFile = cachePath + INDEX_JSON;

        cacheIndexRepository = new CacheIndexRepository(objectMapper, cacheIndexFile);

        index = initCache();
    }

    /**
     * Get latest versions of all file filters
     */
    @Nonnull
    List<CachedFileFilter> getFileFilters() {
        return index.getAllLatestFileFilters();
    }

    /**
     * Get all versions of all file filters
     */
    @Nonnull
    List<CachedFileFilter> getAllFileFilters() {
        return index.getAllFileFilters();
    }

    /**
     * Get latest version of a cached file filter
     */
    @Nullable
    CachedFileFilter getLatestFileFilterById(int id) {
        return index.getLatestFileFilterById(id);
    }

    CachedFilterKey getLatestFileFilterKeyById(int id) {
        CachedFileFilter latestFileFilterById = index.getLatestFileFilterById(id);
        if (latestFileFilterById != null) {
            return latestFileFilterById.getKey();
        }
        return null;
    }

    /**
     * Get all versions of a cached file filter
     */
    @Nullable
    List<CachedFileFilter> getFileFiltersById(int id) {
        return index.getFileFilterById(id);
    }

    CachedFileFilter storeFileFilter(int id, long version, String format, @Nullable String filterFile, @Nonnull String bloomFile) throws IOException {
        CachedFilterKey key = new CachedFilterKey(id, version);

        String cacheFilterFile = null;
        if (filterFile != null) {
            cacheFilterFile = LIST_DIR + "/" + key + FILTER_FILE_EXTENSION;
            Files.copy(Paths.get(filterFile), Paths.get(cachePath + "/" + cacheFilterFile),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        String cacheBloomFile = LIST_DIR + "/" + key + BLOOM_FILE_EXTENSION;
        Files.copy(Paths.get(bloomFile), Paths.get(cachePath + "/" + cacheBloomFile),
                StandardCopyOption.REPLACE_EXISTING);

        CachedFileFilter importedFilter = new CachedFileFilter(key, cacheBloomFile, cacheFilterFile, format, false);

        index.addFilterSortedByVersion(importedFilter);
        cacheIndexRepository.write(index);

        return importedFilter;
    }

    void removeFileFilter(int id, long version) throws IOException {
        CachedFilterKey key = new CachedFilterKey(id, version);
        String cacheFilterFile = LIST_DIR + "/" + key + FILTER_FILE_EXTENSION;
        String cacheBloomFile = LIST_DIR + "/" + key + BLOOM_FILE_EXTENSION;

        Files.deleteIfExists(Paths.get(cachePath + "/" + cacheFilterFile));
        Files.deleteIfExists(Paths.get(cachePath + "/" + cacheBloomFile));

        index.removeFileFilter(key);
        cacheIndexRepository.write(index);
    }

    void markOldVersionsAsDeleted() {
        Set<CachedFilterKey> newestKeys = getFileFilters().stream().map(CachedFileFilter::getKey).collect(Collectors.toSet());

        getAllFileFilters().stream()
                .map(CachedFileFilter::getKey)
                .filter(key -> !newestKeys.contains(key))
                .forEach(key -> {
                    log.debug("marking filter {} as deleted as a newer one exists", key);
                    markFilterAsDeleted(key);
                });
    }

    void markNonExistingFiltersAsDeleted(Set<Integer> ids) {
        getAllFileFilters().stream()
                .map(CachedFileFilter::getKey)
                .filter(key -> !ids.contains(key.getId()))
                .forEach(key -> {
                    log.debug("marking filter {} as deleted as it does not exist anymore", key);
                    markFilterAsDeleted(key);
                });
    }

    void markFilterAsDeleted(CachedFilterKey key) {
        try {
            CachedFileFilter filter = index.getAllFileFilters().stream()
                    .filter(e -> e.getKey().equals(key))
                    .findFirst()
                    .orElse(null);
            if (filter != null) {
                filter.setDeleted();
                cacheIndexRepository.write(index);
            }
        } catch (IOException ioe) {
            log.warn("failed to mark filter {} as deleted: ", key, ioe);
        }
    }

    List<CachedFilterKey> deleteMarkedFilters() {
        log.info("removing filters marked as deleted");
        List<CachedFilterKey> filterKeysToDelete = getFilterKeysMarkedAsDeleted();
        filterKeysToDelete.forEach(this::deleteFilter);
        return filterKeysToDelete;
    }

    List<CachedFilterKey> getFilterKeysMarkedAsDeleted() {
        return getAllFileFilters().stream()
                .filter(CachedFileFilter::isDeleted)
                .map(CachedFileFilter::getKey).collect(Collectors.toList());
    }

    private void deleteFilter(CachedFilterKey key) {
        try {
            log.info("Removing filter {}.", key);
            removeFileFilter(key.getId(), key.getVersion());
        } catch (IOException e) {
            log.error("Failed to delete filter: {}", key, e);
        }
    }

    private CacheIndex initCache() throws IOException {
        if (checkIndexFileAndUpgrade()) {
            return cacheIndexRepository.read();
        } else {
            log.info("creating new cache");
            deleteCachedFilters();
            CacheIndex cacheIndex = new CacheIndex();
            cacheIndexRepository.write(cacheIndex);
            return cacheIndex;
        }
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

    private boolean checkIndexFileAndUpgrade() throws IOException {
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
        if (cacheFormatField == null || cacheFormatField.intValue() < CacheIndex.CACHE_FORMAT) {
            cacheIndexRepository.upgradeJsonFile(node);
            // remove obsolete profile filters
            deleteFilesInDirectory(PROFILES_DIR);
        }

        return true;
    }

}
