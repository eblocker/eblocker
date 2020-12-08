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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.eblocker.server.http.service.DiagnosticsReportService;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.NotFoundException;

import java.io.IOException;

@Singleton
public class DiagnosticsReportController {

    private final DiagnosticsReportService diagnosticsReportService;

    @Inject
    public DiagnosticsReportController(DiagnosticsReportService diagnosticsReportService) {
        this.diagnosticsReportService = diagnosticsReportService;
    }

    @SuppressWarnings("unused")
    public void startReport(Request request, Response response) throws IOException {
        diagnosticsReportService.startGeneration(request.getRemoteAddress().getAddress().getHostAddress());
    }

    @SuppressWarnings("unused")
    public ByteBuf getReport(Request request, Response response) throws IOException {
        switch (diagnosticsReportService.getStatus()) {
            case FINISHED:
                response.addHeader("Content-Type", "application/octet-stream");
                response.addHeader("Content-Disposition", "attachment; filename=\"eblocker-diagnostics-report.tgz\"");
                return Unpooled.wrappedBuffer(diagnosticsReportService.getReport());
            case ERROR:
                response.setResponseStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
                return null;
            default:
                throw new NotFoundException();
        }
    }

    @SuppressWarnings("unused")
    public DiagnosticsReportService.State getReportStatus(Request request, Response response) {
        return diagnosticsReportService.getStatus();
    }
}
