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

import org.eblocker.registration.ProductFeature;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.UserModuleOld;
import org.eblocker.server.common.data.WhiteListConfig;
import org.eblocker.server.common.data.dashboard.DashboardCard;
import org.eblocker.server.http.service.DashboardService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SchemaMigrationVersion33Test {

    private DataSource dataSource;
    private DashboardService dashboardService;
    private SchemaMigration migration;

    private UserMigrationService userMigrationService;
    //    private JedisPool jedisPool;
    //    private Jedis jedis;
    //    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        dashboardService = new DashboardService(dataSource);

        //        jedis = Mockito.mock(Jedis.class);
        //        jedisPool = Mockito.mock(JedisPool.class);
        //        objectMapper = new ObjectMapper();
        userMigrationService = Mockito.mock(UserMigrationService.class); // new UserMigrationService(jedisPool, objectMapper);

        // Mocks
        // DNS Statistics card for Alice still has old feature "WOL" required
        UserModuleOld userA = generateUserModule(1, 1, "alice", true, ProductFeature.WOL);
        //        String userAJson = objectMapper.writeValueAsString(userA);
        // eBlocker Mobile card for Bob already has new feature "PRO" required
        UserModuleOld userB = generateUserModule(2, 2, "bob", false, null);
        //        String userBJson = objectMapper.writeValueAsString(userB);
        List<UserModuleOld> userList = new ArrayList<>();
        userList.add(userA);
        userList.add(userB);

        Mockito.when(userMigrationService.getAll()).thenReturn(userList);

        //        TreeSet<String> keys = new TreeSet<>();
        //        keys.add("UserModule:1");
        //        keys.add("UserModule:2");

        // Mock direct access to data store
        //        Mockito.when(jedis.get("UserModule:1")).thenReturn(userAJson);
        //        Mockito.when(jedis.get("UserModule:2")).thenReturn(userBJson);
        //        Mockito.when(jedisPool.getResource()).thenReturn(jedis);
        //        Mockito.when(jedis.keys(any(String.class))).thenReturn(keys);

        migration = new SchemaMigrationVersion33(dataSource, dashboardService, userMigrationService);
    }

    @Test
    public void getSourceVersion() {
        Assert.assertEquals("32", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() {
        Assert.assertEquals("33", migration.getTargetVersion());
    }

    @Test
    public void testUserProfileUpdated() {

        migration.migrate();

        ArgumentCaptor<UserModuleOld> userModule = ArgumentCaptor.forClass(UserModuleOld.class);

        // Inspect saved data for User Module "Alice"
        Mockito.verify(userMigrationService, Mockito.times(3)).save(userModule.capture(), Mockito.eq(1));
        Assert.assertEquals(ProductFeature.PRO.name(),
            userModule.getValue().getDashboardCards().stream()
                .filter(c -> c.getId() == dashboardService.generateDnsStatisticsCard().getId()).findFirst()
                .get().getRequiredFeature());

        // Bob saved twice for reordering the cards, but not for the change of the required feature.
        Mockito.verify(userMigrationService, Mockito.times(2)).save(userModule.capture(), Mockito.eq(2));

        Mockito.verify(dataSource).setVersion("33");
    }

    private UserModuleOld generateUserModule(int id, int associatedProfileId, String name, boolean changeRequiredFeature,
                                             ProductFeature newRequiredFeatre) {
        DashboardService realDashboardService = new DashboardService(dataSource);
        List<DashboardCard> dashboardCards = realDashboardService.generateDashboardCards();
        // The DNS Statistics card is expected to still show "WOL" as the
        // required featureset
        if (changeRequiredFeature) {
            DashboardCard oldStatisticsCard = dashboardCards.stream()
                .filter(c -> c.getId() == realDashboardService.generateDnsStatisticsCard().getId()).findFirst()
                .get();
            DashboardCard newStatisticsCard = new DashboardCard(oldStatisticsCard.getId(), newRequiredFeatre.name(),
                oldStatisticsCard.getTranslateSuffix(), oldStatisticsCard.getHtml(), oldStatisticsCard.isVisible(),
                oldStatisticsCard.isAlwaysVisible(), oldStatisticsCard.getDefaultPos(), oldStatisticsCard.getCustomPos());
            dashboardCards = dashboardCards.stream().map(c -> c.getId() == newStatisticsCard.getId() ? newStatisticsCard : c)
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
