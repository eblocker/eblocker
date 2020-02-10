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
package org.eblocker.server.common.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eblocker.server.http.service.UserService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import org.junit.Assert;

import static org.mockito.Mockito.*;

public class DeviceFactoryTest {
    private DeviceFactory deviceFactory;
    private DataSource dataSource;
    private UserService userService;
    @Before
    public void setUp() throws IOException {
        // Mock DataSource
        dataSource = Mockito.mock(DataSource.class);
        userService = Mockito.mock(UserService.class);

        Language lang = new Language("de", "German");
        UserModule user = new UserModule(123456, null, null, null, null, null,true, null, null, null, null, null);

        Mockito.when(dataSource.getCurrentLanguage()).thenReturn(lang);
        Mockito.when(userService.createDefaultSystemUser(any())).thenReturn(user);
        deviceFactory = new DeviceFactory(dataSource);
    }

    @After
    public void tearDown() {

    }

    @Test
    public void testCreateDevice() {
        String deviceId = "dev:00:11:22:33:44:55";
        List<IpAddress> ipAddresses = new ArrayList<>();
        ipAddresses.add(IpAddress.parse("1.2.3.4"));
        ipAddresses.add(IpAddress.parse("4.3.2.1"));
        boolean fixed = false;
        Device device = deviceFactory.createDevice(deviceId, ipAddresses, fixed);
        Assert.assertEquals(deviceId, device.getId());
        Assert.assertEquals(ipAddresses, device.getIpAddresses());
        Assert.assertEquals(fixed, device.isIpAddressFixed());
        Assert.assertEquals(true, device.isEnabled());
    }

    @Test
    public void testCreateNameForNewDevice(){
        Set<Device> existingDevices = new HashSet<>();
        Device devA = new Device();
        devA.setName("Dell Inc. (No. 1)");
        devA.setId("device:1866da463e1c");
        Device devB = new Device();
        devB.setName("Dell Inc. (No. 2)");
        devB.setId("device:1866da463e1d");
        Device devD = new Device();
        devD.setName("Dell Inc. (No. 4)");
        devD.setId("device:1866da463e1f");
        existingDevices.add(devA);
        existingDevices.add(devB);
        // The tested method is meant to find a name for a device C right in this gap...
        existingDevices.add(devD);
        // ...not after

        Mockito.when(dataSource.getDevices()).thenReturn(existingDevices);

        String nameForNewDevice = deviceFactory.createNameForNewDevice("1866da");
        String expectedName = "Dell Inc. (No. 3)";
        Assert.assertEquals(expectedName, nameForNewDevice);
    }
}
