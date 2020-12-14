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
import org.eblocker.server.common.data.UserModuleOld;
import org.eblocker.server.common.data.UserRole;
import org.eblocker.server.common.data.dashboard.DashboardCard;
import org.eblocker.server.common.data.dashboard.DashboardCardPosition;
import org.eblocker.server.common.data.dashboard.UiCard;
import org.eblocker.server.common.data.dashboard.UiCardColumnPosition;
import org.eblocker.server.http.service.DashboardCardService;
import org.eblocker.server.http.service.DashboardService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;

public class SchemaMigrationVersion39Test {

    private DataSource dataSource;
    private DashboardService dashboardService;
    private DashboardCardService dashboardCardService;
    private UserMigrationService userMigrationService;
    private SchemaMigration migration;

    private UserModuleOld userModule;
    private UserModuleOld userModule2;
    private UserModuleOld userModule3;

    private UserModule newUserModule;
    private UserModule newUserModule2;
    private UserModule newUserModule3;

    private List<UiCard> savedCards = new ArrayList<>();
    private List<UserModuleOld> userModuleOlds = new ArrayList<>();
    private List<UserModule> userModuleNew = new ArrayList<>();

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        dashboardCardService = new DashboardCardService(dataSource);
        dashboardService = new DashboardService(dataSource);
        userMigrationService = Mockito.mock(UserMigrationService.class); // new UserMigrationService(jedisPool, objectMapper);
        migration = new SchemaMigrationVersion39(dataSource, dashboardCardService, userMigrationService);

        Mockito.when(dataSource.nextId(eq(UserModule.class))).thenReturn(1001);
        Mockito.when(dataSource.getAll(UiCard.class)).thenReturn(savedCards);
        Mockito.when(dataSource.getAll(UserModule.class)).thenReturn(userModuleNew);
        Mockito.when(userMigrationService.getAll()).thenReturn(userModuleOlds);

        /*
         * when a dashboard card is saved to the datastore, we mock this here by also adding it to our mock-list.
         */
        Mockito.when(dataSource.save(Mockito.any(UiCard.class), Mockito.anyInt())).thenAnswer(invocationOnMock -> {
            UiCard saved = invocationOnMock.getArgument(0);
            savedCards.add(saved);
            return saved;
        });

        // Mock existing dashboard card list to verify that migration of cards works
        List<DashboardCard> filledList = new ArrayList<>();
        DashboardCardPosition[] defaultPosPause = {
            new DashboardCardPosition(1, 1),
            new DashboardCardPosition(1, 1),
            new DashboardCardPosition(1, 1) };
        filledList.add(new DashboardCard(-10, null, "PAUSE_CARD", null, true, true, defaultPosPause, null));
        DashboardCardPosition[] defaultPosOt = {
            new DashboardCardPosition(1, 1),
            new DashboardCardPosition(1, 1),
            new DashboardCardPosition(1, 1) };
        filledList.add(new DashboardCard(-11, null, "ONLINE_TIME_CARD", null, true, true, defaultPosOt, null));
        DashboardCardPosition[] defaultPosSsl = {
            new DashboardCardPosition(1, 13),
            new DashboardCardPosition(1, 7),
            new DashboardCardPosition(2, 5) };
        filledList.add(new DashboardCard(-12, null, "SSL_CARD", null, true, true, defaultPosSsl, null));
        DashboardCardPosition[] defaultPosMsg = {
            new DashboardCardPosition(1, 12),
            new DashboardCardPosition(2, 4),
            new DashboardCardPosition(3, 2) };
        filledList.add(new DashboardCard(-13, null, "MESSAGE_CARD", null, true, true, defaultPosMsg, null));

        List<DashboardCard> completeList = dashboardService.generateDashboardCards();
        userModule = new UserModuleOld(1, 1, "user1", null, null, null, false, null, null, completeList, null, null);
        userModule2 = new UserModuleOld(2, 2, "user2", null, null, null, false, null, null, filledList, null, null);
        userModule3 = new UserModuleOld(3, 3, "user3", null, null, UserRole.CHILD, false, null, null, completeList, null, null);
        userModuleOlds.add(userModule);
        userModuleOlds.add(userModule2);
        userModuleOlds.add(userModule3);

        Mockito.when(dataSource.save(Mockito.any(UserModule.class), eq(userModule.getId()))).thenAnswer(invocationOnMock -> {
            UserModule newUser = invocationOnMock.getArgument(0);
            newUserModule = newUser;
            userModuleNew.add(newUserModule);
            return newUser;
        });
        Mockito.when(dataSource.save(Mockito.any(UserModule.class), eq(userModule2.getId()))).thenAnswer(invocationOnMock -> {
            UserModule newUser = invocationOnMock.getArgument(0);
            newUserModule2 = newUser;
            userModuleNew.add(newUserModule2);
            return newUser;
        });
        Mockito.when(dataSource.save(Mockito.any(UserModule.class), eq(userModule3.getId()))).thenAnswer(invocationOnMock -> {
            UserModule newUser = invocationOnMock.getArgument(0);
            newUserModule3 = newUser;
            userModuleNew.add(newUserModule3);
            return newUser;
        });
    }

    @Test
    public void getSourceVersion() {
        assertEquals("38", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() {
        assertEquals("39", migration.getTargetVersion());
    }

    @Test
    public void migrate() {
        migration.migrate();
        Mockito.verify(dataSource).setVersion("39");
    }

    @Test
    public void testUserModelMigration() {
        migration.migrate();
        /*
         * Migration should convert old users 'userModule' and 'userModule2' into the new model.
         * So both 'newUserModule' and 'newUserModule2' must be set correctly after migration
         */
        assertNotNull(newUserModule);
        assertNotNull(newUserModule2);
        // name must be the same
        assertEquals(newUserModule.getName(), userModule.getName());
        assertEquals(newUserModule2.getName(), newUserModule2.getName());
        assertEquals(newUserModule.getNameKey(), userModule.getNameKey());
        assertEquals(newUserModule2.getNameKey(), newUserModule2.getNameKey());

        // we want to maintain the ID to keep user/device relations
        assertEquals(newUserModule.getId(), userModule.getId());
        assertEquals(newUserModule2.getId(), newUserModule2.getId());

        assertEquals(newUserModule.getAssociatedProfileId(), userModule.getAssociatedProfileId());
        assertEquals(newUserModule2.getAssociatedProfileId(), newUserModule2.getAssociatedProfileId());

        assertEquals(newUserModule.getBirthday(), userModule.getBirthday());
        assertEquals(newUserModule2.getBirthday(), newUserModule2.getBirthday());

        assertEquals(newUserModule.getUserRole(), userModule.getUserRole());
        assertEquals(newUserModule2.getUserRole(), newUserModule2.getUserRole());

        assertEquals(newUserModule.isSystem(), userModule.isSystem());
        assertEquals(newUserModule2.isSystem(), newUserModule2.isSystem());

        assertNotNull(newUserModule.getDashboardColumnsView());
        assertNotNull(newUserModule2.getDashboardColumnsView());

        Mockito.verify(dataSource, Mockito.times(1)).save(Mockito.any(UserModule.class), Mockito.eq(1));
        Mockito.verify(dataSource, Mockito.times(1)).delete(UserModule.class, 1);
        Mockito.verify(dataSource, Mockito.times(1)).save(Mockito.any(UserModule.class), Mockito.eq(2));
        Mockito.verify(dataSource, Mockito.times(1)).delete(UserModule.class, 2);
    }

    @Test
    public void testNumberOfDashboardCards() {
        AtomicInteger id = new AtomicInteger(0);
        Mockito.when(dataSource.nextId(eq(UiCard.class))).thenAnswer(invocation -> {
            return id.incrementAndGet();
        });

        /*
         * The actual migration
         */
        migration.migrate();

        // there a 14 fixed dashboard cards in the migration
        final int NUMBER_OF_ALL_CARDS = 14;

        assertEquals(savedCards.size(), NUMBER_OF_ALL_CARDS);
        // all cards have to be saved to the data store
        Mockito.verify(dataSource, Mockito.times(savedCards.size())).save(Mockito.any(UiCard.class), Mockito.anyInt());

        /*
         * Migration should convert dashboard cards into DashboardCardsColumns object.
         * Either the user already has cards (newUserModule2 e.g. has 4 -- just test, not practical example) or
         * all dashboard cards are created (as for newUserModule)
         */
        final int NUMBER_OF_CARDS_OF_USER = 4;
        assertEquals(newUserModule.getDashboardColumnsView().getOneColumn().size(), NUMBER_OF_ALL_CARDS);
        assertEquals(newUserModule2.getDashboardColumnsView().getOneColumn().size(), NUMBER_OF_CARDS_OF_USER);

        /*
         * Now test that the two-column representation also contains all cards
         */
        assertEquals(newUserModule.getDashboardColumnsView().getTwoColumn().size(), NUMBER_OF_ALL_CARDS);
        assertEquals(newUserModule2.getDashboardColumnsView().getTwoColumn().size(), NUMBER_OF_CARDS_OF_USER);

        /*
         * Now test that the three-column representation also contains all cards
         */
        assertEquals(newUserModule.getDashboardColumnsView().getThreeColumn().size(), NUMBER_OF_ALL_CARDS);
        assertEquals(newUserModule2.getDashboardColumnsView().getThreeColumn().size(), NUMBER_OF_CARDS_OF_USER);
    }

    /**
     * Test that the order of dashboard card is maintained for the default position (user has not re-ordered the cards)
     */
    @Test
    public void testDefaultCardPosition() {
        AtomicInteger id = new AtomicInteger(1);
        Mockito.when(dataSource.nextId(eq(UiCard.class))).thenAnswer(invocation -> {
            return id.incrementAndGet();
        });

        /*
         * The actual migration
         */

        migration.migrate();

        // The migration creates the DashboardColumnsView of the user based on the newly saved dashboard cards. These
        // cards have other IDs than the old static dashboard cards. So when we compare the IDs from the DashboardColumnsView
        // of the user, we need to use the new IDs of the cards in savedCards (list of newly saved cards).
        // The newUserModule dashboardColumnsView positions need to match the userModules dashboard card positions

        newUserModule.getDashboardColumnsView().getOneColumn().forEach(rep -> {
            UiCard newCard = getNewCardById(savedCards, rep.getId());
            DashboardCard oldCard = getOldCardByName(userModule.getDashboardCards(), newCard.getName());
            verifyCardDefaultPositionAgainstRepresentation(oldCard, rep, 1);
        });
        newUserModule.getDashboardColumnsView().getTwoColumn().forEach(rep -> {
            UiCard newCard = getNewCardById(savedCards, rep.getId());
            DashboardCard oldCard = getOldCardByName(userModule.getDashboardCards(), newCard.getName());
            verifyCardDefaultPositionAgainstRepresentation(oldCard, rep, 2);
        });
        newUserModule.getDashboardColumnsView().getThreeColumn().forEach(rep -> {
            UiCard newCard = getNewCardById(savedCards, rep.getId());
            DashboardCard oldCard = getOldCardByName(userModule.getDashboardCards(), newCard.getName());
            verifyCardDefaultPositionAgainstRepresentation(oldCard, rep, 3);
        });
    }

    /**
     * Test that the order of dashboard card is maintained for custom positions (user has re-ordered the cards)
     */
    @Test
    public void testCustomCardPosition() {
        AtomicInteger id = new AtomicInteger(1);
        Mockito.when(dataSource.nextId(eq(UiCard.class))).thenAnswer(invocation -> {
            return id.incrementAndGet();
        });

        // ** manually set the custom position for this test (in practice this is done when the user drags and drops a card)
        List<DashboardCard> updateListWithCustomPos = new ArrayList<>();
        userModule.getDashboardCards().forEach(card -> {
            updateListWithCustomPos.add(setCustomPosition(card));
        });

        // ** now actually set the new dashboard card layout (customPos) for the user, so that the migration will
        // generate the correct DashboardColumnsView
        userModule.setDashboardCards(updateListWithCustomPos); // update cards that now have a custom position set.


        /*
         * The actual migration
         */
        migration.migrate();

        /*
         * We want to verify that the custom position of an old card has been used to determine the position of the
         * migrated / new card. For this test we need to map each newUserModules position by card ID to the old users
         * dashboard card's custom position. So we get the newCard first (by ID saved in new user) and use the name
         * for mapping to the old dashboard card. This old card's custom position must match the new cards position.
         */
        newUserModule.getDashboardColumnsView().getOneColumn().forEach(rep -> {
            UiCard newCard = getNewCardById(savedCards, rep.getId());
            DashboardCard card = getOldCardByName(userModule.getDashboardCards(), newCard.getName());
            verifyCardCustomPositionAgainstRepresentation(card, rep, 1);
        });
        newUserModule.getDashboardColumnsView().getTwoColumn().forEach(rep -> {
            UiCard newCard = getNewCardById(savedCards, rep.getId());
            DashboardCard card = getOldCardByName(userModule.getDashboardCards(), newCard.getName());
            verifyCardCustomPositionAgainstRepresentation(card, rep, 2);
        });
        newUserModule.getDashboardColumnsView().getThreeColumn().forEach(rep -> {
            UiCard newCard = getNewCardById(savedCards, rep.getId());
            DashboardCard card = getOldCardByName(userModule.getDashboardCards(), newCard.getName());
            verifyCardCustomPositionAgainstRepresentation(card, rep, 3);
        });
    }

    private void verifyCardDefaultPositionAgainstRepresentation(DashboardCard card, UiCardColumnPosition rep, int numOfColumns) {
        assertEquals(card.getDefaultPos()[numOfColumns - 1].getColumn(), rep.getColumn());
        assertEquals(card.getDefaultPos()[numOfColumns - 1].getOrder(), rep.getIndex());
        assertEquals(card.isVisible(), rep.isVisible());
    }

    private void verifyCardCustomPositionAgainstRepresentation(DashboardCard card, UiCardColumnPosition rep, int numOfColumns) {
        assertEquals(card.getCustomPos()[numOfColumns - 1].getColumn(), rep.getColumn());
        assertEquals(card.getCustomPos()[numOfColumns - 1].getOrder(), rep.getIndex());
        assertEquals(card.isVisible(), rep.isVisible());
    }

    /**
     * Just set a testable custom position.
     *
     * @param card
     * @return
     */
    private DashboardCard setCustomPosition(DashboardCard card) {

        DashboardCardPosition[] cp = {
            new DashboardCardPosition(1, card.getId()),
            new DashboardCardPosition(2, card.getId()),
            new DashboardCardPosition(3, card.getId())
        };

        return new DashboardCard(card.getId(),
            card.getRequiredFeature(),
            card.getTranslateSuffix(),
            card.getHtml(),
            card.isVisible(),
            card.isAlwaysVisible(),
            card.getDefaultPos(),
            cp);

    }

    private UiCard getNewCardById(List<UiCard> list, int id) {
        return list.stream()
            .filter(c -> c.getId() == id).findFirst()
            .get();
    }

    private UiCard getCardByName(List<UiCard> list, String name) {
        return list.stream()
            .filter(c -> c.getName().equals(name)).findFirst()
            .get();
    }

    private DashboardCard getOldCardByName(List<DashboardCard> list, String name) {
        return list.stream()
            .filter(c -> c.getTranslateSuffix().equals(name + "_CARD") || (c.getTranslateSuffix().equals("ANON_CARD") && name.equals("ANONYMIZATION"))).findFirst()
            .get();
    }

}
