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

import org.eblocker.server.common.data.UserRole;
import org.eblocker.server.common.data.dashboard.DashboardColumnsView;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.migrations.DefaultEntities;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class UserServiceInitTest {

    private DataSource dataSource;
    private DeviceService deviceService;
    private DashboardCardService dashboardService;
    private UserService userService;

    private AtomicInteger id = new AtomicInteger(1000);

    // Existing users
    private final UserModule ALICE = createUser(1001, "Alice", false);
    private final UserModule BOB   = createUser(1002, "Bob", false);

    // Not existing user
    private final UserModule XAVER = createUser(1101, "Xaver", false);

    // Existing system user
    private final UserModule SYSTEM = createUser(1201, "system", true);

    // Not existing system user
    private final UserModule INVALID = createUser(1301, "invalid-system", true);

    // New system user, that is being "created", if necessary
    private final UserModule NEW = createUser(1401, "new-system", true);

    // Virtual "logged-out" user, which is used to lock a device
    private final UserModule LOCKED = createUser(DefaultEntities.PARENTAL_CONTROL_LIMBO_USER_ID, "locked-system", true);

    @Before
    public void setup() {
        dataSource = mock(DataSource.class);
        deviceService = mock(DeviceService.class);
        dashboardService = mock(DashboardCardService.class);

        // Initially known users
        when(dataSource.getAll(UserModule.class)).thenReturn(Arrays.asList(ALICE, BOB, SYSTEM, LOCKED));

        // Dashboard cards not needed here
        when(dashboardService.getNewDashboardCardColumns(Mockito.any(UserRole.class))).thenReturn(new DashboardColumnsView());

        userService = new UserService(dataSource, deviceService, dashboardService, "SHARED.USER.NAME.STANDARD_USER");

        // Call done by constructor - not of interest here
        verify(deviceService).addListener(any());

        // If necessary, return next DB id
        when(dataSource.nextId(UserModule.class)).thenReturn(id.incrementAndGet());
    }

    @Test
    public void test_allSystemUsers_ok() {
        doTest(
            SYSTEM, SYSTEM, SYSTEM,
            SYSTEM, SYSTEM, SYSTEM
        );
    }

    @Test
    public void test_sameOperatingUser_ok() {
        doTest(
            SYSTEM, ALICE, ALICE,
            SYSTEM, ALICE, ALICE
        );
    }

    @Test
    public void test_differentOperatingUser_ok() {
        doTest(
            SYSTEM, ALICE, BOB,
            SYSTEM, ALICE, BOB
        );
    }

    @Test
    public void doTest_notExistingSystemUser_notOk() {
        doTest(
            INVALID, INVALID, INVALID,
            NEW, NEW, NEW
        );
    }

    @Test
    public void doTest_notExistingSystemUser_sameOperatingUser_notOk() {
        doTest(
            INVALID, ALICE, ALICE,
            NEW, ALICE, ALICE
        );
    }

    @Test
    public void doTest_notExistingSystemUser_differentOperatingUser_notOk() {
        doTest(
            INVALID, ALICE, BOB,
            NEW, ALICE, BOB
        );
    }

    @Test
    public void doTest_notExistingAssignedUser_notOk() {
        doTest(
            INVALID, XAVER, BOB,
            // Even though BOB exists, he cannot remain operating user,
            // if assigned user is being reset to default system user!
            NEW, NEW, NEW
        );
    }

    @Test
    public void doTest_invalidSystemUser_notOk() {
        doTest(
            ALICE, ALICE, BOB,
            NEW, ALICE, BOB
        );
    }

    @Test
    public void doTest_assignedSystemUser_notOk() {
        doTest(
            SYSTEM, SYSTEM, BOB,
            SYSTEM, SYSTEM, SYSTEM
        );
    }

    @Test
    public void doTest_operatingSystemUser_notOk() {
        doTest(
            SYSTEM, ALICE, SYSTEM,
            SYSTEM, ALICE, ALICE
        );
    }

    @Test
    public void doTest_operatingUserLocked_ok() {
        doTest(
            SYSTEM, ALICE, LOCKED,
            SYSTEM, ALICE, LOCKED
        );
    }

    @Test
    public void doTest_assignedUserLocked_notOk() {
        doTest(
            SYSTEM, LOCKED, BOB,
            SYSTEM, SYSTEM, SYSTEM
        );
    }

    private void doTest(
        UserModule currentDefaultSystemUser, UserModule currentAssignedUser, UserModule currentOperatingUser,
        UserModule expectedDefaultSystemUser, UserModule expectedAssignedUser, UserModule expectedOperatingUser) {
        // Provide test scenario
        Device device = createDevice(currentDefaultSystemUser, currentAssignedUser, currentOperatingUser);
        when(deviceService.getDevices(true)).thenReturn(Collections.singletonList(device));
        when(dataSource.save(any(UserModule.class), anyInt())).thenAnswer(invocation -> {
            UserModule user = invocation.getArgument(0);
            assertEquals(device.getId(), user.getName());
            return NEW;
        });

        // Method to be tested
        userService.init();

        // Check expectations
        assertEquals((int) expectedDefaultSystemUser.getId(), device.getDefaultSystemUser());
        assertEquals((int) expectedAssignedUser.getId(), device.getAssignedUser());
        assertEquals((int) expectedOperatingUser.getId(), device.getOperatingUser());

        // Additional consistency checks

        if (expectedDefaultSystemUser == NEW) {
            // Assert that default system user has been changed
            assertNotEquals((int) currentDefaultSystemUser.getId(), device.getDefaultSystemUser());
            assertEquals((int) NEW.getId(), device.getDefaultSystemUser());
        } else {
            // Assert that default system user has not been changed
            assertEquals((int) currentDefaultSystemUser.getId(), device.getDefaultSystemUser());
        }

        if (expectedAssignedUser != currentAssignedUser) {
            // Assert that assigned user has been changed
            assertNotEquals((int) currentAssignedUser.getId(), device.getAssignedUser());
            assertEquals(device.getDefaultSystemUser(), device.getAssignedUser());
        } else {
            // Assert that assigned user has not been changed
            assertEquals((int) currentAssignedUser.getId(), device.getAssignedUser());
        }

        if (expectedOperatingUser != currentOperatingUser) {
            assertNotEquals((int) currentOperatingUser.getId(), device.getOperatingUser());
            assertEquals(device.getAssignedUser(), device.getOperatingUser());
        } else {
            assertEquals((int) currentOperatingUser.getId(), device.getOperatingUser());
        }

        // Expected service calls
        verify(dataSource).getAll(UserModule.class);
        verify(deviceService).getDevices(true);
        if (expectedDefaultSystemUser != currentDefaultSystemUser) {
            verify(dataSource).nextId(UserModule.class);
            verify(dataSource).save(any(UserModule.class), anyInt());
            verify(dataSource).delete(UserModule.class, SYSTEM.getId());
            verify(dashboardService).getNewDashboardCardColumns(UserRole.OTHER);
        }
        if (expectedDefaultSystemUser != currentDefaultSystemUser ||
            expectedAssignedUser != currentAssignedUser ||
            expectedOperatingUser != currentOperatingUser) {
            verify(deviceService).updateDevice(device);
        }

        verifyNoMoreInteractions(dataSource);
        verifyNoMoreInteractions(deviceService);
    }

    private Device createDevice(UserModule defaultSystemUser, UserModule assignedUser, UserModule operatingUser) {
        Device device = new Device();
        device.setId(UUID.randomUUID().toString());
        device.setDefaultSystemUser(defaultSystemUser.getId());
        device.setAssignedUser(assignedUser.getId());
        device.setOperatingUser(operatingUser.getId());
        return device;
    }

    private UserModule createUser(int id, String name, boolean system) {
        return new UserModule(
            id,
            null,
            name,
            null,
            null, UserRole.OTHER,
            system,
            null,
            null,
            null,
            null,
            null
        );
    }

}
