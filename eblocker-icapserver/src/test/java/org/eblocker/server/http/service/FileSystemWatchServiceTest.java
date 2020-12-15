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
package org.eblocker.server.http.service;

import org.eblocker.server.common.util.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class FileSystemWatchServiceTest {

    private Path directory;
    private FileSystemWatchService watchService;
    private ExecutorService executor;

    @Before
    public void setUp() throws IOException {
        directory = Files.createTempDirectory("fs-watch-unit-test");
        executor = Executors.newSingleThreadExecutor();
        watchService = new FileSystemWatchService(executor);

    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(directory);
        executor.shutdownNow();
    }

    @Test(timeout = 15000)
    public void testFileCreation() throws IOException, InterruptedException {
        Semaphore semaphore = new Semaphore(1);
        semaphore.acquire();

        Path file = directory.resolve("test.txt");

        registerWatcher(file, semaphore);

        Files.createFile(file);

        semaphore.acquire();
    }

    @Test(timeout = 15000)
    public void testFileUpdate() throws IOException, InterruptedException {
        Path file = createFileInThePast("test.txt");

        Semaphore semaphore = new Semaphore(1);
        semaphore.acquire();

        registerWatcher(file, semaphore);

        Files.write(file, "Hello World".getBytes());

        semaphore.acquire();
    }

    @Test(timeout = 15000)
    public void testMultipleFileUpdates() throws IOException, InterruptedException {
        Path[] files = new Path[]{
                createFileInThePast("test0.txt"),
                createFileInThePast("test1.txt")
        };

        Semaphore semaphore = new Semaphore(2);
        semaphore.acquire(2);

        registerWatcher(files[0], semaphore);
        registerWatcher(files[1], semaphore);

        Files.write(files[0], new byte[0]);
        Files.write(files[1], new byte[0]);

        semaphore.acquire(2);
    }

    @Test(timeout = 5000)
    public void testFileDelete() throws IOException, InterruptedException {
        Path file = directory.resolve("test.txt");
        Files.createFile(file);

        Semaphore semaphore = new Semaphore(1);
        semaphore.acquire();

        registerWatcher(file, semaphore);

        Files.delete(file);

        Assert.assertFalse(semaphore.tryAcquire(2500, TimeUnit.MILLISECONDS));
    }

    private void registerWatcher(Path file, Semaphore semaphore) throws IOException {
        WatchEvent.Modifier[] modifier = getOSSpecificWatchEventModifier();
        if (modifier == null) {
            watchService.watch(file, p -> {
                Assert.assertEquals(file, p);
                semaphore.release();
            });
        } else {
            watchService.watch(file, p -> {
                Assert.assertEquals(file, p);
                semaphore.release();
            }, modifier);
        }

    }

    /**
     * Set file creation time to 61 seconds in the past.
     * This is needed for MacOS, because a file modification would not
     * trigger an event, if it occurs in the same time minute as the creation.
     */
    private Path createFileInThePast(String fileName) throws IOException {
        Path file = directory.resolve(fileName);
        Files.createFile(file);

        // Set file creation time to 61 seconds in the past.
        // This is needed for MacOS, because a file modification would not
        // trigger an event, if it occurs in the same time minute as the creation.
        BasicFileAttributeView attributes = Files.getFileAttributeView(file, BasicFileAttributeView.class);
        FileTime time = FileTime.fromMillis(System.currentTimeMillis() - 1000L * 61);
        attributes.setTimes(time, time, time);

        return file;
    }

    /**
     * Set OS specific watch modifier for MacOS.
     * The default on Mac OS takes 10 seconds to notify any file changes.
     * The modifier com.sun.nio.file.SensitivityWatchEventModifier.HIGH reduces this to 2 seconds.
     * <p>
     * To avoid a hard dependency on a com.sun package, use reflection to obtain this modifier.
     */
    private WatchEvent.Modifier[] getOSSpecificWatchEventModifier() {
        if (!System.getProperty("os.name").startsWith("Mac OS")) {
            return null;
        }
        try {
            Class<?> c = Class.forName("com.sun.nio.file.SensitivityWatchEventModifier");
            Field f = c.getField("HIGH");
            return new WatchEvent.Modifier[]{ (WatchEvent.Modifier) f.get(c) };
        } catch (Exception e) {
            return null;
        }
    }

}
