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
package org.eblocker.server.common.network;

import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;

public class ZeroconfRegistrationServiceTest {
    private ZeroconfRegistrationService service;
    private DeviceRegistrationProperties deviceRegistrationProperties;
    NetworkInterfaceWrapper networkInterface;

    @Before
    public void setUp() throws Exception {
        String defaultName = "Default name with.dots.";
        deviceRegistrationProperties = Mockito.mock(DeviceRegistrationProperties.class);
        service = new ZeroconfRegistrationService(3000, defaultName, deviceRegistrationProperties, networkInterface);
    }

    @Test
    public void testEscapeDots() {
        String name = service.getServiceName();
        assertEquals("Default name with_dots_", name);
    }

    @Test
    public void useDeviceNameIfPossible() {
        Mockito.when(deviceRegistrationProperties.getDeviceName()).thenReturn("Registered Device Name");
        assertEquals("Registered Device Name", service.getServiceName());
    }
}
