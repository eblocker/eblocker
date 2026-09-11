package org.eblocker.server.http.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.system.LoggingProcess;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.http.model.WireGuardStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

@Singleton
public class WireGuardServerControlService {

    private static final Pattern WIREGUARD_PUBLIC_KEY =
            Pattern.compile("^[A-Za-z0-9+/]{43}=$");

    private static final Pattern WIREGUARD_PRIVATE_KEY =
            Pattern.compile("^[A-Za-z0-9+/]{43}=$");

    private static final Path DEFAULT_KEY_TRANSFER_DIRECTORY =
            Paths.get("/opt/eblocker-icap/tmp");

    private final ScriptRunner scriptRunner;
    private final ObjectMapper objectMapper;
    private final String wireGuardServerCommand;
    private final Path keyTransferDirectory;

    @Inject
    public WireGuardServerControlService(
            ScriptRunner scriptRunner,
            ObjectMapper objectMapper,
            @Named("wireguard.server.command") String wireGuardServerCommand,
            @Named("tmpDir") String tmpDir) {

        this(
                scriptRunner,
                objectMapper,
                wireGuardServerCommand,
                Paths.get(tmpDir)
        );
    }

    /**
     * Source-compatible convenience constructor for focused callers.
     * Production Guice uses the configured application tmpDir above.
     */
    public WireGuardServerControlService(
            ScriptRunner scriptRunner,
            ObjectMapper objectMapper,
            String wireGuardServerCommand) {

        this(
                scriptRunner,
                objectMapper,
                wireGuardServerCommand,
                DEFAULT_KEY_TRANSFER_DIRECTORY
        );
    }

    WireGuardServerControlService(
            ScriptRunner scriptRunner,
            ObjectMapper objectMapper,
            String wireGuardServerCommand,
            Path keyTransferDirectory) {

        this.scriptRunner = scriptRunner;
        this.objectMapper = objectMapper;
        this.wireGuardServerCommand = wireGuardServerCommand;
        this.keyTransferDirectory =
                keyTransferDirectory
                        .toAbsolutePath()
                        .normalize();
    }

    public void start() {
        runControlCommand("start");
    }

    public void stop() {
        runControlCommand("stop");
    }

    public void restart() {
        runControlCommand("restart");
    }

    public WireGuardStatus getStatus() {
        ScriptOutput output = runOutputCommand("status-json");

        if (output.exitCode != 0) {
            throw new IllegalStateException(
                    "WireGuard status command failed with exit code "
                            + output.exitCode + ".");
        }

        Exception lastParseException = null;

        for (String line : output.lines) {
            String candidate = line.trim();

            if (!candidate.startsWith("{") || !candidate.endsWith("}")) {
                continue;
            }

            try {
                return objectMapper.readValue(
                        candidate,
                        WireGuardStatus.class
                );
            } catch (IOException e) {
                lastParseException = e;
            }
        }

        if (lastParseException != null) {
            throw new IllegalStateException(
                    "WireGuard status command returned invalid JSON.",
                    lastParseException
            );
        }

        throw new IllegalStateException(
                "WireGuard status command returned no JSON status."
        );
    }

    public String getPublicKey() {
        ScriptOutput output = runOutputCommand("public-key");

        if (output.exitCode != 0) {
            throw new IllegalStateException(
                    "WireGuard public-key command failed with exit code "
                            + output.exitCode + ".");
        }

        for (String line : output.lines) {
            String candidate = line.trim();

            if (WIREGUARD_PUBLIC_KEY.matcher(candidate).matches()) {
                return candidate;
            }
        }

        throw new IllegalStateException(
                "WireGuard server public key is not available."
        );
    }

    /**
     * Returns the persistent server private key for encrypted backup only.
     * The privileged script copies it to an owner-only temporary file.
     */
    public String exportPrivateKeyForBackup() {
        Path keyFile = createSecureKeyTransferFile();

        try {
            runControlCommand(
                    "export-private-key",
                    keyFile.toAbsolutePath().toString()
            );

            String value =
                    Files.readString(
                            keyFile,
                            StandardCharsets.US_ASCII
                    ).trim();

            if (value.isEmpty()) {
                return null;
            }

            if (!WIREGUARD_PRIVATE_KEY.matcher(value).matches()) {
                throw new IllegalStateException(
                        "WireGuard server private key has invalid format."
                );
            }

            return value;

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read WireGuard server key backup transfer.",
                    e
            );

        } finally {
            deleteKeyTransferFile(keyFile);
        }
    }

    /**
     * Restores server identity from encrypted backup.
     * A null value represents a server that had no generated identity yet.
     */
    public void restorePrivateKeyForBackup(
            String privateKey) {

        if (privateKey == null) {
            runControlCommand(
                    "clear-private-key"
            );
            return;
        }

        String normalized =
                privateKey.trim();

        if (!WIREGUARD_PRIVATE_KEY.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "WireGuard server private key has invalid format."
            );
        }

        Path keyFile = createSecureKeyTransferFile();

        try {
            Files.writeString(
                    keyFile,
                    normalized + "\n",
                    StandardCharsets.US_ASCII
            );

            runControlCommand(
                    "import-private-key",
                    keyFile.toAbsolutePath().toString()
            );

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not prepare WireGuard server key restore transfer.",
                    e
            );

        } finally {
            deleteKeyTransferFile(keyFile);
        }
    }

    private Path createSecureKeyTransferFile() {
        try {
            Path path =
                    Files.createTempFile(
                            keyTransferDirectory,
                            "eblocker-wireguard-server-key-",
                            ".key"
                    );

            try {
                Files.setPosixFilePermissions(
                        path,
                        EnumSet.of(
                                PosixFilePermission.OWNER_READ,
                                PosixFilePermission.OWNER_WRITE
                        )
                );
            } catch (UnsupportedOperationException ignored) {
                // Production is POSIX/Linux.
            }

            return path;

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not create WireGuard server key transfer file.",
                    e
            );
        }
    }

    private void deleteKeyTransferFile(
            Path path) {

        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort; privileged script removes import files too.
        }
    }

    private void runControlCommand(
            String action,
            String argument) {

        try {
            int exitCode = scriptRunner.runScript(
                    wireGuardServerCommand,
                    action,
                    argument
            );

            if (exitCode != 0) {
                throw new IllegalStateException(
                        "WireGuard command "
                                + action
                                + " failed with exit code "
                                + exitCode + "."
                );
            }

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not run WireGuard command " + action + ".",
                    e
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Interrupted while running WireGuard command "
                            + action + ".",
                    e
            );
        }
    }

    private void runControlCommand(String action) {
        try {
            int exitCode = scriptRunner.runScript(
                    wireGuardServerCommand,
                    action
            );

            if (exitCode != 0) {
                throw new IllegalStateException(
                        "WireGuard command "
                                + action
                                + " failed with exit code "
                                + exitCode
                                + "."
                );
            }

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not run WireGuard command " + action + ".",
                    e
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Interrupted while running WireGuard command "
                            + action + ".",
                    e
            );
        }
    }

    private ScriptOutput runOutputCommand(String action) {
        try {
            return executeOutputCommand(action);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not run WireGuard command " + action + ".",
                    e
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Interrupted while running WireGuard command "
                            + action + ".",
                    e
            );
        }
    }

    private ScriptOutput executeOutputCommand(String action)
            throws IOException, InterruptedException {

        LoggingProcess process = scriptRunner.startScript(
                wireGuardServerCommand,
                action
        );

        try {
            int exitCode = process.waitFor();

            List<String> lines = new ArrayList<>();

            String line;
            while ((line = process.pollStdout()) != null) {
                lines.add(line);
            }

            return new ScriptOutput(exitCode, lines);

        } catch (InterruptedException e) {
            stopRunningProcess(process, e);
            throw e;
        }
    }

    private void stopRunningProcess(
            LoggingProcess process,
            InterruptedException originalException) {

        if (!process.isAlive()) {
            return;
        }

        try {
            scriptRunner.stopScript(process);

        } catch (IOException | InterruptedException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }

    private static class ScriptOutput {

        private final int exitCode;
        private final List<String> lines;

        private ScriptOutput(int exitCode, List<String> lines) {
            this.exitCode = exitCode;
            this.lines = lines;
        }
    }
}
