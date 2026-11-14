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
import com.google.common.net.InternetDomainName;
import org.eblocker.server.common.blocker.ExternalDefinition;
import org.eblocker.server.common.blocker.Format;
import org.eblocker.server.common.blocker.Type;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.common.util.FileUtils;
import org.eblocker.server.http.security.JsonWebTokenHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URI;
import java.util.List;
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
    public final static String CUSTOM_LISTS_ENTRY = PREFIX + "custom-list-diagnostics.tsv";

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
    private final DataSource dataSource;

    private volatile State state = State.NOT_STARTED;

    @Inject
    public DiagnosticsReportService(@Named("diagnostics.report.file") String diagnosticsReportFile,
                                    @Named("diagnostics.report.command") String reportCreateScript,
                                    @Named("lowPrioScheduledExecutor") ScheduledExecutorService executorService,
                                    ScriptRunner scriptRunner,
                                    JsonWebTokenHandler tokenHandler,
                                    @Named("tmpDir") String tmpDir,
                                    @Named("diagnostics.logfile.system") String systemLog,
                                    EventService eventService,
                                    DataSource dataSource) {
        this.diagnosticsReportFile = Paths.get(tmpDir).resolve(diagnosticsReportFile);
        this.reportCreateScript = reportCreateScript;
        this.scriptRunner = scriptRunner;
        this.tokenHandler = tokenHandler;
        this.executorService = executorService;
        this.systemLog = Paths.get(systemLog);
        this.eventService = eventService;
        this.dataSource = dataSource;
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
                log.warn("Unexpected return code from report script: {}", result);
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
            String customListDiagnostics;
            try {
                customListDiagnostics = getCustomListDiagnostics();
            } catch (RuntimeException e) {
                log.warn("Could not generate custom list diagnostics", e);
                customListDiagnostics = "finding\tinspection_error\nLIST_DIAGNOSTICS_FAILED\t" +
                        e.getClass().getSimpleName() + "\n";
            }
            addString(CUSTOM_LISTS_ENTRY, customListDiagnostics, zipOut);
            zipOut.finish();
        }
    }

    private String getEventsLog() {
        return eventService.getEvents().stream()
                .map(event -> event.toString() + "\n")
                .collect(Collectors.joining());
    }

    private String getCustomListDiagnostics() {
        StringBuilder report = new StringBuilder("id\tname\ttype\tcategory\tformat\tenabled\tupdate_status\tupdate_error\tsource_host\tbytes\tlines\tblank_lines\tcandidate_rules\tsampled_rules\trecognized_sampled_rules\twildcard_only_lines\tfinding\tinspection_error\n");
        List<ExternalDefinition> definitions;
        try {
            definitions = dataSource.getAll(ExternalDefinition.class);
        } catch (RuntimeException e) {
            log.warn("Could not enumerate custom lists for diagnostics", e);
            return report.append("0\t\t\t\t\t\t\t\t\t-1\t0\t0\t0\t0\t0\t0\tLIST_ENUMERATION_FAILED\t")
                    .append(e.getClass().getSimpleName()).append('\n').toString();
        }
        if (definitions == null) {
            return report.append("0\t\t\t\t\t\t\t\t\t-1\t0\t0\t0\t0\t0\t0\tLIST_ENUMERATION_FAILED\tnull result\n").toString();
        }
        for (ExternalDefinition definition : definitions) {
            long bytes = -1;
            long lines = 0;
            long blanks = 0;
            long candidates = 0;
            long sampled = 0;
            long recognized = 0;
            long wildcards = 0;
            String inspectionError = "";
            try {
                Path path = Paths.get(definition.getFile());
                bytes = Files.size(path);
                try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.ISO_8859_1)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines++;
                        if (line.isBlank()) blanks++;
                        String rule = line.strip();
                        if (!rule.isEmpty() && !rule.startsWith("!") && !rule.startsWith("#") && !rule.startsWith("[")) {
                            candidates++;
                            if (definition.getType() == Type.DOMAIN && sampled < 1000) {
                                sampled++;
                                if (isDomainRule(definition.getFormat(), rule)) recognized++;
                            }
                        }
                        if (rule.equals("*") || rule.equals("*$document") || rule.equals("$document")) wildcards++;
                    }
                }
            } catch (IOException | RuntimeException e) {
                log.warn("Could not inspect custom list {} for diagnostics", definition.getId(), e);
                inspectionError = e.getClass().getSimpleName();
            }
            String sourceHost = "";
            if (definition.getUrl() != null) {
                try {
                    sourceHost = URI.create(definition.getUrl()).getHost();
                } catch (IllegalArgumentException e) {
                    sourceHost = "invalid URL";
                }
            }
            report.append(definition.getId()).append('\t')
                    .append(safe(definition.getName())).append('\t')
                    .append(definition.getType()).append('\t')
                    .append(definition.getCategory()).append('\t')
                    .append(definition.getFormat()).append('\t')
                    .append(definition.isEnabled()).append('\t')
                    .append(definition.getUpdateStatus()).append('\t')
                    .append(safe(definition.getUpdateError())).append('\t')
                    .append(safe(sourceHost)).append('\t')
                    .append(bytes).append('\t').append(lines).append('\t')
                    .append(blanks).append('\t').append(candidates).append('\t')
                    .append(sampled).append('\t').append(recognized).append('\t').append(wildcards).append('\t')
                    .append(inspectionError.isEmpty() ? (wildcards > 0 ? "UNIVERSAL_RULE" :
                            candidates == 0 ? "NO_RULES" :
                            sampled >= 10 && recognized * 2 < sampled ? "FORMAT_MISMATCH" :
                            definition.getUpdateError() != null ? "UPDATE_FAILED" : "NONE") : "INSPECTION_FAILED")
                    .append('\t').append(inspectionError).append('\n');
        }
        return report.toString();
    }

    private boolean isDomainRule(Format format, String rule) {
        if (format == Format.DOMAINS || format == Format.SQUID_ACL) {
            return InternetDomainName.isValid(rule.startsWith(".") ? rule.substring(1) : rule);
        }
        if (format == Format.ETC_HOSTS) {
            return rule.matches("[0-9.]+\\s+\\S+.*");
        }
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
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
