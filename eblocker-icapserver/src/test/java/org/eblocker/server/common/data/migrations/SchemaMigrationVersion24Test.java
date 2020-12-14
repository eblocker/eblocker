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
package org.eblocker.server.common.data.migrations;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DeviceFactory;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.Language;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SchemaMigrationVersion24Test {

    private DataSource dataSource;
    private DeviceFactory deviceFactory;
    private SchemaMigration migration;

    @Before
    public void setUp() throws IOException {
        dataSource = Mockito.mock(DataSource.class);
        // Some devices
        Device devAlpha = createDevice("device:1cc11afedcba", "1.1.1.1", null);
        Device devBravo = createDevice("device:222222222222", "2.2.2.2", "Bravo");
        Device devCharlie = createDevice("device:333333333333", "3.3.3.3", "");
        Set<Device> devices = new HashSet<>();
        devices.add(devAlpha);
        devices.add(devBravo);
        devices.add(devCharlie);
        Mockito.when(dataSource.getDevices()).thenReturn(devices, devices, devices, devices);

        Language lang = new Language("de", "Deutsch");
        Mockito.when(dataSource.getCurrentLanguage()).thenReturn(lang, lang, lang, lang);

        deviceFactory = new DeviceFactory(dataSource);

        migration = new SchemaMigrationVersion24(dataSource, deviceFactory);
    }

    @Test
    public void getSourceVersion() {
        Assert.assertEquals("23", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() {
        Assert.assertEquals("24", migration.getTargetVersion());
    }

    @Test
    public void migrate() {
        migration.migrate();

        Device expectedDevAlpha = createDevice("device:1cc11afedcba", "1.1.1.1", "Wavetronix (No. 1)");
        // Device bravo was not changed
        Device expectedDevCharlie = createDevice("device:333333333333", "3.3.3.3", "Gerät (No. 1)");

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        // Only two device (alpha, charlie) needed a name
        Mockito.verify(dataSource, Mockito.times(2)).save(captor.capture());

        // Check device Alpha
        Device actualDevAlpha = captor.getAllValues().stream().filter((device) -> device.getId().endsWith("a"))
            .findAny().get();
        Assert.assertEquals(expectedDevAlpha.getId(), actualDevAlpha.getId());
        Assert.assertEquals(expectedDevAlpha.getName(), actualDevAlpha.getName());

        // Device Bravo already had a name; it was skipped

        // Check device charlie
        Device actualDevCharlie = captor.getAllValues().stream().filter((device) -> device.getId().endsWith("3"))
            .findAny().get();
        Assert.assertEquals(expectedDevCharlie.getId(), actualDevCharlie.getId());
        Assert.assertEquals(expectedDevCharlie.getName(), actualDevCharlie.getName());

        Mockito.verify(dataSource).setVersion("24");

        // One call to find devices that needed a name, two additional calls for
        // two unnamed devices to see if a candidate name was still available
        Mockito.verify(dataSource, Mockito.times(3)).getDevices();
    }

    private Device createDevice(String id, String ipAddress, String name) {
        Device device = new Device();
        device.setId(id);
        if (ipAddress != null) {
            device.setIpAddresses(Collections.singletonList(IpAddress.parse(ipAddress)));
        }
        device.setName(name);
        return device;
    }

}
