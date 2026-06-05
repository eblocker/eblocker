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

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.TestDeviceFactory;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.UserRole;
import org.eblocker.server.http.service.DashboardCardService;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.ParentalControlService;
import org.eblocker.server.http.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UsersBackupProviderTest extends BackupProviderTestBase {
    private UsersBackupProvider providerExport, providerImport;
    private DeviceService deviceServiceExport, deviceServiceImport;
    private UserService userServiceExport, userServiceImport;
    private ParentalControlService parentalControlServiceExport, parentalControlServiceImport;
    private DashboardCardService dashboardCardServiceExport, dashboardCardServiceImport;
    private DataSource dataSourceExport, dataSourceImport;
    private TestDeviceFactory tdfExport, tdfImport;
    private final String deviceMacA = "aaaaaaaaaaaa";
    private final String deviceMacB = "bbbbbbbbbbbb";
    private final String deviceMacC = "cccccccccccc";
    private Device deviceA, deviceB, deviceB2, deviceC;
    UserProfileModule defaultProfile, childProfileExport, childProfileImport;
    UserModule child;

    @BeforeEach
    void setUp() {
        deviceServiceExport = Mockito.mock(DeviceService.class);
        userServiceExport = Mockito.mock(UserService.class);
        parentalControlServiceExport = Mockito.mock(ParentalControlService.class);
        dashboardCardServiceExport = Mockito.mock(DashboardCardService.class);
        dataSourceExport = Mockito.mock(DataSource.class);
        providerExport = new UsersBackupProvider(deviceServiceExport, userServiceExport, parentalControlServiceExport, dashboardCardServiceExport, dataSourceExport);
        setUpDataBeforeExport();

        deviceServiceImport = Mockito.mock(DeviceService.class);
        userServiceImport = Mockito.mock(UserService.class);
        parentalControlServiceImport = Mockito.mock(ParentalControlService.class);
        dashboardCardServiceImport = Mockito.mock(DashboardCardService.class);
        dataSourceImport = Mockito.mock(DataSource.class);
        providerImport = new UsersBackupProvider(deviceServiceImport, userServiceImport, parentalControlServiceImport, dashboardCardServiceImport, dataSourceImport);
        setUpDataBeforeImport();
    }

    /**
     * Devices A and B are exported to the backup.
     */
    void setUpDataBeforeExport() {
        tdfExport = new TestDeviceFactory(deviceServiceExport);
        deviceA = tdfExport.addDevice(deviceMacA, "192.168.1.10", true);
        deviceB = tdfExport.addDevice(deviceMacB, "192.168.1.11", true);
        tdfExport.commit();
        defaultProfile = createProfile(1, null, true, true);
        childProfileExport = createProfile(101, "PROFILE_FOR_USER_103", false, false);
        UserModule systemUser1 = createSystemDefaultUser(101, deviceA.getId());
        UserModule systemUser2 = createSystemDefaultUser(102, deviceB.getId());
        child = createUser(103, childProfileExport.getId(), "Billy", UserRole.CHILD);
        setAllUsers(deviceA, systemUser1.getId());
        deviceB.setDefaultSystemUser(systemUser2.getId());
        deviceB.setAssignedUser(child.getId());
        deviceB.setOperatingUser(child.getId());

        Mockito.when(userServiceExport.getUsers(Mockito.anyBoolean())).thenReturn(List.of(systemUser1, systemUser2, child));
        Mockito.when(parentalControlServiceExport.getProfiles()).thenReturn(List.of(defaultProfile, childProfileExport));
    }

    /**
     * Devices B and C are in the DB before the import.
     */
    void setUpDataBeforeImport() {
        tdfImport = new TestDeviceFactory(deviceServiceImport);
        deviceB2 = tdfImport.addDevice(deviceMacB, "192.168.1.23", true);
        deviceC = tdfImport.addDevice(deviceMacC, "192.168.1.13", true);
        tdfImport.commit();
        UserModule systemUser2 = createSystemDefaultUser(102, deviceB2.getId());
        UserModule systemUser3 = createSystemDefaultUser(103, deviceC.getId());
        setAllUsers(deviceB2, systemUser2.getId());
        setAllUsers(deviceC, systemUser3.getId());

        childProfileImport = createProfile(102, "PROFILE_FOR_USER_103", false, false);
        Mockito.when(userServiceImport.getUsers(Mockito.anyBoolean())).thenReturn(List.of(systemUser2, systemUser3));
        Mockito.when(userServiceImport.createUser(childProfileImport.getId(), "Billy", null, null, UserRole.CHILD, null))
                .thenReturn(createUser(104, childProfileImport.getId(), "Billy", UserRole.CHILD));
        Mockito.when(userServiceImport.restoreDefaultSystemUser(deviceA.getId())).thenReturn(createSystemDefaultUser(105, deviceA.getId()));
        Mockito.when(userServiceImport.restoreDefaultSystemUser(deviceB.getId())).thenReturn(createSystemDefaultUser(106, deviceB.getId()));
        Mockito.when(userServiceImport.restoreDefaultSystemUser(deviceC.getId())).thenReturn(createSystemDefaultUser(107, deviceC.getId()));
        Mockito.when(parentalControlServiceImport.getProfiles()).thenReturn(List.of(defaultProfile));
        Mockito.when(parentalControlServiceImport.storeNewProfile(Mockito.argThat(profile -> profile.getName().equals("PROFILE_FOR_USER_103"))))
                .thenReturn(childProfileImport);
    }

    private UserModule createSystemDefaultUser(int id, String name) {
        return new UserModule(id, defaultProfile.getId(), name, null, null, null, true, null, null, null, null, null);
    }

    private UserModule createUser(int id, int profileId, String name, UserRole role) {
        return new UserModule(id, profileId, name, null, null, role, false, null, null, null, null, null);
    }

    private UserProfileModule createProfile(int id, String name, boolean builtin, boolean standard) {
        UserProfileModule profile = new UserProfileModule(id, name, null, null, null, standard, false, null, null, null, null, null, null, null, null);
        profile.setBuiltin(builtin);
        return profile;
    }

    private void setAllUsers(Device device, Integer userId) {
        device.setDefaultSystemUser(userId);
        device.setOperatingUser(userId);
        device.setAssignedUser(userId);
    }

    @Test
    void missingUser() {
        deviceA.setOperatingUser(404);
        assertThrows(CorruptedBackupException.class, () -> {
            exportBackup(providerExport);
        });
    }

    @Test
    void missingProfile() {
        child.setAssociatedProfileId(404);
        assertThrows(CorruptedBackupException.class, () -> {
            exportBackup(providerExport);
        });
    }

    @Test
    void testImport() throws IOException {
        byte[] backup = exportBackup(providerExport);
        importBackup(backup, providerImport);
        Mockito.verify(deviceServiceImport).delete(deviceC);
    }
}