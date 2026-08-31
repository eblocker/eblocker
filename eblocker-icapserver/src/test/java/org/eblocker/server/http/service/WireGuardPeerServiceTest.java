package org.eblocker.server.http.service;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WireGuardPeerServiceTest {

    private DataSource dataSource;
    private WireGuardPeerSyncService peerSyncService;
    private NetworkStateMachine networkStateMachine;
    private Path fakeWg;

    @Before
    public void setUp() throws Exception {
        dataSource = Mockito.mock(DataSource.class);

        peerSyncService =
                Mockito.mock(WireGuardPeerSyncService.class);

        networkStateMachine =
                Mockito.mock(NetworkStateMachine.class);

        fakeWg = createFakeWg(false);
    }

    @After
    public void tearDown() throws Exception {
        Files.deleteIfExists(fakeWg);
    }

    @Test
    public void createPeerUsesDonorKeyFlowAndPersistsIntId()
            throws Exception {

        Mockito.when(
                dataSource.getAll(WireGuardPeer.class)
        ).thenReturn(Collections.emptyList());

        Mockito.when(
                dataSource.nextId(WireGuardPeer.class)
        ).thenReturn(7);

        Mockito.when(
                dataSource.save(
                        Mockito.any(WireGuardPeer.class),
                        Mockito.eq(7)
                )
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        WireGuardPeer peer =
                service(fakeWg).createPeer(" Phone ");

        assertEquals(7, peer.getId());
        assertEquals("Phone", peer.getName());
        assertEquals(
                "10.13.13.2/32",
                peer.getAllowedIp()
        );
        assertEquals("private-key", peer.getPrivateKey());
        assertEquals("public-key", peer.getPublicKey());
        assertEquals(
                "preshared-key",
                peer.getPresharedKey()
        );
        assertFalse(peer.isAllowLanAccess());

        Mockito.verify(dataSource).save(peer, 7);

        ArgumentCaptor<List> captor =
                ArgumentCaptor.forClass(List.class);

        Mockito.verify(peerSyncService)
                .synchronize(captor.capture());

        List<?> synchronizedPeers =
                captor.getValue();

        assertEquals(1, synchronizedPeers.size());
        assertEquals(peer, synchronizedPeers.get(0));
    }

    @Test
    public void createPeerAllocatesFirstFreeAddress()
            throws Exception {

        WireGuardPeer first =
                peer(1, "10.13.13.2/32");

        WireGuardPeer gapAfter =
                peer(2, "10.13.13.4/32");

        Mockito.when(
                dataSource.getAll(WireGuardPeer.class)
        ).thenReturn(
                Arrays.asList(first, gapAfter)
        );

        Mockito.when(
                dataSource.nextId(WireGuardPeer.class)
        ).thenReturn(3);

        Mockito.when(
                dataSource.save(
                        Mockito.any(WireGuardPeer.class),
                        Mockito.eq(3)
                )
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        WireGuardPeer created =
                service(fakeWg).createPeer(null);

        assertEquals("Peer", created.getName());
        assertEquals(
                "10.13.13.3/32",
                created.getAllowedIp()
        );
    }

    @Test
    public void failedCreateSyncAttemptsPersistentAndRuntimeRollback()
            throws Exception {

        WireGuardPeer existing =
                peer(1, "10.13.13.2/32");

        Mockito.when(
                dataSource.getAll(WireGuardPeer.class)
        ).thenReturn(
                Collections.singletonList(existing)
        );

        Mockito.when(
                dataSource.nextId(WireGuardPeer.class)
        ).thenReturn(2);

        Mockito.when(
                dataSource.save(
                        Mockito.any(WireGuardPeer.class),
                        Mockito.eq(2)
                )
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        Mockito.doThrow(
                new IllegalStateException("sync failed")
        ).doNothing()
                .when(peerSyncService)
                .synchronize(Mockito.anyList());

        try {
            service(fakeWg).createPeer("Phone");
            fail("Expected IllegalStateException");

        } catch (IllegalStateException e) {
            assertTrue(
                    e.getMessage().contains(
                            "rollback was attempted"
                    )
            );
        }

        Mockito.verify(dataSource).delete(
                WireGuardPeer.class,
                2
        );

        Mockito.verify(
                peerSyncService,
                Mockito.times(2)
        ).synchronize(Mockito.anyList());
    }

    @Test
    public void failedCreateRollbackReportsPeerStillPersisted()
            throws Exception {

        Mockito.when(
                dataSource.getAll(WireGuardPeer.class)
        ).thenReturn(Collections.emptyList());

        Mockito.when(
                dataSource.nextId(WireGuardPeer.class)
        ).thenReturn(2);

        Mockito.when(
                dataSource.save(
                        Mockito.any(WireGuardPeer.class),
                        Mockito.eq(2)
                )
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        WireGuardPeer stillPersisted =
                peer(2, "10.13.13.2/32");

        Mockito.when(
                dataSource.get(WireGuardPeer.class, 2)
        ).thenReturn(stillPersisted);

        Mockito.doThrow(
                new IllegalStateException("sync failed")
        ).doNothing()
                .when(peerSyncService)
                .synchronize(Mockito.anyList());

        try {
            service(fakeWg).createPeer("Phone");
            fail("Expected IllegalStateException");

        } catch (IllegalStateException e) {
            assertTrue(
                    e.getMessage().contains(
                            "rollback was attempted"
                    )
            );

            Throwable cause = e.getCause();
            assertNotNull(cause);

            Throwable[] suppressed =
                    cause.getSuppressed();

            assertEquals(1, suppressed.length);

            assertTrue(
                    suppressed[0].getMessage().contains(
                            "remained in persistent storage"
                    )
            );
        }

        Mockito.verify(dataSource).delete(
                WireGuardPeer.class,
                2
        );

        Mockito.verify(dataSource).get(
                WireGuardPeer.class,
                2
        );

        Mockito.verify(
                peerSyncService,
                Mockito.times(2)
        ).synchronize(Mockito.anyList());
    }

    @Test
    public void deletePeerRevokesRuntimeBeforePersistentDelete()
            throws Exception {

        WireGuardPeer existing =
                peer(7, "10.13.13.2/32");

        Mockito.when(
                dataSource.get(WireGuardPeer.class, 7)
        ).thenReturn(existing, null);

        Mockito.when(
                dataSource.getAll(WireGuardPeer.class)
        ).thenReturn(
                Collections.singletonList(existing)
        );

        assertTrue(service(fakeWg).deletePeer(7));

        org.mockito.InOrder order =
                Mockito.inOrder(
                        peerSyncService,
                        dataSource,
                        networkStateMachine
                );

        order.verify(peerSyncService)
                .synchronize(Mockito.anyList());

        order.verify(dataSource)
                .delete(WireGuardPeer.class, 7);

        order.verify(dataSource)
                .get(WireGuardPeer.class, 7);

        order.verify(networkStateMachine)
                .updateFirewall();
    }

    @Test
    public void failedPersistentDeleteRestoresRuntimePeerSet()
            throws Exception {

        WireGuardPeer existing =
                peer(7, "10.13.13.2/32");

        Mockito.when(
                dataSource.get(WireGuardPeer.class, 7)
        ).thenReturn(existing);

        Mockito.when(
                dataSource.getAll(WireGuardPeer.class)
        ).thenReturn(
                Collections.singletonList(existing)
        );

        Mockito.doThrow(
                new IllegalStateException(
                        "redis delete failed"
                )
        ).when(dataSource)
                .delete(WireGuardPeer.class, 7);

        try {
            service(fakeWg).deletePeer(7);
            fail("Expected IllegalStateException");

        } catch (IllegalStateException e) {
            assertTrue(
                    e.getMessage().contains(
                            "runtime rollback was attempted"
                    )
            );
        }

        Mockito.verify(
                peerSyncService,
                Mockito.times(2)
        ).synchronize(Mockito.anyList());
    }

    @Test
    public void getPeerByDeviceIdFindsAssociatedPeer() {
        WireGuardPeer unbound =
                peer(2, "10.13.13.2/32");
        WireGuardPeer bound =
                peer(3, "10.13.13.3/32");

        bound.setDeviceId("device:001122334455");

        Mockito.when(
                dataSource.getAll(WireGuardPeer.class)
        ).thenReturn(java.util.Arrays.asList(unbound, bound));

        WireGuardPeer result =
                service(fakeWg)
                        .getPeerByDeviceId(
                                "device:001122334455"
                        );

        assertNotNull(result);
        assertEquals(3, result.getId());
        assertEquals(
                "device:001122334455",
                result.getDeviceId()
        );
    }

    @Test
    public void getPeerByDeviceIdReturnsNullForUnboundDevice() {
        WireGuardPeer unbound =
                peer(2, "10.13.13.2/32");

        Mockito.when(
                dataSource.getAll(WireGuardPeer.class)
        ).thenReturn(
                java.util.Collections.singletonList(unbound)
        );

        assertNull(
                service(fakeWg)
                        .getPeerByDeviceId(
                                "device:aabbccddeeff"
                        )
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void createPeerForDeviceRejectsBlankDeviceId() {
        service(fakeWg).createPeerForDevice(
                "Phone",
                "   "
        );
    }

    @Test
    public void createPeerForDevicePersistsAssociationOnInitialSave()
            throws Exception {

        Mockito.when(
                dataSource.getAll(WireGuardPeer.class)
        ).thenReturn(java.util.Collections.emptyList());

        Mockito.when(
                dataSource.nextId(WireGuardPeer.class)
        ).thenReturn(7);

        Mockito.when(
                dataSource.save(
                        Mockito.any(WireGuardPeer.class),
                        Mockito.eq(7)
                )
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        WireGuardPeer created =
                service(fakeWg).createPeerForDevice(
                        "Phone",
                        "device:001122334455"
                );

        assertEquals(
                "device:001122334455",
                created.getDeviceId()
        );

        Mockito.verify(
                dataSource,
                Mockito.times(1)
        ).save(
                Mockito.argThat((WireGuardPeer peer) ->
                        "device:001122334455".equals(
                                peer.getDeviceId()
                        )
                ),
                Mockito.eq(7)
        );

        Mockito.verify(peerSyncService)
                .synchronize(Mockito.argThat(peers ->
                        peers != null
                                && peers.size() == 1
                                && "device:001122334455".equals(
                                        peers.get(0).getDeviceId()
                                )
                ));
    }

    @Test
    public void ordinaryCreatePeerRemainsUnbound()
            throws Exception {

        Mockito.when(
                dataSource.getAll(WireGuardPeer.class)
        ).thenReturn(java.util.Collections.emptyList());

        Mockito.when(
                dataSource.nextId(WireGuardPeer.class)
        ).thenReturn(7);

        Mockito.when(
                dataSource.save(
                        Mockito.any(WireGuardPeer.class),
                        Mockito.eq(7)
                )
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        WireGuardPeer created =
                service(fakeWg).createPeer("Phone");

        assertNull(created.getDeviceId());
    }

    @Test(expected = IllegalStateException.class)
    public void createPeerForDeviceRejectsDuplicateDeviceBinding() {
        WireGuardPeer existing =
                peer(2, "10.13.13.2/32");
        existing.setDeviceId("device:001122334455");

        Mockito.when(
                dataSource.getAll(WireGuardPeer.class)
        ).thenReturn(
                java.util.Collections.singletonList(existing)
        );

        service(fakeWg).createPeerForDevice(
                "Second",
                "device:001122334455"
        );
    }

    @Test
    public void setLanAccessPersistsPolicyThenRefreshesFirewall()
            throws Exception {

        WireGuardPeer existing =
                peer(7, "10.13.13.2/32");

        Mockito.when(
                dataSource.get(WireGuardPeer.class, 7)
        ).thenReturn(existing);

        Mockito.when(
                dataSource.save(existing, 7)
        ).thenReturn(existing);

        assertTrue(
                service(fakeWg).setLanAccess(7, true)
        );

        assertTrue(existing.isAllowLanAccess());

        org.mockito.InOrder order =
                Mockito.inOrder(
                        dataSource,
                        networkStateMachine
                );

        order.verify(dataSource)
                .save(existing, 7);

        order.verify(networkStateMachine)
                .updateFirewall();

        Mockito.verifyNoInteractions(peerSyncService);
    }

    @Test
    public void setLanAccessReconcilesFirewallWhenPolicyAlreadyPersisted()
            throws Exception {

        WireGuardPeer existing =
                peer(7, "10.13.13.2/32");

        existing.setAllowLanAccess(true);

        Mockito.when(
                dataSource.get(WireGuardPeer.class, 7)
        ).thenReturn(existing);

        assertTrue(
                service(fakeWg).setLanAccess(7, true)
        );

        // The desired value is already persistent, so no duplicate
        // write is needed. Runtime firewall state is still reconciled.
        Mockito.verify(
                dataSource,
                Mockito.never()
        ).save(
                Mockito.any(WireGuardPeer.class),
                Mockito.anyInt()
        );

        Mockito.verify(networkStateMachine)
                .updateFirewall();

        Mockito.verifyNoInteractions(peerSyncService);
    }

    @Test
    public void setLanAccessReturnsFalseForMissingPeer()
            throws Exception {

        Mockito.when(
                dataSource.get(WireGuardPeer.class, 404)
        ).thenReturn(null);

        assertFalse(
                service(fakeWg).setLanAccess(404, true)
        );

        Mockito.verify(
                dataSource,
                Mockito.never()
        ).save(
                Mockito.any(WireGuardPeer.class),
                Mockito.anyInt()
        );

        Mockito.verifyNoInteractions(
                peerSyncService,
                networkStateMachine
        );
    }

    @Test
    public void reconcileUsesDataSourceAsSourceOfTruth()
            throws Exception {

        List<WireGuardPeer> peers =
                Arrays.asList(
                        peer(1, "10.13.13.2/32"),
                        peer(2, "10.13.13.3/32")
                );

        Mockito.when(
                dataSource.getAll(WireGuardPeer.class)
        ).thenReturn(peers);

        service(fakeWg).reconcilePeers();

        Mockito.verify(peerSyncService)
                .synchronize(peers);
    }

    @Test
    public void keyGenerationFailureDoesNotPersistPeer()
            throws Exception {

        Path failingWg = createFakeWg(true);

        try {
            Mockito.when(
                    dataSource.getAll(WireGuardPeer.class)
            ).thenReturn(Collections.emptyList());

            try {
                service(failingWg).createPeer("Phone");
                fail("Expected IllegalStateException");

            } catch (IllegalStateException e) {
                assertTrue(
                        e.getMessage().contains(
                                "exit code 9"
                        )
                );
            }

            Mockito.verify(
                    dataSource,
                    Mockito.never()
            ).save(
                    Mockito.any(WireGuardPeer.class),
                    Mockito.anyInt()
            );

            Mockito.verifyNoInteractions(
                    peerSyncService
            );

        } finally {
            Files.deleteIfExists(failingWg);
        }
    }

    private WireGuardPeerService service(Path wg) {
        return new WireGuardPeerService(
                dataSource,
                peerSyncService,
                networkStateMachine,
                wg.toString()
        );
    }

    private WireGuardPeer peer(
            int id,
            String allowedIp) {

        WireGuardPeer peer =
                new WireGuardPeer();

        peer.setId(id);
        peer.setName("Peer " + id);
        peer.setAllowedIp(allowedIp);
        peer.setPrivateKey("private-" + id);
        peer.setPublicKey("public-" + id);
        peer.setPresharedKey("psk-" + id);

        return peer;
    }

    private Path createFakeWg(boolean fail)
            throws IOException {

        Path script =
                Files.createTempFile(
                        "fake-wg-",
                        ".sh"
                );

        String body;

        if (fail) {
            body = "#!/bin/sh\n"
                    + "echo fake failure >&2\n"
                    + "exit 9\n";

        } else {
            body = "#!/bin/sh\n"
                    + "case \"$1\" in\n"
                    + "  genkey)\n"
                    + "    printf 'private-key\\n'\n"
                    + "    ;;\n"
                    + "  pubkey)\n"
                    + "    cat >/dev/null\n"
                    + "    printf 'public-key\\n'\n"
                    + "    ;;\n"
                    + "  genpsk)\n"
                    + "    printf 'preshared-key\\n'\n"
                    + "    ;;\n"
                    + "  *)\n"
                    + "    exit 2\n"
                    + "    ;;\n"
                    + "esac\n";
        }

        Files.write(
                script,
                body.getBytes(StandardCharsets.UTF_8)
        );

        try {
            Files.setPosixFilePermissions(
                    script,
                    Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE
                    )
            );

        } catch (UnsupportedOperationException e) {
            assertTrue(
                    "Could not make fake wg executable",
                    script.toFile().setExecutable(true)
            );
        }

        return script;
    }
}
