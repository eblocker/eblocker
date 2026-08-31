package org.eblocker.server.http.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.http.model.WireGuardAuthorizationView;

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

        // Effective WireGuard authorization depends on the assigned user and
        // on the continued existence of the device. DeviceService publishes
        // synchronous lifecycle callbacks after persistence/cache updates.
        // Reconcile runtime on all such changes so an already active peer can
        // never retain access after reassignment, deletion or reset.
        this.deviceService.addListener(
                new DeviceService.DeviceChangeListener() {
                    @Override
                    public void onChange(Device device) {
                        reconcileRuntimeIfEnabled();
                    }

                    @Override
                    public void onDelete(Device device) {
                        reconcileRuntimeIfEnabled();
                    }

                    @Override
                    public void onReset(Device device) {
                        reconcileRuntimeIfEnabled();
                    }
                }
        );
    }

    public WireGuardAuthorizationView getDeviceAuthorization(
            String deviceId) {

        Device device = deviceService.getDeviceById(deviceId);

        if (device == null) {
            return null;
        }

        WireGuardAuthorizationService.Decision decision =
                authorizationService.evaluate(device);

        UserModule assignedUser =
                userService.getUserById(device.getAssignedUser());

        return WireGuardAuthorizationView.from(
                device,
                assignedUser,
                dataSource.getWireGuardServerState(),
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
