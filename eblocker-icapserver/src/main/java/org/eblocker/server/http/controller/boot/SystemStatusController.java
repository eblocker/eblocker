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
import org.eblocker.server.common.data.systemstatus.ExecutionState;
import org.eblocker.server.common.data.systemstatus.SystemStatusDetails;
import org.eblocker.server.http.service.ShutdownService;
import org.eblocker.server.http.service.SystemStatusService;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.UnauthorizedException;

@Singleton
public class SystemStatusController {
    private final SystemStatusService systemStatusService;

    private final ShutdownService shutdownService;

    @Inject
    public SystemStatusController(
        SystemStatusService systemStatusService,
        ShutdownService shutdownService
    ) {
        this.systemStatusService = systemStatusService;
        this.shutdownService = shutdownService;
    }

    public SystemStatusDetails get(Request request, Response response) {
        return systemStatusService.getSystemStatusDetails();
    }

    public void shutdownOnError(Request request, Response response) {
        // Unauthorized shutdown is only possible, if the eBlocker is in error state!
        if (systemStatusService.getExecutionState() == ExecutionState.RUNNING || systemStatusService.getExecutionState() == ExecutionState.BOOTING) {
            throw new UnauthorizedException("error.shutdown.notAuthorized");
        }
        shutdownService.shutdown();
    }

    public void rebootOnError(Request request, Response response) {
        // Unauthorized reboot is only possible, if the eBlocker is in error state!
        if (systemStatusService.getExecutionState() == ExecutionState.RUNNING || systemStatusService.getExecutionState() == ExecutionState.BOOTING) {
            throw new UnauthorizedException("error.reboot.notAuthorized");
        }
        shutdownService.reboot();
    }

    public void shutdown(Request request, Response response) {
        shutdownService.shutdown();
    }

    public void reboot(Request request, Response response) {
        shutdownService.reboot();
    }

}
