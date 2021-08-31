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
package org.eblocker.server.common.status;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Writes a status report to an HTML file during startup
 */
public class StartupStatusReporter {
    private static final Logger log = LoggerFactory.getLogger(StartupStatusReporter.class);

    private List<Message> messages = Collections.synchronizedList(new ArrayList<Message>());
    private final String writeFileCommand;
    private final ScriptRunner scriptRunner;
    private final NetworkInterfaceWrapper networkInterface;
    private final String version;
    private final Clock clock;

    @Inject
    public StartupStatusReporter(@Named("project.version") String version,
                                 @Named("write.startup.status.file.command") String writeFileCommand,
                                 ScriptRunner scriptRunner,
                                 NetworkInterfaceWrapper networkInterface,
                                 @Named("localClock") Clock clock) {

        this.version = version;
        this.writeFileCommand = writeFileCommand;
        this.scriptRunner = scriptRunner;
        this.networkInterface = networkInterface;
        this.clock = clock;
    }

    public void consoleStarted() {
        messages.add(new Message(MessageStatus.INFO, String.format("eBlocker console started (version %s)", version)));
    }

    public void portConnected(String protocol, String url) {
        boolean up = networkInterface.isUp();
        String msg = protocol + " port connected at %s";
        messages.add(new URLMessage(up ? MessageStatus.OK : MessageStatus.WARNING, msg, url));
    }

    public void startupCompleted() {
        writeStatusFile();
    }

    public void startupCompletedWithWarning(List<Exception> warnings) {
        warnings.forEach(w -> messages.add(new ErrorMessage(w)));
        writeStatusFile();
    }

    public void startupFailed(Throwable t) {
        messages.add(new ErrorMessage(t));
        writeStatusFile();
    }

    private void writeStatusFile() {
        String date = ZonedDateTime.now(clock).format(DateTimeFormatter.RFC_1123_DATE_TIME);
        messages.add(new Message(MessageStatus.INFO, String.format("Report created at %s", date)));
        try {
            String template = ResourceHandler.load(new SimpleResource("classpath:html-inlays/startup-status-report.html"));
            File outFile = File.createTempFile("StartupStatusReporter", "html");
            outFile.deleteOnExit();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile))) {
                String content = messages.stream()
                        .map(Message::render)
                        .collect(Collectors.joining("\n    "));
                String result = template.replace("@MESSAGES@", content);
                writer.append(result);
            }
            scriptRunner.runScript(writeFileCommand, outFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("Could not log startup status", e);
        }
    }

    public void testNetworkInterface() {
        boolean up = networkInterface.isUp();

        messages.add(new Message(up ? MessageStatus.INFO : MessageStatus.WARNING,
                "Network interface is " + (up ? "up" : "DOWN")));
    }

}
