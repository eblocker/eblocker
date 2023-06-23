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

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DeviceFactory;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.UserRole;
import org.eblocker.server.common.network.IpResponseTable;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.eblocker.server.http.security.PasswordUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.restexpress.exception.BadRequestException;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class UserServiceTest {

    private DataSource dataSource;
    private DeviceService deviceService;
    private UserService userService;
    private UserService.UserChangeListener listener;
    private UserAgentService userAgentService;
    private NetworkInterfaceWrapper networkInterfaceWrapper;
    private DashboardCardService dashboardCardService;
    private List<UserModule> users;
    private Set<Device> devices;
    private static byte[] pinA;
    private DeviceRegistrationProperties deviceRegistrationProperties;
    private DeviceFactory deviceFactory;
    private IpResponseTable ipResponseTable;
    private Clock clock;

    @Before
    public void setup() throws IOException {
        // setup data source and mock users
        dataSource = Mockito.mock(DataSource.class);
        dashboardCardService = Mockito.mock(DashboardCardService.class);
        userAgentService = Mockito.mock(UserAgentService.class);
        networkInterfaceWrapper = Mockito.mock(NetworkInterfaceWrapper.class);
        deviceRegistrationProperties = Mockito.mock(DeviceRegistrationProperties.class);
        deviceFactory = Mockito.mock(DeviceFactory.class);
        ipResponseTable = new IpResponseTable();
        deviceService = new DeviceService(dataSource, deviceRegistrationProperties, userAgentService,
                networkInterfaceWrapper, deviceFactory, ipResponseTable, clock, 90);
        deviceService.init();

        users = new ArrayList<>();
        users.add(createMockUser(1, "system", 1, true));
        users.add(createMockUser(100, "alice", 100));
        users.add(createMockUser(101, "bob", 102));
        Mockito.when(dataSource.getAll(UserModule.class)).then(i -> copyUsers(users));
        // mock saving a user
        Mockito.when(
                dataSource.save(Mockito.any(UserModule.class), Mockito.anyInt()))
                .then(AdditionalAnswers.returnsFirstArg());

        // Mock device
        Device device = new Device();
        device.setId("device:001122334455");
        device.setIpAddresses(Collections.singletonList(IpAddress.parse("192.168.0.10")));
        device.setAssignedUser(101);
        device.setOperatingUser(101);
        devices = new HashSet<>();
        devices.add(device);
        Mockito.when(dataSource.getDevices()).thenReturn(devices);

        // Mock profile
        List<UserProfileModule> profiles = new ArrayList<>();

        UserProfileModule profileS = new UserProfileModule(1, "profileS",
                "descriptionS", "nameKeyS", "descriptionKeyS", true, false, null,
                null, null, null, null, null, false, null);
        Mockito.when(dataSource.get(UserProfileModule.class, 1)).thenReturn(
                profileS);
        profiles.add(profileS);
        UserProfileModule profileA = new UserProfileModule(100, "profileA",
                "descriptionA", "nameKeyA", "descriptionKeyA", false, false, null,
                null, null, null, null, null, false, null);
        Mockito.when(dataSource.get(UserProfileModule.class, 100)).thenReturn(
                profileA);
        profiles.add(profileA);
        UserProfileModule profileB = new UserProfileModule(102, "profileB",
                "descriptionB", "nameKeyB", "descriptionKeyB", false, false, null,
                null, null, null, null, null, false, null);
        Mockito.when(dataSource.get(UserProfileModule.class, 102)).thenReturn(
                profileB);
        profiles.add(profileB);

        Mockito.when(dataSource.getAll(UserProfileModule.class)).thenReturn(profiles);

        // setup listener mock
        listener = Mockito.mock(UserService.UserChangeListener.class);

        // Setup user service
        userService = new UserService(dataSource, deviceService, dashboardCardService, "SHARED.USER.NAME.STANDARD_USER");
        userService.refresh();
        userService.addListener(listener);

        pinA = PasswordUtil.hashPassword("a");
    }

    private UserModule createMockUser(int userId, String userName,
                                      int associatedProfileId) {
        return new UserModule(userId, associatedProfileId, userName, userName, null, null,
                false, null, Collections.emptyMap(), null, null, null);
    }

    private UserModule createMockUser(int userId, String userName,
                                      int associatedProfileId, boolean systemUser) {
        return new UserModule(userId, associatedProfileId, userName, userName, null, null,
                systemUser, null, Collections.emptyMap(), null, null, null);
    }

    private UserModule createMockUser(int userId, String userName,
                                      int associatedProfileId, boolean hasPin, byte[] pin) {
        return new UserModule(userId, associatedProfileId, userName, userName, null, null,
                false, pin, Collections.emptyMap(), null, null, null);
    }

    private List<UserModule> copyUsers(Collection<UserModule> users) {
        return users.stream().map(this::copyUser).collect(Collectors.toList());
    }

    private UserModule copyUser(UserModule user) {
        return new UserModule(user.getId(), user.getAssociatedProfileId(),
                user.getName(), user.getNameKey(), null, user.getUserRole(), user.isSystem(), user.getPin(),
                new HashMap<>(user.getWhiteListConfigByDomains()), null, null, null);
    }

    @Test
    public void testGetUserById() {
        for (UserModule user : users) {
            UserModule retrievedUser = userService.getUserById(user.getId());
            Assert.assertNotNull(retrievedUser);
            Assert.assertEquals(user.getId(), retrievedUser.getId());
        }
    }

    @Test
    public void testDeleteUser() {
        UserModule deletedUser = users.get(1);
        userService.deleteUser(deletedUser.getId());
        // make sure proper methods have been called
        // One for refresh during creation of UserService
        Mockito.verify(dataSource, Mockito.times(2)).getDevices();
        Mockito.verify(dataSource).delete(UserModule.class, deletedUser.getId());
        // make sure user is not returned anymore
        for (UserModule user : userService.getUsers(false)) {
            if (user.getId() == deletedUser.getId()) {
                // If deleted user can still be found
                Assert.fail();
            }
        }
    }

    @Test
    public void testDeleteSystemUser() {
        UserModule deletedUser = users.get(0);
        // delete user
        try {
            userService.deleteUser(deletedUser.getId());
            Assert.fail();
        } catch (Exception e) {
            // Exception was expected
        }
        // make sure proper methods have been called
        Mockito.verify(dataSource, Mockito.times(1)).getDevices();
        Mockito.verify(dataSource, Mockito.never()).delete(UserModule.class, deletedUser.getId());
        // make sure user is still returned
        boolean systemUserFound = false;
        for (UserModule user : userService.getUsers(false)) {
            if (user.getId() == deletedUser.getId()) {
                // If system user can still be found
                Assert.assertEquals(deletedUser.getId(), user.getId());
                Assert.assertEquals(deletedUser.getName(), user.getName());
                Assert.assertEquals(deletedUser.getAssociatedProfileId(), user.getAssociatedProfileId());
                systemUserFound = true;
                break;
            }
        }
        Assert.assertTrue(systemUserFound);
    }

    @Test
    public void testDeleteUserAssignedToDevice() {
        UserModule deletedUser = users.get(2);
        // delete user
        Assert.assertFalse(userService.deleteUser(deletedUser.getId()));
        // make sure proper methods have been called
        Mockito.verify(dataSource, Mockito.times(2)).getDevices();
        Mockito.verify(dataSource, Mockito.never()).delete(UserModule.class, deletedUser.getId());
        // make sure user is still returned
        boolean userFound = false;
        for (UserModule user : userService.getUsers(false)) {
            if (user.getId() == deletedUser.getId()) {
                // If deleted user can still be found
                Assert.assertEquals(deletedUser.getId(), user.getId());
                Assert.assertEquals(deletedUser.getName(), user.getName());
                Assert.assertEquals(deletedUser.getAssociatedProfileId(), user.getAssociatedProfileId());
                userFound = true;
                break;
            }
        }
        Assert.assertTrue(userFound);
    }

    @Test
    public void testUpdateUser() {
        UserModule user = users.get(1);

        // update user (change profile)
        UserModule updatedUser = userService.updateUser(
                users.get(1).getId(),
                102,
                users.get(1).getName(),
                users.get(1).getNameKey(),
                null, null,
                null);

        // check it has been updated correctly
        UserModule retrievedUser = userService.getUserById(user.getId());
        Assert.assertNotNull(retrievedUser);
        Assert.assertEquals(user.getId(), retrievedUser.getId());
        Assert.assertEquals(user.getName(), retrievedUser.getName());
        Assert.assertEquals(updatedUser.getAssociatedProfileId(), retrievedUser.getAssociatedProfileId());

        // make sure proper methods have been called
        Mockito.verify(dataSource, Mockito.times(2)).get(Mockito.eq(UserProfileModule.class), Mockito.eq(updatedUser.getAssociatedProfileId()));
        Mockito.verify(dataSource).save(Mockito.any(UserModule.class), Mockito.eq(user.getId()));
        Mockito.verify(listener).onChange(updatedUser);
    }

    @Test
    public void testUpdateSystemUser() {
        UserModule user = users.get(0);

        updateSystemUser("associatedProfileId", user.getId(), user.getAssociatedProfileId() + 1, user.getName(), user.getNameKey(), null);
        updateSystemUser("name", user.getId(), user.getAssociatedProfileId(), user.getName() + "_test", user.getNameKey(), null);
        updateSystemUser("nameKey", user.getId(), user.getAssociatedProfileId(), user.getName(), user.getNameKey() + "_test", null);
        updateSystemUser("nameKey", user.getId(), user.getAssociatedProfileId(), user.getName(), user.getNameKey(), "1234");

        UserModule updatedUser = userService.updateUser(user.getId(), user.getAssociatedProfileId(), user.getName(), user.getNameKey(), null, null, null);
        Assert.assertEquals(user.getId(), updatedUser.getId());
        Assert.assertEquals(user.getAssociatedProfileId(), updatedUser.getAssociatedProfileId());
        Assert.assertEquals(user.getName(), updatedUser.getName());
        Assert.assertEquals(user.getNameKey(), updatedUser.getNameKey());
        Assert.assertEquals(user.getPin(), updatedUser.getPin());
        Mockito.verify(dataSource).save(updatedUser, updatedUser.getId());
    }

    private void updateSystemUser(String error, Integer id, Integer associatedProfileId, String name, String nameKey, String newPin) {
        try {
            userService.updateUser(id, associatedProfileId, name, nameKey, null, null, newPin);
            Assert.fail("updating " + error + " must fail with " + BadRequestException.class);
        } catch (BadRequestException e) {
            Mockito.verify(dataSource, Mockito.never()).save(Mockito.any(UserModule.class), Mockito.anyInt());
        }
    }

    @Test
    public void testUpdateUserPin() {
        UserModule user = users.get(1);

        // setup updated user (changing profile)
        UserModule updatedUser = createMockUser(users.get(1).getId(), users.get(1).getName(), 100, true, pinA);

        // update user
        userService.updateUser(
                users.get(1).getId(),
                100,
                users.get(1).getName(),
                users.get(1).getNameKey(),
                null, null,
                "a");

        // check it has been updated correctly
        UserModule retrievedUser = userService.getUserById(user.getId());
        Assert.assertNotNull(retrievedUser);
        Assert.assertEquals(user.getId(), retrievedUser.getId());
        Assert.assertEquals(user.getName(), retrievedUser.getName());
        Assert.assertEquals(updatedUser.getAssociatedProfileId(), retrievedUser.getAssociatedProfileId());

        Assert.assertTrue(PasswordUtil.verifyPassword("a", retrievedUser.getPin()));

        // make sure proper methods have been called
        Mockito.verify(dataSource, Mockito.times(2)).get(Mockito.eq(UserProfileModule.class), Mockito.eq(updatedUser.getAssociatedProfileId()));
        Mockito.verify(dataSource).save(Mockito.any(UserModule.class), Mockito.eq(user.getId()));
        Mockito.verify(listener).onChange(updatedUser);

    }

    @Test
    public void testUpdateUserAssignedProfileNotExisting() {
        UserModule user = users.get(2);

        // setup updated user (changing profile)
        UserModule updatedUser = createMockUser(users.get(2).getId(), users.get(2).getName(), 123);

        // update user
        try {
            userService.updateUser(
                    users.get(2).getId(),
                    123,
                    users.get(2).getName(),
                    users.get(2).getNameKey(),
                    null, null,
                    null);
            Assert.fail();
        } catch (Exception e) {
            // Exception was expected
        }

        // check it has not been updated
        UserModule retrievedUser = userService.getUserById(user.getId());
        Assert.assertNotNull(retrievedUser);
        Assert.assertEquals(user.getId(), retrievedUser.getId());
        Assert.assertEquals(user.getName(), retrievedUser.getName());
        Assert.assertEquals(user.getAssociatedProfileId(), retrievedUser.getAssociatedProfileId());

        // make sure proper methods have (not) been called
        // Query could not be answered from cache
        Mockito.verify(dataSource).get(UserProfileModule.class, updatedUser.getAssociatedProfileId());
        Mockito.verify(dataSource, Mockito.never()).save(UserModule.class, user.getId());
        Mockito.verify(listener, Mockito.never()).onChange(updatedUser);
    }

    @Test
    public void testUpdateUserRoleParentToChild() {
        UserModule user = users.get(1);

        user.setUserRole(UserRole.PARENT);

        // update user (change profile)
        UserModule updatedUser = userService.updateUser(
                user.getId(),
                102,
                user.getName(),
                user.getNameKey(),
                null, UserRole.CHILD,
                null);

        // make sure proper methods have been called
        Mockito.verify(dashboardCardService).createParentalControlCard(Mockito.eq(user.getId()), Mockito.eq("PARENTAL_CONTROL"), Mockito.eq("FAM"));
    }

    @Test
    public void testUpdateUserRoleChildToParent() {

        UserModule user = users.get(1);

        users.get(1).setUserRole(UserRole.CHILD);
        userService.refresh();

        // update user (change profile)
        UserModule updatedUser = userService.updateUser(
                user.getId(),
                102,
                user.getName(),
                user.getNameKey(),
                null, UserRole.PARENT,
                null);

        // make sure proper methods have been called
        Mockito.verify(dashboardCardService).removeParentalControlCard(Mockito.eq(user.getId()));
    }

    @Test
    public void testCreateChildUser() {
        UserModule user = userService.createUser(102, "Test user", "Name key", null, UserRole.CHILD, null);
        // make sure proper methods have been called
        Mockito.verify(dashboardCardService).createParentalControlCard(Mockito.eq(user.getId()), Mockito.eq("PARENTAL_CONTROL"), Mockito.eq("FAM"));
    }

}
