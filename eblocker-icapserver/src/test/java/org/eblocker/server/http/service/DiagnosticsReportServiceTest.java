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

import org.eblocker.server.common.data.events.Events;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.blocker.Category;
import org.eblocker.server.common.blocker.ExternalDefinition;
import org.eblocker.server.common.blocker.Format;
import org.eblocker.server.common.blocker.Type;
import org.eblocker.server.common.blocker.UpdateInterval;
import org.eblocker.server.common.blocker.UpdateStatus;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.common.util.FileUtils;
import org.eblocker.server.http.security.AppContext;
import org.eblocker.server.http.security.JsonWebToken;
import org.eblocker.server.http.security.JsonWebTokenHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DiagnosticsReportServiceTest {
    private static final String REPORT_FILE = "eblocker-diagnostics-report.zip";
    private static final String REPORT_CREATE_SCRIPT = "generate_report";
    private static final String SYSTEM_TOKEN = "---UNIT-TEST-TOKEN---";
    private static final String LOG_CONTENT = "This is the log file";

    private Path systemLogPath;
    private ScheduledExecutorService executorService;
    private ScriptRunner scriptRunner;
    private JsonWebTokenHandler tokenHandler;
    private DiagnosticsReportService reportService;
    private Path tmpDir;
    private EventService eventService;
    private DataSource dataSource;

    @Before
    public void setUp() throws IOException {
        systemLogPath = Files.createTempFile("eblocker-system", ".log");
        Files.writeString(systemLogPath, LOG_CONTENT, StandardCharsets.UTF_8);

        executorService = Mockito.mock(ScheduledExecutorService.class);

        scriptRunner = Mockito.mock(ScriptRunner.class);

        tokenHandler = Mockito.mock(JsonWebTokenHandler.class);
        Mockito.when(tokenHandler.generateSystemToken()).thenReturn(new JsonWebToken(SYSTEM_TOKEN, AppContext.SYSTEM, Long.MAX_VALUE, false));

        tmpDir = Files.createTempDirectory(null);

        eventService = Mockito.mock(EventService.class);
        Mockito.when(eventService.getEvents()).thenReturn(List.of(Events.serverIcapServerStarted()));

        dataSource = Mockito.mock(DataSource.class);
        Mockito.when(dataSource.getAll(ExternalDefinition.class)).thenReturn(List.of());
        reportService = new DiagnosticsReportService(REPORT_FILE, REPORT_CREATE_SCRIPT, executorService, scriptRunner, tokenHandler, tmpDir.toString(), systemLogPath.toString(), eventService, dataSource);
    }

    @Test
    public void customListDiagnosticsIdentifyUnsafeLinesWithoutExportingListContent() throws IOException {
        Path listFile = tmpDir.resolve("test-list.txt");
        Files.writeString(listFile, "! title\n\n*\n*$document\n||example.com^\n");
        ExternalDefinition definition = new ExternalDefinition(1013, "Test list", "", Category.ADS,
                Type.PATTERN, 1002, Format.EASYLIST, "https://example.com/private/list.txt",
                UpdateInterval.DAILY, UpdateStatus.READY, null, listFile.toString(), true, "blacklist");
        Mockito.when(dataSource.getAll(ExternalDefinition.class)).thenReturn(List.of(definition));

        reportService.startGeneration("10.11.12.13");
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        String diagnostic = readZipFile(reportService.getReport()).get(DiagnosticsReportService.CUSTOM_LISTS_ENTRY);
        Assert.assertTrue(diagnostic.contains("1013\tTest list\tPATTERN\tADS\tEASYLIST\ttrue\tREADY"));
        Assert.assertTrue(diagnostic.contains("\texample.com\t"));
        Assert.assertTrue(diagnostic.contains("\t5\t1\t3\t0\t0\t2\tUNIVERSAL_RULE\t\n"));
        Assert.assertFalse(diagnostic.contains("private/list.txt"));
        Assert.assertFalse(diagnostic.contains("||example.com^"));
    }

    @Test
    public void missingCustomListDoesNotBreakWholeReport() throws IOException {
        ExternalDefinition definition = new ExternalDefinition(1013, "Missing list", "", Category.ADS,
                Type.PATTERN, 1002, Format.EASYLIST, "https://example.com/private/list.txt",
                UpdateInterval.DAILY, UpdateStatus.READY, null, tmpDir.resolve("missing.txt").toString(), true, "blacklist");
        Mockito.when(dataSource.getAll(ExternalDefinition.class)).thenReturn(List.of(definition));

        reportService.startGeneration("10.11.12.13");
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        Assert.assertEquals(DiagnosticsReportService.State.FINISHED, reportService.getStatus());
        String diagnostic = readZipFile(reportService.getReport()).get(DiagnosticsReportService.CUSTOM_LISTS_ENTRY);
        Assert.assertTrue(diagnostic.contains("INSPECTION_FAILED\tNoSuchFileException"));
    }

    @Test
    public void commentOnlyListIsReportedAsHavingNoRules() throws IOException {
        Path listFile = tmpDir.resolve("comments-only.txt");
        Files.writeString(listFile, "! title\n# metadata\n\n");
        ExternalDefinition definition = new ExternalDefinition(1013, "uBlock Annoyances", "", Category.TRACKER,
                Type.PATTERN, 1002, Format.EASYLIST, "https://example.com/list.txt",
                UpdateInterval.DAILY, UpdateStatus.READY, null, listFile.toString(), true, "blacklist");
        Mockito.when(dataSource.getAll(ExternalDefinition.class)).thenReturn(List.of(definition));

        reportService.startGeneration("10.11.12.13");
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        String diagnostic = readZipFile(reportService.getReport()).get(DiagnosticsReportService.CUSTOM_LISTS_ENTRY);
        Assert.assertTrue(diagnostic.contains("1013\tuBlock Annoyances"));
        Assert.assertTrue(diagnostic.contains("\t3\t1\t0\t0\t0\t0\tNO_RULES\t"));
    }

    @Test
    public void adblockFileInDomainModeIsReportedAsFormatMismatch() throws IOException {
        Path listFile = tmpDir.resolve("adblock-as-domains.txt");
        StringBuilder content = new StringBuilder("[Adblock Plus]\n! Syntax: AdBlock\n");
        for (int i = 0; i < 12; i++) content.append("||example").append(i).append(".com^\n");
        Files.writeString(listFile, content.toString());
        ExternalDefinition definition = new ExternalDefinition(1001, "Wrong format", "", Category.MALWARE,
                Type.DOMAIN, 1002, Format.DOMAINS, "https://example.com/adblock.txt",
                UpdateInterval.DAILY, UpdateStatus.READY, null, listFile.toString(), true, "blacklist");
        Mockito.when(dataSource.getAll(ExternalDefinition.class)).thenReturn(List.of(definition));

        reportService.startGeneration("10.11.12.13");
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        String diagnostic = readZipFile(reportService.getReport()).get(DiagnosticsReportService.CUSTOM_LISTS_ENTRY);
        Assert.assertTrue(diagnostic.contains("1001\tWrong format\tDOMAIN\tMALWARE\tDOMAINS"));
        Assert.assertTrue(diagnostic.contains("\t12\t12\t0\t0\tFORMAT_MISMATCH\t"));
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(systemLogPath);
        FileUtils.deleteDirectory(tmpDir);
    }

    @Test
    public void testReportGeneration() throws IOException, InterruptedException {
        Assert.assertEquals(DiagnosticsReportService.State.NOT_STARTED, reportService.getStatus());
        Assert.assertNull(reportService.getReport());

        reportService.startGeneration("10.11.12.13");
        Assert.assertEquals(DiagnosticsReportService.State.PENDING, reportService.getStatus());
        Assert.assertNull(reportService.getReport());
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService).execute(runnableCaptor.capture());

        String statusLogContent = "The status log content";
        String otherLogContent = "Other log content";

        Mockito.when(scriptRunner.runScript(Mockito.eq(REPORT_CREATE_SCRIPT), Mockito.anyString(), Mockito.eq(SYSTEM_TOKEN), Mockito.eq("10.11.12.13"))).thenAnswer(invocationOnMock -> {
            // Simulate script: write a status file
            Path scriptOutputDir = Paths.get((String)invocationOnMock.getArgument(1));
            Files.writeString(scriptOutputDir.resolve("eblocker-status.log"), statusLogContent);
            Path subdir = scriptOutputDir.resolve("apt");
            Files.createDirectory(subdir);
            Files.writeString(subdir.resolve("history.log"), otherLogContent);
            return 0;
        });

        runnableCaptor.getValue().run();

        Assert.assertEquals(DiagnosticsReportService.State.FINISHED, reportService.getStatus());

        Map<String, String> entries = readZipFile(reportService.getReport());
        Assert.assertEquals(8, entries.size());
        Assert.assertTrue(entries.get(DiagnosticsReportService.CUSTOM_LISTS_ENTRY).contains("blank_lines"));
        Assert.assertEquals("", entries.get(DiagnosticsReportService.PREFIX));
        Assert.assertEquals(LOG_CONTENT, entries.get(DiagnosticsReportService.SYSTEM_LOG_ENTRY));
        Assert.assertEquals(statusLogContent, entries.get(DiagnosticsReportService.PREFIX + "eblocker-status.log"));
        Assert.assertEquals("", entries.get(DiagnosticsReportService.PREFIX + "apt/"));
        Assert.assertEquals(otherLogContent, entries.get(DiagnosticsReportService.PREFIX + "apt/history.log"));
        Assert.assertTrue(entries.get(DiagnosticsReportService.NETTY_POOL_ENTRY).contains("heap arena(s):"));
        Assert.assertTrue(entries.get(DiagnosticsReportService.EVENTS_LOG_ENTRY).contains("ICAP_SERVER_STARTED"));
    }

    @Test
    public void testSystemLogNotFound() throws IOException {
        Files.delete(systemLogPath);
        reportService.startGeneration("10.11.12.13");

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        Map<String, String> entries = readZipFile(reportService.getReport());
        Assert.assertTrue(entries.get(DiagnosticsReportService.SYSTEM_LOG_ENTRY).contains("java.nio.file.NoSuchFileException"));
    }


    @Test
    public void testScriptFailure() throws IOException, InterruptedException {
        // even if the report script fails we expect the system log file
        reportService.startGeneration("10.11.12.13");

        Mockito.when(scriptRunner.runScript(Mockito.eq(REPORT_CREATE_SCRIPT), Mockito.any())).thenThrow(IOException.class);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        Map<String, String> entries = readZipFile(reportService.getReport());
        Assert.assertEquals(LOG_CONTENT, entries.get(DiagnosticsReportService.SYSTEM_LOG_ENTRY));
    }

    @Test
    public void testReportGenerationNoSecondStart() throws IOException {
        Assert.assertEquals(DiagnosticsReportService.State.NOT_STARTED, reportService.getStatus());
        Assert.assertNull(reportService.getReport());

        reportService.startGeneration("10.11.12.13");
        Assert.assertEquals(DiagnosticsReportService.State.PENDING, reportService.getStatus());
        Assert.assertNull(reportService.getReport());
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService).execute(runnableCaptor.capture());

        reportService.startGeneration("10.11.12.13");
        Mockito.verifyNoMoreInteractions(executorService);
    }

    @Test
    public void testReportGenerationFailure() throws IOException {
        FileUtils.deleteDirectory(tmpDir); // now the zip file can not be written

        Assert.assertEquals(DiagnosticsReportService.State.NOT_STARTED, reportService.getStatus());
        Assert.assertNull(reportService.getReport());

        reportService.startGeneration("10.11.12.13");
        Assert.assertEquals(DiagnosticsReportService.State.PENDING, reportService.getStatus());
        Assert.assertNull(reportService.getReport());
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService).execute(runnableCaptor.capture());

        runnableCaptor.getValue().run();

        Assert.assertEquals(DiagnosticsReportService.State.ERROR, reportService.getStatus());
        Assert.assertNull(reportService.getReport());
    }

    // Read a binary zip file, returning a map from entry names to file contents.
    // Directory entries are mapped to empty strings.
    private Map<String, String> readZipFile(byte[] report) throws IOException {
        Map<String, String> entries = new HashMap();
        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(report))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                String data = new String(zipIn.readAllBytes(), StandardCharsets.UTF_8);
                entries.put(entry.getName(), data);
                zipIn.closeEntry();
            }
        }
        return entries;
    }
}
