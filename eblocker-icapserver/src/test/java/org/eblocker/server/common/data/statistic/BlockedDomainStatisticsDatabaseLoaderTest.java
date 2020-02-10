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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class BlockedDomainStatisticsDatabaseLoaderTest {

    private Path dbPath;
    private BlockedDomainStatisticsDatabaseLoader loader;

    @Before
    public void setUp() throws IOException {
        dbPath = Files.createTempFile(BlockedDomainsStatisticServiceTest.class.getName(), ".db");
        Files.delete(dbPath);
        loader = new BlockedDomainStatisticsDatabaseLoader();
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(dbPath);
    }

    @Test
    public void testCreate() {
        DB db = loader.createOrOpen(dbPath.toString());
        Assert.assertNotNull(db);
        Assert.assertFalse(db.isClosed());

        db.createHashMap("test").keySerializer(Serializer.STRING_ASCII).valueSerializer(Serializer.STRING_ASCII).makeOrGet().put("unit", "test");
        Assert.assertEquals("test", db.getHashMap("test").get("unit"));
    }

    @Test
    public void testOpen() {
        DB db = DBMaker.newFileDB(dbPath.toFile()).transactionDisable().make();
        db.createHashMap("test").keySerializer(Serializer.STRING_ASCII).valueSerializer(Serializer.STRING_ASCII).makeOrGet().put("unit", "test");
        db.close();

        db = loader.createOrOpen(dbPath.toString());
        Assert.assertNotNull(db);
        Assert.assertFalse(db.isClosed());
        Assert.assertEquals("test", db.getHashMap("test").get("unit"));
    }

    @Test
    public void testCorrupted() throws IOException {
        DB db = DBMaker.newFileDB(dbPath.toFile()).transactionDisable().make();
        db.createHashMap("test").keySerializer(Serializer.STRING_ASCII).valueSerializer(Serializer.STRING_ASCII).makeOrGet().put("unit", "test");
        db.close();

        try (FileChannel channel = FileChannel.open(dbPath, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.allocate(32);
            channel.write(buffer, 0);
        }

        db = loader.createOrOpen(dbPath.toString());
        Assert.assertNotNull(db);
        Assert.assertFalse(db.isClosed());

        db.createHashMap("test").keySerializer(Serializer.STRING_ASCII).valueSerializer(Serializer.STRING_ASCII).makeOrGet().put("unit2", "test2");
        Assert.assertNull(db.getHashMap("test").get("unit"));
        Assert.assertEquals("test2", db.getHashMap("test").get("unit2"));
    }

    @Test
    public void testEmpty() throws IOException {
        Files.createFile(dbPath);

        DB db = loader.createOrOpen(dbPath.toString());
        Assert.assertNotNull(db);
        Assert.assertFalse(db.isClosed());
        db.createHashMap("test").keySerializer(Serializer.STRING_ASCII).valueSerializer(Serializer.STRING_ASCII).makeOrGet().put("unit", "test");
        Assert.assertEquals("test", db.getHashMap("test").get("unit"));
    }

    @Test
    public void testNoMapDb() throws IOException {
        try (OutputStream out = Files.newOutputStream(dbPath)) {
            for (int i = 0; i < 1024; ++i) {
                out.write(i % 256);
            }
        }

        DB db = loader.createOrOpen(dbPath.toString());
        Assert.assertNotNull(db);
        Assert.assertFalse(db.isClosed());
        db.createHashMap("test").keySerializer(Serializer.STRING_ASCII).valueSerializer(Serializer.STRING_ASCII).makeOrGet().put("unit", "test");
        Assert.assertEquals("test", db.getHashMap("test").get("unit"));
    }

    @Test
    public void testIoError() {
        Assert.assertNull(loader.createOrOpen("/tmp/non-existing-path" + Math.random() + "/" + Math.random()));
    }
}
