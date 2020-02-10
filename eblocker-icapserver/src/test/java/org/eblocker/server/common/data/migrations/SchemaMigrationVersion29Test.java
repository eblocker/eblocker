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
import org.eblocker.server.common.data.DeviceFactory;
import org.eblocker.server.common.data.openvpn.PortForwardingMode;

import java.io.IOException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class SchemaMigrationVersion29Test {

    private DataSource dataSource;
    private DeviceFactory deviceFactory;
    private SchemaMigration migration;
    private int port = 1094;

    @Before
    public void setUp() throws IOException {
        dataSource = Mockito.mock(DataSource.class);

        migration = new SchemaMigrationVersion29(dataSource, port);
    }

    @Test
    public void getSourceVersion() {
        Assert.assertEquals("28", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() {
        Assert.assertEquals("29", migration.getTargetVersion());
    }

    @Test
    public void test_openVpnInUse() {
        Mockito.when(dataSource.getOpenVpnServerState()).thenReturn(true);
        migration.migrate();

        Mockito.verify(dataSource).setOpenVpnMappedPort(port);
        Mockito.verify(dataSource).setOpenVpnPortForwardingMode(Mockito.eq(PortForwardingMode.MANUAL));
    }

    @Test
    public void test_openVpnNotInUse() {
        Mockito.when(dataSource.getOpenVpnServerState()).thenReturn(false);
        migration.migrate();

        Mockito.verify(dataSource).setOpenVpnMappedPort(port);
        Mockito.verify(dataSource).setOpenVpnPortForwardingMode(Mockito.eq(PortForwardingMode.AUTO));
    }

}