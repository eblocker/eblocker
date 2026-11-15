package org.eblocker.server.http.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eblocker.server.common.system.LoggingProcess;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.http.model.WireGuardStatus;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WireGuardServerControlServiceTest {

    private static final String COMMAND =
            "wireguard-server-control";

    @Test
    public void startChecksSuccessfulExitCode()
            throws Exception {

        ScriptRunner scriptRunner =
                Mockito.mock(ScriptRunner.class);

        Mockito.when(
                scriptRunner.runScript(COMMAND, "start")
        ).thenReturn(0);

        WireGuardServerControlService service =
                service(scriptRunner);

        service.start();

        Mockito.verify(scriptRunner)
                .runScript(COMMAND, "start");
    }

    @Test
    public void startRejectsNonZeroExitCode()
            throws Exception {

        ScriptRunner scriptRunner =
                Mockito.mock(ScriptRunner.class);

        Mockito.when(
                scriptRunner.runScript(COMMAND, "start")
        ).thenReturn(5);

        WireGuardServerControlService service =
                service(scriptRunner);

        try {
            service.start();
            fail("Expected IllegalStateException");

        } catch (IllegalStateException e) {
            assertTrue(
                    e.getMessage().contains("exit code 5")
            );
        }
    }

    @Test
    public void statusParsesJsonOutput()
            throws Exception {

        ScriptRunner scriptRunner =
                Mockito.mock(ScriptRunner.class);

        LoggingProcess process =
                Mockito.mock(LoggingProcess.class);

        Mockito.when(
                scriptRunner.startScript(
                        COMMAND,
                        "status-json"
                )
        ).thenReturn(process);

        Mockito.when(process.waitFor())
                .thenReturn(0);

        Mockito.when(process.pollStdout())
                .thenReturn(
                        "{\"iface\":\"wg0\","
                                + "\"service\":\"active\","
                                + "\"wg\":\"up\","
                                + "\"peers\":3,"
                                + "\"error\":\"\"}",
                        null
                );

        WireGuardStatus status =
                service(scriptRunner).getStatus();

        assertEquals("wg0", status.getIface());
        assertEquals("active", status.getService());
        assertEquals("up", status.getWg());
        assertEquals(3, status.getPeers());
        assertEquals("", status.getError());
        assertEquals(0, status.getPeerTelemetry().size());
    }

    @Test
    public void statusParsesSecretFreePeerTelemetry()
            throws Exception {

        ScriptRunner scriptRunner =
                Mockito.mock(ScriptRunner.class);

        LoggingProcess process =
                Mockito.mock(LoggingProcess.class);

        Mockito.when(
                scriptRunner.startScript(
                        COMMAND,
                        "status-json"
                )
        ).thenReturn(process);

        Mockito.when(process.waitFor())
                .thenReturn(0);

        String publicKey =
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

        Mockito.when(process.pollStdout())
                .thenReturn(
                        "{\"iface\":\"wg0\","
                                + "\"service\":\"active\","
                                + "\"wg\":\"up\","
                                + "\"peers\":1,"
                                + "\"error\":\"\","
                                + "\"peerTelemetry\":[{"
                                + "\"publicKey\":\"" + publicKey + "\","
                                + "\"latestHandshakeEpochSeconds\":"
                                + "1725000000,"
                                + "\"rxBytes\":1234,"
                                + "\"txBytes\":5678"
                                + "}]}",
                        null
                );

        WireGuardStatus status =
                service(scriptRunner).getStatus();

        assertEquals(1, status.getPeerTelemetry().size());

        assertEquals(
                publicKey,
                status.getPeerTelemetry()
                        .get(0)
                        .getPublicKey()
        );

        assertEquals(
                1725000000L,
                status.getPeerTelemetry()
                        .get(0)
                        .getLatestHandshakeEpochSeconds()
        );

        assertEquals(
                1234L,
                status.getPeerTelemetry()
                        .get(0)
                        .getRxBytes()
        );

        assertEquals(
                5678L,
                status.getPeerTelemetry()
                        .get(0)
                        .getTxBytes()
        );
    }

    @Test
    public void publicKeyReturnsValidatedWireGuardKey()
            throws Exception {

        ScriptRunner scriptRunner =
                Mockito.mock(ScriptRunner.class);

        LoggingProcess process =
                Mockito.mock(LoggingProcess.class);

        Mockito.when(
                scriptRunner.startScript(
                        COMMAND,
                        "public-key"
                )
        ).thenReturn(process);

        Mockito.when(process.waitFor())
                .thenReturn(0);

        String publicKey =
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

        Mockito.when(process.pollStdout())
                .thenReturn(
                        "some unrelated output",
                        publicKey,
                        null
                );

        assertEquals(
                publicKey,
                service(scriptRunner).getPublicKey()
        );
    }

    @Test
    public void interruptedOutputCommandStopsRunningProcess()
            throws Exception {

        ScriptRunner scriptRunner =
                Mockito.mock(ScriptRunner.class);

        LoggingProcess process =
                Mockito.mock(LoggingProcess.class);

        Mockito.when(
                scriptRunner.startScript(
                        COMMAND,
                        "status-json"
                )
        ).thenReturn(process);

        Mockito.when(process.waitFor())
                .thenThrow(new InterruptedException());

        Mockito.when(process.isAlive())
                .thenReturn(true);

        try {
            service(scriptRunner).getStatus();
            fail("Expected IllegalStateException");

        } catch (IllegalStateException e) {
            assertTrue(
                    e.getMessage().contains("Interrupted")
            );

            assertTrue(
                    Thread.currentThread().isInterrupted()
            );

        } finally {
            Thread.interrupted();
        }

        Mockito.verify(scriptRunner)
                .stopScript(process);
    }

    @Test
    public void ioFailureIsNotSilentlyIgnored()
            throws Exception {

        ScriptRunner scriptRunner =
                Mockito.mock(ScriptRunner.class);

        Mockito.when(
                scriptRunner.runScript(COMMAND, "restart")
        ).thenThrow(new IOException("test"));

        try {
            service(scriptRunner).restart();
            fail("Expected IllegalStateException");

        } catch (IllegalStateException e) {
            assertTrue(
                    e.getMessage().contains(
                            "Could not run WireGuard command restart"
                    )
            );
        }
    }

    private WireGuardServerControlService service(
            ScriptRunner scriptRunner) {

        return new WireGuardServerControlService(
                scriptRunner,
                new ObjectMapper(),
                COMMAND
        );
    }
}
