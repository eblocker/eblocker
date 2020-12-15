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
import org.eblocker.server.common.data.openvpn.OpenVpnProfile;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;

public class SchemaMigrationVersion22Test {

    private DataSource dataSource;
    private SchemaMigration migration;

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        Mockito.when(dataSource.getAll(OpenVpnProfile.class)).thenReturn(Arrays.asList(
                createProfile(0),
                createProfile(1),
                createProfile(2)
        ));

        migration = new SchemaMigrationVersion22(dataSource);
    }

    @Test
    public void getSourceVersion() {
        Assert.assertEquals("21", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() {
        Assert.assertEquals("22", migration.getTargetVersion());
    }

    @Test
    public void migrate() {
        migration.migrate();

        Mockito.verify(dataSource).setVersion("22");

        ArgumentCaptor<OpenVpnProfile> captor = ArgumentCaptor.forClass(OpenVpnProfile.class);
        Mockito.verify(dataSource).save(captor.capture(), Mockito.eq(0));
        Mockito.verify(dataSource).save(captor.capture(), Mockito.eq(1));
        Mockito.verify(dataSource).save(captor.capture(), Mockito.eq(2));
        Assert.assertTrue(captor.getAllValues().get(0).isNameServersEnabled());
        Assert.assertTrue(captor.getAllValues().get(1).isNameServersEnabled());
        Assert.assertTrue(captor.getAllValues().get(2).isNameServersEnabled());
    }

    private OpenVpnProfile createProfile(int id) {
        OpenVpnProfile profile = new OpenVpnProfile(id, "profile-" + id);
        profile.setNameServersEnabled(false);
        return profile;
    }
}
