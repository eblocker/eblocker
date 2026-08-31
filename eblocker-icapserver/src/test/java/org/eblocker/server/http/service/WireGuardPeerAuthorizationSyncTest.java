package org.eblocker.server.http.service;

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.common.system.ScriptRunner;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WireGuardPeerAuthorizationSyncTest {

    @Test
    public void filtersDeniedAndMissingBoundDevicesButKeepsUnboundPeers() {
        ScriptRunner scriptRunner = Mockito.mock(ScriptRunner.class);
        DeviceService deviceService = Mockito.mock(DeviceService.class);
        WireGuardAuthorizationService authorizationService =
                Mockito.mock(WireGuardAuthorizationService.class);

        WireGuardPeerSyncService service =
                new WireGuardPeerSyncService(
                        scriptRunner,
                        "wireguard-server-control",
                        deviceService,
                        authorizationService
                );

        WireGuardPeer allowed = peer(1, "device:allowed");
        WireGuardPeer denied = peer(2, "device:denied");
        WireGuardPeer missing = peer(3, "device:missing");
        WireGuardPeer unbound = peer(4, null);

        Device allowedDevice = device("device:allowed");
        Device deniedDevice = device("device:denied");

        Mockito.when(
                deviceService.getDeviceById("device:allowed")
        ).thenReturn(allowedDevice);

        Mockito.when(
                deviceService.getDeviceById("device:denied")
        ).thenReturn(deniedDevice);

        Mockito.when(
                authorizationService.isDevicePolicyAllowed(allowedDevice)
        ).thenReturn(true);

        Mockito.when(
                authorizationService.isDevicePolicyAllowed(deniedDevice)
        ).thenReturn(false);

        List<WireGuardPeer> result =
                service.filterRuntimePeers(
                        Arrays.asList(
                                allowed,
                                denied,
                                missing,
                                unbound
                        )
                );

        assertEquals(2, result.size());
        assertTrue(result.contains(allowed));
        assertTrue(result.contains(unbound));
        assertFalse(result.contains(denied));
        assertFalse(result.contains(missing));
    }

    private Device device(String id) {
        Device device = new Device();
        device.setId(id);
        return device;
    }

    private WireGuardPeer peer(int id, String deviceId) {
        WireGuardPeer peer = new WireGuardPeer();
        peer.setId(id);
        peer.setDeviceId(deviceId);
        peer.setPublicKey("public-" + id);
        peer.setPresharedKey("psk-" + id);
        peer.setAllowedIp("10.13.13." + (id + 1) + "/32");
        return peer;
    }
}
