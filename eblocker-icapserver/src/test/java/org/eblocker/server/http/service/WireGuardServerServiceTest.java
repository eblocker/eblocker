package org.eblocker.server.http.service;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WireGuardServerServiceTest {

    private DataSource dataSource;
    private WireGuardPeerService peerService;
    private WireGuardServerControlService controlService;
    private NetworkStateMachine networkStateMachine;
    private ScheduledExecutorService executorService;
    private WireGuardServerService service;

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        peerService = Mockito.mock(WireGuardPeerService.class);
        controlService =
                Mockito.mock(WireGuardServerControlService.class);
        networkStateMachine =
                Mockito.mock(NetworkStateMachine.class);
        executorService =
                Mockito.mock(ScheduledExecutorService.class);

        service = new WireGuardServerService(
                dataSource,
                peerService,
                controlService,
                networkStateMachine,
                executorService
        );
    }

    @Test
    public void enabledDefaultsAreReadFromDataSource() {
        Mockito.when(
                dataSource.getWireGuardServerState()
        ).thenReturn(false, true);

        assertFalse(service.isEnabled());
        assertTrue(service.isEnabled());
    }

    @Test
    public void initDoesNothingWhenDisabled() {
        Mockito.when(
                dataSource.getWireGuardServerState()
        ).thenReturn(false);

        service.init();

        Mockito.verifyNoInteractions(
                peerService,
                controlService,
                networkStateMachine,
                executorService
        );
    }

    @Test
    public void initSchedulesRestoreWhenEnabled() {
        Mockito.when(
                dataSource.getWireGuardServerState()
        ).thenReturn(true);

        service.init();

        Mockito.verify(executorService)
                .execute(Mockito.any(Runnable.class));
    }

    @Test
    public void startupRestoreReconcilesBeforeStart() {
        Mockito.when(
                dataSource.getWireGuardServerState()
        ).thenReturn(true);

        Mockito.doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(executorService)
                .execute(Mockito.any(Runnable.class));

        service.init();

        InOrder order = Mockito.inOrder(
                peerService,
                controlService
        );

        order.verify(peerService).reconcilePeers();
        order.verify(controlService).start();

        Mockito.verify(
                dataSource,
                Mockito.never()
        ).setWireGuardServerState(Mockito.anyBoolean());

        Mockito.verifyNoInteractions(networkStateMachine);
    }

    @Test
    public void enableReconcilesStartsThenPersists() {
        service.enable();

        InOrder order = Mockito.inOrder(
                peerService,
                controlService,
                dataSource,
                networkStateMachine
        );

        order.verify(peerService).reconcilePeers();
        order.verify(controlService).start();
        order.verify(dataSource)
                .setWireGuardServerState(true);
        order.verify(networkStateMachine)
                .updateFirewall();
    }

    @Test
    public void failedEnableReconcileDoesNotStartOrPersist() {
        Mockito.doThrow(
                new IllegalStateException("sync failed")
        ).when(peerService)
                .reconcilePeers();

        try {
            service.enable();
            fail("Expected IllegalStateException");

        } catch (IllegalStateException expected) {
            // Expected.
        }

        Mockito.verifyNoInteractions(
                controlService,
                networkStateMachine
        );

        Mockito.verify(
                dataSource,
                Mockito.never()
        ).setWireGuardServerState(Mockito.anyBoolean());
    }

    @Test
    public void failedEnablePersistenceStopsRuntimeAgain() {
        Mockito.doThrow(
                new IllegalStateException("redis failed")
        ).when(dataSource)
                .setWireGuardServerState(true);

        try {
            service.enable();
            fail("Expected IllegalStateException");

        } catch (IllegalStateException e) {
            assertTrue(
                    e.getMessage().contains(
                            "runtime rollback was attempted"
                    )
            );
        }

        InOrder order = Mockito.inOrder(
                peerService,
                controlService,
                dataSource
        );

        order.verify(peerService).reconcilePeers();
        order.verify(controlService).start();
        order.verify(dataSource)
                .setWireGuardServerState(true);
        order.verify(controlService).stop();

        Mockito.verifyNoInteractions(networkStateMachine);
    }

    @Test
    public void disableStopsThenPersists() {
        service.disable();

        InOrder order = Mockito.inOrder(
                controlService,
                dataSource,
                networkStateMachine
        );

        order.verify(controlService).stop();
        order.verify(dataSource)
                .setWireGuardServerState(false);
        order.verify(networkStateMachine)
                .updateFirewall();
    }

    @Test
    public void failedDisablePersistenceRestoresRuntime() {
        Mockito.doThrow(
                new IllegalStateException("redis failed")
        ).when(dataSource)
                .setWireGuardServerState(false);

        try {
            service.disable();
            fail("Expected IllegalStateException");

        } catch (IllegalStateException e) {
            assertTrue(
                    e.getMessage().contains(
                            "runtime rollback was attempted"
                    )
            );
        }

        InOrder order = Mockito.inOrder(
                controlService,
                dataSource,
                peerService
        );

        order.verify(controlService).stop();
        order.verify(dataSource)
                .setWireGuardServerState(false);
        order.verify(peerService).reconcilePeers();
        order.verify(controlService).start();

        Mockito.verifyNoInteractions(networkStateMachine);
    }
}
