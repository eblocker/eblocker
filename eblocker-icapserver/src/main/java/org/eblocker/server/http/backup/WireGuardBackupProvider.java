package org.eblocker.server.http.backup;

import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import org.eblocker.crypto.CryptoService;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.backup.BackupWarning;
import org.eblocker.server.common.data.wireguard.WireGuardEndpointConfig;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.common.data.wireguard.WireGuardTunnelMode;
import org.eblocker.server.http.service.WireGuardClientConfigurationService;
import org.eblocker.server.http.service.WireGuardCustomRouteValidator;
import org.eblocker.server.http.service.WireGuardServerControlService;
import org.eblocker.server.http.service.WireGuardServerService;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;

/**
 * Native WireGuard provider for Boris' configuration backup framework.
 *
 * Persistent semantic intent is backed up. Runtime telemetry and generated
 * runtime state are not.
 */
public class WireGuardBackupProvider extends BackupProvider {

    public static final String WIREGUARD_ENTRY =
            "eblocker-config/wireGuard.json";

    private static final Pattern WIREGUARD_KEY =
            Pattern.compile("^[A-Za-z0-9+/]{43}=$");

    private static final String PEER_NETWORK_PREFIX =
            "10.13.13.";

    private final DataSource dataSource;
    private final WireGuardServerService serverService;
    private final WireGuardServerControlService controlService;
    private final WireGuardClientConfigurationService clientConfigurationService;
    private final WireGuardCustomRouteValidator customRouteValidator;

    private boolean desiredEnabled;

    @AssistedInject
    public WireGuardBackupProvider(
            DataSource dataSource,
            WireGuardServerService serverService,
            WireGuardServerControlService controlService,
            WireGuardClientConfigurationService clientConfigurationService,
            WireGuardCustomRouteValidator customRouteValidator,
            @Assisted @Nullable CryptoService cryptoService) {

        super(cryptoService);
        this.dataSource = dataSource;
        this.serverService = serverService;
        this.controlService = controlService;
        this.clientConfigurationService = clientConfigurationService;
        this.customRouteValidator = customRouteValidator;
    }

    @Override
    public void exportConfiguration(
            JarOutputStream outputStream) throws IOException {

        WireGuardBackup backup = new WireGuardBackup();
        backup.setEnabled(dataSource.getWireGuardServerState());
        backup.setEndpointConfig(
                dataSource.get(WireGuardEndpointConfig.class)
        );

        if (canEncrypt()) {
            List<WireGuardPeer> peers =
                    new ArrayList<>(
                            dataSource.getAll(WireGuardPeer.class)
                    );

            peers.sort(
                    Comparator.comparingInt(
                            WireGuardPeer::getId
                    )
            );

            backup.setPeers(peers);
            backup.setServerPrivateKey(
                    controlService.exportPrivateKeyForBackup()
            );
            backup.setSecretsIncluded(true);
        } else {
            // Never serialize peer private/preshared keys without Boris'
            // password-derived JsonEncryptionModule.
            backup.setPeers(new ArrayList<>());
            backup.setServerPrivateKey(null);
            backup.setSecretsIncluded(false);
        }

        writeNextEntry(
                outputStream,
                WIREGUARD_ENTRY,
                objectMapper.writeValueAsBytes(backup)
        );
    }

    /**
     * Runs before any provider mutates configuration during an import.
     * This eliminates an active WireGuard intermediate state while users,
     * devices and peer bindings are being reconstructed.
     */
    @Override
    public void prepareImport() {
        desiredEnabled = false;
        serverService.disable();
    }

    @Override
    public void finishImport() {
        if (desiredEnabled) {
            serverService.enable();
        }
    }

    @Override
    public void importConfiguration(
            JarInputStream inputStream,
            int schemaVersion) throws IOException {

        importConfiguration(
                inputStream,
                schemaVersion,
                false
        );
    }

    @Override
    public void verifyConfiguration(
            JarInputStream inputStream,
            int schemaVersion) throws IOException {

        importConfiguration(
                inputStream,
                schemaVersion,
                true
        );
    }

    private void importConfiguration(
            JarInputStream inputStream,
            int schemaVersion,
            boolean dryRun) throws IOException {

        getNextEntry(
                inputStream,
                WIREGUARD_ENTRY
        );

        if (!canDecrypt()) {
            addWarning(
                    new BackupWarning(
                            BackupWarning.Id
                                    .NO_PASSWORD_WIREGUARD_NOT_IMPORTED
                    )
            );
            desiredEnabled = false;
            return;
        }

        WireGuardBackup backup =
                objectMapper.readValue(
                        inputStream,
                        WireGuardBackup.class
                );

        if (backup == null) {
            throw new CorruptedBackupException(
                    "Deserialized WireGuard backup is null"
            );
        }

        if (!backup.isSecretsIncluded()) {
            addWarning(
                    new BackupWarning(
                            BackupWarning.Id
                                    .NO_PASSWORD_WIREGUARD_NOT_IMPORTED
                    )
            );
            desiredEnabled = false;
            return;
        }

        WireGuardBackup normalized =
                validateAndNormalize(backup);

        if (!dryRun) {
            restoreBackup(normalized);
            desiredEnabled = normalized.isEnabled();
        }
    }

    private WireGuardBackup validateAndNormalize(
            WireGuardBackup backup) throws IOException {

        if (backup.isEnabled()
                && !isValidKey(
                        backup.getServerPrivateKey())) {

            throw corrupted(
                    "Enabled WireGuard backup has no valid server key"
            );
        }

        WireGuardBackup normalized =
                new WireGuardBackup();

        normalized.setEnabled(
                backup.isEnabled()
        );
        normalized.setSecretsIncluded(true);
        normalized.setServerPrivateKey(
                normalizeOptionalKey(
                        backup.getServerPrivateKey(),
                        "server private key"
                )
        );

        WireGuardEndpointConfig endpoint =
                backup.getEndpointConfig();

        if (endpoint != null) {
            try {
                endpoint =
                        clientConfigurationService
                                .normalizeEndpointConfig(endpoint);
            } catch (IllegalArgumentException e) {
                throw corrupted(
                        "Invalid WireGuard endpoint",
                        e
                );
            }
        }

        normalized.setEndpointConfig(endpoint);

        List<WireGuardPeer> peers =
                backup.getPeers();

        Set<Integer> ids = new HashSet<>();
        Set<String> addresses = new HashSet<>();
        Set<String> publicKeys = new HashSet<>();
        List<WireGuardPeer> normalizedPeers =
                new ArrayList<>();

        for (WireGuardPeer peer : peers) {
            normalizedPeers.add(
                    validateAndNormalizePeer(
                            peer,
                            ids,
                            addresses,
                            publicKeys
                    )
            );
        }

        normalizedPeers.sort(
                Comparator.comparingInt(
                        WireGuardPeer::getId
                )
        );

        normalized.setPeers(normalizedPeers);

        return normalized;
    }

    private WireGuardPeer validateAndNormalizePeer(
            WireGuardPeer peer,
            Set<Integer> ids,
            Set<String> addresses,
            Set<String> publicKeys) throws IOException {

        if (peer == null) {
            throw corrupted(
                    "WireGuard peer is null"
            );
        }

        if (peer.getId() <= 0
                || !ids.add(peer.getId())) {

            throw corrupted(
                    "Invalid or duplicate WireGuard peer id"
            );
        }

        String name =
                peer.getName() == null
                        ? null
                        : peer.getName().trim();

        if (name == null || name.isEmpty()) {
            throw corrupted(
                    "WireGuard peer name is missing"
            );
        }

        String privateKey =
                requireKey(
                        peer.getPrivateKey(),
                        "peer private key"
                );

        String publicKey =
                requireKey(
                        peer.getPublicKey(),
                        "peer public key"
                );

        String presharedKey =
                requireKey(
                        peer.getPresharedKey(),
                        "peer preshared key"
                );

        if (!publicKeys.add(publicKey)) {
            throw corrupted(
                    "Duplicate WireGuard peer public key"
            );
        }

        String allowedIp =
                normalizeAllowedIp(
                        peer.getAllowedIp()
                );

        if (!addresses.add(allowedIp)) {
            throw corrupted(
                    "Duplicate WireGuard peer address"
            );
        }

        WireGuardTunnelMode tunnelMode =
                peer.getTunnelMode();

        List<String> customAllowedIps =
                new ArrayList<>();

        if (tunnelMode == WireGuardTunnelMode.CUSTOM) {
            try {
                customAllowedIps =
                        customRouteValidator.normalize(
                                peer.getCustomAllowedIps()
                        );
            } catch (IllegalArgumentException e) {
                throw corrupted(
                        "Invalid WireGuard custom routes",
                        e
                );
            }
        }

        String deviceId =
                peer.getDeviceId();

        if (deviceId != null) {
            deviceId = deviceId.trim();
            if (deviceId.isEmpty()) {
                deviceId = null;
            }
        }

        WireGuardPeer result =
                new WireGuardPeer();

        result.setId(peer.getId());
        result.setName(name);
        result.setPrivateKey(privateKey);
        result.setPublicKey(publicKey);
        result.setPresharedKey(presharedKey);
        result.setAllowedIp(allowedIp);
        result.setDeviceId(deviceId);
        result.setAllowLanAccess(
                peer.isAllowLanAccess()
        );
        result.setTunnelMode(tunnelMode);
        result.setCustomAllowedIps(
                customAllowedIps
        );

        return result;
    }

    private void restoreBackup(
            WireGuardBackup backup) throws IOException {

        try {
            // Server identity first. The privileged action always leaves the
            // runtime stopped and rebuilds wg0.conf from the restored key.
            controlService.restorePrivateKeyForBackup(
                    backup.getServerPrivateKey()
            );

            dataSource.deleteAll(
                    WireGuardPeer.class
            );

            int maxId = 0;

            for (WireGuardPeer peer :
                    backup.getPeers()) {

                WireGuardPeer saved =
                        dataSource.save(
                                peer,
                                peer.getId()
                        );

                if (saved == null) {
                    throw new IllegalStateException(
                            "Could not persist restored WireGuard peer."
                    );
                }

                maxId = Math.max(
                        maxId,
                        peer.getId()
                );
            }

            // Jedis nextId() increments first, so sequence == max restored id
            // guarantees the next peer gets max+1.
            dataSource.setIdSequence(
                    WireGuardPeer.class,
                    maxId
            );

            WireGuardEndpointConfig endpoint =
                    backup.getEndpointConfig();

            if (endpoint == null) {
                dataSource.delete(
                        WireGuardEndpointConfig.class
                );
            } else {
                clientConfigurationService
                        .setEndpointConfig(endpoint);
            }

            // prepareImport() already persisted global=false. Do not enable
            // here. finishImport() runs only after every provider succeeds.
            dataSource.setWireGuardServerState(false);

        } catch (RuntimeException e) {
            desiredEnabled = false;

            try {
                dataSource.setWireGuardServerState(false);
                controlService.stop();
            } catch (RuntimeException failClosedException) {
                e.addSuppressed(
                        failClosedException
                );
            }

            throw new IOException(
                    "Could not restore WireGuard backup; "
                            + "WireGuard remains disabled fail-closed.",
                    e
            );
        }
    }

    private String normalizeOptionalKey(
            String value,
            String description) throws IOException {

        if (value == null) {
            return null;
        }

        return requireKey(
                value,
                description
        );
    }

    private String requireKey(
            String value,
            String description) throws IOException {

        if (!isValidKey(value)) {
            throw corrupted(
                    "Invalid WireGuard "
                            + description
            );
        }

        return value.trim();
    }

    private boolean isValidKey(
            String value) {

        return value != null
                && WIREGUARD_KEY.matcher(
                        value.trim()
                ).matches();
    }

    private String normalizeAllowedIp(
            String value) throws IOException {

        if (value == null
                || !value.startsWith(
                        PEER_NETWORK_PREFIX)
                || !value.endsWith("/32")) {

            throw corrupted(
                    "Invalid WireGuard peer address"
            );
        }

        String hostText =
                value.substring(
                        PEER_NETWORK_PREFIX.length(),
                        value.length() - 3
                );

        final int host;

        try {
            host = Integer.parseInt(
                    hostText
            );
        } catch (NumberFormatException e) {
            throw corrupted(
                    "Invalid WireGuard peer address",
                    e
            );
        }

        if (host < 2 || host > 254) {
            throw corrupted(
                    "Invalid WireGuard peer address"
            );
        }

        return PEER_NETWORK_PREFIX
                + host
                + "/32";
    }

    private CorruptedBackupException corrupted(
            String message) {

        return new CorruptedBackupException(
                message
        );
    }

    private CorruptedBackupException corrupted(
            String message,
            Exception cause) {

        CorruptedBackupException exception =
                new CorruptedBackupException(
                        message
                );

        exception.initCause(cause);
        return exception;
    }
}
