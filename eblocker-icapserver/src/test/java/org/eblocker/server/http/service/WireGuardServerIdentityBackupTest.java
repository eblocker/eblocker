package org.eblocker.server.http.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eblocker.server.common.system.ScriptRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WireGuardServerIdentityBackupTest {

    private static final String COMMAND =
            "wireguard-server-control";

    private static final String KEY =
            String.valueOf('K').repeat(43) + "=";

    @TempDir
    Path tempDir;

    private ScriptRunner scriptRunner;
    private WireGuardServerControlService service;

    @BeforeEach
    void setUp() {
        scriptRunner =
                Mockito.mock(ScriptRunner.class);

        service =
                new WireGuardServerControlService(
                        scriptRunner,
                        new ObjectMapper(),
                        COMMAND,
                        tempDir
                );
    }

    @Test
    void exportUsesTemporaryFileRatherThanSecretArgument()
            throws Exception {

        Mockito.when(
                scriptRunner.runScript(
                        Mockito.eq(COMMAND),
                        Mockito.eq("export-private-key"),
                        Mockito.anyString()
                )
        ).thenAnswer(invocation -> {
            Path path =
                    Path.of(
                            (String) invocation.getArgument(2)
                    );

            Files.writeString(
                    path,
                    KEY + "\n",
                    StandardCharsets.US_ASCII
            );

            return 0;
        });

        assertEquals(
                KEY,
                service.exportPrivateKeyForBackup()
        );

        Mockito.verify(
                scriptRunner
        ).runScript(
                Mockito.eq(COMMAND),
                Mockito.eq("export-private-key"),
                Mockito.argThat(
                        value ->
                                value.startsWith(
                                        tempDir.resolve(
                                                "eblocker-wireguard-server-key-"
                                        ).toString()
                                )
                                && !value.contains(KEY)
                )
        );
    }

    @Test
    void restoreTransfersKeyThroughTemporaryFile()
            throws Exception {

        Mockito.when(
                scriptRunner.runScript(
                        Mockito.eq(COMMAND),
                        Mockito.eq("import-private-key"),
                        Mockito.anyString()
                )
        ).thenAnswer(invocation -> {
            Path path =
                    Path.of(
                            (String) invocation.getArgument(2)
                    );

            assertTrue(
                    path.startsWith(
                            tempDir
                    )
            );

            String transferred =
                    Files.readString(
                            path,
                            StandardCharsets.US_ASCII
                    ).trim();

            assertEquals(
                    KEY,
                    transferred
            );

            assertFalse(
                    path.toString().contains(KEY)
            );

            return 0;
        });

        service.restorePrivateKeyForBackup(
                KEY
        );
    }

    @Test
    void nullRestoreClearsServerIdentity()
            throws Exception {

        Mockito.when(
                scriptRunner.runScript(
                        COMMAND,
                        "clear-private-key"
                )
        ).thenReturn(0);

        service.restorePrivateKeyForBackup(
                null
        );

        Mockito.verify(
                scriptRunner
        ).runScript(
                COMMAND,
                "clear-private-key"
        );
    }

    @Test
    void invalidPrivateKeyIsRejectedBeforeScriptInvocation() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.restorePrivateKeyForBackup(
                                "invalid"
                        )
        );

        Mockito.verifyNoInteractions(
                scriptRunner
        );
    }
}
