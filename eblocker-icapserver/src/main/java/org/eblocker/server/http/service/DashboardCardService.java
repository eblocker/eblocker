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
package org.eblocker.server.http.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.UserRole;
import org.eblocker.server.common.data.dashboard.AccessRight;
import org.eblocker.server.common.data.dashboard.DashboardColumnsView;
import org.eblocker.server.common.data.dashboard.ParentalControlCard;
import org.eblocker.server.common.data.dashboard.UiCard;
import org.eblocker.server.common.data.dashboard.UiCardColumnPosition;
import org.eblocker.server.common.data.dashboard.UiCardDefaultPositionMapping;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Singleton
@SubSystemService(value = SubSystem.SERVICES)
public class DashboardCardService {
    private final DataSource dataSource;

    @Inject
    public DashboardCardService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public DashboardColumnsView getNewDashboardCardColumns(UserRole userRole) {
        List<UiCard> allCards = getAll();
        UserRole tmpUserRole = userRole == null ? UserRole.OTHER : userRole;
        Map<Integer, UiCardColumnPosition[]> cardIdPositionMapping = UiCardDefaultPositionMapping.get(allCards);
        return getUpdatedColumnsView(new DashboardColumnsView(cardIdPositionMapping), tmpUserRole, Collections.emptyList());
    }

    /**
     * Create a new parental control dashboard card. Required e.g. for creation of cards for CHILD users. It should not
     * be necessary to call this method from the UI. The UserService calls this method to create dashboard cards
     * when needed.
     *
     * @param referencingUserId the (CHILD) user that this card has been created for (to maintain specific order of that child-card)
     * @param requiredFeature   License that is necessary to display this card (e.g. for CHILD card it is 'FAM')
     *                          e.g. ProductFeature.FAM.name()
     * @param name              used in UI for translations (json prefix)
     * @return the saved card.
     */
    public UiCard createParentalControlCard(int referencingUserId, String name, String requiredFeature) {
        if (getByReferencingUserId(referencingUserId) == null) {
            // dashboard card does not exist: create
            int nextId = dataSource.nextId(UiCard.class);
            UiCard newCard = new ParentalControlCard(nextId, name, requiredFeature, Collections.singletonList(UserRole.PARENT), null, referencingUserId);
            return saveNewDashboardCard(newCard);
        }
        return null;
    }

    public Integer removeParentalControlCard(int referencingUserId) {
        UiCard toBeDeleted = getByReferencingUserId(referencingUserId);
        if (toBeDeleted != null) {
            dataSource.delete(ParentalControlCard.class, toBeDeleted.getId());
            return toBeDeleted.getId();
        } else {
            return null;
        }
    }

    public void removeCardByName(String name) {
        getAll().forEach(card -> {
            if (card.getName().equals(name)) {
                dataSource.delete(UiCard.class, card.getId());
            }
        });
    }

    public UiCard saveNewDashboardCard(UiCard card) {
        return dataSource.save(card, card.getId());
    }

    public UiCard saveDashboardCardIfNotExists(UiCard card) {
        if (getAll().stream().noneMatch(c -> c.getName().equals(card.getName()))) {
            return dataSource.save(card, card.getId());
        }
        return null;
    }

    public List<UiCard> getAll() {
        List<UiCard> allCards = new ArrayList<>();
        allCards.addAll(dataSource.getAll(UiCard.class));
        allCards.addAll(dataSource.getAll(ParentalControlCard.class));
        return allCards;
    }

    public List<UiCard> getDashboardCards(DashboardColumnsView columns) {
        return getAll().stream().filter(card -> columns.contains(card.getId())).collect(Collectors.toList());
    }

    /**
     * This method finds all cards that are either valid for a user and are not yet included in the user's DashboardColumnsView
     * or that are still in the user's DashboardColumnsView, but are no longer valid for that user.
     * The validity of a card for a user is given by the user's role and the accessRights.
     *
     * @return updated DashboardColumnsView
     */
    public DashboardColumnsView getUpdatedColumnsView(DashboardColumnsView columns, UserRole userRole, List<AccessRight> accessRights) {
        List<UiCard> allCards = getAll();
        addMissingCard(allCards, columns, userRole, accessRights);
        removeOutdatedCards(allCards, columns, userRole, accessRights);
        return columns;
    }

    public UiCard getById(List<UiCard> cards, int id) {
        return cards.stream().filter(card -> card.getId() == id).findFirst().orElse(null);
    }

    private void removeOutdatedCards(List<UiCard> allCards, DashboardColumnsView columns, UserRole userRole, List<AccessRight> accessRights) {
        // - card does not exist anymore (child parental control deleted, due to child-user removed) OR BOTH
        //     - user role does not allow the card (user role of user has changed, e.g. PARENT to CHILD) AND
        //     - the access rights are not valid anymore (list accessRights does not contain required rule, e.g. fragFINN disabled)
        Predicate<UiCardColumnPosition> notContained = pos -> !contains(allCards, pos.getId());
        Predicate<UiCardColumnPosition> notAllowed = pos -> userRole == null || !isCardAllowed(allCards, pos.getId(), userRole);
        Predicate<UiCardColumnPosition> noAccessRights = pos -> !hasCardsWithAccessRights(allCards, pos.getId(), accessRights);

        Predicate<UiCardColumnPosition> predicate;

        // when no access rights are specified, the cards are shown based on user role
        // when access rights are specified, the cards are shown based on access rights
        if (accessRights.isEmpty()) {
            predicate = notContained.or(notAllowed);
        } else {
            predicate = notContained.or(noAccessRights);
        }

        columns.getOneColumn().removeAll(columns.getOneColumn().stream().filter(predicate).collect(Collectors.toList()));
        columns.getTwoColumn().removeAll(columns.getTwoColumn().stream().filter(predicate).collect(Collectors.toList()));
        columns.getThreeColumn().removeAll(columns.getThreeColumn().stream().filter(predicate).collect(Collectors.toList()));
    }

    /**
     * If accessRights contains a right, we only want to keep cards that also have that right within
     * getRequiredAccessRights(). But if accessRights isEmpty, we want to return all cards regardless of
     * the getRequiredAccessRights()
     */
    private boolean hasCardsWithAccessRights(List<UiCard> cards, int cardId, List<AccessRight> accessRights) {
        Predicate<UiCard> accessRulePredicate = card -> card.getId() == cardId &&
                card.getRequiredAccessRights() != null &&
                accessRights.stream().anyMatch(right -> card.getRequiredAccessRights().contains(right));
        return cards.stream().anyMatch(accessRulePredicate);
    }

    private boolean isCardAllowed(List<UiCard> cards, int cardId, UserRole userRole) {
        Predicate<UiCard> predicate = card -> card.getId() == cardId && (card.getRequiredUserRoles() != null && card.getRequiredUserRoles().contains(userRole));
        return cards.stream().anyMatch(predicate);
    }

    /**
     * We need to stream the cards, because we are looking for missing cards in 'columns' which can only
     * be found in allCards list.
     *
     * @param allCards     all cards
     * @param columns      current (possibly outdated) columns representation of user (card's ID and positions)
     * @param userRole     current user role of user in question
     * @param accessRights current access rights granted to user (e.g. fragFINN, if profile has fragFINN exception)
     */
    private void addMissingCard(List<UiCard> allCards, DashboardColumnsView columns, UserRole userRole, List<AccessRight> accessRights) {
        // card is not yet contained in columns of user
        Predicate<UiCard> notContained = card -> !columns.contains(card.getId());
        // card is valid for userRole
        Predicate<UiCard> isAllowed = card -> card.getRequiredUserRoles() != null && userRole != null && card.getRequiredUserRoles().contains(userRole);
        // card needs to have at least one accessRule from accessRights list, if card has accessRights defined (e.g. fragFINN)
        Predicate<UiCard> hasAccessRights = card -> card.getRequiredAccessRights() != null && card.getRequiredAccessRights().stream().anyMatch(accessRights::contains);

        Predicate<UiCard> predicate;

        if (accessRights.isEmpty()) {
            predicate = notContained.and(isAllowed);
        } else {
            predicate = notContained.and(hasAccessRights);
        }

        allCards.
                stream().
                filter(predicate).forEach((card) -> {
            UiCardColumnPosition[] pos = UiCardDefaultPositionMapping.getStaticPositions(card);
            columns.getOneColumn().add(pos[0]);
            columns.getTwoColumn().add(pos[1]);
            columns.getThreeColumn().add(pos[2]);
        });
    }

    private UiCard getByReferencingUserId(int id) {
        return getAll().stream().
                filter(card -> card instanceof ParentalControlCard && ((ParentalControlCard) card).getReferencingUserId() == id).
                findFirst().
                orElse(null);
    }

    private boolean contains(List<UiCard> allCards, int cardId) {
        return allCards.stream().anyMatch(c -> c.getId() == cardId);
    }
}
