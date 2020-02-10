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
package org.eblocker.server.common.registration;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class LicenseExpirationServiceTest {
    private DeviceRegistrationProperties deviceRegistrationProperties;
    private LicenseExpirationService service;
    private LicenseExpirationService.LicenseExpirationListener listener;

    @Before
    public void setUp() {
        deviceRegistrationProperties = Mockito.mock(DeviceRegistrationProperties.class);
        service = new LicenseExpirationService(deviceRegistrationProperties);
        listener = Mockito.mock(LicenseExpirationService.LicenseExpirationListener.class);
    }

    @Test
    public void testisExpired() {
        Mockito.when(deviceRegistrationProperties.isLicenseExpired()).thenReturn(true);
        service.addListener(listener);

        assertTrue(service.isExpired());
        Mockito.verify(listener).onChange();
    }

    @Test
    public void testisNotExpired() {
        Mockito.when(deviceRegistrationProperties.isLicenseExpired()).thenReturn(false);

        assertFalse(service.isExpired());
        Mockito.verify(listener, Mockito.never()).onChange();
    }

    @Test
    public void testInit() {
        Mockito.when(deviceRegistrationProperties.isLicenseExpired()).thenReturn(true);
        service.addListener(listener);
        service.init();

        Mockito.verify(listener).onChange();
    }
}
