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
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
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

    @Inject
    public UsersBackupProvider(DeviceService deviceService, UserService userService, ParentalControlService parentalControlService, DashboardCardService dashboardCardService, DataSource dataSource) {
        this.deviceService = deviceService;
        this.userService = userService;
        this.parentalControlService = parentalControlService;
        this.dashboardCardService = dashboardCardService;
        this.dataSource = dataSource;
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
        return backup;
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

    private void restoreBackupAlternative(UsersBackup backup) throws IOException {
        int maxUserId = getMaximumUserId(backup);
        userService.ensureNextUserIdGreaterThan(maxUserId);
        deleteNonSystemUsers();
        List<Device> devicesNotInBackup = getDevicesNotInBackup(backup);
        deleteOrResetDevices(devicesNotInBackup);
        // importProfiles(backup);
        // importUsers(backup);
        // importDevices(backup);
    }

    private void restoreBackup(UsersBackup backup) throws IOException {
        Collection<Device> currentDevices = deviceService.getDevices(true);
        deleteOrResetDevices(new ArrayList<>(currentDevices));
        deleteNonSystemUsers();
        Map<Integer, Integer> profileIdMapping = new HashMap<>(); // map old (from backup) to new profile IDs
        importProfiles(backup.getProfiles(), profileIdMapping);
        updateProfileIds(backup.getUsers(), profileIdMapping);
        Map<Integer, Integer> userIdMapping = new HashMap<>(); // map old (from backup) to new user IDs
        importUsers(backup.getUsers(), userIdMapping);
        updateUserIds(backup.getDevices(), userIdMapping);
        importDevices(backup.getDevices());
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
                UserProfileModule newProfile = parentalControlService.storeNewProfile(profile);
                profileIdMapping.put(oldId, newProfile.getId());
            }
        }
        // TODO: default profile 1 can also have restrictions! So it must be updated in place.
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
                // TODO: update "real" user with settings from backup
            }
        }
    }

    private void updateUserIds(List<Device> devices, Map<Integer, Integer> userIdMapping) {
        for (Device device : devices) {
            if (userIdMapping.containsKey(device.getAssignedUser())) {
                device.setAssignedUser(userIdMapping.get(device.getAssignedUser()));
            }
            if (userIdMapping.containsKey(device.getOperatingUser())) {
                device.setOperatingUser(userIdMapping.get(device.getOperatingUser()));
            }
        }
        // TODO: update ParentalControlCard.referencingUserId
    }

    private void importDevices(List<Device> devices) {
        for (Device device : devices) {
            device.setIpAddresses(List.of());
            deviceService.updateDevice(device);
        }
    }

    private List<Device> getDevicesNotInBackup(UsersBackup backup) {
        Collection<Device> currentDevices = deviceService.getDevices(true);

        Set<String> deviceIdsInBackup = backup.getDevices().stream()
                .map(Device::getId)
                .collect(Collectors.toSet());

        return currentDevices.stream()
                .filter(device -> !deviceIdsInBackup.contains(device.getId()))
                .collect(Collectors.toList());
    }

    private int getMaximumUserId(UsersBackup backup) {
        return backup.getUsers().stream()
                .mapToInt(UserModule::getId)
                .max()
                .orElse(0);
    }
}
