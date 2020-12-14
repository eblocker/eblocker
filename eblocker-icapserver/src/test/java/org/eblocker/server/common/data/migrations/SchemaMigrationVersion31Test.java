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
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserModuleOld;
import org.eblocker.server.common.data.WhiteListConfig;
import org.eblocker.server.common.data.dashboard.DashboardCard;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class SchemaMigrationVersion31Test {

    private DataSource dataSource;
    private SchemaMigration migration;
    private UserMigrationService userMigrationService;

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        userMigrationService = Mockito.mock(UserMigrationService.class);
        migration = new SchemaMigrationVersion31(dataSource, userMigrationService);
    }

    @Test
    public void test_standardUser() {
        int userId = 1003; // id for new default system user
        UserModuleOld standardUser = createStandardUser(null, null, null);
        setMocks(
                standardUser,
                userId,
                createDeviceForUser(standardUser, standardUser, null)
        );

        migration.migrate();

        verifyMocks(
                userId, // expected assigned user
                userId, // expected operating user
                userId  // expected (new) default system user
        );
    }

    @Test
    public void test_realUser() {
        int userId = 1004; // id for new default system user
        UserModuleOld standardUser = createStandardUser(null, null, null);
        UserModuleOld realUser = createRealUser(2345);
        setMocks(
                standardUser,
                userId,
                createDeviceForUser(realUser, realUser, null)
        );

        migration.migrate();

        verifyMocks(
                realUser.getId(), // expected assigned user
                realUser.getId(), // expected operating user
                userId            // expected (new) default system user
        );
    }

    @Test
    public void test_realUserDifferentAssignedUser() {
        int userId = 1004; // id for new default system user
        UserModuleOld standardUser = createStandardUser(null, null, null);
        UserModuleOld assignedUser = createRealUser(777);
        UserModuleOld operatingUser = createRealUser(9999);
        setMocks(
                standardUser,
                userId,
                createDeviceForUser(assignedUser, operatingUser, null)
        );

        migration.migrate();

        verifyMocks(
                assignedUser.getId(), // expected assigned user
                operatingUser.getId(), // expected operating user
                userId            // expected (new) default system user
        );
    }

    @Test
    public void test_noAction_alreadyMigrated() {
        int userId = 1003; // id for new default system user
        UserModuleOld standardUser = createStandardUser(null, null, null);
        UserModuleOld systemUser = createSystemUser(userId);
        setMocks(
                standardUser,
                userId,
                createDeviceForUser(systemUser, systemUser, systemUser)
        );

        migration.migrate();

        verify(dataSource, never()).save(any(Device.class));
    }

    private void setMocks(UserModuleOld standardUser, int userId, Device... devices) {
        when(userMigrationService.get(UserModuleOld.class, DefaultEntities.PARENTAL_CONTROL_DEFAULT_USER_ID)).thenReturn(standardUser);
        when(userMigrationService.get(UserModuleOld.class, userId)).thenReturn(standardUser);
        when(dataSource.getDevices()).thenReturn(new HashSet<>(Arrays.asList(devices)));
        when(dataSource.nextId(UserModule.class)).thenReturn(userId);
        when(userMigrationService.save(any(UserModuleOld.class), eq(userId))).thenAnswer(i -> i.getArguments()[0]);
    }

    private void verifyMocks(int assignedUserId, int operatingUserId, int defaultSystemUserId) {
        verify(userMigrationService).get(UserModuleOld.class, DefaultEntities.PARENTAL_CONTROL_DEFAULT_USER_ID);
        verify(dataSource).getDevices();
        verify(userMigrationService).get(UserModuleOld.class, new Device().getDefaultSystemUser());

        verify(dataSource).nextId(UserModule.class);

        ArgumentCaptor<UserModuleOld> userCaptor = ArgumentCaptor.forClass(UserModuleOld.class);
        verify(userMigrationService).save(userCaptor.capture(), eq(defaultSystemUserId));
        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
        verify(dataSource).save(deviceCaptor.capture());

        assertEquals(defaultSystemUserId, (int) userCaptor.getValue().getId());
        assertEquals(assignedUserId, deviceCaptor.getValue().getAssignedUser());
        assertEquals(operatingUserId, deviceCaptor.getValue().getOperatingUser());
        assertEquals(defaultSystemUserId, deviceCaptor.getValue().getDefaultSystemUser());

        verify(userMigrationService).delete(UserModuleOld.class, DefaultEntities.PARENTAL_CONTROL_DEFAULT_USER_ID);
        verify(dataSource).setVersion("31");

        verifyNoMoreInteractions(dataSource);
    }

    private UserModuleOld createStandardUser(
            Map<String, WhiteListConfig> whiteListConfigByDomains,
            List<DashboardCard> dashboardCards,
            Integer plugAndPlayFilterWhitelistId
    ) {
        return new UserModuleOld(
                DefaultEntities.PARENTAL_CONTROL_DEFAULT_USER_ID,
                1,
                "old-standard-user",
                "name-key",
                null,
                null,
                true,
                null,
                whiteListConfigByDomains,
                dashboardCards,
                plugAndPlayFilterWhitelistId,
                null
        );
    }

    private UserModuleOld createRealUser(int userId) {
        return new UserModuleOld(
                userId,
                1,
                "real-user-" + userId,
                "name-key",
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null
        );
    }

    private UserModuleOld createSystemUser(int userId) {
        return new UserModuleOld(
                userId,
                1,
                "system-user-" + userId,
                "name-key",
                null,
                null,
                true,
                null,
                null,
                null,
                null,
                null
        );
    }

    private Device createDeviceForUser(UserModuleOld associatedUser, UserModuleOld operatingUser, UserModuleOld defaultSystemUser) {
        Device device = new Device();
        device.setId(UUID.randomUUID().toString());
        if (associatedUser != null) {
            device.setAssignedUser(associatedUser.getId());
        }
        if (operatingUser != null) {
            device.setOperatingUser(operatingUser.getId());
        }
        if (defaultSystemUser != null) {
            device.setDefaultSystemUser(defaultSystemUser.getId());
        }
        return device;
    }
}
