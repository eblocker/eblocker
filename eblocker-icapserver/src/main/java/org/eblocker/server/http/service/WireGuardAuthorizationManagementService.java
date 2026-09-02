package org.eblocker.server.http.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.http.model.WireGuardAuthorizationOverviewView;
import org.eblocker.server.http.model.WireGuardAuthorizationView;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Persists WireGuard authorization changes and immediately reconciles runtime.
 *
 * Persistent authorization is the source of truth. If runtime reconciliation
 * fails while WireGuard is globally enabled, the runtime server is stopped
 * fail-closed instead of rolling a revocation back to an allowed state.
 */
@Singleton
public class WireGuardAuthorizationManagementService {

    private final DataSource dataSource;
    private final DeviceService deviceService;
    private final UserService userService;
    private final WireGuardAuthorizationService authorizationService;
    private final WireGuardPeerService peerService;
    private final WireGuardServerControlService controlService;
    private final NetworkStateMachine networkStateMachine;

    /**
     * Last observed facts that can change effective WireGuard device policy.
     * DeviceService.onChange() is broader and also reports operational
     * changes such as newly learned IP addresses.
     */
    private final Map<String, DevicePolicyState> devicePolicyStates =
            new ConcurrentHashMap<>();

    @Inject
    public WireGuardAuthorizationManagementService(
            DataSource dataSource,
            DeviceService deviceService,
            UserService userService,
            WireGuardAuthorizationService authorizationService,
            WireGuardPeerService peerService,
            WireGuardServerControlService controlService,
            NetworkStateMachine networkStateMachine) {

        this.dataSource = dataSource;
        this.deviceService = deviceService;
        this.userService = userService;
        this.authorizationService = authorizationService;
        this.peerService = peerService;
        this.controlService = controlService;
        this.networkStateMachine = networkStateMachine;

        // Effective WireGuard authorization depends on the device permission,
        // assigned user and continued existence/reset of the device.
        // DeviceService.onChange() itself is broader than these facts.
        this.deviceService.addListener(
                new DeviceService.DeviceChangeListener() {
                    @Override
                    public void onChange(Device device) {
                        if (devicePolicyChanged(device)) {
                            reconcileRuntimeIfEnabled();
                        }
                    }

                    @Override
                    public void onDelete(Device device) {
                        forgetDevicePolicyState(device);
                        reconcileRuntimeIfEnabled();
                    }

                    @Override
                    public void onReset(Device device) {
                        rememberDevicePolicyState(device);
                        reconcileRuntimeIfEnabled();
                    }
                }
        );
    }

    public WireGuardAuthorizationOverviewView getDeviceAuthorizations() {
        boolean globalEnabled =
                dataSource.getWireGuardServerState();

        List<WireGuardAuthorizationView> devices =
                deviceService.getDevices(true)
                        .stream()
                        .sorted(Comparator.comparing(Device::getId))
                        .map(device -> toAuthorizationView(
                                device,
                                globalEnabled
                        ))
                        .collect(Collectors.toList());

        return new WireGuardAuthorizationOverviewView(
                globalEnabled,
                devices
        );
    }

    public WireGuardAuthorizationView getDeviceAuthorization(
            String deviceId) {

        Device device = deviceService.getDeviceById(deviceId);

        if (device == null) {
            return null;
        }

        return toAuthorizationView(
                device,
                dataSource.getWireGuardServerState()
        );
    }

    private WireGuardAuthorizationView toAuthorizationView(
            Device device,
            boolean globalEnabled) {

        WireGuardAuthorizationService.Decision decision =
                authorizationService.evaluate(
                        device,
                        globalEnabled
                );

        UserModule assignedUser =
                userService.getUserById(device.getAssignedUser());

        return WireGuardAuthorizationView.from(
                device,
                assignedUser,
                globalEnabled,
                decision
        );
    }

    public synchronized boolean setDeviceAuthorization(
            String deviceId,
            boolean enabled) {

        Device device = deviceService.getDeviceById(deviceId);

        if (device == null) {
            return false;
        }

        if (device.isWireGuardEnabled() == enabled) {
            reconcileRuntimeIfEnabled();
            return true;
        }

        device.setWireGuardEnabled(enabled);

        // DeviceService synchronously publishes onChange() after persistence.
        // The listener registered in this service performs the single runtime
        // reconciliation. Do not reconcile a second time here.
        deviceService.updateDevice(device);

        return true;
    }

    public synchronized boolean setUserAuthorization(
            int userId,
            boolean enabled) {

        UserModule user = userService.getUserById(userId);

        if (user == null) {
            throw new IllegalArgumentException(
                    "WireGuard user not found."
            );
        }

        if (user.isSystem()) {
            throw new IllegalArgumentException(
                    "WireGuard user authorization does not apply to "
                            + "built-in system users."
            );
        }

        userService.setWireGuardEnabled(userId, enabled);
        reconcileRuntimeIfEnabled();
        return enabled;
    }

    private boolean devicePolicyChanged(
            Device device) {

        if (device == null
                || device.getId() == null) {

            return true;
        }

        DevicePolicyState current =
                DevicePolicyState.from(device);

        DevicePolicyState previous =
                devicePolicyStates.put(
                        device.getId(),
                        current
                );

        // The first observation is intentionally relevant once. This keeps
        // authorization fail-closed without introducing constructor-time
        // persistence reads. Subsequent ARP/IP-only updates are ignored.
        return previous == null
                || !previous.samePolicyAs(current);
    }

    private void rememberDevicePolicyState(
            Device device) {

        if (device == null
                || device.getId() == null) {

            return;
        }

        devicePolicyStates.put(
                device.getId(),
                DevicePolicyState.from(device)
        );
    }

    private void forgetDevicePolicyState(
            Device device) {

        if (device == null
                || device.getId() == null) {

            return;
        }

        devicePolicyStates.remove(
                device.getId()
        );
    }

    private static final class DevicePolicyState {

        private final boolean wireGuardEnabled;
        private final int assignedUser;

        private DevicePolicyState(
                boolean wireGuardEnabled,
                int assignedUser) {

            this.wireGuardEnabled = wireGuardEnabled;
            this.assignedUser = assignedUser;
        }

        private static DevicePolicyState from(
                Device device) {

            return new DevicePolicyState(
                    device.isWireGuardEnabled(),
                    device.getAssignedUser()
            );
        }

        private boolean samePolicyAs(
                DevicePolicyState other) {

            return other != null
                    && wireGuardEnabled == other.wireGuardEnabled
                    && assignedUser == other.assignedUser;
        }
    }

    private void reconcileRuntimeIfEnabled() {
        if (!dataSource.getWireGuardServerState()) {
            return;
        }

        try {
            peerService.reconcilePeers();
            networkStateMachine.updateFirewall();

        } catch (RuntimeException reconcileException) {
            try {
                controlService.stop();
            } catch (RuntimeException stopException) {
                reconcileException.addSuppressed(stopException);
            }

            throw new IllegalStateException(
                    "WireGuard authorization was persisted, but runtime "
                            + "reconciliation failed. WireGuard runtime was "
                            + "stopped fail-closed.",
                    reconcileException
            );
        }
    }
}
