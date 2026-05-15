package org.eblocker.server.http.backup;

import com.google.inject.Inject;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.http.service.DashboardCardService;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.ParentalControlService;
import org.eblocker.server.http.service.UserService;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

public class UsersBackupProvider extends BackupProvider {
    public static final String USERS_ENTRY = "eblocker-config/users.json";

    private final DeviceService deviceService;
    private final UserService userService;
    private final ParentalControlService parentalControlService;
    private final DashboardCardService dashboardCardService;

    @Inject
    public UsersBackupProvider(DeviceService deviceService, UserService userService, ParentalControlService parentalControlService, DashboardCardService dashboardCardService) {
        this.deviceService = deviceService;
        this.userService = userService;
        this.parentalControlService = parentalControlService;
        this.dashboardCardService = dashboardCardService;
    }

    @Override
    public void exportConfiguration(JarOutputStream outputStream) throws IOException {
        UsersBackup backup = createBackup();
        byte[] backupBytes = objectMapper.writeValueAsBytes(backup);
        checkConsistency(backupBytes);
        writeNextEntry(outputStream, USERS_ENTRY, backupBytes);
    }

    /**
     * Since there are cross-references between devices, users and profiles, we better check them
     * before writing the backup data. (We do not have any database transactions that could ensure
     * consistency.)
     */
    private void checkConsistency(byte[] backupBytes) throws IOException {
        UsersBackup backup = objectMapper.readValue(backupBytes, UsersBackup.class);
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
            throw new CorruptedBackupException("There are missing users: " + referencedUserIds);
        }

        Set<Integer> profileIds = profiles.stream().map(UserProfileModule::getId).collect(Collectors.toSet());
        Set<Integer> referencedProfileIds = users.stream().map(UserModule::getAssociatedProfileId).collect(Collectors.toSet());
        referencedProfileIds.removeAll(profileIds);
        if (referencedProfileIds.size() > 0) {
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

    }

    @Override
    public void verifyConfiguration(JarInputStream inputStream, int schemaVersion) throws IOException {

    }
}
