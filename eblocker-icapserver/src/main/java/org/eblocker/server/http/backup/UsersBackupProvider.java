/*
 * Copyright 2026 eBlocker Open Source GmbH
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
package org.eblocker.server.http.backup;

import com.google.inject.Inject;
import org.eblocker.server.common.blocker.Blocker;
import org.eblocker.server.common.blocker.BlockerService;
import org.eblocker.server.common.blocker.Category;
import org.eblocker.server.common.blocker.ExternalDefinition;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.dashboard.DashboardColumnsView;
import org.eblocker.server.common.data.dashboard.ParentalControlCard;
import org.eblocker.server.common.data.dashboard.UiCard;
import org.eblocker.server.common.data.dashboard.UiCardColumnPosition;
import org.eblocker.server.common.data.migrations.DefaultEntities;
import org.eblocker.server.http.model.CustomDomainFilterConfig;
import org.eblocker.server.http.service.CustomDomainFilterConfigService;
import org.eblocker.server.http.service.DashboardCardService;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.ParentalControlService;
import org.eblocker.server.http.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

public class UsersBackupProvider extends BackupProvider {
    private static final Logger LOG = LoggerFactory.getLogger(UsersBackupProvider.class);
    public static final String USERS_ENTRY = "eblocker-config/users.json";

    private final DeviceService deviceService;
    private final UserService userService;
    private final ParentalControlService parentalControlService;
    private final DashboardCardService dashboardCardService;
    private final DataSource dataSource;
    private final CustomDomainFilterConfigService filterConfigService;
    private final BlockerService blockerService;

    @Inject
    public UsersBackupProvider(DeviceService deviceService, UserService userService, ParentalControlService parentalControlService, DashboardCardService dashboardCardService,
                               DataSource dataSource, CustomDomainFilterConfigService filterConfigService, BlockerService blockerService) {
        this.deviceService = deviceService;
        this.userService = userService;
        this.parentalControlService = parentalControlService;
        this.dashboardCardService = dashboardCardService;
        this.dataSource = dataSource;
        this.filterConfigService = filterConfigService;
        this.blockerService = blockerService;
    }

    @Override
    public void exportConfiguration(JarOutputStream outputStream) throws IOException {
        UsersBackup backup = createBackup();
        byte[] backupBytes = objectMapper.writeValueAsBytes(backup);
        checkConsistency(backupBytes);
        writeNextEntry(outputStream, USERS_ENTRY, backupBytes);
    }

    private void checkConsistency(byte[] backupBytes) throws IOException {
        UsersBackup backup = objectMapper.readValue(backupBytes, UsersBackup.class);
        checkConsistency(backup);
    }

    /**
     * Since there are cross-references between devices, users and profiles, we better check them
     * before writing the backup data. (We do not have any database transactions that could ensure
     * consistency.)
     */
    private void checkConsistency(UsersBackup backup) throws IOException {
        List<Device> devices = backup.getDevices();
        List<UserModule> users = backup.getUsers();
        List<UserProfileModule> profiles = backup.getProfiles();

        Set<Integer> userIds = users.stream().map(UserModule::getId).collect(Collectors.toSet());
        Set<Integer> referencedUserIds = new HashSet<>();
        referencedUserIds.addAll(devices.stream().map(Device::getDefaultSystemUser).collect(Collectors.toSet()));
        referencedUserIds.addAll(devices.stream().map(Device::getAssignedUser).collect(Collectors.toSet()));
        referencedUserIds.addAll(devices.stream().map(Device::getOperatingUser).collect(Collectors.toSet()));
        referencedUserIds.removeAll(userIds);
        if (referencedUserIds.size() > 0) {
            LOG.error("Could not create backup due to inconsistent data. Missing users: {}", referencedUserIds);
            throw new CorruptedBackupException("There are missing users: " + referencedUserIds);
        }

        Set<Integer> profileIds = profiles.stream().map(UserProfileModule::getId).collect(Collectors.toSet());
        Set<Integer> referencedProfileIds = users.stream().map(UserModule::getAssociatedProfileId).collect(Collectors.toSet());
        referencedProfileIds.removeAll(profileIds);
        if (referencedProfileIds.size() > 0) {
            LOG.error("Could not create backup due to inconsistent data. Missing profiles: {}", referencedProfileIds);
            throw new CorruptedBackupException("There are missing profiles: " + referencedProfileIds);
        }
    }

    private UsersBackup createBackup() {
        UsersBackup backup = new UsersBackup();
        List<Device> allDevices = deviceService.getDevices(true).stream()
                .filter(device -> !device.isEblocker())
                .collect(Collectors.toList());
        backup.setDevices(allDevices);
        Collection<UserModule> users = userService.getUsers(true);
        backup.setUsers(List.copyOf(users));
        backup.setProfiles(parentalControlService.getProfiles());
        backup.setUiCards(dashboardCardService.getAll());
        backup.setCustomDomainFilters(collectCustomDomainFilters(users));
        backup.setParentalControlBlockers(collectCustomParentalControlBlockers());
        return backup;
    }

    private Map<Integer, Blocker> collectCustomParentalControlBlockers() {
        Map<Integer, Blocker> customParentalControlBlockersById = blockerService.getBlockers().stream()
                .filter(blocker -> blocker.getCategory() == Category.PARENTAL_CONTROL)
                .filter(blocker -> !blocker.isProvidedByEblocker())
                .collect(Collectors.toMap(Blocker::getId, Function.identity()));

        // ExternalDefinition.id == Blocker.id, see BlockerService
        return dataSource.getAll(ExternalDefinition.class).stream()
                .filter(definition -> definition.getCategory() == Category.PARENTAL_CONTROL)
                .filter(definition -> definition.getReferenceId() != null)
                .filter(definition -> customParentalControlBlockersById.containsKey(definition.getId()))
                .collect(Collectors.toMap(
                        ExternalDefinition::getReferenceId,
                        definition -> customParentalControlBlockersById.get(definition.getId())
                        ));
    }

    private Map<Integer, CustomDomainFilterConfig> collectCustomDomainFilters(Collection<UserModule> users) {
        Map<Integer, CustomDomainFilterConfig> result = new HashMap<>();
        for (UserModule user: users) {
            if (user.getCustomWhitelistId() != null || user.getCustomBlacklistId() != null) {
                result.put(user.getId(), filterConfigService.getCustomDomainFilterConfig(user.getId()));
            }
        }
        return result;
    }

    @Override
    public void importConfiguration(JarInputStream inputStream, int schemaVersion) throws IOException {
        importConfiguration(inputStream, schemaVersion, false);
    }

    @Override
    public void verifyConfiguration(JarInputStream inputStream, int schemaVersion) throws IOException {
        importConfiguration(inputStream, schemaVersion, true);
    }

    private void importConfiguration(JarInputStream inputStream, int schemaVersion, boolean dryRun) throws IOException {
        getNextEntry(inputStream, USERS_ENTRY);
        UsersBackup backup = objectMapper.readValue(inputStream, UsersBackup.class);
        if (backup == null) {
            throw new CorruptedBackupException("Deserialized backup object is null");
        }

        checkConsistency(backup);

        if (!dryRun) {
            restoreBackup(backup);
        }
    }

    private void restoreBackup(UsersBackup backup) throws IOException {
        Collection<Device> currentDevices = deviceService.getDevices(true);
        deleteOrResetDevices(new ArrayList<>(currentDevices));
        deleteNonSystemUsers();
        deleteCustomParentalControlBlockers();
        Map<Integer, Integer> listIdMapping = new HashMap<>(); // map old (from backup) to new custom parental control lists
        importCustomParentalControlBlockers(backup.getParentalControlBlockers(), listIdMapping);
        updateCustomParentalControlBlockers(backup.getProfiles(), listIdMapping);
        Map<Integer, Integer> profileIdMapping = new HashMap<>(); // map old (from backup) to new profile IDs
        importProfiles(backup.getProfiles(), profileIdMapping);
        updateProfileIds(backup.getUsers(), profileIdMapping);
        Map<Integer, Integer> userIdMapping = new HashMap<>(); // map old (from backup) to new user IDs
        importUsers(backup.getUsers(), userIdMapping);
        updateUserIds(backup.getDevices(), userIdMapping);
        Map<Integer, Integer> cardIdMapping = getCardIdMapping(backup.getUiCards(), userIdMapping);
        updateDashboardColumnsViews(backup.getUsers(), userIdMapping, cardIdMapping);
        importDevices(backup.getDevices());
        importCustomDomainFilters(backup.getCustomDomainFilters(), userIdMapping);
    }

    private void updateCustomParentalControlBlockers(List<UserProfileModule> profiles, Map<Integer, Integer> listIdMapping) {
        for (UserProfileModule profile: profiles) {
            Set<Integer> accessible = profile.getAccessibleSitesPackages().stream()
                    .map(id -> listIdMapping.getOrDefault(id, id))
                    .collect(Collectors.toSet());
            Set<Integer> inaccessible = profile.getInaccessibleSitesPackages().stream()
                    .map(id -> listIdMapping.getOrDefault(id, id))
                    .collect(Collectors.toSet());
            profile.setAccessibleSitesPackages(accessible);
            profile.setInaccessibleSitesPackages(inaccessible);
        }
    }

    private void importCustomParentalControlBlockers(Map<Integer, Blocker> parentalControlBlockers, Map<Integer, Integer> listIdMapping) {
        for (Integer listId: parentalControlBlockers.keySet()) {
            Blocker blocker = parentalControlBlockers.get(listId);
            Blocker newBlocker = blockerService.createBlockerSynchronously(blocker);
            ExternalDefinition newDefinition = dataSource.get(ExternalDefinition.class, newBlocker.getId());
            Integer newListId = newDefinition.getReferenceId();
            listIdMapping.put(listId, newListId);
        }
    }

    private void deleteCustomParentalControlBlockers() {
        List<Integer> customParentalControlBlockerIds = blockerService.getBlockers().stream()
                .filter(blocker -> blocker.getCategory() == Category.PARENTAL_CONTROL)
                .filter(blocker -> !blocker.isProvidedByEblocker())
                .map(Blocker::getId)
                .collect(Collectors.toList());
        for (Integer id: customParentalControlBlockerIds) {
            blockerService.deleteBlocker(id);
        }
    }

    private void importCustomDomainFilters(Map<Integer, CustomDomainFilterConfig> customDomainFilters, Map<Integer, Integer> userIdMapping) {
        for (Integer oldUserId: customDomainFilters.keySet()) {
            Integer newUserId = userIdMapping.get(oldUserId);
            if (newUserId != null) {
                filterConfigService.setCustomDomainFilterConfig(newUserId, customDomainFilters.get(oldUserId));
            } else {
                LOG.warn("Could not import custom domain filters for user ID {}", oldUserId);
            }
        }
    }

    private void updateDashboardColumnsViews(List<UserModule> users, Map<Integer, Integer> userIdMapping, Map<Integer, Integer> cardIdMapping) {
        for (UserModule user: users) {
            DashboardColumnsView view = user.getDashboardColumnsView();
            view = updateUiCardIds(view, cardIdMapping);
            userService.updateUser(userIdMapping.get(user.getId()), view);
        }
    }

    private DashboardColumnsView updateUiCardIds(DashboardColumnsView view, Map<Integer, Integer> cardIdMapping) {
        Function<UiCardColumnPosition, UiCardColumnPosition> mapper = (UiCardColumnPosition pos) ->
                new UiCardColumnPosition(cardIdMapping.get(pos.getId()), pos.getColumn(), pos.getIndex(), pos.isVisible(), pos.isExpanded());
        List<UiCardColumnPosition> oneColumn = view.getOneColumn().stream().map(mapper).collect(Collectors.toList());
        List<UiCardColumnPosition> twoColumn = view.getTwoColumn().stream().map(mapper).collect(Collectors.toList());
        List<UiCardColumnPosition> threeColumn = view.getThreeColumn().stream().map(mapper).collect(Collectors.toList());
        return new DashboardColumnsView(oneColumn, twoColumn, threeColumn);
    }

    /**
     * Create a map from old (from backup) to new (from DB) UiCard and ParentalControlCard IDs.
     *
     * ParentalControlCards are mapped via their referencing user ID (the child). The user IDs might have changed.
     * Normal UiCards are mapped via their names.
     */
    private Map<Integer, Integer> getCardIdMapping(List<UiCard> backupCards, Map<Integer, Integer> userIdMapping) {
        Map<Integer, Integer> cardIdMapping = new HashMap<>(backupCards.size());
        List<UiCard> newCards = dashboardCardService.getAll();
        Map<String, Integer> cardName2newCardId = new HashMap<>();
        Map<Integer, Integer> newUserId2newCardId = new HashMap<>();
        for (UiCard card: newCards) {
            if (card instanceof ParentalControlCard) {
                Integer newUserId = ((ParentalControlCard) card).getReferencingUserId();
                newUserId2newCardId.put(newUserId, card.getId());
            } else {
                cardName2newCardId.put(card.getName(), card.getId());
            }
        }
        for (UiCard card: backupCards) {
            if (card instanceof ParentalControlCard) {
                Integer userId = ((ParentalControlCard) card).getReferencingUserId();
                cardIdMapping.put(card.getId(), newUserId2newCardId.get(userIdMapping.get(userId)));
            } else {
                cardIdMapping.put(card.getId(), cardName2newCardId.get(card.getName()));
            }
        }
        return cardIdMapping;
    }

    private void deleteOrResetDevices(List<Device> devices) {
        LOG.warn("Delete or reset: {}", devices);
        for (Device device : devices) {
            deviceService.delete(device);
        }
    }

    private void deleteNonSystemUsers() {
        Collection<UserModule> users = userService.getUsers(true);
        for (UserModule user : users) {
            if (!user.isSystem()) {
                userService.deleteUser(user.getId());
            }
        }
        List<UserProfileModule> profiles = parentalControlService.getProfiles();
        for (UserProfileModule profile : profiles) {
            if (!profile.isBuiltin()) {
                parentalControlService.deleteProfile(profile.getId());
            }
        }
    }

    private void importProfiles(List<UserProfileModule> profiles, Map<Integer, Integer> profileIdMapping) {
        for (UserProfileModule profile : profiles) {
            if (!profile.isBuiltin()) {
                Integer oldId = profile.getId();
                profile.setId(null);
                profile.setName(null); // remove names, they are not defined by the admin anymore
                UserProfileModule newProfile = parentalControlService.storeNewProfile(profile);
                profileIdMapping.put(oldId, newProfile.getId());
            } else if (profile.getId() == DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID) {
                parentalControlService.updateProfile(profile);
            }
        }
    }

    private void updateProfileIds(List<UserModule> users, Map<Integer, Integer> profileIdMapping) {
        for (UserModule user : users) {
            if (profileIdMapping.containsKey(user.getAssociatedProfileId())) {
                user.setAssociatedProfileId(profileIdMapping.get(user.getAssociatedProfileId()));
            }
        }
    }

    private void importUsers(List<UserModule> users, Map<Integer, Integer> userIdMapping) {
        for (UserModule user : users) {
            Integer oldId = user.getId();
            if (user.isSystem()) {
                UserModule newUser = userService.restoreDefaultSystemUser(user.getName());
                userIdMapping.put(oldId, newUser.getId());
                // TODO: update system user with settings from backup
            } else {
                UserModule newUser = userService.createUser(user.getAssociatedProfileId(), user.getName(), user.getNameKey(), user.getBirthday(), user.getUserRole(), null);
                userIdMapping.put(oldId, newUser.getId());
                // to avoid hashing the already hashed PIN again, re-save the new user:
                if (user.getPin() != null) {
                    newUser.setPin(user.getPin());
                    dataSource.save(newUser, newUser.getId());
                }
                // TODO: update "real" user with custom black/whitelist settings from backup
            }
        }
    }

    private void updateUserIds(List<Device> devices, Map<Integer, Integer> userIdMapping) {
        for (Device device : devices) {
            if (userIdMapping.containsKey(device.getDefaultSystemUser())) {
                device.setDefaultSystemUser(userIdMapping.get(device.getDefaultSystemUser()));
            }
            if (userIdMapping.containsKey(device.getAssignedUser())) {
                device.setAssignedUser(userIdMapping.get(device.getAssignedUser()));
            }
            if (userIdMapping.containsKey(device.getOperatingUser())) {
                device.setOperatingUser(userIdMapping.get(device.getOperatingUser()));
            }
        }
    }

    private void importDevices(List<Device> devices) {
        for (Device device : devices) {
            device.setIpAddresses(List.of());
            deviceService.updateDevice(device);
        }
    }
}
