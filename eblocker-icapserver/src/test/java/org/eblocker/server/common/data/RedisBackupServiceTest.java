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
package org.eblocker.server.common.data;

import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.util.FileUtils;
import org.eblocker.server.common.system.ScriptRunner;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import javax.xml.ws.Holder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RedisBackupServiceTest {

    private static final long PERIOD = 60;
    private static final int MAX_BACKUPS = 3;
    private static final int TIMEOUT = 100;

    private EventLogger eventLogger;
    private ScheduledExecutorService scheduledExecutorService;
    private ScriptRunner scriptRunner;
    private String snapshotDirectory;
    private Path snapshotPath;

    @Before
    public void setup() throws IOException {
        eventLogger = Mockito.mock(EventLogger.class);
        scheduledExecutorService = Mockito.mock(ScheduledExecutorService.class);
        scriptRunner = Mockito.mock(ScriptRunner.class);

        snapshotDirectory = Files.createTempDirectory("redis-backup-unit-test").toString();
        snapshotPath = Files.createFile(Paths.get(snapshotDirectory + "/dump.rdb"));
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(Paths.get(snapshotDirectory));
    }

    @Test
    public void testCheckRedisOk() throws RedisBackupService.RedisServiceException, IOException, InterruptedException {
        Mockito.when(scriptRunner.runScript("redis-service", "status")).thenReturn(0);

        RedisBackupService service = new RedisBackupService(PERIOD, MAX_BACKUPS, TIMEOUT, snapshotDirectory.toString(), eventLogger, scheduledExecutorService, scriptRunner);
        Assert.assertFalse(service.check());

        Mockito.verify(scriptRunner).runScript("redis-service", "status");
        Mockito.verify(scheduledExecutorService).scheduleAtFixedRate(Mockito.any(Runnable.class), Mockito.eq(PERIOD), Mockito.eq(PERIOD), Mockito.eq(TimeUnit.SECONDS));
    }

    @Test
    public void testBackup() throws IOException, InterruptedException, RedisBackupService.RedisServiceException {
        Mockito.when(scriptRunner.runScript("redis-service", "status")).thenReturn(0);

        InOrder scriptRunnerInOrder = Mockito.inOrder(scriptRunner);

        RedisBackupService service = new RedisBackupService(PERIOD, MAX_BACKUPS, TIMEOUT, snapshotDirectory.toString(), eventLogger, scheduledExecutorService, scriptRunner);
        service.check();

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(scheduledExecutorService).scheduleAtFixedRate(captor.capture(), Mockito.anyLong(), Mockito.anyLong(), Mockito.any(TimeUnit.class));
        Runnable backup = captor.getValue();

        Instant now = Instant.now();
        Files.setLastModifiedTime(snapshotPath, FileTime.from(now));
        backup.run();
        scriptRunnerInOrder.verify(scriptRunner).runScript("redis-backup", "backup", "dump.rdb.0");

        backup.run();
        scriptRunnerInOrder.verifyNoMoreInteractions();

        Files.setLastModifiedTime(snapshotPath, FileTime.from(now.plus(1, ChronoUnit.MINUTES)));
        backup.run();
        scriptRunnerInOrder.verify(scriptRunner).runScript("redis-backup", "backup", "dump.rdb.1");
    }

    @Test
    public void testMaxBackups() throws RedisBackupService.RedisServiceException, IOException, InterruptedException {
        Mockito.when(scriptRunner.runScript("redis-service", "status")).thenReturn(0);
        Mockito.when(scriptRunner.runScript(Mockito.eq("redis-backup"), Mockito.eq("backup"), Mockito.anyString())).then(im -> {
            try {
                Files.createFile(Paths.get(snapshotDirectory + "/" + im.getArgument(2)));
                return 0;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        createFile(Paths.get(snapshotDirectory + "/dump.rdb.2"), Instant.now().minus(1, ChronoUnit.MINUTES));
        createFile(Paths.get(snapshotDirectory + "/dump.rdb.1"), Instant.now().minus(2, ChronoUnit.MINUTES));
        createFile(Paths.get(snapshotDirectory + "/dump.rdb.0"), Instant.now().minus(3, ChronoUnit.MINUTES));

        InOrder scriptRunnerInOrder = Mockito.inOrder(scriptRunner);

        RedisBackupService service = new RedisBackupService(PERIOD, MAX_BACKUPS, TIMEOUT, snapshotDirectory.toString(), eventLogger, scheduledExecutorService, scriptRunner);
        service.check();

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(scheduledExecutorService).scheduleAtFixedRate(captor.capture(), Mockito.anyLong(), Mockito.anyLong(), Mockito.any(TimeUnit.class));
        Runnable backup = captor.getValue();

        Files.setLastModifiedTime(snapshotPath, FileTime.from(Instant.now().plus(1, ChronoUnit.MINUTES)));
        backup.run();
        scriptRunnerInOrder.verify(scriptRunner).runScript("redis-backup", "backup", "dump.rdb.3");
        scriptRunnerInOrder.verify(scriptRunner).runScript("redis-backup", "delete", "dump.rdb.0");
    }

    @Test
    public void testRestore() throws IOException, InterruptedException, RedisBackupService.RedisServiceException {
        Holder<Boolean> firstStatusCall = new Holder<>(true);
        Mockito.when(scriptRunner.runScript("redis-service", "status")).thenAnswer(im -> {
            if (firstStatusCall.value) {
                firstStatusCall.value = false;
                return 1;
            }
            return 0;
        });
        Mockito.when(scriptRunner.runScript("redis-check", "dump.rdb")).thenReturn(1);
        Mockito.when(scriptRunner.runScript("redis-check", "dump.rdb.0")).thenReturn(0);
        Mockito.when(scriptRunner.runScript("redis-check", "dump.rdb.1")).thenReturn(1);

        createFile(Paths.get(snapshotDirectory + "/dump.rdb.0"), Instant.now().minus(3, ChronoUnit.MINUTES));
        createFile(Paths.get(snapshotDirectory + "/dump.rdb.1"), Instant.now().minus(2, ChronoUnit.MINUTES));

        InOrder scriptRunnerInOrder = Mockito.inOrder(scriptRunner);

        RedisBackupService service = new RedisBackupService(PERIOD, MAX_BACKUPS, TIMEOUT, snapshotDirectory.toString(), eventLogger, scheduledExecutorService, scriptRunner);
        Assert.assertTrue(service.check());

        scriptRunnerInOrder.verify(scriptRunner).runScript("redis-service", "status");
        scriptRunnerInOrder.verify(scriptRunner).runScript("redis-check", "dump.rdb");
        scriptRunnerInOrder.verify(scriptRunner).runScript("redis-check", "dump.rdb.1");
        scriptRunnerInOrder.verify(scriptRunner).runScript("redis-check", "dump.rdb.0");
        scriptRunnerInOrder.verify(scriptRunner).runScript("redis-backup", "restore", "dump.rdb.0");
        scriptRunnerInOrder.verify(scriptRunner).runScript("redis-service", "start");
        scriptRunnerInOrder.verify(scriptRunner).runScript("redis-service", "status");
    }

    @Test
    public void testRestoreFailAllCorrupt() throws RedisBackupService.RedisServiceException, IOException, InterruptedException {
        Mockito.when(scriptRunner.runScript("redis-service", "status")).thenReturn(1);
        Mockito.when(scriptRunner.runScript("redis-check", "dump.rdb")).thenReturn(1);
        Mockito.when(scriptRunner.runScript("redis-check", "dump.rdb.0")).thenReturn(1);
        Mockito.when(scriptRunner.runScript("redis-check", "dump.rdb.1")).thenReturn(1);

        createFile(Paths.get(snapshotDirectory + "/dump.rdb.0"), Instant.now().minus(3, ChronoUnit.MINUTES));
        createFile(Paths.get(snapshotDirectory + "/dump.rdb.1"), Instant.now().minus(2, ChronoUnit.MINUTES));

        RedisBackupService service = new RedisBackupService(PERIOD, MAX_BACKUPS, TIMEOUT, snapshotDirectory.toString(), eventLogger, scheduledExecutorService, scriptRunner);
        try {
            service.check();
            Assert.fail("expected restore to fail");
        } catch (RedisBackupService.RedisServiceException e) {
            Assert.assertTrue(e.getMessage().contains("restoring"));
        }
    }

    @Test
    public void testRestoreFailServiceFails() throws RedisBackupService.RedisServiceException, IOException, InterruptedException {
        Mockito.when(scriptRunner.runScript("redis-service", "status")).thenReturn(1);
        Mockito.when(scriptRunner.runScript("redis-check", "dump.rdb")).thenReturn(0);
        Mockito.when(scriptRunner.runScript("redis-check", "dump.rdb.0")).thenReturn(0);
        Mockito.when(scriptRunner.runScript("redis-check", "dump.rdb.1")).thenReturn(0);

        createFile(Paths.get(snapshotDirectory + "/dump.rdb.0"), Instant.now().minus(3, ChronoUnit.MINUTES));
        createFile(Paths.get(snapshotDirectory + "/dump.rdb.1"), Instant.now().minus(2, ChronoUnit.MINUTES));

        RedisBackupService service = new RedisBackupService(PERIOD, MAX_BACKUPS, TIMEOUT, snapshotDirectory.toString(), eventLogger, scheduledExecutorService, scriptRunner);
        try {
            service.check();
            Assert.fail("expected restore to fail");
        } catch (RedisBackupService.RedisServiceException e) {
            Assert.assertTrue(e.getMessage().contains("service state"));
        }
    }

    @Test
    public void testNoRestore() throws IOException, InterruptedException, RedisBackupService.RedisServiceException {
        Mockito.when(scriptRunner.runScript("redis-service", "status")).thenReturn(1);
        Mockito.when(scriptRunner.runScript("redis-check", "dump.rdb")).thenReturn(0);

        RedisBackupService service = new RedisBackupService(PERIOD, MAX_BACKUPS, TIMEOUT, snapshotDirectory.toString(), eventLogger, scheduledExecutorService, scriptRunner);
        try {
            service.check();
            Assert.fail("expected to fail");
        } catch (RedisBackupService.RedisServiceException e) {
            Assert.assertTrue(e.getMessage().contains("service state"));
        }
    }

    private void createFile(Path path, Instant lastModifiedTime) throws IOException {
        Files.createFile(path);
        Files.setLastModifiedTime(path, FileTime.from(lastModifiedTime));
    }
}
