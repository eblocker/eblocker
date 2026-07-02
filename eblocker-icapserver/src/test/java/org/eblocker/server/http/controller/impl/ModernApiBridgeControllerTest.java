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

import org.eblocker.registration.TosContainer;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.systemstatus.SystemStatusDetails;
import org.eblocker.server.common.registration.DeviceRegistrationInfo;
import org.eblocker.server.http.controller.AuthenticationController;
import org.eblocker.server.http.controller.DeviceController;
import org.eblocker.server.http.controller.DeviceRegistrationController;
import org.eblocker.server.http.controller.SplashController;
import org.eblocker.server.http.controller.TosController;
import org.eblocker.server.http.controller.boot.SystemStatusController;
import org.eblocker.server.http.security.JsonWebToken;
import org.eblocker.server.http.security.PasswordResetToken;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restexpress.Request;
import org.restexpress.Response;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModernApiBridgeControllerTest {
    private ModernApiBridgeController controller;
    private DeviceController deviceController;
    private SystemStatusController systemStatusController;
    private AuthenticationController authenticationController;
    private DeviceRegistrationController deviceRegistrationController;
    private SplashController splashController;
    private TosController tosController;
    private Request request;
    private Response response;

    @Before
    public void setUp() {
        deviceController = Mockito.mock(DeviceController.class);
        systemStatusController = Mockito.mock(SystemStatusController.class);
        authenticationController = Mockito.mock(AuthenticationController.class);
        deviceRegistrationController = Mockito.mock(DeviceRegistrationController.class);
        splashController = Mockito.mock(SplashController.class);
        tosController = Mockito.mock(TosController.class);
        controller = new ModernApiBridgeController(deviceController, systemStatusController, authenticationController, deviceRegistrationController, splashController, tosController);
        request = Mockito.mock(Request.class);
        response = new Response();
    }

    @Test
    public void delegatesDeviceListToExistingLiveDeviceController() {
        Object devices = Collections.singletonList(new Device());
        when(deviceController.getAllDevices(request, response)).thenReturn((java.util.List<Device>) devices);

        assertSame(devices, controller.getDevices(request, response));
        verify(deviceController).getAllDevices(request, response);
    }

    @Test
    public void delegatesDeviceDetailAndMutationEndpoints() {
        Device device = new Device();
        when(deviceController.getDeviceById(request, response)).thenReturn(device);
        when(deviceController.updateDevice(request, response)).thenReturn(device);
        when(deviceController.deleteDevice(request, response)).thenReturn(device);
        when(deviceController.resetDevice(request, response)).thenReturn(device);

        assertSame(device, controller.getDevice(request, response));
        assertSame(device, controller.updateDevice(request, response));
        assertSame(device, controller.deleteDevice(request, response));
        assertSame(device, controller.resetDevice(request, response));

        verify(deviceController).getDeviceById(request, response);
        verify(deviceController).updateDevice(request, response);
        verify(deviceController).deleteDevice(request, response);
        verify(deviceController).resetDevice(request, response);
    }

    @Test
    public void delegatesDeviceDiscoveryEndpoints() {
        Object scanResult = new Object();
        when(deviceController.scanDevices(request, response)).thenReturn(scanResult);
        when(deviceController.isScanningAvailable(request, response)).thenReturn(Boolean.TRUE);
        when(deviceController.getScanningInterval(request, response)).thenReturn(300L);
        when(deviceController.isAutoEnableNewDevices(request, response)).thenReturn(Boolean.TRUE);

        assertSame(scanResult, controller.scanDevices(request, response));
        assertSame(Boolean.TRUE, controller.isScanningAvailable(request, response));
        assertEquals(Long.valueOf(300L), controller.getScanningInterval(request, response));
        controller.setScanningInterval(request, response);
        assertSame(Boolean.TRUE, controller.isAutoEnableNewDevices(request, response));
        controller.setAutoEnableNewDevices(request, response);

        verify(deviceController).scanDevices(request, response);
        verify(deviceController).isScanningAvailable(request, response);
        verify(deviceController).getScanningInterval(request, response);
        verify(deviceController).setScanningInterval(request, response);
        verify(deviceController).isAutoEnableNewDevices(request, response);
        verify(deviceController).setAutoEnableNewDevices(request, response);
    }

    @Test
    public void delegatesSystemStatusAndPowerActions() {
        SystemStatusDetails details = Mockito.mock(SystemStatusDetails.class);
        when(systemStatusController.get(request, response)).thenReturn(details);

        assertSame(details, controller.getSystemStatus(request, response));
        controller.shutdown(request, response);
        controller.reboot(request, response);
        controller.shutdownOnError(request, response);
        controller.rebootOnError(request, response);

        verify(systemStatusController).get(request, response);
        verify(systemStatusController).shutdown(request, response);
        verify(systemStatusController).reboot(request, response);
        verify(systemStatusController).shutdownOnError(request, response);
        verify(systemStatusController).rebootOnError(request, response);
    }

    @Test
    public void delegatesAuthenticationAndPasswordResetLifecycleEndpoints() {
        JsonWebToken token = Mockito.mock(JsonWebToken.class);
        PasswordResetToken resetToken = Mockito.mock(PasswordResetToken.class);
        when(authenticationController.generateConsoleToken(request, response)).thenReturn(token);
        when(authenticationController.login(request, response)).thenReturn(token);
        when(authenticationController.renewToken(request, response)).thenReturn(token);
        when(authenticationController.passwordEntryInSeconds(request, response)).thenReturn(42L);
        when(authenticationController.initiateReset(request, response)).thenReturn(resetToken);

        assertSame(token, controller.generateConsoleToken(request, response));
        assertSame(token, controller.login(request, response));
        assertSame(token, controller.renewToken(request, response));
        assertEquals(42L, controller.passwordEntryInSeconds(request, response));
        assertSame(resetToken, controller.initiateReset(request, response));
        controller.executeReset(request, response);
        controller.cancelReset(request, response);

        verify(authenticationController).generateConsoleToken(request, response);
        verify(authenticationController).login(request, response);
        verify(authenticationController).renewToken(request, response);
        verify(authenticationController).passwordEntryInSeconds(request, response);
        verify(authenticationController).initiateReset(request, response);
        verify(authenticationController).executeReset(request, response);
        verify(authenticationController).cancelReset(request, response);
    }

    @Test
    public void delegatesRegistrationSplashAndTosLifecycleEndpoints() throws Exception {
        DeviceRegistrationInfo registrationInfo = Mockito.mock(DeviceRegistrationInfo.class);
        TosContainer tosContainer = Mockito.mock(TosContainer.class);
        when(deviceRegistrationController.registrationStatus(request, response)).thenReturn(registrationInfo);
        when(deviceRegistrationController.register(request, response)).thenReturn(registrationInfo);
        when(splashController.get(request, response)).thenReturn(Boolean.TRUE);
        when(tosController.getTos(request, response)).thenReturn(tosContainer);

        assertSame(registrationInfo, controller.registrationStatus(request, response));
        assertSame(registrationInfo, controller.register(request, response));
        controller.resetRegistration(request, response);
        assertSame(Boolean.TRUE, controller.getSplash(request, response));
        controller.setSplash(request, response);
        assertSame(tosContainer, controller.getTos(request, response));

        verify(deviceRegistrationController).registrationStatus(request, response);
        verify(deviceRegistrationController).register(request, response);
        verify(deviceRegistrationController).resetRegistration(request, response);
        verify(splashController).get(request, response);
        verify(splashController).set(request, response);
        verify(tosController).getTos(request, response);
    }
}
