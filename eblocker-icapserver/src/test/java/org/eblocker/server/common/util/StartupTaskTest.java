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
package org.eblocker.server.common.util;

import java.util.HashSet;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import org.eblocker.server.common.PauseDeviceController;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.http.service.DeviceService;

public class StartupTaskTest {
    private StartupTask startupTask;
    private DeviceService deviceService;
    private PauseDeviceController pauseDeviceController;
    private Device alpha;
    private Device bravo;
    
    @Before
    public void setup() {
        // Mocked device
        // Device not paused, not touched
        alpha = Mockito.mock(Device.class);
        Mockito.when(alpha.isPaused()).thenReturn(false);
        // Device paused, not routed through TOR, SSL disabled
        bravo = Mockito.mock(Device.class);
        Mockito.when(bravo.isPaused()).thenReturn(true);
        
        // Collection of Devices
        Set<Device> devices = new HashSet<>();
        devices.add(alpha);
        devices.add(bravo);

        deviceService = Mockito.mock(DeviceService.class);
        Mockito.when(deviceService.getDevices(Mockito.anyBoolean())).thenReturn(devices);
        
        pauseDeviceController = Mockito.mock(PauseDeviceController.class);

        startupTask = new StartupTask(deviceService, pauseDeviceController);
    }
    
    @After
    public void teardown() {
        
    }
    
    @Test
    public void testReenablingPausedDevices() {
        startupTask.run();
        
        Mockito.verify(deviceService).getDevices(Mockito.eq(false));
        // Device alpha not changed, not put back into device service nor squid config controller notified
        Mockito.verify(alpha).isPaused();
        Mockito.verify(pauseDeviceController, Mockito.never()).reactivateDevice(Mockito.eq(alpha));
        // Device bravo changed, put back into device service, squid config controller not notified
        Mockito.verify(bravo).isPaused();
        Mockito.verify(pauseDeviceController).reactivateDevice(Mockito.eq(bravo));
    }

}
