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

import org.eblocker.server.common.data.UserModuleOld;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import org.eblocker.server.common.data.dashboard.DashboardCard;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.WhiteListConfig;
import org.eblocker.server.http.service.DashboardService;
import org.eblocker.registration.ProductFeature;

public class SchemaMigrationVersion32Test {

    private DataSource dataSource;
    private DashboardService dashboardService;
    private SchemaMigration migration;
    private UserMigrationService userMigrationService;

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        dashboardService = new DashboardService(dataSource);

        userMigrationService = Mockito.mock(UserMigrationService.class); // new UserMigrationService(jedisPool, objectMapper);

        // Mocks
        // eBlocker Mobile card for Alice still has old feature "PRO" required
        UserModuleOld userA = generateUserModule(1, 1, "alice", true);
        // eBlocker Mobile card for Bob already has new feature "BAS" required
        UserModuleOld userB = generateUserModule(2, 2, "bob", false);
        List<UserModuleOld> userList = new ArrayList<>();
        userList.add(userA);
        userList.add(userB);

        Mockito.when(userMigrationService.getAll()).thenReturn(userList);

        migration = new SchemaMigrationVersion32(dataSource, dashboardService, userMigrationService);
    }

    @Test
    public void getSourceVersion() {
        Assert.assertEquals("31", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() {
        Assert.assertEquals("32", migration.getTargetVersion());
    }

    @Test
    public void testUserProfileUpdated() {

        migration.migrate();

        ArgumentCaptor<UserModuleOld> userModule = ArgumentCaptor.forClass(UserModuleOld.class);

        // Inspect saved data for User Module "Alice"
        Mockito.verify(userMigrationService).save(userModule.capture(), Mockito.eq(1));
        Assert.assertEquals(ProductFeature.BAS.name(),
                userModule.getValue().getDashboardCards().stream()
                        .filter(c -> c.getId() == dashboardService.generateEblockerMobileCard().getId()).findFirst()
                        .get().getRequiredFeature());

        // There was only this one userModule saved, not the one for Bob
        Mockito.verify(userMigrationService).save(Mockito.any(UserModuleOld.class), Mockito.eq(1));

        Mockito.verify(dataSource).setVersion("32");
    }

    private UserModuleOld generateUserModule(int id, int associatedProfileId, String name, boolean setToPro) {
        DashboardService realDashboardService = new DashboardService(dataSource);
        List<DashboardCard> dashboardCards = realDashboardService.generateDashboardCards();
        // The eBlocker Mobile card is expected to still show "PRO" as the
        // required featureset
        if (setToPro) {
            DashboardCard oldMobileCard = dashboardCards.stream()
                    .filter(c -> c.getId() == realDashboardService.generateEblockerMobileCard().getId()).findFirst()
                    .get();
            DashboardCard newMobileCard = new DashboardCard(oldMobileCard.getId(), ProductFeature.PRO.name(),
                    oldMobileCard.getTranslateSuffix(), oldMobileCard.getHtml(), oldMobileCard.isVisible(),
                    oldMobileCard.isAlwaysVisible(), oldMobileCard.getDefaultPos(), oldMobileCard.getCustomPos());
            dashboardCards = dashboardCards.stream().map(c -> c.getId() == newMobileCard.getId() ? newMobileCard : c)
                    .collect(Collectors.toList());
        }

        Map<String, WhiteListConfig> whiteListConfigByDomains = null;
        String nameKey = null;
        byte[] pin = null;
        Integer customBlacklistId = null;
        Integer customWhitelistId = null;
        UserModuleOld result = new UserModuleOld(id, associatedProfileId, name, nameKey, null, null, false, pin, whiteListConfigByDomains,
                dashboardCards, customBlacklistId, customWhitelistId);

        return result;
    }

}
