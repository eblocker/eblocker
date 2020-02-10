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
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.UserRole;
import org.eblocker.server.common.data.dashboard.AccessRight;
import org.eblocker.server.common.data.dashboard.DashboardColumnsView;
import org.eblocker.server.common.data.dashboard.UiCard;
import org.eblocker.server.common.data.dashboard.UiCardColumnPosition;
import org.eblocker.server.http.service.DashboardCardService;
import org.eblocker.registration.ProductFeature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mockito;

public class SchemaMigrationVersion40Test {

    private DataSource dataSource;
    private DashboardCardService dashboardCardService;
    private SchemaMigration migration;

    private UserModule alice;
    private UserModule bob;
    private UserModule carla;
    private UserModule dante;

    private UserProfileModule profile1;
    private UserProfileModule profile2;
    private UserProfileModule profileStandard;

    private List<UiCard> savedCards = new ArrayList<>();
    private List<UserProfileModule> savedProfiles = new ArrayList<>();
    private List<UserModule> userList = new ArrayList<>();

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        dashboardCardService = new DashboardCardService(dataSource);
        migration = new SchemaMigrationVersion40(dataSource, dashboardCardService);

        Mockito.when(dataSource.getAll(UiCard.class)).thenReturn(savedCards);

        AtomicInteger profileId = new AtomicInteger(105);
        Mockito.when(dataSource.nextId(eq(UserProfileModule.class))).thenAnswer(invocation -> {
            return profileId.incrementAndGet();
        });

        AtomicInteger cardId = new AtomicInteger(1000);
        Mockito.when(dataSource.nextId(eq(UiCard.class))).thenAnswer(invocation -> {
            return cardId.incrementAndGet();
        });

        Set<Integer> inAccessibleSitesPackagesProfile1 = new HashSet<>();
        Set<Integer> accessibleSitesPackagesProfile2 = new HashSet<>();
        inAccessibleSitesPackagesProfile1.add(10);
        accessibleSitesPackagesProfile2.add(1);

        profile1 = new UserProfileModule(101, "Test Profile One", "descr", "TEST_PROFILE", "", false, false, null, inAccessibleSitesPackagesProfile1, UserProfileModule.InternetAccessRestrictionMode.BLACKLIST, null, null, false, null, null);
        profile2 = new UserProfileModule(102, "Test Profile Two", "descr", "TEST_PROFILE", "", false, false, accessibleSitesPackagesProfile2,null, UserProfileModule.InternetAccessRestrictionMode.WHITELIST,null,null,false,null, null);
        profileStandard = new UserProfileModule(DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID,"Standard Profile","descr","STANDARD_PROFILE","",true,false,null,null,null,null,null,false,null, null);

        Mockito.when(dataSource.getAll(UserModule.class)).thenReturn(userList);
        Mockito.when(dataSource.get(UserProfileModule.class, profile1.getId())).thenReturn(profile1);
        Mockito.when(dataSource.get(UserProfileModule.class, profile2.getId())).thenReturn(profile2);
        Mockito.when(dataSource.get(UserProfileModule.class, profileStandard.getId())).thenReturn(profileStandard);

        alice = new UserModule(1001, 101, "Alice", "TEST_USER", null, null, false, null, null, null, null, null);
        bob = new UserModule(1002, 102, "Bob", "TEST_USER", null, null, false, null, null, null, null, null);
        carla = new UserModule(1003, 101, "Carla", "TEST_USER", null, null, false, null, null, null, null, null);
        // profile 33 does not exist. Testing if standard profile is used.
        dante = new UserModule(1004, 33, "Dante", "TEST_USER", null, null, false, null, null, null, null, null);

        userList.add(alice);
        userList.add(bob);
        userList.add(carla);
        userList.add(dante);

        /*
         * when a dashboard card is saved to the datastore, we mock this here by also adding it to our mock-list.
         */
        Mockito.when(dataSource.save(Mockito.any(UiCard.class), Mockito.anyInt())).thenAnswer(invocationOnMock -> {
            UiCard saved = invocationOnMock.getArgument(0);
            savedCards.add(saved);
            return saved;
        });

        Mockito.when(dataSource.save(Mockito.any(UserProfileModule.class), Mockito.anyInt())).thenAnswer(invocationOnMock -> {
            UserProfileModule saved = invocationOnMock.getArgument(0);
            savedProfiles.add(saved);
            return saved;
        });

    }

    @Test
    public void getSourceVersion() {
        assertEquals("39", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() {
        assertEquals("40", migration.getTargetVersion());
    }

    @Test
    public void migrate() {
        migration.migrate();
        Mockito.verify(dataSource).setVersion("40");
    }

    @Test
    public void testCreationOfFragFinnCard() {
//        Mockito.when(dataSource.nextId(eq(UiCard.class))).thenReturn(1001);
        migration.migrate();
        Mockito.verify(dataSource, Mockito.times(1)).save(Mockito.any(UiCard.class), Mockito.eq(1001));
        assertEquals(1, savedCards.size());
        assertEquals("FRAG_FINN", savedCards.get(0).getName());
    }

    @Test
    public void testUserProfileMigration() {

        profile1.setControlmodeUrls(true);
        profile2.setControlmodeUrls(true);

        migration.migrate();

        // verify that profiles are saved with expected ID
        Mockito.verify(dataSource, Mockito.times(1)).save(Mockito.any(UserProfileModule.class), Mockito.eq(106));
        Mockito.verify(dataSource, Mockito.times(1)).save(Mockito.any(UserProfileModule.class), Mockito.eq(107));
        Mockito.verify(dataSource, Mockito.times(1)).save(Mockito.any(UserProfileModule.class), Mockito.eq(108));

        //
        assertEquals(UserRole.CHILD, alice.getUserRole());
        assertEquals(UserRole.CHILD, bob.getUserRole());
        assertEquals(UserRole.CHILD, carla.getUserRole());
        assertEquals(UserRole.PARENT, dante.getUserRole());

        Mockito.verify(dataSource, Mockito.times(1)).save(Mockito.any(UserModule.class), Mockito.eq(alice.getId()));
        Mockito.verify(dataSource, Mockito.times(1)).save(Mockito.any(UserModule.class), Mockito.eq(bob.getId()));
        Mockito.verify(dataSource, Mockito.times(1)).save(Mockito.any(UserModule.class), Mockito.eq(carla.getId()));
        // parents get updated twice, due to creation of parental control cards
        Mockito.verify(dataSource, Mockito.times(2)).save(Mockito.any(UserModule.class), Mockito.eq(dante.getId()));
    }

    @Test
    public void testGenerateParentalControlCardsForEachChildUser() {
        profile1.setControlmodeUrls(true);
        profile2.setControlmodeUrls(true);

        migration.migrate();

        Mockito.verify(dataSource, Mockito.times(1)).save(Mockito.any(UiCard.class), Mockito.eq(1002));
        Mockito.verify(dataSource, Mockito.times(1)).save(Mockito.any(UiCard.class), Mockito.eq(1003));

        assertEquals(1002, savedCards.get(1).getId());
        assertEquals("PARENTAL_CONTROL", savedCards.get(1).getName());
        assertEquals(1003, savedCards.get(2).getId());
        assertEquals("PARENTAL_CONTROL", savedCards.get(2).getName());
    }

    @Test
    public void testAddLimboRightsToUserCard() {
        UiCard userCard = new UiCard(111, "USER", null, null, null);
        savedCards.add(userCard);

        Mockito.when(dataSource.getAll(UiCard.class)).thenReturn(Collections.singletonList(userCard));

        migration.migrate();

        Mockito.verify(dataSource, Mockito.times(1)).delete(UiCard.class, userCard.getId());
        Mockito.verify(dataSource, Mockito.times(1)).save(Mockito.any(UiCard.class), Mockito.eq(userCard.getId()));

        assertEquals(savedCards.get(2).getId(), userCard.getId());
        assertEquals(savedCards.get(2).getRequiredAccessRights().size(), 1);
        assertEquals(savedCards.get(2).getRequiredAccessRights().get(0), AccessRight.USER);
    }

    @Test
    public void testUpdateLimboUserDashboardCards() {
        UiCard userCard = new UiCard(111, "USER", null, null, Collections.singletonList(AccessRight.USER));
        UiCard fragFinnCard = new UiCard(222, "FRAG_FINN", null, null, Collections.singletonList(AccessRight.FRAG_FINN));
        UiCard otherCard = new UiCard(333, "OTHER", null, null, null);
        savedCards.add(userCard);
        savedCards.add(fragFinnCard); // should not be added to limbo user
        savedCards.add(otherCard); // should not be added to limbo user

        Mockito.when(dataSource.getAll(UiCard.class)).thenReturn(Collections.singletonList(userCard));

        UserModule limboUser = new UserModule(DefaultEntities.PARENTAL_CONTROL_LIMBO_USER_ID, 33, "Limbo", "TEST_LIMBO_USER", null, null, false, null, null, null, null, null);
        // list to save limboUser in on save
        List<UserModule> updatedLimbo = new ArrayList<>();
        // make sure user is found on getAll(UserModule.class)
        userList.add(limboUser);

        Mockito.when(dataSource.save(Mockito.any(UserModule.class), Mockito.eq(limboUser.getId()))).thenAnswer(invocationOnMock -> {
            UserModule saved = invocationOnMock.getArgument(0);
            if (!updatedLimbo.contains(saved)) {
                updatedLimbo.add(saved);
            }
            return saved;
        });

        migration.migrate();

        // should only save limbo user once
        assertEquals(1, updatedLimbo.size());
        // should only have user card
        assertEquals(1, updatedLimbo.get(0).getDashboardColumnsView().getOneColumn().size());
        assertEquals(userCard.getId(), updatedLimbo.get(0).getDashboardColumnsView().getOneColumn().get(0).getId());
    }

    private List<UserRole> getDefaultRoles() {
        return Arrays.asList(UserRole.PARENT, UserRole.OTHER);
    }

    @Test
    public void testRemoveNotExistingCards() {
        UiCardColumnPosition[] list = new UiCardColumnPosition[3];
        UiCard userCard = new UiCard(111, "USER", ProductFeature.BAS.name(), getDefaultRoles(), null);
        UiCard filterCard = new UiCard(222, "FILTER", ProductFeature.BAS.name(), getDefaultRoles(), null);
        UiCard otherCard = new UiCard(333, "OTHER", ProductFeature.BAS.name(), getDefaultRoles(), null);
        savedCards.add(userCard);
        savedCards.add(filterCard);
        savedCards.add(otherCard);

        List<UiCard> copy = new ArrayList<>();
        copy.addAll(savedCards);
        Mockito.when(dataSource.getAll(UiCard.class)).thenReturn(copy);

        DashboardColumnsView dcv = new DashboardColumnsView();
        list[0] = new UiCardColumnPosition(userCard.getId(), 1, 1, true, false);
        list[1] = new UiCardColumnPosition(userCard.getId(), 2, 1, true, true);
        list[2] = new UiCardColumnPosition(userCard.getId(), 3, 1, true, true);
        dcv.addUiCard(list);

        list[0] = new UiCardColumnPosition(filterCard.getId(), 1, 2, true, false);
        list[1] = new UiCardColumnPosition(filterCard.getId(), 2, 2, true, true);
        list[2] = new UiCardColumnPosition(filterCard.getId(), 3, 2, true, true);
        dcv.addUiCard(list);

        list[0] = new UiCardColumnPosition(otherCard.getId(), 1, 3, true, false);
        list[1] = new UiCardColumnPosition(otherCard.getId(), 2, 3, true, true);
        list[2] = new UiCardColumnPosition(otherCard.getId(), 3, 3, true, true);
        dcv.addUiCard(list);

        // does not exist
        list[0] = new UiCardColumnPosition(123, 1, 4, true, false);
        list[1] = new UiCardColumnPosition(123, 2, 4, true, true);
        list[2] = new UiCardColumnPosition(123, 3, 4, true, true);
        dcv.addUiCard(list);

        UserModule user = new UserModule(1006, profileStandard.getId(), "User", "TEST_USER", null, UserRole.OTHER, false, null, null, null, null, null);
        user.setDashboardColumnsView(dcv);
        userList.add(user);

        assertEquals(4, user.getDashboardColumnsView().getOneColumn().size());

        migration.migrate();

        assertEquals(3, user.getDashboardColumnsView().getOneColumn().size());
    }

}
