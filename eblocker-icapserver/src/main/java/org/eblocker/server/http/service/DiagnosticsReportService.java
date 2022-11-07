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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.netty.buffer.PooledByteBufAllocator;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.common.util.FileUtils;
import org.eblocker.server.http.security.JsonWebTokenHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Singleton
public class DiagnosticsReportService {
    public final static String PREFIX = "eblocker-diagnostics-report/";
    public final static String SYSTEM_LOG_ENTRY = PREFIX + "eblocker-system.log";
    public final static String EVENTS_LOG_ENTRY = PREFIX + "events.log";
    public final static String NETTY_POOL_ENTRY = PREFIX + "netty-pool.txt";

    private static final Logger log = LoggerFactory.getLogger(DiagnosticsReportService.class);
    private static final Logger STATUS = LoggerFactory.getLogger("STATUS");

    public enum State {NOT_STARTED, PENDING, FINISHED, ERROR}

    private final Path diagnosticsReportFile;
    private final ScriptRunner scriptRunner;
    private final JsonWebTokenHandler tokenHandler;
    private final String reportCreateScript;
    private final ScheduledExecutorService executorService;
    private final Path systemLog;
    private final EventService eventService;

    private volatile State state = State.NOT_STARTED;

    @Inject
    public DiagnosticsReportService(@Named("diagnostics.report.file") String diagnosticsReportFile,
                                    @Named("diagnostics.report.command") String reportCreateScript,
                                    @Named("lowPrioScheduledExecutor") ScheduledExecutorService executorService,
                                    ScriptRunner scriptRunner,
                                    JsonWebTokenHandler tokenHandler,
                                    @Named("tmpDir") String tmpDir,
                                    @Named("diagnostics.logfile.system") String systemLog,
                                    EventService eventService) {
        this.diagnosticsReportFile = Paths.get(tmpDir).resolve(diagnosticsReportFile);
        this.reportCreateScript = reportCreateScript;
        this.scriptRunner = scriptRunner;
        this.tokenHandler = tokenHandler;
        this.executorService = executorService;
        this.systemLog = Paths.get(systemLog);
        this.eventService = eventService;
    }

    public synchronized void startGeneration(String remoteAddress) throws IOException {
        if (state == State.PENDING) {
            log.info("Report generation already running, ignoring request");
            return;
        }

        state = State.PENDING;

        log.info("Creating automated diagnostics report...");
        Files.deleteIfExists(diagnosticsReportFile);
        Path reportOutputDir = Files.createTempDirectory(null);
        executorService.execute(() -> {
            try {
                runReportScript(reportOutputDir, remoteAddress);
                writeZipFile(reportOutputDir);
                STATUS.info("Created diagnostics report!");
                state = State.FINISHED;
            } catch (InterruptedException e) {
                log.debug("Interrupted while creating automated diagnostics report file: ", e);
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                log.error("failed to create report", e);
                state = State.ERROR;
            } finally {
                try {
                    FileUtils.deleteDirectory(reportOutputDir);
                } catch (IOException e) {
                    log.error("Could not clean up diagnostics report directory", e);
                }
            }
        });
    }

    public void runReportScript(Path reportOutputDir, String remoteAddress) throws InterruptedException {
        try {
            int result = scriptRunner.runScript(reportCreateScript,
                    reportOutputDir.toString(),
                    tokenHandler.generateSystemToken().getToken(),
                    remoteAddress);
            if (result != 0) {
                log.warn("Unexpected return code from report script: " + result);
            }
        } catch (IOException e) {
            log.error("failed to run report script", e);
            // continue, so at least the system log is in the diagnostics report
        }
    }

    private void writeZipFile(Path reportOutputDir) throws IOException {
        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(diagnosticsReportFile))) {
            addReportOutput(reportOutputDir, zipOut);
            addFile(SYSTEM_LOG_ENTRY, systemLog, zipOut);
            addString(EVENTS_LOG_ENTRY, getEventsLog(), zipOut);
            addString(NETTY_POOL_ENTRY, PooledByteBufAllocator.DEFAULT.dumpStats(), zipOut);
            zipOut.finish();
        }
    }

    private String getEventsLog() {
        return eventService.getEvents().stream()
                .map(event -> event.toString() + "\n")
                .collect(Collectors.joining());
    }

    private void addReportOutput(Path reportOutputDir, ZipOutputStream zipOut) throws IOException {
        // recursively add all files from reportOutputDir
        Files.walk(reportOutputDir).forEach(path -> {
            try {
                addEntry(path, reportOutputDir, zipOut);
            } catch (IOException e) {
                log.error("Could not add file '{}' to diagnostics report", path, e);
            }
        });
    }

    private void addEntry(Path path, Path root, ZipOutputStream zipOut) throws IOException {
        Path rel = root.relativize(path);
        String entryName = PREFIX + rel.toString();
        if (Files.isDirectory(path)) {
            addDirectory(entryName, zipOut);
        } else {
            addFile(entryName, path, zipOut);
        }
    }

    private void addFile(String entryName, Path path, ZipOutputStream zipOut) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zipOut.putNextEntry(entry);
        try (InputStream logIn = Files.newInputStream(path)) {
            logIn.transferTo(zipOut);
        } catch (IOException e) {
            String msg = "Could not read '" + path + "': " + e.toString();
            zipOut.write(msg.getBytes(StandardCharsets.UTF_8));
            log.error(msg);
        }
        zipOut.closeEntry();
    }
    private void addString(String entryName, String content, ZipOutputStream zipOut) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zipOut.putNextEntry(entry);
        if (content == null) {
            content = "null";
        }
        zipOut.write(content.getBytes(StandardCharsets.UTF_8));
        zipOut.closeEntry();
    }

    private void addDirectory(String entryName, ZipOutputStream zipOut) throws IOException {
        if (!entryName.endsWith("/")) {
            entryName += "/";
        }
        ZipEntry entry = new ZipEntry(entryName);
        zipOut.putNextEntry(entry);
        zipOut.closeEntry();
    }

    public synchronized State getStatus() {
        return state;
    }

    @SuppressWarnings("squid:S1168")
    public synchronized byte[] getReport() throws IOException {
        if (state != State.FINISHED) {
            return null;
        }

        return Files.readAllBytes(diagnosticsReportFile);
    }
}
