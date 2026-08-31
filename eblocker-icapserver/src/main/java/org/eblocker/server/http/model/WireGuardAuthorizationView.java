package org.eblocker.server.http.model;

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.http.service.WireGuardAuthorizationService;

public class WireGuardAuthorizationView {

    private final String deviceId;
    private final boolean globalEnabled;
    private final boolean deviceEnabled;
    private final Integer assignedUserId;
    private final boolean userPermissionRequired;
    private final Boolean userEnabled;
    private final boolean allowed;
    private final String reason;

    public WireGuardAuthorizationView(
            String deviceId,
            boolean globalEnabled,
            boolean deviceEnabled,
            Integer assignedUserId,
            boolean userPermissionRequired,
            Boolean userEnabled,
            boolean allowed,
            String reason) {

        this.deviceId = deviceId;
        this.globalEnabled = globalEnabled;
        this.deviceEnabled = deviceEnabled;
        this.assignedUserId = assignedUserId;
        this.userPermissionRequired = userPermissionRequired;
        this.userEnabled = userEnabled;
        this.allowed = allowed;
        this.reason = reason;
    }

    public static WireGuardAuthorizationView from(
            Device device,
            UserModule assignedUser,
            boolean globalEnabled,
            WireGuardAuthorizationService.Decision decision) {

        Boolean userEnabled = null;

        if (decision.isUserPermissionRequired()
                && assignedUser != null
                && !assignedUser.isSystem()) {

            userEnabled = assignedUser.isWireGuardEnabled();
        }

        return new WireGuardAuthorizationView(
                device.getId(),
                globalEnabled,
                device.isWireGuardEnabled(),
                decision.getAssignedUserId(),
                decision.isUserPermissionRequired(),
                userEnabled,
                decision.isAllowed(),
                decision.getReason().name()
        );
    }

    public String getDeviceId() {
        return deviceId;
    }

    public boolean isGlobalEnabled() {
        return globalEnabled;
    }

    public boolean isDeviceEnabled() {
        return deviceEnabled;
    }

    public Integer getAssignedUserId() {
        return assignedUserId;
    }

    public boolean isUserPermissionRequired() {
        return userPermissionRequired;
    }

    public Boolean getUserEnabled() {
        return userEnabled;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getReason() {
        return reason;
    }
}
