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

import org.eblocker.server.common.data.BlockedDomainsStats;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.parentalcontrol.BlockedDomainLogEntry;
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.ParentalControlFilterListsService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mapdb.BTreeKeySerializer;
import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BlockedDomainsStatisticServiceTest {

    private static final String ID = "001020304050";
    private static final String DEVICE_ID = "device:" + ID;

    private Path dbPath;
    private DeviceService deviceService;
    private ParentalControlFilterListsService filterListsService;

    private DB db;
    private BlockedDomainStatisticsDatabaseLoader databaseLoader;
    private BlockedDomainsStatisticService statisticService;

    @Before
    public void setUp() throws IOException {
        dbPath = Files.createTempFile(BlockedDomainsStatisticServiceTest.class.getName(), ".db");
        Files.delete(dbPath);

        databaseLoader = Mockito.mock(BlockedDomainStatisticsDatabaseLoader.class);
        Mockito.when(databaseLoader.createOrOpen(dbPath.toString())).then(im -> {
            if (db == null) {
                createOpenDb();
            }
            return db;
        });

        deviceService = Mockito.mock(DeviceService.class);
        Mockito.when(deviceService.getDeviceById(DEVICE_ID)).thenReturn(new Device());

        filterListsService = Mockito.mock(ParentalControlFilterListsService.class);
        Mockito.when(filterListsService.getParentalControlFilterMetaData(0)).thenReturn(mockFilterData(0, Category.ADS));
        Mockito.when(filterListsService.getParentalControlFilterMetaData(1)).thenReturn(mockFilterData(1, Category.CUSTOM));
        Mockito.when(filterListsService.getParentalControlFilterMetaData(2)).thenReturn(mockFilterData(2, Category.PARENTAL_CONTROL));
        Mockito.when(filterListsService.getParentalControlFilterMetaData(3)).thenReturn(mockFilterData(3, Category.TRACKERS));
    }

    @After
    public void tearDown() throws IOException {
        if (db != null && !db.isClosed()) {
            try {
                db.close();
            } catch (Exception e) {
                // ignore
            }
        }
        Files.deleteIfExists(dbPath);
        Files.deleteIfExists(dbPath.resolveSibling(dbPath.getFileName() + ".p"));
        Files.deleteIfExists(dbPath.resolveSibling(dbPath.getFileName() + ".t"));
    }

    @Test
    public void testCountingHeapOnly() {
        statisticService = new BlockedDomainsStatisticService(dbPath.toString(), 5, databaseLoader, deviceService, filterListsService);
        statisticService.init();

        for (int i = 0; i < 4; ++i) {
            for (int j = 0; j < 5; ++j) {
                countBlockedDomain(DEVICE_ID, "blocked" + i + "-" + j + ".com", i, j + 1);
            }
        }

        BlockedDomainsStats blockedDomainsStats = statisticService.getStatsByDeviceId(DEVICE_ID);
        Assert.assertNotNull(blockedDomainsStats);
        Map<Category, List<Entry>> stats = blockedDomainsStats.getStats();
        Assert.assertNotNull(stats);
        Category[] categories = { Category.ADS, Category.CUSTOM, Category.PARENTAL_CONTROL, Category.TRACKERS };
        for (int i = 0; i < categories.length; ++i) {
            Assert.assertNotNull(stats.get(categories[i]));
            Assert.assertEquals(5, stats.get(categories[i]).size());
            for (int j = 0; j < 5; ++j) {
                Assert.assertEquals("blocked" + i + "-" + (4 - j) + ".com", stats.get(categories[i]).get(j).getDomain());
                Assert.assertEquals((5 - j), stats.get(categories[i]).get(j).getCount());
            }
        }
    }

    @Test
    public void testCountingPersistent() {
        createOpenDb();
        BTreeMap<String, Integer> persistentStats = db.createTreeMap("001020304050:ADS").keySerializer(BTreeKeySerializer.STRING).valueSerializer(Serializer.INTEGER).makeOrGet();
        persistentStats.put("blocked10", 10);
        persistentStats.put("blocked5", 5);
        persistentStats.put("blocked1", 1);

        statisticService = new BlockedDomainsStatisticService(dbPath.toString(), 2, databaseLoader, deviceService, filterListsService);
        statisticService.init();

        // check heap is set up correctly
        Map<Category, List<Entry>> stats = statisticService.getStatsByDeviceId(DEVICE_ID).getStats();
        Assert.assertEquals(1, stats.size());
        Assert.assertNotNull(stats.get(Category.ADS));
        Assert.assertEquals(2, stats.get(Category.ADS).size());
        Assert.assertEquals("blocked10", stats.get(Category.ADS).get(0).getDomain());
        Assert.assertEquals(10, stats.get(Category.ADS).get(0).getCount());
        Assert.assertEquals("blocked5", stats.get(Category.ADS).get(1).getDomain());
        Assert.assertEquals(5, stats.get(Category.ADS).get(1).getCount());

        // check increasing off-heap counter works as expected
        statisticService.countBlockedDomain(new BlockedDomainLogEntry(DEVICE_ID, "blocked1", 0));

        stats = statisticService.getStatsByDeviceId(DEVICE_ID).getStats();
        Assert.assertEquals(3, stats.get(Category.ADS).size());
        Assert.assertEquals("blocked10", stats.get(Category.ADS).get(0).getDomain());
        Assert.assertEquals(10, stats.get(Category.ADS).get(0).getCount());
        Assert.assertEquals("blocked5", stats.get(Category.ADS).get(1).getDomain());
        Assert.assertEquals(5, stats.get(Category.ADS).get(1).getCount());
        Assert.assertEquals("blocked1", stats.get(Category.ADS).get(2).getDomain());
        Assert.assertEquals(2, stats.get(Category.ADS).get(2).getCount());
    }

    @Test
    public void testUpdatePersistentStats() {
        statisticService = new BlockedDomainsStatisticService(dbPath.toString(), 5, databaseLoader, deviceService, filterListsService);
        statisticService.init();

        for (int i = 0; i < 10; ++i) {
            countBlockedDomain(DEVICE_ID, "blocked" + i + ".com", 0, i + 1);
        }

        BlockedDomainsStats blockedDomainsStats = statisticService.getStatsByDeviceId(DEVICE_ID);
        Assert.assertNotNull(blockedDomainsStats);
        Map<Category, List<Entry>> stats = blockedDomainsStats.getStats();
        Assert.assertNotNull(stats);
        Assert.assertNotNull(stats.get(Category.ADS));
        Assert.assertEquals(10, stats.get(Category.ADS).size());
        for (int i = 0; i < 10; ++i) {
            Assert.assertEquals("blocked" + (9 - i) + ".com", stats.get(Category.ADS).get(i).getDomain());
            Assert.assertEquals(10 - i, stats.get(Category.ADS).get(i).getCount());
        }

        statisticService.updatePersistentStats();

        // check persistent stats
        Assert.assertNotNull(db.get("001020304050:ADS"));
        Assert.assertTrue(db.get("001020304050:ADS") instanceof BTreeMap);
        BTreeMap<String, Integer> persistentStats = db.get("001020304050:ADS");
        Assert.assertEquals(10, persistentStats.size());
        for (int i = 0; i < 10; ++i) {
            Assert.assertEquals(Integer.valueOf(10 - i), persistentStats.get("blocked" + (9 - i) + ".com"));
        }

        // check heap stats has been truncated
        blockedDomainsStats = statisticService.getStatsByDeviceId(DEVICE_ID);
        Assert.assertNotNull(blockedDomainsStats);
        stats = blockedDomainsStats.getStats();
        Assert.assertNotNull(stats);
        Assert.assertNotNull(stats.get(Category.ADS));
        Assert.assertEquals(5, stats.get(Category.ADS).size());
        for (int i = 0; i < 5; ++i) {
            Assert.assertEquals("blocked" + (9 - i) + ".com", stats.get(Category.ADS).get(i).getDomain());
            Assert.assertEquals(10 - i, stats.get(Category.ADS).get(i).getCount());
        }
    }

    @Test
    public void testCountingMixedListIdCategoryEntries() {
        statisticService = new BlockedDomainsStatisticService(dbPath.toString(), 3, databaseLoader, deviceService, filterListsService);
        statisticService.init();
        statisticService.countBlockedDomain(new BlockedDomainLogEntry(DEVICE_ID, "blocked.com", 0));
        statisticService.countBlockedDomain(new BlockedDomainLogEntry(DEVICE_ID, "blocked.com", Category.ADS));
        Assert.assertNotNull(statisticService.getStatsByDeviceId(DEVICE_ID).getStats());
        Assert.assertNotNull(statisticService.getStatsByDeviceId(DEVICE_ID).getStats().get(Category.ADS));
        Assert.assertEquals(1, statisticService.getStatsByDeviceId(DEVICE_ID).getStats().get(Category.ADS).size());
        Assert.assertEquals("blocked.com", statisticService.getStatsByDeviceId(DEVICE_ID).getStats().get(Category.ADS).get(0).getDomain());
        Assert.assertEquals(2, statisticService.getStatsByDeviceId(DEVICE_ID).getStats().get(Category.ADS).get(0).getCount());
    }

    @Test
    public void testResetStatsDevice() {
        createOpenDb();
        BTreeMap<String, Integer> persistentStats = db.createTreeMap("001020304050:ADS").keySerializer(BTreeKeySerializer.STRING).valueSerializer(Serializer.INTEGER).makeOrGet();
        persistentStats.put("blocked10", 10);
        persistentStats.put("blocked5", 5);
        persistentStats.put("blocked1", 1);

        Instant lastReset = ZonedDateTime.of(2018, 7, 10, 10, 0, 0, 0, ZoneId.systemDefault()).toInstant().truncatedTo(ChronoUnit.MILLIS);
        BTreeMap<String, Long> resetMap = db.createTreeMap("reset").keySerializer(BTreeKeySerializer.STRING).valueSerializer(Serializer.LONG).makeOrGet();
        resetMap.put(ID, lastReset.toEpochMilli());

        // init service and check initial loaded stats
        statisticService = new BlockedDomainsStatisticService(dbPath.toString(), 3, databaseLoader, deviceService, filterListsService);
        statisticService.init();
        BlockedDomainsStats blockedStats = statisticService.getStatsByDeviceId(DEVICE_ID);
        Assert.assertEquals(lastReset, blockedStats.getLastReset());
        Assert.assertNotNull(blockedStats.getStats());
        Assert.assertEquals(1, blockedStats.getStats().size());
        Assert.assertNotNull(blockedStats.getStats().get(Category.ADS));
        Assert.assertEquals(3, blockedStats.getStats().get(Category.ADS).size());

        // reset stats and check they have been cleared
        statisticService.resetStats(DEVICE_ID);
        blockedStats = statisticService.getStatsByDeviceId(DEVICE_ID);
        Assert.assertTrue(Duration.between(blockedStats.getLastReset(), Instant.now()).toMinutes() < 1);
        Assert.assertNotNull(blockedStats.getStats());
        Assert.assertEquals(0, blockedStats.getStats().size());
        Assert.assertEquals(Long.valueOf(blockedStats.getLastReset().toEpochMilli()), resetMap.get(ID));
        Assert.assertEquals(0, persistentStats.size());

        Set<String> dbNames = db.getAll().keySet();
        Assert.assertFalse(dbNames.contains("001020304050:ADS"));
    }

    @Test
    public void testResetStats() {
        Mockito.when(deviceService.getDeviceById("device:000000000000")).thenReturn(new Device());
        Mockito.when(deviceService.getDeviceById("device:000000000001")).thenReturn(new Device());

        createOpenDb();
        BTreeMap<String, Integer> persistentStatsDeviceA = db.createTreeMap("000000000000:ADS").keySerializer(BTreeKeySerializer.STRING).valueSerializer(Serializer.INTEGER).makeOrGet();
        BTreeMap<String, Integer> persistentStatsDeviceB = db.createTreeMap("000000000001:TRACKERS").keySerializer(BTreeKeySerializer.STRING).valueSerializer(Serializer.INTEGER).makeOrGet();
        persistentStatsDeviceA.put("blocked10", 10);
        persistentStatsDeviceA.put("blocked5", 5);
        persistentStatsDeviceA.put("blocked1", 1);
        persistentStatsDeviceB.putAll(persistentStatsDeviceA);

        Instant lastReset = ZonedDateTime.of(2018, 7, 10, 10, 0, 0, 0, ZoneId.systemDefault()).toInstant().truncatedTo(ChronoUnit.MILLIS);
        BTreeMap<String, Long> resetMap = db.createTreeMap("reset").keySerializer(BTreeKeySerializer.STRING).valueSerializer(Serializer.LONG).makeOrGet();
        resetMap.put("000000000000", lastReset.toEpochMilli());
        resetMap.put("000000000001", lastReset.toEpochMilli());

        // init service and check initial loaded stats
        statisticService = new BlockedDomainsStatisticService(dbPath.toString(), 3, databaseLoader, deviceService, filterListsService);
        statisticService.init();
        BlockedDomainsStats blockedStats = statisticService.getStatsByDeviceId("device:000000000000");
        Assert.assertEquals(lastReset, blockedStats.getLastReset());
        Assert.assertNotNull(blockedStats.getStats());
        Assert.assertEquals(1, blockedStats.getStats().size());
        Assert.assertNotNull(blockedStats.getStats().get(Category.ADS));
        Assert.assertEquals(3, blockedStats.getStats().get(Category.ADS).size());

        blockedStats = statisticService.getStatsByDeviceId("device:000000000001");
        Assert.assertEquals(lastReset, blockedStats.getLastReset());
        Assert.assertNotNull(blockedStats.getStats());
        Assert.assertEquals(1, blockedStats.getStats().size());
        Assert.assertNotNull(blockedStats.getStats().get(Category.TRACKERS));
        Assert.assertEquals(3, blockedStats.getStats().get(Category.TRACKERS).size());

        // reset stats and check they have been cleared
        statisticService.resetStats();
        blockedStats = statisticService.getStatsByDeviceId("device:000000000000");
        Assert.assertTrue(Duration.between(blockedStats.getLastReset(), Instant.now()).toMinutes() < 1);
        Assert.assertNotNull(blockedStats.getStats());
        Assert.assertEquals(0, blockedStats.getStats().size());
        Assert.assertTrue(Duration.between(blockedStats.getLastReset(), Instant.ofEpochMilli(resetMap.get("000000000000"))).toMinutes() < 1);
        Assert.assertEquals(0, persistentStatsDeviceA.size());

        blockedStats = statisticService.getStatsByDeviceId("device:000000000001");
        Assert.assertTrue(Duration.between(blockedStats.getLastReset(), Instant.now()).toMinutes() < 1);
        Assert.assertNotNull(blockedStats.getStats());
        Assert.assertEquals(0, blockedStats.getStats().size());
        Assert.assertTrue(Duration.between(blockedStats.getLastReset(), Instant.ofEpochMilli(resetMap.get("000000000001"))).toMinutes() < 1);
        Assert.assertEquals(0, persistentStatsDeviceA.size());

        Set<String> dbNames = db.getAll().keySet();
        Assert.assertFalse(dbNames.contains("000000000000:ADS"));
        Assert.assertFalse(dbNames.contains("000000000001:TRACKERS"));
    }

    @Test
    public void testStripLeadingDot() {
        statisticService = new BlockedDomainsStatisticService(dbPath.toString(), 3, databaseLoader, deviceService, filterListsService);
        statisticService.init();
        statisticService.countBlockedDomain(new BlockedDomainLogEntry(DEVICE_ID, ".blocked.com", 0));
        statisticService.countBlockedDomain(new BlockedDomainLogEntry(DEVICE_ID, "blocked.com", 0));
        Assert.assertNotNull(statisticService.getStatsByDeviceId(DEVICE_ID).getStats());
        Assert.assertNotNull(statisticService.getStatsByDeviceId(DEVICE_ID).getStats().get(Category.ADS));
        Assert.assertEquals(1, statisticService.getStatsByDeviceId(DEVICE_ID).getStats().get(Category.ADS).size());
        Assert.assertEquals("blocked.com", statisticService.getStatsByDeviceId(DEVICE_ID).getStats().get(Category.ADS).get(0).getDomain());
        Assert.assertEquals(2, statisticService.getStatsByDeviceId(DEVICE_ID).getStats().get(Category.ADS).get(0).getCount());
    }

    @Test
    public void testOrphanedStatsAreDeleted() {
        createOpenDb();
        BTreeMap<String, Integer> persistentStats = db.createTreeMap("000000000001:ADS").keySerializer(BTreeKeySerializer.STRING).valueSerializer(Serializer.INTEGER).makeOrGet();
        persistentStats.put("blocked10", 10);
        persistentStats.put("blocked5", 5);
        persistentStats.put("blocked1", 1);

        statisticService = new BlockedDomainsStatisticService(dbPath.toString(), 2, databaseLoader, deviceService, filterListsService);
        statisticService.init();

        Assert.assertFalse(db.getAll().keySet().contains("000000000001:ADS"));
        Assert.assertNotNull(statisticService.getStatsByDeviceId("device:000000000001"));
        Assert.assertEquals(Collections.emptyMap(), statisticService.getStatsByDeviceId("device:000000000001").getStats());
    }

    @Test
    public void testTotalStats() {
        statisticService = new BlockedDomainsStatisticService(dbPath.toString(), 3, databaseLoader, deviceService, filterListsService);
        statisticService.init();
        countBlockedDomain("device:000000000000", "a01.com", 0, 1);
        countBlockedDomain("device:000000000000", "b02.com", 0, 2);
        countBlockedDomain("device:000000000000", "c03.com", 0, 3);
        countBlockedDomain("device:000000000000", "d04.com", 0, 4);
        countBlockedDomain("device:000000000000", "a11.com", 1, 1);
        countBlockedDomain("device:000000000000", "b12.com", 1, 2);
        countBlockedDomain("device:000000000000", "c13.com", 1, 3);
        countBlockedDomain("device:000000000000", "d14.com", 1, 4);
        countBlockedDomain("device:000000000001", "b02.com", 0, 2);
        countBlockedDomain("device:000000000001", "c03.com", 0, 3);
        countBlockedDomain("device:000000000001", "d04.com", 0, 4);
        countBlockedDomain("device:000000000001", "e05.com", 0, 5);
        countBlockedDomain("device:000000000001", "b22.com", 2, 2);
        countBlockedDomain("device:000000000001", "c23.com", 2, 3);
        countBlockedDomain("device:000000000001", "d24.com", 2, 4);
        countBlockedDomain("device:000000000001", "e25.com", 2, 5);

        BlockedDomainsStats stats = statisticService.getStats();
        Assert.assertNotNull(stats);
        Assert.assertNotNull(stats.getStats());
        Assert.assertEquals(3, stats.getStats().size());

        Assert.assertNotNull(stats.getStats().get(Category.ADS));
        Assert.assertEquals(5, stats.getStats().get(Category.ADS).size());
        assertEntry("d04.com", 8, stats.getStats().get(Category.ADS).get(0));
        assertEntry("c03.com", 6, stats.getStats().get(Category.ADS).get(1));
        assertEntry("e05.com", 5, stats.getStats().get(Category.ADS).get(2));
        assertEntry("b02.com", 4, stats.getStats().get(Category.ADS).get(3));
        assertEntry("a01.com", 1, stats.getStats().get(Category.ADS).get(4));

        Assert.assertNotNull(stats.getStats().get(Category.CUSTOM));
        Assert.assertEquals(4, stats.getStats().get(Category.CUSTOM).size());
        assertEntry("d14.com", 4, stats.getStats().get(Category.CUSTOM).get(0));
        assertEntry("c13.com", 3, stats.getStats().get(Category.CUSTOM).get(1));
        assertEntry("b12.com", 2, stats.getStats().get(Category.CUSTOM).get(2));
        assertEntry("a11.com", 1, stats.getStats().get(Category.CUSTOM).get(3));

        Assert.assertNotNull(stats.getStats().get(Category.PARENTAL_CONTROL));
        Assert.assertEquals(4, stats.getStats().get(Category.PARENTAL_CONTROL).size());
        assertEntry("e25.com", 5, stats.getStats().get(Category.PARENTAL_CONTROL).get(0));
        assertEntry("d24.com", 4, stats.getStats().get(Category.PARENTAL_CONTROL).get(1));
        assertEntry("c23.com", 3, stats.getStats().get(Category.PARENTAL_CONTROL).get(2));
        assertEntry("b22.com", 2, stats.getStats().get(Category.PARENTAL_CONTROL).get(3));
    }

    @Test
    public void testInitWithCorruptDatabase() throws IOException {
        createOpenDb();
        BTreeMap<String, Integer> persistentStatsDeviceA = db.createTreeMap("000000000000:ADS").keySerializer(BTreeKeySerializer.STRING).valueSerializer(Serializer.INTEGER).makeOrGet();
        BTreeMap<String, Integer> persistentStatsDeviceB = db.createTreeMap("000000000001:TRACKERS").keySerializer(BTreeKeySerializer.STRING).valueSerializer(Serializer.INTEGER).makeOrGet();
        persistentStatsDeviceA.put("blocked10", 10);
        persistentStatsDeviceA.put("blocked5", 5);
        persistentStatsDeviceA.put("blocked1", 1);
        persistentStatsDeviceB.putAll(persistentStatsDeviceA);
        db.close();
        db = null;

        byte[] rawDbContent = Files.readAllBytes(dbPath);
        try (OutputStream out = Files.newOutputStream(dbPath)) {
            out.write(rawDbContent, 0, rawDbContent.length - 5);
        }

        // need to replace mock to ensure db is actually reloaded
        Mockito.when(databaseLoader.createOrOpen(dbPath.toString())).then(im -> {
            createOpenDb();
            return db;
        });

        statisticService = new BlockedDomainsStatisticService(dbPath.toString(), 2, databaseLoader, deviceService, filterListsService);
        statisticService.init();
        Assert.assertTrue(Files.exists(dbPath));
        Assert.assertFalse(db.exists("000000000000:ADS"));

        countBlockedDomain("device:000000000000", "a01.com", 0, 1);
        statisticService.updatePersistentStats();
        Assert.assertTrue(db.exists("000000000000:ADS"));
        BTreeMap<String, Integer> persistent = db.createTreeMap("000000000000:ADS").keySerializer(BTreeKeySerializer.STRING).valueSerializer(Serializer.INTEGER).makeOrGet();
        Assert.assertEquals(Integer.valueOf(1), persistent.get("a01.com"));
    }

    private ParentalControlFilterMetaData mockFilterData(int id, Category category) {
        return new ParentalControlFilterMetaData(id, null, null, category, null, null, null, null, null, false, false, null, null, null);
    }

    private void createOpenDb() {
        db = DBMaker
            .newFileDB(dbPath.toFile())
            .cacheDisable()
            .closeOnJvmShutdown()
            .transactionDisable()
            .make();
    }

    private void countBlockedDomain(String deviceId, String domain, Integer listId, int count) {
        for (int i = 0; i < count; ++i) {
            statisticService.countBlockedDomain(new BlockedDomainLogEntry(deviceId, domain, listId));
        }
    }

    private void assertEntry(String domain, int count, Entry entry) {
        Assert.assertNotNull(entry);
        Assert.assertEquals(domain, entry.getDomain());
        Assert.assertEquals(count, entry.getCount());
    }
}
