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

import com.google.common.collect.Sets;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.FilterMode;
import org.eblocker.server.common.data.UserModuleOld;
import org.eblocker.server.common.data.dashboard.DashboardCard;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SchemaMigrationVersion27Test {

    private DataSource dataSource;
    private SchemaMigrationVersion27 migration;
    private UserMigrationService userMigrationService;

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        userMigrationService = Mockito.mock(UserMigrationService.class);
        migration = new SchemaMigrationVersion27(dataSource, userMigrationService);
    }

    @Test
    public void getSourceVersion() {
        Assert.assertEquals("26", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() {
        Assert.assertEquals("27", migration.getTargetVersion());
    }

    @Test
    public void test_migrate_createDashboardCard() {
        UserModuleOld user = createUser(
            0,
            createCard(0, "<dashboard-example-one></dashboard-example-one>"),
            createCard(1, "<dashboard-dns-statistics></dashboard-dns-statistics>"),
            createCard(2, "<dashboard-example-two></dashboard-example-two>"));

        List<UserModuleOld> list = new ArrayList<>();
        list.add(user);
        Mockito.when(userMigrationService.getAll()).thenReturn(list);

        migration.migrate();

        ArgumentCaptor<UserModuleOld> captor = ArgumentCaptor.forClass(UserModuleOld.class);
        Mockito.verify(userMigrationService).save(captor.capture(), Mockito.eq(user.getId()));
        UserModuleOld savedUser = captor.getValue();
        Assert.assertEquals(3, savedUser.getDashboardCards().size());
        Assert.assertEquals("<dashboard-example-one></dashboard-example-one>", savedUser.getDashboardCards().get(0).getHtml());
        Assert.assertEquals("<dashboard-filter-statistics></dashboard-filter-statistics>", savedUser.getDashboardCards().get(1).getHtml());
        Assert.assertEquals("<dashboard-example-two></dashboard-example-two>", savedUser.getDashboardCards().get(2).getHtml());

        Mockito.verify(dataSource).setVersion("27");
    }

    private UserModuleOld createUser(int id, DashboardCard... cards) {
        return new UserModuleOld(id, null, null, null, null, null, false, null, null, Arrays.asList(cards), null, null);
    }

    private DashboardCard createCard(int id, String html) {
        return new DashboardCard(id, null, null, html, false, false, null, null);
    }

    @Test
    public void test_migrate_setFilterMode() {
        Device[] devices = {
            createDevice("device:000000000000", false, false),
            createDevice("device:000000000001", false, true),
            createDevice("device:000000000002", true, false),
            createDevice("device:000000000003", true, true)
        };
        Mockito.when(dataSource.getDevices()).thenReturn(Sets.newHashSet(devices));

        migration.migrate();

        Assert.assertEquals(FilterMode.PLUG_AND_PLAY, devices[0].getFilterMode());
        Mockito.verify(dataSource).save(devices[0]);
        Assert.assertEquals(FilterMode.ADVANCED, devices[1].getFilterMode());
        Mockito.verify(dataSource).save(devices[1]);
        Assert.assertEquals(FilterMode.PLUG_AND_PLAY, devices[2].getFilterMode());
        Mockito.verify(dataSource).save(devices[2]);
        Assert.assertEquals(FilterMode.ADVANCED, devices[3].getFilterMode());
        Mockito.verify(dataSource).save(devices[3]);

        Mockito.verify(dataSource).setVersion("27");
    }

    private Device createDevice(String id, boolean enabled, boolean sslEnabled) {
        Device device = new Device();
        device.setId(id);
        device.setEnabled(enabled);
        device.setSslEnabled(sslEnabled);
        return device;
    }

}
