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
 * global WireGuard state -> (device permission OR assigned-user permission).
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

        return evaluate(
                device,
                dataSource.getWireGuardServerState()
        );
    }

    /**
     * Evaluates against an already captured global state.
     *
     * Aggregate authorization reads use one global-state snapshot so all
     * returned device decisions describe the same control-plane instant.
     */
    public Decision evaluate(
            Device device,
            boolean globalEnabled) {

        if (device == null) {
            return denied(
                    Reason.DEVICE_NOT_FOUND,
                    null,
                    false);
        }

        Decision devicePolicy =
                evaluateDevicePolicy(device);

        if (!globalEnabled) {
            return denied(
                    Reason.GLOBAL_DISABLED,
                    devicePolicy.getAssignedUserId(),
                    devicePolicy.isUserPermissionRequired());
        }

        return devicePolicy;
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
            return denied(
                    Reason.DEVICE_NOT_FOUND,
                    null,
                    false);
        }

        int assignedUserId = device.getAssignedUser();
        UserModule assignedUser =
                userService.getUserById(assignedUserId);

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

            // The device-specific system user represents no real user.
            // Only the device-specific WireGuard grant applies.
            if (device.isWireGuardEnabled()) {
                return allowed(
                        Reason.ALLOWED_NO_ASSIGNED_USER,
                        assignedUserId,
                        false);
            }

            return denied(
                    Reason.DEVICE_DISABLED,
                    assignedUserId,
                    false);
        }

        // A real assigned user and the individual device are independent
        // grants. Either grant is sufficient; both off means denied.
        if (device.isWireGuardEnabled()
                || assignedUser.isWireGuardEnabled()) {

            return allowed(
                    Reason.ALLOWED,
                    assignedUserId,
                    true);
        }

        return denied(
                Reason.DEVICE_DISABLED,
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
