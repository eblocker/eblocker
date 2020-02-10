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

import org.eblocker.server.app.DeviceProperties;
import org.eblocker.server.common.data.systemstatus.ExecutionState;
import org.eblocker.server.common.network.TelnetConnection;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;

public class StatusLedServiceTest {
    private static final String DEVICE_WITH_LED_PROPERTIES = "classpath:device-with-led.properties";
    private static final String DEVICE_WITHOUT_LED_PROPERTIES = "classpath:device-without-led.properties";
    private static final String HOST = "localhost";
    private static final int PORT = 9000;
    private TelnetConnection telnetConnection;
    private SystemStatusService systemStatusService;

    @Before
    public void setUp() throws IOException {
        telnetConnection = Mockito.mock(TelnetConnection.class);
        Mockito.when(telnetConnection.readLine()).thenReturn("OK");
        systemStatusService = Mockito.mock(SystemStatusService.class);
    }

    @Test
    public void setStatus() throws IOException {
        StatusLedService service = createService(DEVICE_WITH_LED_PROPERTIES);
        service.setStatus(ExecutionState.RUNNING);
        Mockito.verify(telnetConnection).connect(HOST, PORT);
        Mockito.verify(telnetConnection).writeLine("status=running");
    }

    @Test
    public void setStatusWithoutHardware() throws IOException {
        StatusLedService service = createService(DEVICE_WITHOUT_LED_PROPERTIES);
        service.setStatus(ExecutionState.RUNNING);
        Mockito.verifyZeroInteractions(telnetConnection);
    }

    @Test
    public void setBrightness() throws IOException {
        StatusLedService service = createService(DEVICE_WITH_LED_PROPERTIES);
        service.setBrightness(0.5f);
        Mockito.verify(telnetConnection).connect(HOST, PORT);
        Mockito.verify(telnetConnection).writeLine("brightness=0.5");
    }

    @Test
    public void getBrightness() throws IOException {
        StatusLedService service = createService(DEVICE_WITH_LED_PROPERTIES);
        Mockito.when(telnetConnection.readLine()).thenReturn("0.7");
        Assert.assertEquals(0.7f, service.getBrightness(), 0.001);
        Mockito.verify(telnetConnection).connect(HOST, PORT);
        Mockito.verify(telnetConnection).writeLine("brightness");
    }

    @Test
    public void getDefaultBrightness() throws IOException {
        StatusLedService service = createService(DEVICE_WITHOUT_LED_PROPERTIES);
        Assert.assertEquals(1.0f, service.getBrightness(), 0.001);
        Mockito.verifyZeroInteractions(telnetConnection);
    }

    @Test
    public void registerForStateChanges() throws IOException {
        StatusLedService service = createService(DEVICE_WITH_LED_PROPERTIES);
        ArgumentCaptor<SystemStatusService.ExecutionStateChangeListener> listener = ArgumentCaptor.forClass(SystemStatusService.ExecutionStateChangeListener.class);
        Mockito.verify(systemStatusService).addListener(listener.capture());
        listener.getValue().onChange(ExecutionState.UPDATING);
        Mockito.verify(telnetConnection).writeLine("status=updating");
    }

    private StatusLedService createService(String propertiesPath) {
        DeviceProperties properties = new DeviceProperties(propertiesPath);
        return new StatusLedService(properties, HOST, PORT, telnetConnection, systemStatusService);
    }
}
