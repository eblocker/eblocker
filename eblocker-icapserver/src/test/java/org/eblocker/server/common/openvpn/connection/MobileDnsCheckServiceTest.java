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
package org.eblocker.server.common.openvpn.connection;

import org.eblocker.server.common.registration.DeviceRegistrationClient;
import org.eblocker.server.http.service.OpenVpnServerService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class MobileDnsCheckServiceTest {

    private DeviceRegistrationClient deviceRegistrationClient;
    private OpenVpnServerService openVpnServerService;
    private MobileDnsCheckService mobileDnsCheckService;

    @Before
    public void setUp() {
        deviceRegistrationClient = Mockito.mock(DeviceRegistrationClient.class);
        openVpnServerService = Mockito.mock(OpenVpnServerService.class);
        mobileDnsCheckService = new MobileDnsCheckService(deviceRegistrationClient, openVpnServerService);
    }

    @Test
    public void checkNoHostname() {
        Assert.assertTrue(mobileDnsCheckService.check());
        Mockito.verify(openVpnServerService).getOpenVpnServerHost();
        Mockito.verifyZeroInteractions(deviceRegistrationClient);
    }

    @Test
    public void checkIpAddress() {
        Mockito.when(openVpnServerService.getOpenVpnServerHost()).thenReturn("1.2.3.4");
        Assert.assertTrue(mobileDnsCheckService.check());
        Mockito.verify(openVpnServerService).getOpenVpnServerHost();
        Mockito.verifyZeroInteractions(deviceRegistrationClient);
    }

    @Test
    public void checkDynDnsSuccess() {
        Mockito.when(openVpnServerService.getOpenVpnServerHost()).thenReturn("customer0123.dyndns.eblocker.com");
        Mockito.when(deviceRegistrationClient.requestMobileDnsCheck("customer0123.dyndns.eblocker.com")).thenReturn(true);
        Assert.assertTrue(mobileDnsCheckService.check());
    }

    @Test
    public void checkDynDnsFail() {
        Mockito.when(openVpnServerService.getOpenVpnServerHost()).thenReturn("customer0123.dyndns.eblocker.com");
        Mockito.when(deviceRegistrationClient.requestMobileDnsCheck("customer0123.dyndns.eblocker.com")).thenReturn(false);
        Assert.assertFalse(mobileDnsCheckService.check());
    }
}
