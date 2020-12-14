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

import org.apache.commons.lang3.RandomUtils;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.http.security.AppContext;
import org.eblocker.server.http.security.JsonWebToken;
import org.eblocker.server.http.security.JsonWebTokenHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;

public class DiagnosticsReportServiceTest {

    private static final String REPORT_CREATE_SCRIPT = "generate_report";
    private static final String SYSTEM_TOKEN = "---UNIT-TEST-TOKEN---";

    private Path reportFilePath;
    private ScheduledExecutorService executorService;
    private ScriptRunner scriptRunner;
    private JsonWebTokenHandler tokenHandler;
    private DiagnosticsReportService reportService;

    @Before
    public void setUp() throws IOException {
        reportFilePath = Files.createTempFile("diagnostic_report", ".txt");
        Files.delete(reportFilePath);

        executorService = Mockito.mock(ScheduledExecutorService.class);

        scriptRunner = Mockito.mock(ScriptRunner.class);

        tokenHandler = Mockito.mock(JsonWebTokenHandler.class);
        Mockito.when(tokenHandler.generateSystemToken()).thenReturn(new JsonWebToken(SYSTEM_TOKEN, AppContext.SYSTEM, Long.MAX_VALUE, false));

        reportService = new DiagnosticsReportService(reportFilePath.toString(), REPORT_CREATE_SCRIPT, executorService, scriptRunner, tokenHandler);
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(reportFilePath);
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

        runnableCaptor.getValue().run();
        Mockito.verify(scriptRunner).runScript(REPORT_CREATE_SCRIPT, SYSTEM_TOKEN, "10.11.12.13");

        byte[] report = RandomUtils.nextBytes(1024);
        Files.write(reportFilePath, report);

        Assert.assertEquals(DiagnosticsReportService.State.FINISHED, reportService.getStatus());
        Assert.assertArrayEquals(report, reportService.getReport());
    }

    @Test
    public void testReportGenerationNoSecondStart() throws IOException, InterruptedException {
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
    public void testReportGenerationFailure() throws IOException, InterruptedException {
        Assert.assertEquals(DiagnosticsReportService.State.NOT_STARTED, reportService.getStatus());
        Assert.assertNull(reportService.getReport());

        reportService.startGeneration("10.11.12.13");
        Assert.assertEquals(DiagnosticsReportService.State.PENDING, reportService.getStatus());
        Assert.assertNull(reportService.getReport());
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService).execute(runnableCaptor.capture());

        Mockito.when(scriptRunner.runScript(Mockito.eq(REPORT_CREATE_SCRIPT), Mockito.any())).thenThrow(IOException.class);
        runnableCaptor.getValue().run();

        Assert.assertEquals(DiagnosticsReportService.State.ERROR, reportService.getStatus());
        Assert.assertNull(reportService.getReport());
    }

}
