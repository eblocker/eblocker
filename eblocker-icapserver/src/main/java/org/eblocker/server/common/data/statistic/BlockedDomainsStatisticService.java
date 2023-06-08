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
package org.eblocker.server.common.data.statistic;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.BlockedDomainsStats;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.parentalcontrol.BlockedDomainLogEntry;
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.ParentalControlFilterListsService;
import org.mapdb.BTreeKeySerializer;
import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOError;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
@SubSystemService(value = SubSystem.BACKGROUND_TASKS, initPriority = -1)
public class BlockedDomainsStatisticService {
    private static final Logger log = LoggerFactory.getLogger(BlockedDomainsStatisticService.class);

    private static final Comparator<Map.Entry<String, Integer>> COUNTER_COMPARATOR = Comparator.comparing((Function<Map.Entry<String, Integer>, Integer>) Map.Entry::getValue).reversed().thenComparing(Map.Entry::getKey);

    private final String dbPath;
    private final int heapSize;
    private final BlockedDomainStatisticsDatabaseLoader databaseLoader;
    private final DeviceService deviceService;
    private final ParentalControlFilterListsService filterListsService;

    private DB db;
    private BTreeMap<String, Long> lastResetById;
    private Map<String, CacheEntry> blockStatsById = new HashMap<>();

    @Inject
    public BlockedDomainsStatisticService(@Named("filter.stats.domains.db.path") String dbPath,
                                          @Named("filter.stats.domains.heap.size") int heapSize,
                                          BlockedDomainStatisticsDatabaseLoader databaseLoader,
                                          DeviceService deviceService,
                                          ParentalControlFilterListsService filterListsService) {
        this.dbPath = dbPath;
        this.heapSize = heapSize;
        this.databaseLoader = databaseLoader;
        this.deviceService = deviceService;
        this.filterListsService = filterListsService;
    }

    @SubSystemInit
    public void init() {
        try {
            initDb(databaseLoader, dbPath, deviceService);
        } catch (AssertionError | Exception | IOError e) {
            log.error("Failed to load data from database", e);
            try {
                log.error("Trying to create new db");
                Files.deleteIfExists(Paths.get(dbPath));
                Files.deleteIfExists(Paths.get(dbPath + ".p"));
                Files.deleteIfExists(Paths.get(dbPath + ".t"));
                initDb(databaseLoader, dbPath, deviceService);
            } catch (Exception e2) {
                log.error("Failed to create database - stats unavailable", e2);
                db = null;
            }
        }
    }

    private void initDb(BlockedDomainStatisticsDatabaseLoader databaseLoader, String dbPath, DeviceService deviceService) {
        this.db = databaseLoader.createOrOpen(dbPath);
        this.lastResetById = db.createTreeMap("reset").keySerializer(BTreeKeySerializer.STRING).valueSerializer(Serializer.LONG).makeOrGet();
        blockStatsById.clear(); // might contain partial data from first try if repeated
        initHeapStats(deviceService);
    }

    public synchronized void countBlockedDomain(BlockedDomainLogEntry logEntry) {
        if (db == null) {
            return;
        }

        if (logEntry.getListId() == null && logEntry.getCategory() == null) {
            log.warn("ignoring entry without list id or category");
            return;
        }

        Category category = logEntry.getCategory();
        if (category == null) {
            ParentalControlFilterMetaData metaData = filterListsService.getParentalControlFilterMetaData(logEntry.getListId());
            if (metaData == null) {
                log.warn("domain blocked by unknown list {}", logEntry.getListId());
                return;
            }
            category = metaData.getCategory();
        }

        CacheEntry cacheEntry = blockStatsById.computeIfAbsent(normalizeDeviceId(logEntry.getDeviceId()), CacheEntry::new);
        Map<String, Integer> stats = cacheEntry.heapStats.computeIfAbsent(category, k -> new HashMap<>());
        String domain = logEntry.getDomain().startsWith(".") ? logEntry.getDomain().substring(1) : logEntry.getDomain();
        Integer count = stats.get(domain);
        if (count == null) {
            BTreeMap<String, Integer> persistentMap = cacheEntry.persistentStats.get(category);
            if (persistentMap != null) {
                count = persistentMap.getOrDefault(domain, 0);
            } else {
                count = 0;
            }
        }
        stats.put(domain, count + 1);
    }

    public synchronized BlockedDomainsStats getStatsByDeviceId(String id) {
        CacheEntry entry = blockStatsById.get(normalizeDeviceId(id));
        if (entry == null) {
            return new BlockedDomainsStats(Instant.EPOCH, Collections.emptyMap());
        }

        Map<Category, List<Entry>> stats = entry.heapStats.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> getTopBlockedDomains(e.getValue())));
        return new BlockedDomainsStats(entry.lastReset, stats);
    }

    public synchronized BlockedDomainsStats getStats() {
        Map<Category, List<Entry>> stats = blockStatsById.values().stream()
                .map(e -> e.heapStats)
                .map(Map::entrySet)
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> {
                            Map<String, Integer> merged = new HashMap<>(a);
                            b.forEach((key, value) -> merged.merge(key, value, Integer::sum));
                            return merged;
                        }))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> getTopBlockedDomains(e.getValue())));

        long oldestReset = lastResetById.values().stream().reduce(Long::min).orElse(0L);
        return new BlockedDomainsStats(Instant.ofEpochMilli(oldestReset), stats);
    }

    public synchronized void updatePersistentStats() {
        log.info("writing blocked domain stats to disk");
        long start = System.currentTimeMillis();

        for (CacheEntry entry : blockStatsById.values()) {
            for (Category category : entry.heapStats.keySet()) {
                updatePersistentStats(entry.id, category);
                dropBottomEntriesFromHeap(entry.id, category);
            }
        }

        db.commit();

        long elapsed = System.currentTimeMillis() - start;
        log.info("writing blocked domains stats to disk finished in {}ms", elapsed);
    }

    public synchronized BlockedDomainsStats resetStats(String deviceId) {
        String id = normalizeDeviceId(deviceId);
        CacheEntry entry = blockStatsById.get(id);
        if (entry == null) {
            return new BlockedDomainsStats(Instant.now(), Collections.emptyMap());
        }

        Instant now = Instant.now();
        lastResetById.put(id, now.toEpochMilli());
        entry.lastReset = now;
        entry.heapStats.clear();
        for (Category category : entry.persistentStats.keySet()) {
            db.delete(createPersistentMapName(entry.id, category));
        }
        db.commit();
        entry.persistentStats.clear();

        return new BlockedDomainsStats(entry.lastReset, Collections.emptyMap());
    }

    public synchronized void resetStats() {
        blockStatsById.keySet().forEach(key -> resetStats(Device.ID_PREFIX + key));
    }

    private List<Entry> getTopBlockedDomains(Map<String, Integer> stats) {
        if (stats == null) {
            return Collections.emptyList();
        }

        return stats.entrySet().stream()
                .sorted(COUNTER_COMPARATOR)
                .map(e -> new Entry(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private void initHeapStats(DeviceService deviceService) {
        if (db == null) {
            return;
        }

        List<String> names = db.getAll().keySet().stream()
                .filter(name -> !"reset".equals(name))
                .collect(Collectors.toList());

        for (String name : names) {
            String[] tokens = name.split(":");
            String deviceId = tokens[0];
            Category category = Category.valueOf(tokens[1]);

            if (deviceService.getDeviceById(Device.ID_PREFIX + deviceId) == null) {
                db.delete(name);
                db.commit();
                continue;
            }

            CacheEntry cacheEntry = blockStatsById.computeIfAbsent(deviceId, CacheEntry::new);
            Long lastReset = lastResetById.get(deviceId);
            if (lastReset != null) {
                cacheEntry.lastReset = Instant.ofEpochMilli(lastReset);
            }

            BTreeMap<String, Integer> persistentStats = db.getTreeMap(name);
            cacheEntry.persistentStats.put(category, persistentStats);
            cacheEntry.heapStats.put(category, createTopEntriesMap(persistentStats));
        }
    }

    private Map<String, Integer> createTopEntriesMap(BTreeMap<String, Integer> persistentStats) {
        PriorityQueue<Map.Entry<String, Integer>> topEntries = new PriorityQueue<>(heapSize, COUNTER_COMPARATOR.reversed());
        for (Map.Entry<String, Integer> entry : persistentStats.entrySet()) {
            if (topEntries.size() < heapSize) {
                topEntries.add(entry);
                continue;
            }

            if (COUNTER_COMPARATOR.compare(topEntries.peek(), entry) > 0) {
                topEntries.poll();
                topEntries.add(entry);
            }
        }

        return topEntries.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void updatePersistentStats(String id, Category category) {
        log.debug("persisting {} stats for {}", category, id);
        Map<String, Integer> heapStats = blockStatsById.get(id).heapStats.get(category);
        BTreeMap<String, Integer> persistentStats = createOrOpenPersistentMap(id, category);
        persistentStats.putAll(heapStats);
        log.debug("wrote {} entries (now persistent: {})", heapStats.size(), persistentStats.size());
    }

    private BTreeMap<String, Integer> createOrOpenPersistentMap(String id, Category category) {
        return blockStatsById.get(id).persistentStats.computeIfAbsent(
                category,
                c -> db.createTreeMap(createPersistentMapName(id, category))
                        .keySerializer(BTreeKeySerializer.STRING)
                        .valueSerializer(Serializer.INTEGER)
                        .makeOrGet());
    }

    private String createPersistentMapName(String id, Category category) {
        return id + ":" + category;
    }

    private void dropBottomEntriesFromHeap(String id, Category category) {
        Map<String, Integer> heapStats = blockStatsById.get(id).heapStats.get(category);
        List<String> toDrop = heapStats.entrySet().stream()
                .sorted(COUNTER_COMPARATOR)
                .skip(heapSize)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        log.debug("dropping {} entries from {} / {}", toDrop.size(), id, category);
        toDrop.forEach(heapStats::remove);
    }

    private String normalizeDeviceId(String deviceId) {
        if (deviceId == null) {
            return null;
        }

        return deviceId.substring(7);
    }

    private class CacheEntry {
        final String id;
        final Map<Category, BTreeMap<String, Integer>> persistentStats = new EnumMap<>(Category.class);
        final EnumMap<Category, Map<String, Integer>> heapStats = new EnumMap<>(Category.class);

        Instant lastReset = Instant.EPOCH;

        CacheEntry(String id) {
            this.id = id;
        }
    }
}
