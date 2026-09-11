package org.eblocker.server.http.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.backup.BackupWarning;
import org.eblocker.server.common.data.wireguard.WireGuardEndpointConfig;
import org.eblocker.server.common.data.wireguard.WireGuardEndpointType;
import org.eblocker.server.common.data.wireguard.WireGuardPeer;
import org.eblocker.server.common.data.wireguard.WireGuardTunnelMode;
import org.eblocker.server.http.service.WireGuardClientConfigurationService;
import org.eblocker.server.http.service.WireGuardCustomRouteValidator;
import org.eblocker.server.http.service.WireGuardServerControlService;
import org.eblocker.server.http.service.WireGuardServerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class WireGuardBackupProviderTest extends BackupProviderTestBase {

    private static final String SERVER_KEY = key('S');
    private static final String PRIVATE_1 = key('A');
    private static final String PUBLIC_1 = key('B');
    private static final String PSK_1 = key('C');
    private static final String PRIVATE_7 = key('D');
    private static final String PUBLIC_7 = key('E');
    private static final String PSK_7 = key('F');

    private DataSource dataSource;
    private WireGuardServerService serverService;
    private WireGuardServerControlService controlService;
    private WireGuardClientConfigurationService clientService;
    private WireGuardCustomRouteValidator routeValidator;

    private WireGuardBackupProvider provider;
    private WireGuardBackupProvider providerNoPassword;

    private WireGuardPeer peer1;
    private WireGuardPeer peer7;
    private WireGuardEndpointConfig endpoint;

    @BeforeEach
    void setUp() throws IOException {
        dataSource = Mockito.mock(DataSource.class);
        serverService = Mockito.mock(WireGuardServerService.class);
        controlService = Mockito.mock(WireGuardServerControlService.class);
        clientService = Mockito.mock(WireGuardClientConfigurationService.class);

        routeValidator = new WireGuardCustomRouteValidator();

        provider = new WireGuardBackupProvider(
                dataSource,
                serverService,
                controlService,
                clientService,
                routeValidator,
                createCryptoService("top secret")
        );

        providerNoPassword = new WireGuardBackupProvider(
                dataSource,
                serverService,
                controlService,
                clientService,
                routeValidator,
                null
        );

        peer1 = peer(
                1,
                "Peer One",
                PRIVATE_1,
                PUBLIC_1,
                PSK_1,
                "10.13.13.2/32",
                "device:001122334455",
                true,
                WireGuardTunnelMode.CUSTOM,
                Arrays.asList(
                        "192.168.10.7/24",
                        "10.23.42.9/8"
                )
        );

        peer7 = peer(
                7,
                "Peer Seven",
                PRIVATE_7,
                PUBLIC_7,
                PSK_7,
                "10.13.13.8/32",
                null,
                false,
                WireGuardTunnelMode.FULL_TUNNEL,
                List.of()
        );

        endpoint = new WireGuardEndpointConfig(
                WireGuardEndpointType.DYN_DNS,
                "vpn.example.org"
        );

        Mockito.when(
                dataSource.getWireGuardServerState()
        ).thenReturn(true);

        Mockito.when(
                dataSource.getAll(WireGuardPeer.class)
        ).thenReturn(
                Arrays.asList(peer7, peer1)
        );

        // Deliberately ahead of max active peer ID 7. Deleted peer IDs must
        // not be made reusable by backup/restore.
        Mockito.when(
                dataSource.getIdSequence(WireGuardPeer.class)
        ).thenReturn(9);

        Mockito.when(
                dataSource.get(WireGuardEndpointConfig.class)
        ).thenReturn(endpoint);

        Mockito.when(
                controlService.exportPrivateKeyForBackup()
        ).thenReturn(SERVER_KEY);

        Mockito.when(
                clientService.normalizeEndpointConfig(
                        Mockito.any(WireGuardEndpointConfig.class)
                )
        ).thenAnswer(invocation -> invocation.getArgument(0));

        Mockito.when(
                clientService.setEndpointConfig(
                        Mockito.any(WireGuardEndpointConfig.class)
                )
        ).thenAnswer(invocation -> invocation.getArgument(0));

        Mockito.when(
                dataSource.save(
                        Mockito.any(WireGuardPeer.class),
                        Mockito.anyInt()
                )
        ).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void encryptedBackupContainsNoPlaintextWireGuardSecrets()
            throws IOException {

        byte[] backup = exportBackup(provider);

        try (JarInputStream jar =
                     new JarInputStream(
                             new ByteArrayInputStream(backup))) {

            assertNotNull(jar.getNextEntry());

            String rawJson =
                    new String(
                            jar.readAllBytes(),
                            Charsets.UTF_8
                    );

            assertTrue(rawJson.contains(PUBLIC_1));
            assertFalse(rawJson.contains(PRIVATE_1));
            assertFalse(rawJson.contains(PSK_1));
            assertFalse(rawJson.contains(SERVER_KEY));
            assertTrue(rawJson.contains("\"secretsIncluded\":true"));
            assertTrue(rawJson.contains("\"peerIdSequence\":9"));
        }
    }

    @Test
    void noPasswordExportOmitsSecretBearingPeers()
            throws IOException {

        byte[] backup = exportBackup(providerNoPassword);

        try (JarInputStream jar =
                     new JarInputStream(
                             new ByteArrayInputStream(backup))) {

            assertNotNull(jar.getNextEntry());

            String rawJson =
                    new String(
                            jar.readAllBytes(),
                            Charsets.UTF_8
                    );

            assertFalse(rawJson.contains(PRIVATE_1));
            assertFalse(rawJson.contains(PSK_1));
            assertFalse(rawJson.contains(SERVER_KEY));
            assertTrue(rawJson.contains("\"secretsIncluded\":false"));
            assertTrue(rawJson.contains("\"peers\":[]"));
        }
    }

    @Test
    void verifyValidatesButDoesNotMutate()
            throws IOException {

        byte[] backup = exportBackup(provider);

        Mockito.clearInvocations(
                dataSource,
                serverService,
                controlService,
                clientService
        );

        verifyBackup(
                backup,
                provider
        );

        Mockito.verify(
                serverService,
                Mockito.never()
        ).disable();

        Mockito.verify(
                serverService,
                Mockito.never()
        ).enable();

        Mockito.verify(
                controlService,
                Mockito.never()
        ).restorePrivateKeyForBackup(
                Mockito.any()
        );

        Mockito.verify(
                dataSource,
                Mockito.never()
        ).deleteAll(
                WireGuardPeer.class
        );
    }

    @Test
    void restoreUsesExactIdsSequenceEndpointAndDeferredEnable()
            throws IOException {

        byte[] backup = exportBackup(provider);

        Mockito.clearInvocations(
                dataSource,
                serverService,
                controlService,
                clientService
        );

        provider.prepareImport();
        importBackup(
                backup,
                provider
        );

        Mockito.verify(serverService).disable();

        Mockito.verify(
                controlService
        ).restorePrivateKeyForBackup(
                SERVER_KEY
        );

        Mockito.verify(
                dataSource
        ).deleteAll(
                WireGuardPeer.class
        );

        Mockito.verify(
                dataSource
        ).setIdSequence(
                WireGuardPeer.class,
                9
        );

        Mockito.verify(
                clientService
        ).setEndpointConfig(
                Mockito.argThat(
                        config ->
                                config.getType()
                                        == WireGuardEndpointType.DYN_DNS
                                && "vpn.example.org".equals(
                                        config.getHost()
                                )
                )
        );

        Mockito.verify(
                serverService,
                Mockito.never()
        ).enable();

        provider.finishImport();

        Mockito.verify(serverService).enable();
    }

    @Test
    void exportRejectsSequenceBehindPersistedPeers()
            throws IOException {

        Mockito.when(
                dataSource.getIdSequence(WireGuardPeer.class)
        ).thenReturn(6);

        IOException exception =
                assertThrows(
                        IOException.class,
                        () -> exportBackup(provider)
                );

        assertTrue(
                exception.getMessage().contains(
                        "sequence is behind"
                )
        );

        Mockito.verify(
                controlService,
                Mockito.never()
        ).exportPrivateKeyForBackup();
    }

    @Test
    void earlyV6BackupWithoutSequenceFallsBackToMaxPeerId()
            throws IOException {

        byte[] backup = rewritePeerIdSequence(
                exportBackup(provider),
                null
        );

        Mockito.clearInvocations(
                dataSource,
                serverService,
                controlService,
                clientService
        );

        provider.prepareImport();
        importBackup(backup, provider);

        Mockito.verify(
                dataSource
        ).setIdSequence(
                WireGuardPeer.class,
                7
        );
    }

    @Test
    void importedSequenceBehindPeerIdsIsRejectedBeforeMutation()
            throws IOException {

        byte[] backup = rewritePeerIdSequence(
                exportBackup(provider),
                6
        );

        Mockito.clearInvocations(
                dataSource,
                controlService
        );

        assertThrows(
                CorruptedBackupException.class,
                () -> verifyBackup(backup, provider)
        );

        Mockito.verify(
                dataSource,
                Mockito.never()
        ).deleteAll(WireGuardPeer.class);

        Mockito.verify(
                controlService,
                Mockito.never()
        ).restorePrivateKeyForBackup(Mockito.any());
    }

    @Test
    void noPasswordImportStaysDisabledAndWarns()
            throws IOException {

        byte[] backup = exportBackup(provider);

        providerNoPassword.prepareImport();

        importBackup(
                backup,
                providerNoPassword
        );

        providerNoPassword.finishImport();

        assertEquals(
                List.of(
                        new BackupWarning(
                                BackupWarning.Id
                                        .NO_PASSWORD_WIREGUARD_NOT_IMPORTED
                        )
                ),
                providerNoPassword.getWarnings()
        );

        Mockito.verify(
                serverService,
                Mockito.atLeastOnce()
        ).disable();

        Mockito.verify(
                serverService,
                Mockito.never()
        ).enable();

        Mockito.verify(
                controlService,
                Mockito.never()
        ).restorePrivateKeyForBackup(
                Mockito.any()
        );
    }

    @Test
    void duplicatePeerIdsAreRejectedBeforeMutation()
            throws IOException {

        WireGuardPeer duplicate =
                peer(
                        1,
                        "Duplicate",
                        PRIVATE_7,
                        PUBLIC_7,
                        PSK_7,
                        "10.13.13.8/32",
                        null,
                        false,
                        WireGuardTunnelMode.FULL_TUNNEL,
                        List.of()
                );

        Mockito.when(
                dataSource.getAll(WireGuardPeer.class)
        ).thenReturn(
                Arrays.asList(
                        peer1,
                        duplicate
                )
        );

        byte[] backup = exportBackup(provider);

        Mockito.clearInvocations(
                dataSource,
                controlService
        );

        assertThrows(
                CorruptedBackupException.class,
                () -> verifyBackup(
                        backup,
                        provider
                )
        );

        Mockito.verify(
                dataSource,
                Mockito.never()
        ).deleteAll(
                WireGuardPeer.class
        );

        Mockito.verify(
                controlService,
                Mockito.never()
        ).restorePrivateKeyForBackup(
                Mockito.any()
        );
    }

    private byte[] rewritePeerIdSequence(
            byte[] backup,
            Integer sequence) throws IOException {

        ObjectMapper mapper = new ObjectMapper();

        try (JarInputStream input =
                     new JarInputStream(
                             new ByteArrayInputStream(backup))) {

            JarEntry sourceEntry = input.getNextJarEntry();
            assertNotNull(sourceEntry);

            ObjectNode root =
                    (ObjectNode) mapper.readTree(
                            input.readAllBytes()
                    );

            if (sequence == null) {
                root.remove("peerIdSequence");
            } else {
                root.put("peerIdSequence", sequence);
            }

            ByteArrayOutputStream bytes =
                    new ByteArrayOutputStream();

            try (JarOutputStream output =
                         new JarOutputStream(bytes)) {

                output.putNextEntry(
                        new JarEntry(sourceEntry.getName())
                );
                output.write(mapper.writeValueAsBytes(root));
                output.closeEntry();
            }

            return bytes.toByteArray();
        }
    }

    private static WireGuardPeer peer(
            int id,
            String name,
            String privateKey,
            String publicKey,
            String presharedKey,
            String allowedIp,
            String deviceId,
            boolean allowLanAccess,
            WireGuardTunnelMode mode,
            List<String> customAllowedIps) {

        WireGuardPeer peer = new WireGuardPeer();
        peer.setId(id);
        peer.setName(name);
        peer.setPrivateKey(privateKey);
        peer.setPublicKey(publicKey);
        peer.setPresharedKey(presharedKey);
        peer.setAllowedIp(allowedIp);
        peer.setDeviceId(deviceId);
        peer.setAllowLanAccess(allowLanAccess);
        peer.setTunnelMode(mode);
        peer.setCustomAllowedIps(customAllowedIps);
        return peer;
    }

    private static String key(char value) {
        return String.valueOf(value).repeat(43) + "=";
    }
}
