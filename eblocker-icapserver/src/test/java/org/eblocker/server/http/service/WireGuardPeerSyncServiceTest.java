package org.eblocker.server.http.service;

import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.common.system.ScriptRunner;
import org.junit.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WireGuardPeerSyncServiceTest {

    private static final String COMMAND =
            "wireguard-server-control";

    @Test
    public void rendersHolgerPeerConfigurationFormat() {
        WireGuardPeerSyncService service =
                service(Mockito.mock(ScriptRunner.class));

        String config = service.renderPeerConfiguration(
                Arrays.asList(
                        peer(
                                1,
                                "public-one",
                                "psk-one",
                                "10.13.13.2/32"
                        ),
                        peer(
                                2,
                                "public-two",
                                "psk-two",
                                "10.13.13.3/32"
                        )
                )
        );

        assertEquals(
                "[Peer]\n"
                        + "PublicKey = public-one\n"
                        + "PresharedKey = psk-one\n"
                        + "AllowedIPs = 10.13.13.2/32\n"
                        + "\n"
                        + "[Peer]\n"
                        + "PublicKey = public-two\n"
                        + "PresharedKey = psk-two\n"
                        + "AllowedIPs = 10.13.13.3/32\n"
                        + "\n",
                config
        );
    }

    @Test
    public void skipsIncompletePeersLikeDonorImplementation() {
        WireGuardPeerSyncService service =
                service(Mockito.mock(ScriptRunner.class));

        WireGuardPeer incomplete =
                peer(1, "public", null, "10.13.13.2/32");

        assertEquals(
                "",
                service.renderPeerConfiguration(
                        Collections.singletonList(incomplete)
                )
        );
    }

    @Test
    public void synchronizeUsesExpectedTmpPathAndPermissions()
            throws Exception {

        ScriptRunner scriptRunner =
                Mockito.mock(ScriptRunner.class);

        AtomicReference<Path> observedPath =
                new AtomicReference<>();

        Mockito.when(
                scriptRunner.runScript(
                        Mockito.eq(COMMAND),
                        Mockito.eq("apply-peers"),
                        Mockito.anyString()
                )
        ).thenAnswer(invocation -> {
            Path path = Path.of(
                    invocation.getArgument(2, String.class)
            );

            observedPath.set(path);

            assertTrue(Files.exists(path));
            assertTrue(Files.isRegularFile(path));
            assertFalse(Files.isSymbolicLink(path));

            assertTrue(
                    path.toString().startsWith(
                            "/tmp/eblocker-wireguard-peers-"
                    )
            );

            String content = Files.readString(
                    path,
                    StandardCharsets.UTF_8
            );

            assertTrue(content.contains("[Peer]"));
            assertTrue(content.contains("PublicKey = public"));
            assertTrue(content.contains("PresharedKey = psk"));
            assertTrue(
                    content.contains(
                            "AllowedIPs = 10.13.13.2/32"
                    )
            );

            try {
                Set<PosixFilePermission> permissions =
                        Files.getPosixFilePermissions(path);

                assertEquals(
                        Set.of(
                                PosixFilePermission.OWNER_READ,
                                PosixFilePermission.OWNER_WRITE
                        ),
                        permissions
                );
            } catch (UnsupportedOperationException ignored) {
                // Test environment may be non-POSIX.
            }

            return 0;
        });

        service(scriptRunner).synchronize(
                Collections.singletonList(
                        peer(
                                1,
                                "public",
                                "psk",
                                "10.13.13.2/32"
                        )
                )
        );

        Path path = observedPath.get();

        assertFalse(
                "Temporary peer file must be removed",
                Files.exists(path)
        );
    }

    @Test
    public void nonZeroApplyPeersExitCodeIsNotIgnored()
            throws Exception {

        ScriptRunner scriptRunner =
                Mockito.mock(ScriptRunner.class);

        AtomicReference<Path> observedPath =
                new AtomicReference<>();

        Mockito.when(
                scriptRunner.runScript(
                        Mockito.eq(COMMAND),
                        Mockito.eq("apply-peers"),
                        Mockito.anyString()
                )
        ).thenAnswer(invocation -> {
            observedPath.set(
                    Path.of(
                            invocation.getArgument(
                                    2,
                                    String.class
                            )
                    )
            );
            return 7;
        });

        try {
            service(scriptRunner).synchronize(
                    Collections.singletonList(
                            peer(
                                    1,
                                    "public",
                                    "psk",
                                    "10.13.13.2/32"
                            )
                    )
            );

            fail("Expected IllegalStateException");

        } catch (IllegalStateException e) {
            assertTrue(
                    e.getMessage().contains("exit code 7")
            );
        }

        assertFalse(Files.exists(observedPath.get()));
    }

    @Test
    public void interruptedApplyPeersRestoresInterruptFlag()
            throws Exception {

        ScriptRunner scriptRunner =
                Mockito.mock(ScriptRunner.class);

        Mockito.when(
                scriptRunner.runScript(
                        Mockito.eq(COMMAND),
                        Mockito.eq("apply-peers"),
                        Mockito.anyString()
                )
        ).thenThrow(new InterruptedException());

        try {
            service(scriptRunner).synchronize(
                    Collections.emptyList()
            );

            fail("Expected IllegalStateException");

        } catch (IllegalStateException e) {
            assertTrue(
                    e.getMessage().contains("Interrupted")
            );
            assertTrue(Thread.currentThread().isInterrupted());

        } finally {
            Thread.interrupted();
        }
    }

    private WireGuardPeerSyncService service(
            ScriptRunner scriptRunner) {

        return new WireGuardPeerSyncService(
                scriptRunner,
                COMMAND
        );
    }

    private WireGuardPeer peer(
            int id,
            String publicKey,
            String presharedKey,
            String allowedIp) {

        WireGuardPeer peer = new WireGuardPeer();

        peer.setId(id);
        peer.setName("Peer " + id);
        peer.setPublicKey(publicKey);
        peer.setPresharedKey(presharedKey);
        peer.setAllowedIp(allowedIp);

        return peer;
    }
}
