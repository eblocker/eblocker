package org.eblocker.server.http.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.common.data.wireguard.WireGuardRuntimePeerSelector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Shared authorization-aware runtime projection.
 *
 * The global WireGuard server state is intentionally not evaluated here:
 * startup reconciles peers before persisting the enabled state.
 */
@Singleton
public class WireGuardAuthorizedRuntimePeerSelector
        implements WireGuardRuntimePeerSelector {

    private final DeviceService deviceService;
    private final WireGuardAuthorizationService authorizationService;

    @Inject
    public WireGuardAuthorizedRuntimePeerSelector(
            DeviceService deviceService,
            WireGuardAuthorizationService authorizationService) {

        this.deviceService = deviceService;
        this.authorizationService = authorizationService;
    }

    @Override
    public List<WireGuardPeer> select(
            Collection<WireGuardPeer> peers) {

        List<WireGuardPeer> allowedPeers = new ArrayList<>();

        if (peers == null) {
            return allowedPeers;
        }

        for (WireGuardPeer peer : peers) {
            if (peer == null) {
                continue;
            }

            String deviceId = peer.getDeviceId();

            // Backward compatibility: legacy/admin peers without a device
            // binding remain outside the device/user authorization layer.
            if (deviceId == null || deviceId.trim().isEmpty()) {
                allowedPeers.add(peer);
                continue;
            }

            Device device =
                    deviceService.getDeviceById(deviceId.trim());

            // Missing bound devices fail closed.
            if (device == null) {
                continue;
            }

            if (authorizationService.isDevicePolicyAllowed(device)) {
                allowedPeers.add(peer);
            }
        }

        return allowedPeers;
    }
}
