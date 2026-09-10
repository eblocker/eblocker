package org.eblocker.server.http.service;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DeviceFactory;
import org.eblocker.server.common.data.MacPrefix;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WireGuardAuthorizationLifecycleTest {
    private DataSource data;
    private DeviceFactory factory;
    private DeviceService devices;
    private UserService users;
    private UserModule user;
    private Device device;
    private WireGuardPeerService peers;
    private WireGuardServerControlService control;
    private NetworkStateMachine network;
    private WireGuardAuthorizationManagementService authorization;
    private WireGuardAuthorizedRuntimePeerSelector selector;
    private WireGuardPeer peer;
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicBoolean persisted = new AtomicBoolean(true);
    private final AtomicReference<List<WireGuardPeer>> active = new AtomicReference<>();
    private final AtomicReference<List<WireGuardPeer>> projected = new AtomicReference<>();

    @BeforeEach
    void setup() {
        data = Mockito.mock(DataSource.class);
        factory = Mockito.mock(DeviceFactory.class);
        users = Mockito.mock(UserService.class);
        user = Mockito.mock(UserModule.class);
        Mockito.when(user.isSystem()).thenReturn(true);
        Mockito.when(users.getUserById(7)).thenReturn(user);
        Mockito.when(data.get(Mockito.eq(UserModule.class), Mockito.anyInt())).thenReturn(user);
        device = device(true);
        device.setOnline(true);
        Mockito.when(data.getDevices()).thenReturn(Collections.singleton(device));
        devices = new DeviceService(data, null, Mockito.mock(UserAgentService.class),
                null, factory, null, Clock.systemUTC(), 90, Mockito.mock(MacPrefix.class));
        assertSame(device, devices.getDeviceById(device.getId()));
        peers = Mockito.mock(WireGuardPeerService.class);
        control = Mockito.mock(WireGuardServerControlService.class);
        network = Mockito.mock(NetworkStateMachine.class);
        WireGuardAuthorizationService policy = new WireGuardAuthorizationService(data, users);
        selector = new WireGuardAuthorizedRuntimePeerSelector(devices, policy);
        peer = new WireGuardPeer();
        peer.setId(1);
        peer.setDeviceId(device.getId());
        active.set(Collections.singletonList(peer));
        projected.set(active.get());
        Mockito.when(data.getWireGuardServerState()).thenAnswer(i -> enabled.get());
        Mockito.doAnswer(i -> { enabled.set(i.getArgument(0)); return null; })
                .when(data).setWireGuardServerState(Mockito.anyBoolean());
        Mockito.doAnswer(i -> {
            persisted.set(((Device) i.getArgument(0)).isWireGuardEnabled());
            return null;
        }).when(data).save(Mockito.any(Device.class));
        Mockito.doAnswer(i -> {
            List<WireGuardPeer> selection = selector.select(Collections.singletonList(peer));
            projected.set(selection);
            if (enabled.get()) {
                active.set(selection);
            }
            return null;
        }).when(peers).reconcilePeers();
        Mockito.doAnswer(i -> { active.set(Collections.emptyList()); return null; }).when(control).stop();
        authorization = new WireGuardAuthorizationManagementService(
                data, devices, users, policy, peers, control, network);
    }

    private Device device(boolean grant) {
        Device result = new Device();
        result.setId("device:001122334455");
        result.setIpAddresses(new ArrayList<>());
        result.setDefaultSystemUser(7);
        result.setAssignedUser(7);
        result.setWireGuardEnabled(grant);
        return result;
    }

    @Test
    void onlineResetProjectsReplacementAfterActualCacheAndSaveOrdering() {
        // Seed the remembered old policy, exactly as an earlier device event does.
        devices.updateDevice(device);
        Device replacement = device(false);
        Mockito.when(factory.createDevice(Mockito.eq(device.getId()), Mockito.anyList(), Mockito.eq(false)))
                .thenReturn(replacement);
        devices.addListener(new DeviceService.DeviceChangeListener() {
            public void onChange(Device changed) {}
            public void onDelete(Device changed) {}
            public void onReset(Device changed) {
                // DeviceService has not published/saved the replacement yet.
                assertSame(device, devices.getDeviceById(device.getId()));
                assertTrue(persisted.get());
            }
        });
        assertSame(replacement, devices.resetDevice(device.getId()));
        Mockito.verify(data, Mockito.never()).delete(Mockito.any(Device.class));
        assertSame(replacement, devices.getDeviceById(device.getId()));
        assertFalse(persisted.get());
        assertTrue(active.get().isEmpty(), "Reset must revoke the previously active peer");
    }

    @Test
    void failedSaveRestoresCacheAndRetryActuallyPersists() {
        AtomicInteger writes = new AtomicInteger();
        Mockito.doAnswer(i -> {
            if (writes.incrementAndGet() == 1) {
                throw new IllegalStateException("Redis write failed");
            }
            persisted.set(((Device) i.getArgument(0)).isWireGuardEnabled());
            return null;
        }).when(data).save(Mockito.any(Device.class));
        assertThrows(IllegalStateException.class,
                () -> authorization.setDeviceAuthorization(device.getId(), false));
        assertTrue(device.isWireGuardEnabled());
        assertTrue(persisted.get());
        Mockito.verify(peers, Mockito.never()).reconcilePeers();
        assertTrue(authorization.setDeviceAuthorization(device.getId(), false));
        assertEquals(2, writes.get());
        assertFalse(persisted.get());
        assertFalse(device.isWireGuardEnabled());
        assertTrue(active.get().isEmpty());
    }

    @Test
    void reconcileFailureDoesNotUndoPersistedRevocation() {
        Mockito.doThrow(new IllegalStateException("runtime apply failed")).when(peers).reconcilePeers();
        assertThrows(IllegalStateException.class,
                () -> authorization.setDeviceAuthorization(device.getId(), false));
        assertFalse(persisted.get());
        assertFalse(device.isWireGuardEnabled());
        assertTrue(active.get().isEmpty());
        Mockito.verify(control).stop();
    }

    @Test
    void enableAndDeviceRevocationAreCoordinated() throws Exception {
        enableAndRevoke(false);
    }

    @Test
    void enableAndUserRevocationAreCoordinated() throws Exception {
        enableAndRevoke(true);
    }

    private void enableAndRevoke(boolean userGrant) throws Exception {
        enabled.set(false);
        AtomicBoolean userEnabled = new AtomicBoolean(userGrant);
        if (userGrant) {
            device.setWireGuardEnabled(false);
            Mockito.when(user.isSystem()).thenReturn(false);
            Mockito.when(user.isWireGuardEnabled()).thenAnswer(i -> userEnabled.get());
            Mockito.when(users.setWireGuardEnabled(Mockito.eq(7), Mockito.anyBoolean()))
                    .thenAnswer(i -> { userEnabled.set(i.getArgument(1)); return user; });
        }
        CountDownLatch afterProjection = new CountDownLatch(1);
        CountDownLatch allowActivation = new CountDownLatch(1);
        Mockito.doAnswer(i -> {
            afterProjection.countDown();
            assertTrue(allowActivation.await(5, TimeUnit.SECONDS));
            assertTrue(userGrant ? userEnabled.get() : device.isWireGuardEnabled(),
                    "Activation must not use a projection whose grant was already revoked");
            active.set(projected.get());
            return null;
        }).when(control).start();
        WireGuardServerService server = new WireGuardServerService(
                data, peers, control, network, Mockito.mock(ScheduledExecutorService.class));
        FutureTask<Void> enableTask = new FutureTask<>(() -> { server.enable(); return null; });
        FutureTask<Void> revokeTask = new FutureTask<>(() -> {
            if (userGrant) {
                authorization.setUserAuthorization(7, false);
            } else {
                authorization.setDeviceAuthorization(device.getId(), false);
            }
            return null;
        });
        Thread enabling = new Thread(enableTask, "wg-test-enable");
        Thread revoking = new Thread(revokeTask, "wg-test-revoke");
        enabling.start();
        try {
            assertTrue(afterProjection.await(5, TimeUnit.SECONDS));
            revoking.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (revoking.getState() != Thread.State.BLOCKED && !revokeTask.isDone()
                    && System.nanoTime() < deadline) {
                Thread.yield();
            }
            assertEquals(Thread.State.BLOCKED, revoking.getState(),
                    "Revocation must wait for projection/activation/enabled-state transition");
            allowActivation.countDown();
            enableTask.get(5, TimeUnit.SECONDS);
            revokeTask.get(5, TimeUnit.SECONDS);
            assertTrue(enabled.get());
            assertTrue(active.get().isEmpty(), "Completed revocation must remove the peer");
            assertFalse(userGrant ? userEnabled.get() : persisted.get());
        } finally {
            allowActivation.countDown();
            enabling.join(5000);
            revoking.join(5000);
            assertFalse(enabling.isAlive());
            assertFalse(revoking.isAlive());
        }
    }
}
