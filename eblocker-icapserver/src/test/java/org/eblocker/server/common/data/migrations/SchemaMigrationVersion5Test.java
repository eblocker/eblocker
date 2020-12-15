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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class SchemaMigrationVersion5Test {

    private DataSource dataSource;
    private SchemaMigrationVersion5 migration;

    @Before
    public void setup() {
        dataSource = Mockito.mock(DataSource.class);
        migration = new SchemaMigrationVersion5(dataSource);
    }

    @Test
    public void getSourceVersion() throws Exception {
        Assert.assertEquals("4", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() throws Exception {
        Assert.assertEquals("5", migration.getTargetVersion());
    }

    @Test
    public void testMigration() {
        // setup mock
        List<Device> devices = new ArrayList<>();
        devices.add(createDevice("0", null, false));
        devices.add(createDevice("1", DefaultEntities.VPN_PROFILE_DELETION, false));
        devices.add(createDevice("2", 1, true));
        devices.add(createDevice("3", DefaultEntities.VPN_PROFILE_DELETION, true));
        Mockito.when(dataSource.getDevices()).thenReturn(new HashSet<>(devices));

        // run migration
        migration.migrate();

        // ensure used profile id have been updated correctly
        Assert.assertNull(devices.get(0).getUseVPNProfileID());
        Assert.assertNull(devices.get(1).getUseVPNProfileID());
        Assert.assertNull(devices.get(3).getUseVPNProfileID());
        Assert.assertEquals(Integer.valueOf(1), devices.get(2).getUseVPNProfileID());
        Mockito.verify(dataSource).save(devices.get(1));

        // ensure that useAnonymization flag is correct:
        Assert.assertFalse(devices.get(0).isUseAnonymizationService());
        Assert.assertFalse(devices.get(1).isUseAnonymizationService());
        Assert.assertTrue(devices.get(2).isUseAnonymizationService());
        Assert.assertTrue(devices.get(3).isUseAnonymizationService());

        // ensure version has been updated
        Mockito.verify(dataSource).setVersion("5");
    }

    private Device createDevice(String id, Integer vpnProfileId, boolean torEnabled) {
        Device device = new Device();
        device.setId(id);
        device.setUseVPNProfileID(vpnProfileId);
        device.setRouteThroughTor(torEnabled);
        device.setUseAnonymizationService(device.isRoutedThroughTor() || device.getUseVPNProfileID() != null); // done by JedisDataSource.getDevice()
        return device;
    }

}
