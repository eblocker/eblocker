package org.eblocker.server.http.service;

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WireGuardAuthorizedRuntimePeerSelectorTest {

    @Test
    public void keepsAllowedAndUnboundPeersAndDeniesUnavailablePolicy() {
        DeviceService deviceService =
                Mockito.mock(DeviceService.class);

        WireGuardAuthorizationService authorizationService =
                Mockito.mock(WireGuardAuthorizationService.class);

        WireGuardAuthorizedRuntimePeerSelector selector =
                new WireGuardAuthorizedRuntimePeerSelector(
                        deviceService,
                        authorizationService
                );

        WireGuardPeer allowed = peer(1, "device:allowed");
        WireGuardPeer denied = peer(2, "device:denied");
        WireGuardPeer missing = peer(3, "device:missing");
        WireGuardPeer unbound = peer(4, null);
        WireGuardPeer blankBound = peer(5, "   ");

        Device allowedDevice = device("device:allowed");
        Device deniedDevice = device("device:denied");

        Mockito.when(
                deviceService.getDeviceById("device:allowed")
        ).thenReturn(allowedDevice);

        Mockito.when(
                deviceService.getDeviceById("device:denied")
        ).thenReturn(deniedDevice);

        Mockito.when(
                authorizationService
                        .isDevicePolicyAllowed(allowedDevice)
        ).thenReturn(true);

        Mockito.when(
                authorizationService
                        .isDevicePolicyAllowed(deniedDevice)
        ).thenReturn(false);

        List<WireGuardPeer> result =
                selector.select(
                        Arrays.asList(
                                allowed,
                                denied,
                                missing,
                                unbound,
                                blankBound,
                                null
                        )
                );

        assertEquals(
                Arrays.asList(
                        allowed,
                        unbound,
                        blankBound
                ),
                result
        );
    }

    @Test
    public void nullInputReturnsEmptyList() {
        WireGuardAuthorizedRuntimePeerSelector selector =
                new WireGuardAuthorizedRuntimePeerSelector(
                        Mockito.mock(DeviceService.class),
                        Mockito.mock(WireGuardAuthorizationService.class)
                );

        assertTrue(selector.select(null).isEmpty());
    }

    private Device device(String id) {
        Device device = new Device();
        device.setId(id);
        return device;
    }

    private WireGuardPeer peer(
            int id,
            String deviceId) {

        WireGuardPeer peer = new WireGuardPeer();
        peer.setId(id);
        peer.setDeviceId(deviceId);
        return peer;
    }
}
