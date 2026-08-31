package org.eblocker.server.http.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.common.system.ScriptRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;

@Singleton
public class WireGuardPeerSyncService {

    private static final Path TEMP_DIRECTORY = Paths.get("/tmp");

    private final ScriptRunner scriptRunner;
    private final String wireGuardServerCommand;

    @Inject
    public WireGuardPeerSyncService(
            ScriptRunner scriptRunner,
            @Named("wireguard.server.command") String wireGuardServerCommand) {

        this.scriptRunner = scriptRunner;
        this.wireGuardServerCommand = wireGuardServerCommand;
    }

    public void synchronize(List<WireGuardPeer> peers) {
        Path peerFile = null;

        try {
            peerFile = Files.createTempFile(
                    TEMP_DIRECTORY,
                    "eblocker-wireguard-peers-",
                    ".conf"
            );

            try {
                Files.setPosixFilePermissions(
                        peerFile,
                        EnumSet.of(
                                PosixFilePermission.OWNER_READ,
                                PosixFilePermission.OWNER_WRITE
                        )
                );
            } catch (UnsupportedOperationException ignored) {
                // Production is POSIX/Linux. Keep development portability.
            }

            String content = renderPeerConfiguration(peers);

            Files.write(
                    peerFile,
                    content.getBytes(StandardCharsets.UTF_8)
            );

            int exitCode = scriptRunner.runScript(
                    wireGuardServerCommand,
                    "apply-peers",
                    peerFile.toAbsolutePath().toString()
            );

            if (exitCode != 0) {
                throw new IllegalStateException(
                        "Could not apply WireGuard peers "
                                + "(script exit code "
                                + exitCode
                                + ")."
                );
            }

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not prepare WireGuard peer configuration.",
                    e
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Interrupted while applying WireGuard peers.",
                    e
            );

        } finally {
            if (peerFile != null) {
                try {
                    Files.deleteIfExists(peerFile);
                } catch (IOException ignored) {
                    // Best effort. The privileged control script also
                    // removes the temporary peer file.
                }
            }
        }
    }

    String renderPeerConfiguration(List<WireGuardPeer> peers) {
        StringBuilder config = new StringBuilder();

        if (peers == null) {
            return "";
        }

        for (WireGuardPeer peer : peers) {
            if (!isCompletePeer(peer)) {
                continue;
            }

            config.append("[Peer]\n")
                    .append("PublicKey = ")
                    .append(peer.getPublicKey().trim())
                    .append('\n')
                    .append("PresharedKey = ")
                    .append(peer.getPresharedKey().trim())
                    .append('\n')
                    .append("AllowedIPs = ")
                    .append(peer.getAllowedIp().trim())
                    .append('\n')
                    .append('\n');
        }

        return config.toString();
    }

    private boolean isCompletePeer(WireGuardPeer peer) {
        return peer != null
                && hasText(peer.getPublicKey())
                && hasText(peer.getPresharedKey())
                && hasText(peer.getAllowedIp());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
