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
package org.eblocker.server.http.controller.impl;

import com.google.inject.Inject;
import org.eblocker.server.http.controller.AuthenticationController;
import org.eblocker.server.http.controller.DeviceController;
import org.eblocker.server.http.controller.DeviceRegistrationController;
import org.eblocker.server.http.controller.SplashController;
import org.eblocker.server.http.controller.TosController;
import org.eblocker.server.http.controller.boot.SystemStatusController;
import org.restexpress.Request;
import org.restexpress.Response;

import java.io.IOException;

/**
 * First /api/v1 bridge for the modern React UI.
 *
 * This controller deliberately delegates to the already-live legacy controllers instead of
 * fabricating new DTOs. It gives the modern UI stable /api/v1 entry points while keeping
 * existing business logic, permissions and side effects in one place during migration.
 */
public class ModernApiBridgeController {
    private final DeviceController deviceController;
    private final SystemStatusController systemStatusController;
    private final AuthenticationController authenticationController;
    private final DeviceRegistrationController deviceRegistrationController;
    private final SplashController splashController;
    private final TosController tosController;

    @Inject
    public ModernApiBridgeController(DeviceController deviceController,
                                     SystemStatusController systemStatusController,
                                     AuthenticationController authenticationController,
                                     DeviceRegistrationController deviceRegistrationController,
                                     SplashController splashController,
                                     TosController tosController) {
        this.deviceController = deviceController;
        this.systemStatusController = systemStatusController;
        this.authenticationController = authenticationController;
        this.deviceRegistrationController = deviceRegistrationController;
        this.splashController = splashController;
        this.tosController = tosController;
    }

    public Object getDevices(Request request, Response response) {
        return deviceController.getAllDevices(request, response);
    }

    public Object getDevice(Request request, Response response) {
        return deviceController.getDeviceById(request, response);
    }

    public Object updateDevice(Request request, Response response) {
        return deviceController.updateDevice(request, response);
    }

    public Object deleteDevice(Request request, Response response) {
        return deviceController.deleteDevice(request, response);
    }

    public Object resetDevice(Request request, Response response) {
        return deviceController.resetDevice(request, response);
    }

    public Object scanDevices(Request request, Response response) {
        return deviceController.scanDevices(request, response);
    }

    public boolean isScanningAvailable(Request request, Response response) {
        return deviceController.isScanningAvailable(request, response);
    }

    public Object getScanningInterval(Request request, Response response) {
        return deviceController.getScanningInterval(request, response);
    }

    public void setScanningInterval(Request request, Response response) {
        deviceController.setScanningInterval(request, response);
    }

    public Object isAutoEnableNewDevices(Request request, Response response) {
        return deviceController.isAutoEnableNewDevices(request, response);
    }

    public void setAutoEnableNewDevices(Request request, Response response) {
        deviceController.setAutoEnableNewDevices(request, response);
    }

    public Object getSystemStatus(Request request, Response response) {
        return systemStatusController.get(request, response);
    }

    public void shutdown(Request request, Response response) {
        systemStatusController.shutdown(request, response);
    }

    public void reboot(Request request, Response response) {
        systemStatusController.reboot(request, response);
    }

    public void shutdownOnError(Request request, Response response) {
        systemStatusController.shutdownOnError(request, response);
    }

    public void rebootOnError(Request request, Response response) {
        systemStatusController.rebootOnError(request, response);
    }

    public Object generateConsoleToken(Request request, Response response) {
        return authenticationController.generateConsoleToken(request, response);
    }

    public Object login(Request request, Response response) {
        return authenticationController.login(request, response);
    }

    public Object renewToken(Request request, Response response) {
        return authenticationController.renewToken(request, response);
    }

    public long passwordEntryInSeconds(Request request, Response response) {
        return authenticationController.passwordEntryInSeconds(request, response);
    }

    public Object initiateReset(Request request, Response response) {
        return authenticationController.initiateReset(request, response);
    }

    public void executeReset(Request request, Response response) {
        authenticationController.executeReset(request, response);
    }

    public void cancelReset(Request request, Response response) {
        authenticationController.cancelReset(request, response);
    }

    public Object registrationStatus(Request request, Response response) {
        return deviceRegistrationController.registrationStatus(request, response);
    }

    public Object register(Request request, Response response) {
        return deviceRegistrationController.register(request, response);
    }

    public void resetRegistration(Request request, Response response) throws IOException, InterruptedException {
        deviceRegistrationController.resetRegistration(request, response);
    }

    public Object getSplash(Request request, Response response) {
        return splashController.get(request, response);
    }

    public void setSplash(Request request, Response response) {
        splashController.set(request, response);
    }

    public Object getTos(Request request, Response response) {
        return tosController.getTos(request, response);
    }
}
