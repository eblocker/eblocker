package org.eblocker.server.http.service;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.common.data.wireguard.WireGuardTunnelMode;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WireGuardRoutingPersistenceServiceTest {

    private DataSource dataSource;
    private WireGuardPeerSyncService peerSyncService;
    private NetworkStateMachine networkStateMachine;
    private WireGuardPeerService service;
    private WireGuardPeer peer;

    @Before
    public void setUp() {
        dataSource =
                Mockito.mock(DataSource.class);

        peerSyncService =
                Mockito.mock(
                        WireGuardPeerSyncService.class
                );

        networkStateMachine =
                Mockito.mock(
                        NetworkStateMachine.class
                );

        service = new WireGuardPeerService(
                dataSource,
                peerSyncService,
                networkStateMachine,
                "wg",
                new WireGuardCustomRouteValidator()
        );

        peer = new WireGuardPeer();
        peer.setId(7);
        peer.setAllowedIp(
                "10.13.13.7/32"
        );

        Mockito.when(
                dataSource.get(
                        WireGuardPeer.class,
                        7
                )
        ).thenReturn(peer);
    }

    @Test
    public void customRoutesAreCanonicalizedAndPersisted() {
        Mockito.when(
                dataSource.save(peer, 7)
        ).thenReturn(peer);

        assertTrue(
                service.setRouting(
                        7,
                        WireGuardTunnelMode.CUSTOM,
                        Arrays.asList(
                                "192.168.50.77/24",
                                "10.23.45.67/8",
                                "192.168.50.0/24"
                        )
                )
        );

        assertEquals(
                WireGuardTunnelMode.CUSTOM,
                peer.getTunnelMode()
        );

        assertEquals(
                Arrays.asList(
                        "192.168.50.0/24",
                        "10.0.0.0/8"
                ),
                peer.getCustomAllowedIps()
        );

        Mockito.verify(dataSource)
                .save(peer, 7);

        assertNoRuntimeReconfiguration();
    }

    @Test
    public void fullTunnelClearsStaleCustomRoutes() {
        peer.setTunnelMode(
                WireGuardTunnelMode.CUSTOM
        );

        peer.setCustomAllowedIps(
                Collections.singletonList(
                        "192.168.1.0/24"
                )
        );

        Mockito.when(
                dataSource.save(peer, 7)
        ).thenReturn(peer);

        assertTrue(
                service.setRouting(
                        7,
                        WireGuardTunnelMode.FULL_TUNNEL,
                        Collections.singletonList(
                                "invalid ignored value"
                        )
                )
        );

        assertEquals(
                WireGuardTunnelMode.FULL_TUNNEL,
                peer.getTunnelMode()
        );

        assertTrue(
                peer.getCustomAllowedIps().isEmpty()
        );

        assertNoRuntimeReconfiguration();
    }

    @Test
    public void lanOnlyClearsStaleCustomRoutes() {
        peer.setTunnelMode(
                WireGuardTunnelMode.CUSTOM
        );

        peer.setCustomAllowedIps(
                Collections.singletonList(
                        "10.0.0.0/8"
                )
        );

        Mockito.when(
                dataSource.save(peer, 7)
        ).thenReturn(peer);

        assertTrue(
                service.setRouting(
                        7,
                        WireGuardTunnelMode.LAN_ONLY,
                        null
                )
        );

        assertEquals(
                WireGuardTunnelMode.LAN_ONLY,
                peer.getTunnelMode()
        );

        assertTrue(
                peer.getCustomAllowedIps().isEmpty()
        );

        assertFalse(
                peer.isAllowLanAccess()
        );

        assertNoRuntimeReconfiguration();
    }

    @Test
    public void invalidCustomRoutesDoNotMutateOrPersist() {
        peer.setTunnelMode(
                WireGuardTunnelMode.LAN_ONLY
        );

        expectIllegalArgument(
                () -> service.setRouting(
                        7,
                        WireGuardTunnelMode.CUSTOM,
                        Collections.singletonList(
                                "2001:db8::/32"
                        )
                )
        );

        assertEquals(
                WireGuardTunnelMode.LAN_ONLY,
                peer.getTunnelMode()
        );

        assertTrue(
                peer.getCustomAllowedIps().isEmpty()
        );

        Mockito.verify(
                dataSource,
                Mockito.never()
        ).save(
                Mockito.any(WireGuardPeer.class),
                Mockito.anyInt()
        );

        assertNoRuntimeReconfiguration();
    }

    @Test
    public void missingPeerReturnsFalse() {
        Mockito.when(
                dataSource.get(
                        WireGuardPeer.class,
                        8
                )
        ).thenReturn(null);

        assertFalse(
                service.setRouting(
                        8,
                        WireGuardTunnelMode.FULL_TUNNEL,
                        null
                )
        );

        Mockito.verify(
                dataSource,
                Mockito.never()
        ).save(
                Mockito.any(WireGuardPeer.class),
                Mockito.anyInt()
        );

        assertNoRuntimeReconfiguration();
    }

    @Test
    public void idempotentUpdateDoesNotWriteOrTouchRuntime() {
        assertTrue(
                service.setRouting(
                        7,
                        WireGuardTunnelMode.FULL_TUNNEL,
                        Collections.singletonList(
                                "stale ignored"
                        )
                )
        );

        Mockito.verify(
                dataSource,
                Mockito.never()
        ).save(
                Mockito.any(WireGuardPeer.class),
                Mockito.anyInt()
        );

        assertNoRuntimeReconfiguration();
    }

    @Test
    public void persistenceExceptionRestoresPreviousState() {
        peer.setTunnelMode(
                WireGuardTunnelMode.LAN_ONLY
        );

        Mockito.when(
                dataSource.save(peer, 7)
        ).thenThrow(
                new IllegalStateException(
                        "storage failed"
                )
        );

        expectIllegalState(
                () -> service.setRouting(
                        7,
                        WireGuardTunnelMode.CUSTOM,
                        Collections.singletonList(
                                "192.168.77.99/24"
                        )
                )
        );

        assertEquals(
                WireGuardTunnelMode.LAN_ONLY,
                peer.getTunnelMode()
        );

        assertTrue(
                peer.getCustomAllowedIps().isEmpty()
        );

        assertNoRuntimeReconfiguration();
    }

    @Test
    public void nullPersistenceResultRestoresPreviousState() {
        peer.setTunnelMode(
                WireGuardTunnelMode.CUSTOM
        );

        peer.setCustomAllowedIps(
                Collections.singletonList(
                        "10.0.0.0/8"
                )
        );

        Mockito.when(
                dataSource.save(peer, 7)
        ).thenReturn(null);

        expectIllegalState(
                () -> service.setRouting(
                        7,
                        WireGuardTunnelMode.LAN_ONLY,
                        null
                )
        );

        assertEquals(
                WireGuardTunnelMode.CUSTOM,
                peer.getTunnelMode()
        );

        assertEquals(
                Collections.singletonList(
                        "10.0.0.0/8"
                ),
                peer.getCustomAllowedIps()
        );

        assertNoRuntimeReconfiguration();
    }

    private void assertNoRuntimeReconfiguration() {
        Mockito.verifyNoInteractions(
                peerSyncService
        );

        Mockito.verifyNoInteractions(
                networkStateMachine
        );
    }

    private void expectIllegalArgument(
            Runnable action) {

        try {
            action.run();
            fail(
                    "Expected IllegalArgumentException"
            );
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private void expectIllegalState(
            Runnable action) {

        try {
            action.run();
            fail(
                    "Expected IllegalStateException"
            );
        } catch (IllegalStateException expected) {
            // expected
        }
    }
}
