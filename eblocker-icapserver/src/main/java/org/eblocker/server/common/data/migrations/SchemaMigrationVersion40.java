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
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaMigrationVersion40 implements SchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationVersion40.class);

    private final DataSource dataSource;
    private final DashboardCardService dashboardCardService;

    @Inject
    public SchemaMigrationVersion40(DataSource dataSource,
                                    DashboardCardService dashboardCardService) {
        this.dataSource = dataSource;
        this.dashboardCardService = dashboardCardService;
    }

    @Override
    public String getSourceVersion() {
        return "39";
    }

    @Override
    public String getTargetVersion() {
        return "40";
    }

    @Override
    public void migrate() {

        List<UserModule> users = dataSource.getAll(UserModule.class);

        // - create and save fragFINN card
        createAndSaveFragFinnCard(users);
        // - get profiles and create single profiles
        // - set userRole based on profile
        checkAndUpdateUserProfileRelations(users);
        // - generate child cards
        // - update parents
        generateParentalControlCardsForEachChildUser(users);
        // - make sure limbo user only has User card
        addLimboRightsToUserCard();
        updateLimboUserDashboardCards(users);
        // - remove not existing cards from users
        removeNotExistingCards(users);

        dataSource.setVersion("40");
    }

    private void updateLimboUserDashboardCards(List<UserModule> users) {
        users.stream().filter(user -> user.getId().equals(DefaultEntities.PARENTAL_CONTROL_LIMBO_USER_ID)).forEach(user -> {
            DashboardColumnsView dcv = getDashboardForUser(user, user.getUserRole());
            user.setDashboardColumnsView(dcv);
            dataSource.save(user, user.getId());
        });
    }

    private void addLimboRightsToUserCard() {
        List<UiCard> cards = dataSource.getAll(UiCard.class);
        cards.stream().filter(card -> card.getName().equals("USER")).forEach(card -> {
            UiCard updated =  new UiCard(card.getId(), "USER", ProductFeature.FAM.name(), getAllRoles(), Arrays.asList(AccessRight.USER));
            dataSource.delete(UiCard.class, card.getId());
            dataSource.save(updated, updated.getId());
        });
    }

    /**
     * Creates one new profile for each user if not already done so (isForSingleUser indicates that the user
     * already has a single profile.
     * The user is then updated to use that single profile. No other user must use that profile. The UI does
     * not provide means to assign any other profile.
     */
    private void checkAndUpdateUserProfileRelations(List<UserModule> users) {

        for (UserModule user: users) {
            Integer associatedProfileId = user.getAssociatedProfileId();
            UserProfileModule associatedProfile = null;
            UserProfileModule toBeCopied = null;

            /*
             * Get and copy the current profile of the use to ensure that we keep the profile settings
             * even for the new singleton profile.
             */
            if (associatedProfileId != null && associatedProfileId >= 0) {
                toBeCopied = dataSource.get(UserProfileModule.class, associatedProfileId);
                associatedProfile = toBeCopied != null ? toBeCopied.copy() : null;
            }

            // still null? get default profile.
            if (associatedProfile == null){
                toBeCopied = dataSource.get(UserProfileModule.class, DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID);
                associatedProfile = toBeCopied != null ? toBeCopied.copy() : null;
            }

            /*
             * Actually create a new profile and assign the profile to the user
             */
            if (!user.isSystem() && associatedProfile != null &&
                (associatedProfile.getNameKey() == null || !associatedProfile.getNameKey().equals("PROFILE_FOR_SINGLE_USER"))) {

                /*
                 * These flags (for UI switches) determine whether parental control is active (hasRestrictions)
                 * If profile does have restrictions, but all are switches off, it effectively has no restrictions.
                 * So we can use these flags for value 'hasRestrictions'.
                 */
                boolean controlTime = associatedProfile.isControlmodeTime();
                boolean controlUrl = associatedProfile.isControlmodeUrls();
                boolean controlMax = associatedProfile.isControlmodeMaxUsage();

                boolean hasRestrictions = controlTime || controlMax || controlUrl;

                // ** make sure user reviews the parental control settings
                associatedProfile.setParentalControlSettingValidated(!hasRestrictions);
                associatedProfile.setHidden(false);
                associatedProfile.setName("PROFILE_FOR_USER_" + user.getId());
                associatedProfile.setNameKey("PROFILE_FOR_SINGLE_USER");

                // update old profile name if not builtin (to avoid name conflict)
                // cannot delete yet, because is still be assigned
                if (!associatedProfile.isBuiltin()) {
                    toBeCopied.setName("Profile_"+ toBeCopied.getId());
                    dataSource.save(toBeCopied, toBeCopied.getId());
                }
                // save as new profile
                int id = dataSource.nextId(UserProfileModule.class);
                associatedProfile.setId(id);
                associatedProfile.setBuiltin(false);
                associatedProfile.setStandard(false);
                UserProfileModule newProfile = dataSource.save(associatedProfile, associatedProfile.getId());
                user.setAssociatedProfileId(newProfile.getId());

                // update user role
                if (hasRestrictions) {
                    user.setUserRole(UserRole.CHILD);
                } else {
                    user.setUserRole(UserRole.PARENT);
                }

                user.setDashboardColumnsView(getDashboardForUser(user, user.getUserRole()));
                dataSource.save(user, user.getId());
            } else if (associatedProfile == null) {
                log.warn("Associated profile {} for user {} was null. Unable to get default profile with id {}", associatedProfileId, user.getId(), DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID);
            }
        }
    }

    private List<AccessRight> getAccessRulesForUser(UserModule user) {
        List<AccessRight> accessRights = new ArrayList<>();

        UserProfileModule profile = dataSource.get(UserProfileModule.class,	user.getAssociatedProfileId());

        Set<Integer> accessible = profile == null ? new HashSet<>() : profile.getAccessibleSitesPackages();

        // when a user has access restrictions, like a frag-finn-user, we explicitly define the cards
        // this user can see. All other cards will be hidden.
        if (accessible != null && accessible.stream().anyMatch(id -> id.equals(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_FRAG_FINN))) {
            accessRights.add(AccessRight.FRAG_FINN);
            accessRights.add(AccessRight.USER);
            accessRights.add(AccessRight.ONLINE_TIME);
        } else if (user.getId().equals(DefaultEntities.PARENTAL_CONTROL_LIMBO_USER_ID)) {
            accessRights.add(AccessRight.USER);
        }

        return accessRights;
    }

    private void generateParentalControlCardsForEachChildUser(List<UserModule> users) {

        List<UserModule> parents = getAllParentUsers(users);

        users.forEach(user -> {
            if (user.getUserRole() != null && user.getUserRole().equals(UserRole.CHILD)) {
                dashboardCardService.createParentalControlCard(user.getId(), "PARENTAL_CONTROL", "FAM");
            }
        });

        parents.forEach((user) -> {
            user.setDashboardColumnsView(getDashboardForUser(user, UserRole.PARENT));
            dataSource.save(user, user.getId());
        });
    }

    private DashboardColumnsView getDashboardForUser(UserModule user, UserRole userRole) {
        UserRole tmpUserRole = userRole == null ? UserRole.OTHER : userRole;
        DashboardColumnsView columns = null;
        List<AccessRight> accessRights = user == null ? Collections.emptyList() : getAccessRulesForUser(user);

        if (user == null || user.getDashboardColumnsView() == null) {
            columns = dashboardCardService.getNewDashboardCardColumns(tmpUserRole);
        } else {
            columns = user.getDashboardColumnsView();
        }

        return dashboardCardService.getUpdatedColumnsView(columns, tmpUserRole, accessRights);
    }

    private List<UserModule> getAllParentUsers(List<UserModule> users) {
        return users.stream().
            filter(user -> user.getUserRole() != null && user.getUserRole().equals(UserRole.PARENT)).
            collect(Collectors.toList());
    }


    private void createAndSaveFragFinnCard(List<UserModule> users) {
        // If there is already a FRAG_FINN card, remove it and all references.
        List<UiCard> cards = dataSource.getAll(UiCard.class);
        for (UiCard card: cards) {
            if ("FRAG_FINN".equals(card.getName())) {
                removeCardFromUsers(card, users);
            }
        }
        int id = dataSource.nextId(UiCard.class);
        UiCard fragFinnCard = new UiCard(id, "FRAG_FINN", ProductFeature.FAM.name(), null, Collections.singletonList(AccessRight.FRAG_FINN));
        dashboardCardService.saveNewDashboardCard(fragFinnCard);
    }

    private void removeCardFromUsers(UiCard card, List<UserModule> users) {
        for (UserModule user: users) {
            if (user.getDashboardColumnsView() != null) {
                removeCard(card, user.getDashboardColumnsView().getOneColumn());
                removeCard(card, user.getDashboardColumnsView().getTwoColumn());
                removeCard(card, user.getDashboardColumnsView().getThreeColumn());
            }
            dataSource.save(user, user.getId());
        }
        dataSource.delete(UiCard.class, card.getId());
    }

    private void removeCard(UiCard card, List<UiCardColumnPosition> positions) {
        UiCardColumnPosition toBeRemoved = null;
        for (UiCardColumnPosition pos: positions) {
            if (pos.getId() == card.getId()) {
                toBeRemoved = pos;
                break;
            }
        }
        if (toBeRemoved != null) {
            positions.remove(toBeRemoved);
        }
    }

    private List<UserRole> getAllRoles() {
        return Arrays.asList(UserRole.PARENT, UserRole.OTHER, UserRole.CHILD);
    }

    private void removeNotExistingCards(List<UserModule> users) {
        List<UiCard> cards = dataSource.getAll(UiCard.class);
        Set<Integer> cardIds = cards.stream().map(c -> c.getId()).collect(Collectors.toSet());
        users.stream().filter(user -> user.getDashboardColumnsView() != null).forEach(user -> {
            boolean f1 = removeNotExistingCardsFromUser(cardIds, user.getDashboardColumnsView().getOneColumn());
            boolean f2 = removeNotExistingCardsFromUser(cardIds, user.getDashboardColumnsView().getTwoColumn());
            boolean f3 = removeNotExistingCardsFromUser(cardIds, user.getDashboardColumnsView().getThreeColumn());
            if (f1 || f2 ||f3) {
                dataSource.save(user, user.getId());
            }
        });
    }

    private boolean removeNotExistingCardsFromUser(Set<Integer> cardIds, List<UiCardColumnPosition> positions) {
        Set<UiCardColumnPosition> toBeRemoved = new HashSet<>();
        positions.forEach(pos -> {
            if (!cardIds.contains(pos.getId())) {
                toBeRemoved.add(pos);
            }
        });
        if (!toBeRemoved.isEmpty()) {
            positions.removeAll(toBeRemoved);
            return true;
        }
        return false;
    }

}
