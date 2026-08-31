package org.eblocker.server.http.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;

/**
 * Computes effective WireGuard authorization for a device.
 *
 * Authorization is deliberately independent from the generic Device
 * enabled/paused state. WireGuard has its own three-level policy:
 *
 * global WireGuard state -> device permission -> assigned-user permission.
 *
 * The device's transient operating user is intentionally not used.
 */
@Singleton
public class WireGuardAuthorizationService {

    public enum Reason {
        ALLOWED,
        ALLOWED_NO_ASSIGNED_USER,
        GLOBAL_DISABLED,
        DEVICE_DISABLED,
        DEVICE_NOT_FOUND,
        USER_NOT_FOUND,
        USER_INCONSISTENT,
        USER_DISABLED
    }

    public static final class Decision {
        private final boolean allowed;
        private final Reason reason;
        private final Integer assignedUserId;
        private final boolean userPermissionRequired;

        private Decision(
                boolean allowed,
                Reason reason,
                Integer assignedUserId,
                boolean userPermissionRequired) {
            this.allowed = allowed;
            this.reason = reason;
            this.assignedUserId = assignedUserId;
            this.userPermissionRequired = userPermissionRequired;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public Reason getReason() {
            return reason;
        }

        public Integer getAssignedUserId() {
            return assignedUserId;
        }

        public boolean isUserPermissionRequired() {
            return userPermissionRequired;
        }
    }

    private final DataSource dataSource;
    private final UserService userService;

    @Inject
    public WireGuardAuthorizationService(
            DataSource dataSource,
            UserService userService) {
        this.dataSource = dataSource;
        this.userService = userService;
    }

    public Decision evaluate(Device device) {
        if (device == null) {
            return denied(Reason.DEVICE_NOT_FOUND, null, false);
        }

        if (!dataSource.getWireGuardServerState()) {
            return denied(
                    Reason.GLOBAL_DISABLED,
                    device.getAssignedUser(),
                    false);
        }

        return evaluateDevicePolicy(device);
    }

    public boolean isAllowed(Device device) {
        return evaluate(device).isAllowed();
    }

    /**
     * Evaluates the device/user part of the policy without the global
     * WireGuard server state.
     *
     * This is used when preparing runtime peers before the server is started:
     * WireGuardServerService reconciles peers first and only then persists the
     * global enabled state. Including the global state here would therefore
     * incorrectly remove all peers during server enable/startup.
     */
    public Decision evaluateDevicePolicy(Device device) {
        if (device == null) {
            return denied(Reason.DEVICE_NOT_FOUND, null, false);
        }

        if (!device.isWireGuardEnabled()) {
            return denied(
                    Reason.DEVICE_DISABLED,
                    device.getAssignedUser(),
                    false);
        }

        int assignedUserId = device.getAssignedUser();
        UserModule assignedUser = userService.getUserById(assignedUserId);

        if (assignedUser == null) {
            return denied(
                    Reason.USER_NOT_FOUND,
                    assignedUserId,
                    true);
        }

        if (assignedUser.isSystem()) {
            if (assignedUserId != device.getDefaultSystemUser()) {
                return denied(
                        Reason.USER_INCONSISTENT,
                        assignedUserId,
                        true);
            }

            // The device-specific system user represents "no real user assigned".
            // In that case the user authorization layer does not apply.
            return allowed(
                    Reason.ALLOWED_NO_ASSIGNED_USER,
                    assignedUserId,
                    false);
        }

        if (!assignedUser.isWireGuardEnabled()) {
            return denied(
                    Reason.USER_DISABLED,
                    assignedUserId,
                    true);
        }

        return allowed(
                Reason.ALLOWED,
                assignedUserId,
                true);
    }

    public boolean isDevicePolicyAllowed(Device device) {
        return evaluateDevicePolicy(device).isAllowed();
    }

    private static Decision allowed(
            Reason reason,
            Integer assignedUserId,
            boolean userPermissionRequired) {
        return new Decision(
                true,
                reason,
                assignedUserId,
                userPermissionRequired);
    }

    private static Decision denied(
            Reason reason,
            Integer assignedUserId,
            boolean userPermissionRequired) {
        return new Decision(
                false,
                reason,
                assignedUserId,
                userPermissionRequired);
    }
}
