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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
class CacheIndex {
    @JsonIgnore
    static final int CACHE_FORMAT = 5;
    private final int format;
    private final Map<Integer, List<CachedFileFilter>> filters;

    CacheIndex() {
        this.format = CACHE_FORMAT;
        filters = new HashMap<>();
    }

    CacheIndex(@Nonnull Map<Integer, List<CachedFileFilter>> filters) {
        this.format = CACHE_FORMAT;
        this.filters = filters;
    }

    CacheIndex(@JsonProperty("format") int format, @Nonnull @JsonProperty("filters") Map<Integer, List<CachedFileFilter>> filters) {
        this.format = format;
        this.filters = filters;
    }

    int getFormat() {
        return format;
    }

    /**
     * Get latest versions of all file filters
     */
    @Nonnull
    List<CachedFileFilter> getAllLatestFileFilters() {
        return filters.values().stream()
                .map(f -> f.get(0))
                .collect(Collectors.toList());
    }

    /**
     * Get all versions of all file filters
     */
    @Nonnull
    List<CachedFileFilter> getAllFileFilters() {
        return filters.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    @Nullable
    CachedFileFilter getLatestFileFilterById(int id) {
        List<CachedFileFilter> cachedFileFilters = filters.get(id);
        return cachedFileFilters == null || cachedFileFilters.isEmpty() ? null : cachedFileFilters.get(0);
    }

    List<CachedFileFilter> getFileFilterById(int id) {
        List<CachedFileFilter> cachedFileFilters = filters.get(id);
        if (cachedFileFilters != null) {
            return new ArrayList<>(cachedFileFilters);
        }
        return null;
    }

    void addFilterSortedByVersion(@Nonnull CachedFileFilter fileFilterToAdd) {
        CachedFilterKey key = fileFilterToAdd.getKey();
        List<CachedFileFilter> filterList = filters.computeIfAbsent(key.getId(), k -> new ArrayList<>());
        int i = 0;
        while (i < filterList.size() && key.getVersion() < filterList.get(i).getKey().getVersion()) {
            ++i;
        }
        filterList.add(i, fileFilterToAdd);
    }

    void removeFileFilter(@Nonnull CachedFilterKey key) {
        List<CachedFileFilter> remainingFilters = filters.computeIfAbsent(key.getId(), k -> new ArrayList<>()).stream()
                .filter(f -> !f.getKey().equals(key))
                .collect(Collectors.toList());
        if (remainingFilters.isEmpty()) {
            filters.remove(key.getId());
        } else {
            filters.put(key.getId(), remainingFilters);
        }
    }

    boolean isEmpty() {
        return filters.isEmpty();
    }

}
