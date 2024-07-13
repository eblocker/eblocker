package org.eblocker.server.common.blacklist;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class CacheIndexRepository {
    @Nonnull
    private final String cacheIndexFile;
    @Nonnull
    private final ObjectMapper objectMapper;

    CacheIndexRepository(@Nonnull ObjectMapper objectMapper, @Nonnull String cacheIndexFile) {
        this.cacheIndexFile = cacheIndexFile;
        this.objectMapper = objectMapper;
    }

    CacheIndex read() throws IOException {
        return objectMapper.readValue(new File(cacheIndexFile), CacheIndex.class);
    }

    void write(@Nonnull CacheIndex index) throws IOException {
        objectMapper.writeValue(new File(cacheIndexFile), index);
    }

    void upgradeJsonFile(@Nonnull JsonNode node) throws IOException {
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
                .collect(Collectors.groupingBy(filter -> filter.getKey().getId()));

        // write upgraded index
        CacheIndex index = new CacheIndex(updatedFilters);
        write(index);
    }
}
