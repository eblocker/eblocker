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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.Funnels;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

public class DomainBlacklistService {
    private static final Logger log = LoggerFactory.getLogger(DomainBlacklistService.class);

    private final Charset charset;
    private final String sourcePath;
    private final String cachePath;

    private final ScheduledExecutorService executorService;
    private final ConcurrentMap<CachedFilterKey, DomainFilter<?>> filtersByName = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new ArrayList<>();

    private Cache cache;

    @Inject
    public DomainBlacklistService(@Named("domainblacklist.charset") String charsetName,
                                  @Named("domainblacklist.source.path") String sourcePath,
                                  @Named("domainblacklist.cache.path") String cachePath,
                                  ObjectMapper objectMapper,
                                  @Named("lowPrioScheduledExecutor") ScheduledExecutorService executorService) {
        this.charset = Charset.forName(charsetName);
        this.sourcePath = sourcePath;
        this.cachePath = cachePath;
        this.executorService = executorService;

        try {
            long start = System.currentTimeMillis();

            this.cache = new Cache(cachePath, objectMapper);
            markOldVersionsAsDeleted();
            deleteMarkedFilters();
            createFiltersFromCache();

            long stop = System.currentTimeMillis();
            log.info("read filters in {}ms", (stop - start));
        } catch (IOException e) {
            log.error("Failed to initialize filters, domain blacklisting will be unavailable", e);
        }
    }

    public DomainFilter getFilter(Integer id) {
        return getFilterKeys(Collections.singleton(id)).stream()
            .map(filtersByName::get)
            .filter(Objects::nonNull)
            .findAny().orElse(null);
    }

    /**
     * Set the filters for blacklisting. This has four effects:
     * <ul>
     * <li>delete old filters marked as deleted (assuming these are not be referenced any more)</li>
     * <li>all filters are created / updated according to the blacklists </li>
     * <li>old versions or non-existing filters are marked as deleted. They can not be deleted right away as they might be in
     * use for some time</li>
     * <li>filters marked as deleted are dropped from cache</li>
     * </ul>
     */
    public void setFilters(Collection<ParentalControlFilterMetaData> blacklists) {
        executorService.execute(() -> {
            synchronized (DomainBlacklistService.this) {
                log.info("updating filters");
                deleteMarkedFilters();

                blacklists.forEach(this::updateFileFilter);
                markOldVersionsAsDeleted();
                markNonExistingFiltersAsDeleted(blacklists);
                dropDeletedFiltersFromMemory();
            }
            listeners.forEach(Listener::onUpdate);
        });
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    private void createFiltersFromCache() {
        cache.getAllFileFilters().forEach(e -> cacheFilter(e.getKey(), loadStoredFilter(e)));
    }

    private DomainFilter<?> loadStoredFilter(CachedFileFilter storedFilter) {
        switch (storedFilter.getFormat()) {
            case "domainblacklist":
            case "domainblacklist/string":
                return loadFileFilter(storedFilter);
            case "domainblacklist/bloom":
                return loadBloomFilter(storedFilter);
            case "domainblacklist/hash-md5":
            case "domainblacklist/hash-sha1":
                return loadHashFilter(storedFilter);
            default:
                log.error("unknown format {}", storedFilter.getFormat());
                return null;
        }
    }

    private DomainFilter<String> loadFileFilter(CachedFileFilter storedFilter) {
        try {
            long start = System.currentTimeMillis();

            DomainFilter<String> fileFilter = new SingleFileFilter(charset, Paths.get(cachePath, storedFilter.getFileFilterFileName()));

            BloomDomainFilter<String> bloomFilter;
            try (InputStream in = Files.newInputStream(Paths.get(cachePath, storedFilter.getBloomFilterFileName()))) {
                bloomFilter = BloomDomainFilter.readFrom(in, new StringFunnel(charset), fileFilter);
            }

            long stop = System.currentTimeMillis();
            log.debug("read file filter {} ({}) with {} domains in {}ms.", storedFilter.getKey().getId(), storedFilter.getKey().getVersion(), fileFilter.getSize(), (stop - start));

            return bloomFilter;
        } catch (IOException e) {
            log.error("Failed to load stored file filter {}", storedFilter.getKey(), e);
            return null;
        }
    }

    private DomainFilter<byte[]> loadHashFilter(CachedFileFilter storedFilter) {
        try {
            long start = System.currentTimeMillis();

            HashFileFilter fileFilter = new HashFileFilter(Paths.get(cachePath, storedFilter.getFileFilterFileName()));

            BloomDomainFilter<byte[]> bloomFilter;
            try (InputStream in = Files.newInputStream(Paths.get(cachePath, storedFilter.getBloomFilterFileName()))) {
                bloomFilter = BloomDomainFilter.readFrom(in, Funnels.byteArrayFunnel(), fileFilter);
            }

            long stop = System.currentTimeMillis();
            log.debug("read hash file filter {} ({}) with {} domains in {}ms.", storedFilter.getKey().getId(), storedFilter.getKey().getVersion(), fileFilter.getSize(), (stop - start));

            return bloomFilter;
        } catch (IOException e) {
            log.error("Failed to load stored hash file filter {}", storedFilter.getKey(), e);
            return null;
        }
    }

    private DomainFilter<String> loadBloomFilter(CachedFileFilter storedFilter) {
        try {
            long start = System.currentTimeMillis();
            BloomDomainFilter<String> bloomFilter;

            try (InputStream in = Files.newInputStream(Paths.get(cachePath, storedFilter.getBloomFilterFileName()))) {
                bloomFilter = BloomDomainFilter.readFrom(in, new StringFunnel(charset), null);
            }

            long stop = System.currentTimeMillis();
            log.debug("read bloom filter {} ({}) in {}ms.", storedFilter.getKey().getId(), storedFilter.getKey().getVersion(), (stop - start));

            return bloomFilter;
        } catch (IOException e) {
            log.error("Failed to load stored bloom filter {}", storedFilter.getKey(), e);
            return null;
        }
    }

    private void updateFileFilter(ParentalControlFilterMetaData blacklist) {
        if (!blacklist.getFormat().startsWith("domainblacklist")) {
            log.debug("ignoring non-domainblacklist format {} for {}", blacklist.getFormat(), blacklist.getId());
            return;
        }

        long version = blacklist.getDate().getTime();
        CachedFileFilter cachedFileFilter = cache.getFileFilterById(blacklist.getId());
        if (cachedFileFilter == null || cachedFileFilter.getKey().getVersion() < version) {
            if (cachedFileFilter == null) {
                log.info("inserting {} version {}", blacklist.getId(), version);
            } else {
                log.info("updating {} from version {} to {}", blacklist.getId(), cachedFileFilter.getKey().getVersion(), version);
            }

            try {
                String filterFileName = null;
                String bloomFileName;

                if ("domainblacklist/bloom".equals(blacklist.getFormat())) {
                    bloomFileName = getAbsoluteFileName(blacklist.getFilenames().get(0));
                } else {
                    filterFileName = getAbsoluteFileName(blacklist.getFilenames().get(0));
                    bloomFileName = getAbsoluteFileName(blacklist.getFilenames().get(1));
                }

                CachedFileFilter importedFilter = cache.storeFileFilter(blacklist.getId(), version, blacklist.getFormat(), filterFileName, bloomFileName);
                DomainFilter<?> filter = loadStoredFilter(importedFilter);
                cacheFilter(importedFilter.getKey(), filter);
            } catch (IOException e) {
                log.error("failed to update file filter", e);
                throw new UncheckedIOException(e);
            }
        } else {
            log.info("{} already at newest version {}", blacklist.getId(), version);
        }
    }

    private void cacheFilter(CachedFilterKey key, DomainFilter<?> filter) {
        if (filter != null) {
            filtersByName.put(key, filter);
        } else {
            filtersByName.remove(key);
        }
    }

    private String getAbsoluteFileName(String fileName) {
        return fileName.startsWith("/") ? fileName : sourcePath + "/" + fileName;
    }

    private void deleteMarkedFilters() {
        log.info("removing filters marked as deleted");
        cache.getAllFileFilters().stream()
            .filter(CachedFileFilter::isDeleted)
            .map(CachedFileFilter::getKey)
            .forEach(this::deleteFilter);
    }

    private void deleteFilter(CachedFilterKey key) {
        try {
            log.info("Removing filter {}.", key);
            cache.removeFileFilter(key.getId(), key.getVersion());
            filtersByName.remove(key);
        } catch (IOException e) {
            log.error("Failed to delete filter: {}", key, e);
        }
    }

    private void markOldVersionsAsDeleted() {
        Set<CachedFilterKey> newestKeys = cache.getFileFilters().stream().map(CachedFileFilter::getKey).collect(Collectors.toSet());

        cache.getAllFileFilters().stream()
            .map(CachedFileFilter::getKey)
            .filter(key -> !newestKeys.contains(key))
            .forEach(key -> {
                log.debug("marking filter {} as deleted as a newer one exists", key);
                markFilterAsDeleted(key);
            });
    }

    private void markNonExistingFiltersAsDeleted(Collection<ParentalControlFilterMetaData> blacklists) {
        Set<Integer> ids = blacklists.stream().map(ParentalControlFilterMetaData::getId).collect(Collectors.toSet());

        cache.getAllFileFilters().stream()
            .map(CachedFileFilter::getKey)
            .filter(key -> !ids.contains(key.getId()))
            .forEach(key -> {
                log.debug("marking filter {} as deleted as it does not exist anymore", key);
                markFilterAsDeleted(key);
            });
    }

    private void markFilterAsDeleted(CachedFilterKey key) {
        try {
            cache.markFilterAsDeleted(key.getId(), key.getVersion());
        } catch (IOException ioe) {
            log.warn("failed to mark filter {} as deleted: ", ioe);
        }
    }

    private void dropDeletedFiltersFromMemory() {
        cache.getAllFileFilters().stream()
            .filter(CachedFileFilter::isDeleted)
            .map(CachedFileFilter::getKey)
            .forEach(filtersByName::remove);
    }

    // get filter keys of latest cached versions
    private Set<CachedFilterKey> getFilterKeys(Set<Integer> filters) {
        Set<CachedFilterKey> filterKeys = new HashSet<>();
        filters.forEach(id -> {
            CachedFileFilter cachedFileFilter = cache.getFileFilterById(id);
            if (cachedFileFilter == null) {
                log.error("no cached file filter for id {}", id);
            } else {
                filterKeys.add(cachedFileFilter.getKey());
            }
        });
        return filterKeys;
    }

    public interface Listener {
        void onUpdate();
    }
}
