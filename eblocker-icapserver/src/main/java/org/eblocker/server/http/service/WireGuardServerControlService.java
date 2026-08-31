package org.eblocker.server.http.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.system.LoggingProcess;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.http.model.WireGuardStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Singleton
public class WireGuardServerControlService {

    private static final Pattern WIREGUARD_PUBLIC_KEY =
            Pattern.compile("^[A-Za-z0-9+/]{43}=$");

    private final ScriptRunner scriptRunner;
    private final ObjectMapper objectMapper;
    private final String wireGuardServerCommand;

    @Inject
    public WireGuardServerControlService(
            ScriptRunner scriptRunner,
            ObjectMapper objectMapper,
            @Named("wireguard.server.command") String wireGuardServerCommand) {

        this.scriptRunner = scriptRunner;
        this.objectMapper = objectMapper;
        this.wireGuardServerCommand = wireGuardServerCommand;
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
