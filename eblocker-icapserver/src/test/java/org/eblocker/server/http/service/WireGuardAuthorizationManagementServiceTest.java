package org.eblocker.server.http.service;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserRole;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.http.model.WireGuardAuthorizationOverviewView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class WireGuardAuthorizationManagementServiceTest {

    private DataSource dataSource;
    private DeviceService deviceService;
    private UserService userService;
    private WireGuardAuthorizationService authorizationService;
    private WireGuardPeerService peerService;
    private WireGuardServerControlService controlService;
    private NetworkStateMachine networkStateMachine;
    private WireGuardAuthorizationManagementService service;
    private DeviceService.DeviceChangeListener deviceChangeListener;

    @BeforeEach
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        deviceService = Mockito.mock(DeviceService.class);
        userService = Mockito.mock(UserService.class);
        authorizationService =
                Mockito.mock(WireGuardAuthorizationService.class);
        peerService = Mockito.mock(WireGuardPeerService.class);
        controlService =
                Mockito.mock(WireGuardServerControlService.class);
        networkStateMachine =
                Mockito.mock(NetworkStateMachine.class);

        service = new WireGuardAuthorizationManagementService(
                dataSource,
                deviceService,
                userService,
                authorizationService,
                peerService,
                controlService,
                networkStateMachine
        );

        ArgumentCaptor<DeviceService.DeviceChangeListener> listenerCaptor =
                ArgumentCaptor.forClass(
                        DeviceService.DeviceChangeListener.class
                );

        Mockito.verify(deviceService).addListener(
                listenerCaptor.capture()
        );

        deviceChangeListener = listenerCaptor.getValue();

        // DeviceService.updateDevice() invokes its listeners synchronously in
        // production. Reproduce that behavior with the mock so authorization
        // setter tests also detect accidental duplicate reconciliation.
        Mockito.doAnswer(invocation -> {
            Device changedDevice = invocation.getArgument(0);
            deviceChangeListener.onChange(changedDevice);
            return null;
        }).when(deviceService).updateDevice(
                Mockito.any(Device.class)
        );
    }

    @Test
    public void constructorRegistersDeviceLifecycleListener() {
        assertNotNull(deviceChangeListener);
    }

    @Test
    public void assignedUserOrOtherDeviceChangeReconcilesRuntime() {
        Mockito.when(
                dataSource.getWireGuardServerState()
        ).thenReturn(true);

        deviceChangeListener.onChange(
                device("device:001122334455", true)
        );

        Mockito.verify(peerService).reconcilePeers();
        Mockito.verify(networkStateMachine).updateFirewall();
    }

    @Test
    public void deviceDeletionReconcilesRuntime() {
        Mockito.when(
                dataSource.getWireGuardServerState()
        ).thenReturn(true);

        deviceChangeListener.onDelete(
                device("device:001122334455", true)
        );

        Mockito.verify(peerService).reconcilePeers();
        Mockito.verify(networkStateMachine).updateFirewall();
    }

    @Test
    public void deviceResetReconcilesRuntime() {
        Mockito.when(
                dataSource.getWireGuardServerState()
        ).thenReturn(true);

        deviceChangeListener.onReset(
                device("device:001122334455", false)
        );

        Mockito.verify(peerService).reconcilePeers();
        Mockito.verify(networkStateMachine).updateFirewall();
    }

    @Test
    public void lifecycleChangeDoesNothingWhileWireGuardIsDisabled() {
        Mockito.when(
                dataSource.getWireGuardServerState()
        ).thenReturn(false);

        deviceChangeListener.onChange(
                device("device:001122334455", true)
        );

        Mockito.verify(
                peerService,
                Mockito.never()
        ).reconcilePeers();

        Mockito.verify(
                networkStateMachine,
                Mockito.never()
        ).updateFirewall();
    }

    @Test
    public void lifecycleReconcileFailureStopsRuntimeFailClosed() {
        Mockito.when(
                dataSource.getWireGuardServerState()
        ).thenReturn(true);

        Mockito.doThrow(
                new IllegalStateException("apply failed")
        ).when(peerService).reconcilePeers();

        assertThrows(
                IllegalStateException.class,
                () -> deviceChangeListener.onDelete(
                        device("device:001122334455", true)
                )
        );

        Mockito.verify(controlService).stop();
    }

    @Test
    public void aggregateReadUsesOneGlobalSnapshotAndAllDevices() {
        DataSource aggregateDataSource =
                Mockito.mock(DataSource.class);
        DeviceService aggregateDeviceService =
                Mockito.mock(DeviceService.class);
        UserService aggregateUserService =
                Mockito.mock(UserService.class);

        Device noUserDevice =
                device("device:a", true);
        noUserDevice.setDefaultSystemUser(10);
        noUserDevice.setAssignedUser(10);

        Device userDevice =
                device("device:b", true);
        userDevice.setDefaultSystemUser(11);
        userDevice.setAssignedUser(20);

        Mockito.when(
                aggregateDataSource.getWireGuardServerState()
        ).thenReturn(true);

        Mockito.when(
                aggregateDeviceService.getDevices(true)
        ).thenReturn(Arrays.asList(
                userDevice,
                noUserDevice
        ));

        Mockito.when(
                aggregateUserService.getUserById(10)
        ).thenReturn(user(10, true, false));

        Mockito.when(
                aggregateUserService.getUserById(20)
        ).thenReturn(user(20, false, true));

        WireGuardAuthorizationService aggregateAuthorizationService =
                new WireGuardAuthorizationService(
                        aggregateDataSource,
                        aggregateUserService
                );

        WireGuardAuthorizationManagementService aggregateService =
                new WireGuardAuthorizationManagementService(
                        aggregateDataSource,
                        aggregateDeviceService,
                        aggregateUserService,
                        aggregateAuthorizationService,
                        Mockito.mock(WireGuardPeerService.class),
                        Mockito.mock(WireGuardServerControlService.class),
                        Mockito.mock(NetworkStateMachine.class)
                );

        WireGuardAuthorizationOverviewView overview =
                aggregateService.getDeviceAuthorizations();

        assertTrue(overview.isGlobalEnabled());
        assertEquals(2, overview.getDevices().size());

        assertEquals(
                "device:a",
                overview.getDevices().get(0).getDeviceId()
        );
        assertEquals(
                "ALLOWED_NO_ASSIGNED_USER",
                overview.getDevices().get(0).getReason()
        );

        assertEquals(
                "device:b",
                overview.getDevices().get(1).getDeviceId()
        );
        assertEquals(
                "ALLOWED",
                overview.getDevices().get(1).getReason()
        );

        Mockito.verify(
                aggregateDataSource,
                Mockito.times(1)
        ).getWireGuardServerState();

        Mockito.verify(
                aggregateDeviceService
        ).getDevices(true);
    }

    @Test
    public void deviceRevocationPersistsThenReconcilesRuntime() {
        Device device = device("device:001122334455", true);

        Mockito.when(
                deviceService.getDeviceById(device.getId())
        ).thenReturn(device);

        Mockito.when(
                dataSource.getWireGuardServerState()
        ).thenReturn(true);

        assertTrue(
                service.setDeviceAuthorization(
                        device.getId(),
                        false
                )
        );

        assertFalse(device.isWireGuardEnabled());
        Mockito.verify(deviceService).updateDevice(device);
        Mockito.verify(peerService).reconcilePeers();
        Mockito.verify(networkStateMachine).updateFirewall();
    }

    @Test
    public void globalDisabledSkipsRuntimeApply() {
        Device device = device("device:001122334455", false);

        Mockito.when(
                deviceService.getDeviceById(device.getId())
        ).thenReturn(device);

        Mockito.when(
                dataSource.getWireGuardServerState()
        ).thenReturn(false);

        assertTrue(
                service.setDeviceAuthorization(
                        device.getId(),
                        true
                )
        );

        assertTrue(device.isWireGuardEnabled());
        Mockito.verify(deviceService).updateDevice(device);
        Mockito.verify(peerService, Mockito.never()).reconcilePeers();
        Mockito.verify(
                networkStateMachine,
                Mockito.never()
        ).updateFirewall();
    }

    @Test
    public void failedRuntimeReconcileStopsServerFailClosed() {
        Device device = device("device:001122334455", true);

        Mockito.when(
                deviceService.getDeviceById(device.getId())
        ).thenReturn(device);

        Mockito.when(
                dataSource.getWireGuardServerState()
        ).thenReturn(true);

        Mockito.doThrow(
                new IllegalStateException("apply failed")
        ).when(peerService).reconcilePeers();

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> service.setDeviceAuthorization(
                                device.getId(),
                                false
                        )
                );

        assertFalse(device.isWireGuardEnabled());
        assertTrue(
                exception.getMessage().contains("fail-closed")
        );
        Mockito.verify(controlService).stop();
    }

    @Test
    public void userAuthorizationUsesDedicatedUserServiceSetter() {
        UserModule user = user(42, false, false);

        Mockito.when(
                userService.getUserById(42)
        ).thenReturn(user);

        Mockito.when(
                dataSource.getWireGuardServerState()
        ).thenReturn(true);

        assertTrue(service.setUserAuthorization(42, true));

        Mockito.verify(userService)
                .setWireGuardEnabled(42, true);
        Mockito.verify(peerService).reconcilePeers();
        Mockito.verify(networkStateMachine).updateFirewall();
    }

    @Test
    public void systemUserAuthorizationIsRejected() {
        UserModule user = user(7, true, false);

        Mockito.when(
                userService.getUserById(7)
        ).thenReturn(user);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.setUserAuthorization(7, true)
        );

        Mockito.verify(
                userService,
                Mockito.never()
        ).setWireGuardEnabled(Mockito.anyInt(), Mockito.anyBoolean());
    }

    private Device device(String id, boolean enabled) {
        Device device = new Device();
        device.setId(id);
        device.setWireGuardEnabled(enabled);
        return device;
    }

    private UserModule user(
            int id,
            boolean system,
            boolean wireGuardEnabled) {

        UserModule user = new UserModule(
                id,
                1,
                "User",
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
    @Test
    public void nonPolicyDeviceChangeAfterInitialObservationDoesNotReconcileRuntime() {
        Device device =
                device(
                        "device:001122334455",
                        false
                );

        Mockito.when(
                dataSource.getWireGuardServerState()
        ).thenReturn(false);

        deviceChangeListener.onChange(device);

        Mockito.clearInvocations(
                peerService,
                networkStateMachine
        );

        device.setVendor(
                "ARP/IP-like non-policy update"
        );

        Mockito.when(
                dataSource.getWireGuardServerState()
        ).thenReturn(true);

        deviceChangeListener.onChange(device);

        Mockito.verify(
                peerService,
                Mockito.never()
        ).reconcilePeers();

        Mockito.verify(
                networkStateMachine,
                Mockito.never()
        ).updateFirewall();
    }

    @Test
    public void assignedUserChangeReconcilesExactlyOnce() {
        Device device =
                device(
                        "device:001122334455",
                        false
                );

        Mockito.when(
                dataSource.getWireGuardServerState()
        ).thenReturn(false);

        deviceChangeListener.onChange(device);

        Mockito.clearInvocations(
                peerService,
                networkStateMachine
        );

        device.setAssignedUser(
                device.getAssignedUser() + 1
        );

        Mockito.when(
                dataSource.getWireGuardServerState()
        ).thenReturn(true);

        deviceChangeListener.onChange(device);

        Mockito.verify(
                peerService,
                Mockito.times(1)
        ).reconcilePeers();

        Mockito.verify(
                networkStateMachine,
                Mockito.times(1)
        ).updateFirewall();
    }


}
