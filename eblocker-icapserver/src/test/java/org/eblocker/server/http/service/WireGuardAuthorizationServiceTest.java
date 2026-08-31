package org.eblocker.server.http.service;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

public class WireGuardAuthorizationServiceTest {

    private DataSource dataSource;
    private UserService userService;
    private WireGuardAuthorizationService service;
    private Device device;

    @BeforeEach
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        userService = Mockito.mock(UserService.class);
        service = new WireGuardAuthorizationService(dataSource, userService);

        device = new Device();
        device.setId("device:001122334455");
        device.setDefaultSystemUser(10);
        device.setAssignedUser(10);
        device.setOperatingUser(10);
        device.setWireGuardEnabled(true);
    }

    @Test
    public void globalDisableHasHighestPrecedence() {
        Mockito.when(dataSource.getWireGuardServerState()).thenReturn(false);

        WireGuardAuthorizationService.Decision decision =
                service.evaluate(device);

        assertFalse(decision.isAllowed());
        assertEquals(
                WireGuardAuthorizationService.Reason.GLOBAL_DISABLED,
                decision.getReason());
    }

    @Test
    public void devicePermissionIsRequired() {
        Mockito.when(dataSource.getWireGuardServerState()).thenReturn(true);
        device.setWireGuardEnabled(false);

        WireGuardAuthorizationService.Decision decision =
                service.evaluate(device);

        assertFalse(decision.isAllowed());
        assertEquals(
                WireGuardAuthorizationService.Reason.DEVICE_DISABLED,
                decision.getReason());
    }

    @Test
    public void missingAssignedUserFailsClosed() {
        Mockito.when(dataSource.getWireGuardServerState()).thenReturn(true);
        Mockito.when(userService.getUserById(10)).thenReturn(null);

        WireGuardAuthorizationService.Decision decision =
                service.evaluate(device);

        assertFalse(decision.isAllowed());
        assertTrue(decision.isUserPermissionRequired());
        assertEquals(
                WireGuardAuthorizationService.Reason.USER_NOT_FOUND,
                decision.getReason());
    }

    @Test
    public void defaultSystemUserMeansNoAdditionalUserPermission() {
        Mockito.when(dataSource.getWireGuardServerState()).thenReturn(true);
        Mockito.when(userService.getUserById(10))
                .thenReturn(user(10, true, false));

        WireGuardAuthorizationService.Decision decision =
                service.evaluate(device);

        assertTrue(decision.isAllowed());
        assertFalse(decision.isUserPermissionRequired());
        assertEquals(
                WireGuardAuthorizationService.Reason.ALLOWED_NO_ASSIGNED_USER,
                decision.getReason());
    }

    @Test
    public void foreignSystemUserFailsClosedAsInconsistent() {
        Mockito.when(dataSource.getWireGuardServerState()).thenReturn(true);
        device.setAssignedUser(11);
        Mockito.when(userService.getUserById(11))
                .thenReturn(user(11, true, false));

        WireGuardAuthorizationService.Decision decision =
                service.evaluate(device);

        assertFalse(decision.isAllowed());
        assertTrue(decision.isUserPermissionRequired());
        assertEquals(
                WireGuardAuthorizationService.Reason.USER_INCONSISTENT,
                decision.getReason());
    }

    @Test
    public void realAssignedUserPermissionIsRequired() {
        Mockito.when(dataSource.getWireGuardServerState()).thenReturn(true);
        device.setAssignedUser(20);
        Mockito.when(userService.getUserById(20))
                .thenReturn(user(20, false, false));

        WireGuardAuthorizationService.Decision decision =
                service.evaluate(device);

        assertFalse(decision.isAllowed());
        assertTrue(decision.isUserPermissionRequired());
        assertEquals(
                WireGuardAuthorizationService.Reason.USER_DISABLED,
                decision.getReason());
    }

    @Test
    public void realAssignedUserCanGrantAccess() {
        Mockito.when(dataSource.getWireGuardServerState()).thenReturn(true);
        device.setAssignedUser(20);
        Mockito.when(userService.getUserById(20))
                .thenReturn(user(20, false, true));

        WireGuardAuthorizationService.Decision decision =
                service.evaluate(device);

        assertTrue(decision.isAllowed());
        assertTrue(decision.isUserPermissionRequired());
        assertEquals(
                WireGuardAuthorizationService.Reason.ALLOWED,
                decision.getReason());
    }

    @Test
    public void operatingUserDoesNotChangeAuthorizationOwner() {
        Mockito.when(dataSource.getWireGuardServerState()).thenReturn(true);
        device.setAssignedUser(20);
        device.setOperatingUser(99);

        Mockito.when(userService.getUserById(20))
                .thenReturn(user(20, false, true));

        WireGuardAuthorizationService.Decision decision =
                service.evaluate(device);

        assertTrue(decision.isAllowed());
        assertEquals(Integer.valueOf(20), decision.getAssignedUserId());
        Mockito.verify(userService).getUserById(20);
        Mockito.verify(userService, Mockito.never()).getUserById(99);
    }

    @Test
    public void genericEnabledPausedStateIsOrthogonalToWireGuardPolicy() {
        Mockito.when(dataSource.getWireGuardServerState()).thenReturn(true);
        device.setEnabled(false);
        device.setPaused(true);

        Mockito.when(userService.getUserById(10))
                .thenReturn(user(10, true, false));

        WireGuardAuthorizationService.Decision decision =
                service.evaluate(device);

        assertTrue(decision.isAllowed());
        assertEquals(
                WireGuardAuthorizationService.Reason.ALLOWED_NO_ASSIGNED_USER,
                decision.getReason());
    }

    private UserModule user(
            int id,
            boolean system,
            boolean wireGuardEnabled) {

        UserModule user = new UserModule(
                id,
                1,
                "User " + id,
                null,
                null,
                UserRole.OTHER,
                system,
                null,
                null,
                null,
                null,
                null);

        user.setWireGuardEnabled(wireGuardEnabled);
        return user;
    }
}
