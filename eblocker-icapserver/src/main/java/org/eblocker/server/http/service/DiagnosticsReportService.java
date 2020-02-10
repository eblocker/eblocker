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

import org.eblocker.server.http.security.JsonWebTokenHandler;
import org.eblocker.server.common.system.ScriptRunner;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ScheduledExecutorService;

@Singleton
public class DiagnosticsReportService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticsReportService.class);
    private static final Logger STATUS = LoggerFactory.getLogger("STATUS");

    public enum State { NOT_STARTED, PENDING, FINISHED, ERROR }

    private final Path diagnosticsReportFile;
    private final ScriptRunner scriptRunner;
    private final JsonWebTokenHandler tokenHandler;
    private final String reportCreateScript;
    private final ScheduledExecutorService executorService;

    private State state = State.NOT_STARTED;

    @Inject
    public DiagnosticsReportService(@Named("diagnostics.report.file") String diagnosticsReportFilePath,
                                    @Named("generateDiagnosticsReport.command") String reportCreateScript,
                                    @Named("lowPrioScheduledExecutor") ScheduledExecutorService executorService,
                                    ScriptRunner scriptRunner,
                                    JsonWebTokenHandler tokenHandler) {
        this.diagnosticsReportFile = Paths.get(diagnosticsReportFilePath);
        this.reportCreateScript = reportCreateScript;
        this.scriptRunner = scriptRunner;
        this.tokenHandler = tokenHandler;
        this.executorService = executorService;
    }

    public synchronized void startGeneration(String remoteAddress) throws IOException {
        if (state == State.PENDING) {
            log.info("Report generation already running, ignoring request");
            return;
        }

        state = State.PENDING;

        log.info("Creating automated diagnostics report...");
        Files.deleteIfExists(diagnosticsReportFile);
        executorService.execute(() -> {
            try {
                scriptRunner.runScript(reportCreateScript,
                    tokenHandler.generateSystemToken().getToken(),
                    remoteAddress);
                STATUS.info("Created diagnostics report!");
                state = State.FINISHED;
            } catch (InterruptedException e) {
                log.debug("Interrupted while creating automated diagnostics report file: ", e);
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                log.error("failed to create report", e);
                state = State.ERROR;
            }
        });
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
