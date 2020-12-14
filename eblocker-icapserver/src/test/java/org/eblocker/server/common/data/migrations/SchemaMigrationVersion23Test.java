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
import org.eblocker.server.common.data.UserModuleOld;
import org.eblocker.server.common.data.dashboard.DashboardCard;
import org.eblocker.server.http.service.DashboardService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;

public class SchemaMigrationVersion23Test {

    private DataSource dataSource;
    private DashboardService dashboardService;
    private SchemaMigration migration;
    private UserModuleOld userModule;
    private UserModuleOld userModule2;
    private UserMigrationService userMigrationService;

    @Before
    public void setUp() {
        DashboardCard presentCard = new DashboardCard(7, "FEAT", "SUFFIX", "<html></html>", true, true, null, null);
        List<DashboardCard> filledList = Collections.singletonList(presentCard);

        userMigrationService = Mockito.mock(UserMigrationService.class); // new UserMigrationService(jedisPool, objectMapper);

        dataSource = Mockito.mock(DataSource.class);
        dashboardService = new DashboardService(null);
        migration = new SchemaMigrationVersion23(dataSource, dashboardService, userMigrationService);

        userModule = new UserModuleOld(1, 1, "user1", null, null, null, false, null, null, new ArrayList<>(), null, null);
        userModule2 = new UserModuleOld(2, 2, "user2", null, null, null, false, null, null, filledList, null, null);

        List<UserModuleOld> userModules = new ArrayList<>();
        userModules.add(userModule);
        userModules.add(userModule2);

        Mockito.when(userMigrationService.getAll()).thenReturn(userModules);
    }

    @Test
    public void getSourceVersion() {
        Assert.assertEquals("22", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() {
        Assert.assertEquals("23", migration.getTargetVersion());
    }

    @Test
    public void migrate() {
        migration.migrate();

        Mockito.verify(dataSource).setVersion("23");
        Mockito.verify(userMigrationService, Mockito.times(1)).save(Mockito.any(UserModuleOld.class), eq(1));

    }
}
