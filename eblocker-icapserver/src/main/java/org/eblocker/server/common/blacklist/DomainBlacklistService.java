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
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

@Singleton
public class DomainBlacklistService {
    private static final Logger log = LoggerFactory.getLogger(DomainBlacklistService.class);

    @Nonnull
    private final String sourcePath;

    @Nonnull
    private final ScheduledExecutorService executorService;
    @Nonnull
    private final FilterByKeys filtersByKey;
    @Nonnull
    private final List<Listener> listeners = new ArrayList<>();

    @Nonnull
    private final Cache cache;

    @Inject
    DomainBlacklistService(@Named("domainblacklist.charset") String charsetName,
                           @Named("domainblacklist.source.path") String sourcePath,
                           @Named("domainblacklist.cache.path") String cachePath,
                           ObjectMapper objectMapper,
                           @Named("lowPrioScheduledExecutor") ScheduledExecutorService executorService) throws IOException {
        this.sourcePath = sourcePath;
        this.executorService = executorService;

        try {
            long start = System.currentTimeMillis();

            cache = new Cache(cachePath, objectMapper);
            cache.markOldVersionsAsDeleted();
            cache.deleteMarkedFilters();
            filtersByKey = new FilterByKeys(cache.getAllFileFilters(), Charset.forName(charsetName), cachePath);

            long stop = System.currentTimeMillis();
            log.info("read filters in {}ms", (stop - start));
        } catch (IOException e) {
            log.error("Failed to initialize filters, domain blacklisting will be unavailable", e);
            throw e;
        }
    }

    @Nullable
    public DomainFilter getFilter(@Nonnull Integer id) {
        return filtersByKey.getFilter(cache.getLatestFileFilterKeyById(id));
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
    public void setFilters(@Nonnull Collection<ParentalControlFilterMetaData> blacklists) {
        executorService.execute(() -> {
            synchronized (DomainBlacklistService.this) {
                log.info("updating filters");
                List<CachedFilterKey> deletedFilterKeys = cache.deleteMarkedFilters();
                filtersByKey.remove(deletedFilterKeys);

                blacklists.forEach(this::updateFileFilter);
                cache.markOldVersionsAsDeleted();
                cache.markNonExistingFiltersAsDeleted(getBlacklists(blacklists));
                filtersByKey.remove(cache.getFilterKeysMarkedAsDeleted());
            }
            listeners.forEach(Listener::onUpdate);
        });
    }

    public void addListener(@Nonnull Listener listener) {
        listeners.add(listener);
    }

    private void updateFileFilter(@Nonnull ParentalControlFilterMetaData blacklist) {
        if (!blacklist.getFormat().startsWith("domainblacklist")) {
            log.debug("ignoring non-domainblacklist format {} for {}", blacklist.getFormat(), blacklist.getId());
            return;
        }

        long version = blacklist.getDate().getTime();
        CachedFileFilter cachedFileFilter = cache.getLatestFileFilterById(blacklist.getId());
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
                filtersByKey.update(importedFilter);
            } catch (IOException e) {
                log.error("failed to update file filter", e);
                throw new UncheckedIOException(e);
            }
        } else {
            log.info("{} already at newest version {}", blacklist.getId(), version);
        }
    }

    @Nonnull
    private String getAbsoluteFileName(@Nonnull String fileName) {
        return fileName.startsWith("/") ? fileName : sourcePath + "/" + fileName;
    }

    @Nonnull
    private static Set<Integer> getBlacklists(@Nonnull Collection<ParentalControlFilterMetaData> blacklists) {
        return blacklists.stream().map(ParentalControlFilterMetaData::getId).collect(Collectors.toSet());
    }

    public interface Listener {
        void onUpdate();
    }

    private static class FilterByKeys {

        @Nonnull
        private final ConcurrentMap<CachedFilterKey, DomainFilter<?>> filters;

        @Nonnull
        private final DomainFilterLoader domainFilterLoader;

        private FilterByKeys(@Nonnull List<CachedFileFilter> allFileFilters, Charset charset, String cachePath) {
            domainFilterLoader = new DomainFilterLoader(charset, cachePath);

            filters = createFilters(allFileFilters);
        }

        private ConcurrentMap<CachedFilterKey, DomainFilter<?>> createFilters(List<CachedFileFilter> allFileFilters) {
            ConcurrentHashMap<CachedFilterKey, DomainFilter<?>> filters = new ConcurrentHashMap<>();
            for (CachedFileFilter fileFilter : allFileFilters) {
                DomainFilter<?> filter = domainFilterLoader.loadStoredFilter(fileFilter);
                if (filter != null) {
                    filters.put(fileFilter.getKey(), filter);
                }
            }
            return filters;
        }

        @Nullable
        private DomainFilter<?> getFilter(@Nullable CachedFilterKey key) {
            if (key == null) {
                return null;
            }
            return filters.get(key);
        }

        private void put(CachedFilterKey key, DomainFilter<?> filter) {
            filters.put(key, filter);
        }

        private void remove(List<CachedFilterKey> toRemove) {
            toRemove.forEach(this::remove);
        }

        private void remove(CachedFilterKey key) {
            filters.remove(key);
        }

        private void update(CachedFileFilter importedFilter) {
            DomainFilter<?> filter = domainFilterLoader.loadStoredFilter(importedFilter);
            CachedFilterKey key = importedFilter.getKey();
            if (filter != null) {
                put(key, filter);
            } else {
                remove(key);
            }
        }
    }
}
