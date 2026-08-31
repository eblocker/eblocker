package org.eblocker.server.http.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.common.network.NetworkStateMachine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Singleton
public class WireGuardPeerService {

    private static final String WG_NET_PREFIX = "10.13.13.";
    private static final int FIRST_PEER_HOST = 2;
    private static final int LAST_PEER_HOST = 254;

    private final DataSource dataSource;
    private final WireGuardPeerSyncService peerSyncService;
    private final NetworkStateMachine networkStateMachine;
    private final String wireGuardCommand;

    @Inject
    public WireGuardPeerService(
            DataSource dataSource,
            WireGuardPeerSyncService peerSyncService,
            NetworkStateMachine networkStateMachine,
            @Named("wireguard.command") String wireGuardCommand) {

        this.dataSource = dataSource;
        this.peerSyncService = peerSyncService;
        this.networkStateMachine = networkStateMachine;
        this.wireGuardCommand = wireGuardCommand;
    }

    /**
     * Creates a peer associated with exactly one eBlocker device.
     *
     * Existing peers created through the admin API remain unbound
     * (deviceId == null). A second peer for the same device is rejected
     * so that dashboard lookup remains deterministic.
     */
    public synchronized WireGuardPeer createPeerForDevice(
            String name,
            String deviceId) {

        String normalizedDeviceId =
                deviceId == null ? null : deviceId.trim();

        if (normalizedDeviceId == null
                || normalizedDeviceId.isEmpty()) {
            throw new IllegalArgumentException(
                    "WireGuard device id is required."
            );
        }

        if (getPeerByDeviceId(normalizedDeviceId) != null) {
            throw new IllegalStateException(
                    "WireGuard peer already exists for device."
            );
        }

        return createPeerInternal(
                name,
                normalizedDeviceId
        );
    }

    public synchronized WireGuardPeer createPeer(String name) {
        return createPeerInternal(name, null);
    }

    private WireGuardPeer createPeerInternal(
            String name,
            String deviceId) {

        List<WireGuardPeer> existingPeers =
                new ArrayList<>(
                        dataSource.getAll(WireGuardPeer.class)
                );

        String allowedIp = allocateNextIp(existingPeers);

        String privateKey = runWg("genkey");
        String publicKey =
                runWgWithStdin("pubkey", privateKey);
        String presharedKey = runWg("genpsk");

        int id = dataSource.nextId(WireGuardPeer.class);

        WireGuardPeer peer = new WireGuardPeer();
        peer.setId(id);
        peer.setName(normalizeName(name));
        peer.setAllowedIp(allowedIp);
        peer.setDeviceId(deviceId);
        peer.setAllowLanAccess(false);
        peer.setPrivateKey(privateKey);
        peer.setPublicKey(publicKey);
        peer.setPresharedKey(presharedKey);

        WireGuardPeer saved =
                dataSource.save(peer, peer.getId());

        if (saved == null) {
            throw new IllegalStateException(
                    "Could not persist new WireGuard peer."
            );
        }

        List<WireGuardPeer> desiredPeers =
                new ArrayList<>(existingPeers);

        desiredPeers.add(peer);

        try {
            peerSyncService.synchronize(desiredPeers);

        } catch (RuntimeException syncException) {
            rollbackFailedCreate(
                    peer,
                    existingPeers,
                    syncException
            );

            throw new IllegalStateException(
                    "Could not activate new WireGuard peer; "
                            + "rollback was attempted.",
                    syncException
            );
        }

        return peer;
    }

    public synchronized boolean deletePeer(int id) {
        WireGuardPeer existing =
                dataSource.get(WireGuardPeer.class, id);

        if (existing == null) {
            return false;
        }

        List<WireGuardPeer> existingPeers =
                new ArrayList<>(
                        dataSource.getAll(WireGuardPeer.class)
                );

        List<WireGuardPeer> desiredPeers =
                new ArrayList<>();

        for (WireGuardPeer peer : existingPeers) {
            if (peer.getId() != id) {
                desiredPeers.add(peer);
            }
        }

        // Revoke runtime access before deleting the source-of-truth entry.
        peerSyncService.synchronize(desiredPeers);

        try {
            dataSource.delete(WireGuardPeer.class, id);

            WireGuardPeer stillPersisted =
                    dataSource.get(WireGuardPeer.class, id);

            if (stillPersisted != null) {
                throw new IllegalStateException(
                        "WireGuard peer remained in persistent storage."
                );
            }

        } catch (RuntimeException persistenceException) {
            rollbackRuntimePeers(
                    existingPeers,
                    persistenceException
            );

            throw new IllegalStateException(
                    "Could not remove WireGuard peer from "
                            + "persistent storage; runtime rollback "
                            + "was attempted.",
                    persistenceException
            );
        }

        refreshFirewallAfterPersistentChange(
                "WireGuard peer deletion"
        );

        return true;
    }

    public synchronized void reconcilePeers() {
        peerSyncService.synchronize(
                dataSource.getAll(WireGuardPeer.class)
        );
    }

    public WireGuardPeer getPeer(int id) {
        return dataSource.get(WireGuardPeer.class, id);
    }

    public WireGuardPeer getPeerByDeviceId(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return null;
        }

        String normalizedDeviceId = deviceId.trim();

        return dataSource.getAll(WireGuardPeer.class)
                .stream()
                .filter(peer ->
                        peer != null
                                && normalizedDeviceId.equals(
                                        peer.getDeviceId()
                                )
                )
                .findFirst()
                .orElse(null);
    }

    public List<WireGuardPeer> getPeers() {
        return dataSource.getAll(WireGuardPeer.class);
    }

    public synchronized boolean setLanAccess(
            int id,
            boolean allowLanAccess) {

        WireGuardPeer peer =
                dataSource.get(WireGuardPeer.class, id);

        if (peer == null) {
            return false;
        }

        if (peer.isAllowLanAccess() == allowLanAccess) {
            // A previous request may have persisted the desired state
            // while its firewall refresh failed. Reconcile runtime on
            // idempotent retries without writing the same value again.
            refreshFirewallAfterPersistentChange(
                    "WireGuard LAN access policy"
            );

            return true;
        }

        boolean previousValue =
                peer.isAllowLanAccess();

        peer.setAllowLanAccess(allowLanAccess);

        WireGuardPeer saved;

        try {
            saved = dataSource.save(
                    peer,
                    peer.getId()
            );

        } catch (RuntimeException persistenceException) {
            peer.setAllowLanAccess(previousValue);

            throw new IllegalStateException(
                    "Could not persist WireGuard LAN access policy.",
                    persistenceException
            );
        }

        if (saved == null) {
            peer.setAllowLanAccess(previousValue);

            throw new IllegalStateException(
                    "Could not persist WireGuard LAN access policy."
            );
        }

        refreshFirewallAfterPersistentChange(
                "WireGuard LAN access policy"
        );

        return true;
    }

    private void refreshFirewallAfterPersistentChange(
            String change) {

        try {
            networkStateMachine.updateFirewall();

        } catch (RuntimeException firewallException) {
            throw new IllegalStateException(
                    change
                            + " was persisted, but firewall refresh "
                            + "failed. The persistent state remains "
                            + "the source of truth.",
                    firewallException
            );
        }
    }

    private void rollbackFailedCreate(
            WireGuardPeer peer,
            List<WireGuardPeer> previousPeers,
            RuntimeException originalException) {

        try {
            dataSource.delete(
                    WireGuardPeer.class,
                    peer.getId()
            );

            WireGuardPeer stillPersisted =
                    dataSource.get(
                            WireGuardPeer.class,
                            peer.getId()
                    );

            if (stillPersisted != null) {
                originalException.addSuppressed(
                        new IllegalStateException(
                                "WireGuard peer remained in persistent "
                                        + "storage after create rollback."
                        )
                );
            }

        } catch (RuntimeException rollbackException) {
            originalException.addSuppressed(
                    rollbackException
            );
        }

        rollbackRuntimePeers(
                previousPeers,
                originalException
        );
    }

    private void rollbackRuntimePeers(
            List<WireGuardPeer> previousPeers,
            RuntimeException originalException) {

        try {
            peerSyncService.synchronize(previousPeers);

        } catch (RuntimeException rollbackException) {
            originalException.addSuppressed(
                    rollbackException
            );
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Peer";
        }

        return name.trim();
    }

    private String allocateNextIp(
            List<WireGuardPeer> peers) {

        Set<String> used = new HashSet<>();

        for (WireGuardPeer peer : peers) {
            if (peer != null
                    && peer.getAllowedIp() != null) {

                used.add(peer.getAllowedIp().trim());
            }
        }

        for (int host = FIRST_PEER_HOST;
             host <= LAST_PEER_HOST;
             host++) {

            String candidate =
                    WG_NET_PREFIX + host + "/32";

            if (!used.contains(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException(
                "No free IPs left in 10.13.13.0/24."
        );
    }

    private String runWg(String argument) {
        return runCommand(
                new String[]{
                        wireGuardCommand,
                        argument
                },
                null
        );
    }

    private String runWgWithStdin(
            String argument,
            String stdin) {

        return runCommand(
                new String[]{
                        wireGuardCommand,
                        argument
                },
                stdin
        );
    }

    private String runCommand(
            String[] command,
            String stdin) {

        Process process;

        try {
            ProcessBuilder builder =
                    new ProcessBuilder(command);

            builder.redirectErrorStream(true);

            process = builder.start();

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to start WireGuard command.",
                    e
            );
        }

        try {
            if (stdin != null) {
                process.getOutputStream().write(
                        stdin.getBytes(StandardCharsets.UTF_8)
                );

                process.getOutputStream().flush();
            }

            process.getOutputStream().close();

            String output =
                    readAll(process.getInputStream()).trim();

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new IllegalStateException(
                        "WireGuard command failed with exit code "
                                + exitCode
                                + "."
                );
            }

            if (output.isEmpty()) {
                throw new IllegalStateException(
                        "WireGuard command returned no output."
                );
            }

            return output;

        } catch (IOException e) {
            process.destroy();

            throw new IllegalStateException(
                    "WireGuard command I/O failed.",
                    e
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroy();

            throw new IllegalStateException(
                    "Interrupted while running WireGuard command.",
                    e
            );
        }
    }

    private String readAll(InputStream inputStream)
            throws IOException {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        byte[] buffer = new byte[4096];
        int count;

        while ((count = inputStream.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }

        return output.toString(
                StandardCharsets.UTF_8.name()
        );
    }
}
