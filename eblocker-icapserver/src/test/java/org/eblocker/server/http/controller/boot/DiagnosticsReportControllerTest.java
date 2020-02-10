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
package org.eblocker.server.http.controller.boot;

import org.eblocker.server.http.service.DiagnosticsReportService;
import org.eblocker.server.junit.Assert5;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.NotFoundException;

import java.io.IOException;
import java.net.InetSocketAddress;

public class DiagnosticsReportControllerTest {

    private static final String IP_ADDRESS = "192.168.3.4";

    private DiagnosticsReportService reportService;
    private DiagnosticsReportController controller;

    private Request request;
    private Response response;

    @Before
    public void setUp() {
        reportService = Mockito.mock(DiagnosticsReportService.class);
        Mockito.when(reportService.getStatus()).thenReturn(DiagnosticsReportService.State.NOT_STARTED);

        controller = new DiagnosticsReportController(reportService);

        request = Mockito.mock(Request.class);
        Mockito.when(request.getRemoteAddress()).thenReturn(new InetSocketAddress(IP_ADDRESS, 48123));

        response = Mockito.mock(Response.class);
    }

    @Test
    public void testStartGeneration() throws IOException {
        controller.startReport(request, response);
        Mockito.verify(reportService).startGeneration(IP_ADDRESS);
    }

    @Test
    public void testGetStatus() {
        Assert.assertEquals(DiagnosticsReportService.State.NOT_STARTED, controller.getReportStatus(request, response));

        Mockito.when(reportService.getStatus()).thenReturn(DiagnosticsReportService.State.PENDING);
        Assert.assertEquals(DiagnosticsReportService.State.PENDING, controller.getReportStatus(request, response));

        Mockito.when(reportService.getStatus()).thenReturn(DiagnosticsReportService.State.FINISHED);
        Assert.assertEquals(DiagnosticsReportService.State.FINISHED, controller.getReportStatus(request, response));

        Mockito.when(reportService.getStatus()).thenReturn(DiagnosticsReportService.State.ERROR);
        Assert.assertEquals(DiagnosticsReportService.State.ERROR, controller.getReportStatus(request, response));
    }

    @Test
    public void testGetReportNotStarted() throws IOException {
        Assert5.assertThrows(NotFoundException.class, () -> controller.getReport(request, response));

        Mockito.when(reportService.getStatus()).thenReturn(DiagnosticsReportService.State.PENDING);
        Assert5.assertThrows(NotFoundException.class, () -> controller.getReport(request, response));

        byte[] report = RandomUtils.nextBytes(1024);
        Mockito.when(reportService.getStatus()).thenReturn(DiagnosticsReportService.State.FINISHED);
        Mockito.when(reportService.getReport()).thenReturn(report);
        Assert.assertEquals(Unpooled.wrappedBuffer(report), controller.getReport(request, response));

        Mockito.when(reportService.getStatus()).thenReturn(DiagnosticsReportService.State.ERROR);
        Assert.assertNull(controller.getReport(request, response));
        Mockito.verify(response).setResponseStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }
}
