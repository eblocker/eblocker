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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.data.events.Events;
import org.eblocker.server.common.system.ScriptRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public class RedisBackupService {
    private static final Logger log = LoggerFactory.getLogger(RedisBackupService.class);

    private static final Pattern BACKUP_FILE_PATTERN = Pattern.compile("dump.rdb.(\\d+)");
    private static final Pattern TEMPORARY_SNAPSHOT_PATTERN = Pattern.compile("temp-\\d+.rdb");

    private final long period;
    private final int maximumNumberOfBackups;
    private final int redisStartTimeout;
    private final String snapshotDirectory;
    private final EventLogger eventLogger;
    private final ScriptRunner scriptRunner;
    private final ScheduledExecutorService executorService;

    private final Path snapshotPath;

    private long lastBackup;
    private int nextId;

    @Inject
    public RedisBackupService(@Named("redis.backup.period") long period,
                              @Named("redis.backup.max") int maximumNumberOfBackups,
                              @Named("redis.backup.start.timeout") int redisStartTimeout,
                              @Named("redis.snapshot.directory") String snapshotDirectory,
                              EventLogger eventLogger,
                              @Named("lowPrioScheduledExecutor") ScheduledExecutorService executorService,
                              ScriptRunner scriptRunner) throws RedisServiceException {
        this.period = period;
        this.maximumNumberOfBackups = maximumNumberOfBackups;
        this.redisStartTimeout = redisStartTimeout;
        this.snapshotDirectory = snapshotDirectory;

        this.eventLogger = eventLogger;
        this.executorService = executorService;
        this.scriptRunner = scriptRunner;

        this.snapshotPath = Paths.get(snapshotDirectory + "/dump.rdb");

        List<Path> backups = findBackups(false);
        nextId = initNextId(backups);
        lastBackup = initLastBackup(backups);
    }

    /**
     * Checks the current state of redis-database. In case of errors restoring the database from backups is attempted.
     * If redis is running it will also schedule a periodic backup task.
     *
     * @Returns restoring from backup has been necessary
     */
    public boolean check() throws RedisServiceException {
        if (isRedisRunning()) {
            log.debug("redis up and running");
            scheduleBackup();
            return false;
        }

        log.warn("redis not running - checking dump");
        if (checkRedisDump(snapshotPath)) {
            log.warn("redis not running but dump is fine - can not be repaired automatically");
            throw new RedisServiceException("unknown service state");
        }

        log.warn("redis database is corrupt, trying to restore from backup");
        if (!restore()) {
            throw new RedisServiceException("restoring backups failed");
        }

        restartDependentServices();
        scheduleBackup();
        return true;
    }

    private void scheduleBackup() {
        executorService.scheduleAtFixedRate(this::backup, period, period, TimeUnit.SECONDS);
    }

    private void backup() {
        try {
            long lastModification = Files.getLastModifiedTime(snapshotPath).toMillis();
            if (lastModification > lastBackup) {
                log.debug("found modified redis snapshot, doing backup {}", nextId);
                redisBackupBackup(nextId);
                ++nextId;
                lastBackup = lastModification;

                cleanUp();
            } else {
                log.debug("no modification of snapshot since last backup found.");
            }
        } catch (IOException | RedisServiceException e) {
            log.error("failed to backup redis snapshot: ", e);
            eventLogger.log(Events.redisBackupFailed());
        }
    }

    private void cleanUp() throws RedisServiceException {
        List<Path> backups = findBackups(false);
        for (int i = maximumNumberOfBackups; i < backups.size(); ++i) {
            redisBackupDelete(backups.get(i));
        }
    }

    private int initNextId(List<Path> backups) {
        if (backups.isEmpty()) {
            return 0;
        }

        Matcher matcher = BACKUP_FILE_PATTERN.matcher(backups.get(0).getFileName().toString());
        if (!matcher.find()) {
            log.error("backups include non-matching file name!");
            return 0;
        }

        return Integer.parseInt(matcher.group(1)) + 1;
    }

    private long initLastBackup(List<Path> backups) {
        if (backups.isEmpty()) {
            return 0;
        }

        try {
            return Files.getLastModifiedTime(backups.get(0)).toMillis();
        } catch (IOException e) {
            log.warn("failed to get modification time of latest backup", e);
            return 0;
        }
    }

    private boolean restore() throws RedisServiceException {
        log.warn("trying to restore latest backup or snapshot");

        List<Path> backups = findBackups(true);
        log.warn("found {} available backups", backups.size());
        for (Path backup : backups) {
            if (restore(backup)) {
                log.warn("successfully restored backup {}", backup);
                eventLogger.log(Events.redisBackupRestored());
                return true;
            }
        }

        log.warn("no backup could be restored");
        eventLogger.log(Events.redisBackupRestoreFailed());
        return false;
    }

    private boolean restore(Path backup) throws RedisServiceException {
        try {
            log.info("checking dump {} from {}", backup, Files.getLastModifiedTime(backup));
            if (!checkRedisDump(backup)) {
                log.warn("dump {} is corrupt", backup);
                return false;
            }

            log.info("trying to restore {}", backup);
            redisBackupRestore(backup);
            if (!startRedis()) {
                log.error("unexpectedly starting redis failed");
                return false;
            }

            // give redis some time to start
            Thread.sleep(redisStartTimeout);

            if (!isRedisRunning()) {
                log.warn("starting redis with backup failed!");
                return false;
            }

            return true;
        } catch (IOException e) {
            log.error("i/o error", e);
            throw new RedisServiceException("i/o error", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("interrupted while waiting for redis restart");
            throw new RedisServiceException("interrupted waiting for redis", e);
        }
    }

    /**
     * Find all available backups sorted descending by modification date. Optionally includes temporary snapshots generated by redis.
     */
    private List<Path> findBackups(boolean includeTemporaryRedisSnapshots) throws RedisServiceException {
        try (Stream<Path> files = Files.list(Paths.get(snapshotDirectory))) {
            return files
                    .filter(p -> fileNameMatches(p, BACKUP_FILE_PATTERN) || includeTemporaryRedisSnapshots && fileNameMatches(p, TEMPORARY_SNAPSHOT_PATTERN))
                    .sorted(Comparator.comparing(this::lastModification).reversed())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("failed to list backups", e);
            throw new RedisServiceException("failed to list backups", e);
        }
    }

    private FileTime lastModification(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean fileNameMatches(Path path, Pattern pattern) {
        return pattern.matcher(path.getFileName().toString()).matches();
    }

    private boolean isRedisRunning() throws RedisServiceException {
        return runScript("redis-service", "status");
    }

    private boolean startRedis() throws RedisServiceException {
        return runScript("redis-service", "start");
    }

    private void redisBackupBackup(int id) throws RedisServiceException {
        runScript("redis-backup", "backup", "dump.rdb." + id);
    }

    private void redisBackupRestore(Path backup) throws RedisServiceException {
        runScript("redis-backup", "restore", backup.getFileName().toString());
    }

    private void redisBackupDelete(Path backup) throws RedisServiceException {
        runScript("redis-backup", "delete", backup.getFileName().toString());
    }

    private boolean checkRedisDump(Path path) throws RedisServiceException {
        return runScript("redis-check", path.getFileName().toString());
    }

    private boolean restartDependentServices() throws RedisServiceException {
        return runScript("redis-dependent-services-restart");
    }

    private boolean runScript(String script, String... args) throws RedisServiceException {
        try {
            return scriptRunner.runScript(script, args) == 0;
        } catch (IOException e) {
            throw new RedisServiceException("unexpected error running " + script, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RedisServiceException("script runner interrupted " + script, e);
        }
    }

    @SuppressWarnings("serial")
    public class RedisServiceException extends Exception {
        private RedisServiceException(String message) {
            super(message);
        }

        private RedisServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
