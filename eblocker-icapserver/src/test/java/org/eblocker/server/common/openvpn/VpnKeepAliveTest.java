/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License. You may obtain a copy of the License at:
 *
 *   https://joinup.ec.europa.eu/page/eupl-text-11-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.eblocker.server.common.openvpn;

import org.eblocker.server.common.system.LoggingProcess;
import org.eblocker.server.common.system.ScriptRunner;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.xml.ws.Holder;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

public class VpnKeepAliveTest {

    private static final String KILL_SCRIPT_NAME = "kill";
    private static final String PING_SCRIPT_NAME = "ping";
    private static final int PING_INTERVAL = 10;
    private static final int NO_ANSWER_THRESHOLD = 5;
    private static final String INTERFACE_NAME = "tun23";
    private static final String TARGET = "8.8.8.8";

    private LoggingProcess process;
    private Executor executor;
    private ArgumentCaptor<Runnable> executorCaptor;
    private ScriptRunner scriptRunner;
    private Runnable callback;
    private VpnKeepAlive keepAlive;

    @Before
    public void setUp() throws IOException {
        process = Mockito.mock(LoggingProcess.class);
        scriptRunner = Mockito.mock(ScriptRunner.class);
        Mockito.when(scriptRunner.startScript(PING_SCRIPT_NAME, "-ODi" + PING_INTERVAL, "-I" + INTERFACE_NAME, TARGET)).thenReturn(process);

        executor = Mockito.mock(Executor.class);
        callback = Mockito.mock(Runnable.class);

        keepAlive = new VpnKeepAlive(KILL_SCRIPT_NAME, PING_SCRIPT_NAME, PING_INTERVAL, NO_ANSWER_THRESHOLD, executor, scriptRunner, INTERFACE_NAME, TARGET, callback);

        executorCaptor = ArgumentCaptor.forClass(Runnable.class);
    }

    @Test(timeout = 5000)
    public void testConnectionOk() throws InterruptedException {
        setupProcessOutput(
            lineReply(System.currentTimeMillis() - 2000, 64, "8.8.8.8", 1, 112, 25.2f),
            lineReply(System.currentTimeMillis() - 1900, 64, "8.8.8.8", 2, 112, 25.2f),
            lineReply(System.currentTimeMillis() - 1800, 64, "8.8.8.8", 3, 112, 25.2f),
            lineReply(System.currentTimeMillis() - 1700, 64, "8.8.8.8", 4, 112, 25.2f),
            lineReply(System.currentTimeMillis() - 1600, 64, "8.8.8.8", 5, 112, 25.2f),
            lineReply(System.currentTimeMillis() - 1500, 64, "8.8.8.8", 6, 112, 25.2f),
            lineReply(System.currentTimeMillis() - 1400, 64, "8.8.8.8", 7, 112, 25.2f),
            lineReply(System.currentTimeMillis() - 1300, 64, "8.8.8.8", 8, 112, 25.2f)
        );

        keepAlive.start();
        Mockito.verify(executor).execute(executorCaptor.capture());
        executorCaptor.getValue().run();
        Mockito.verifyZeroInteractions(callback);
    }

    @Test(timeout = 5000)
    public void testConnectionFlakyButAboveThreshold() throws InterruptedException {
        setupProcessOutput(
            lineReply(System.currentTimeMillis() - 2000, 64, "8.8.8.8", 1, 112, 25.2f),
            lineReply(System.currentTimeMillis() - 1900, 64, "8.8.8.8", 2, 112, 25.2f),
            lineReply(System.currentTimeMillis() - 1800, 64, "8.8.8.8", 3, 112, 25.2f),
            lineReply(System.currentTimeMillis() - 1700, 64, "8.8.8.8", 4, 112, 25.2f),
            lineNoReply(System.currentTimeMillis() - 1600, 5),
            lineNoReply(System.currentTimeMillis() - 1500, 6),
            lineNoReply(System.currentTimeMillis() - 1400, 7),
            lineNoReply(System.currentTimeMillis() - 1300, 8),
            lineReply(System.currentTimeMillis() - 1200, 64, "8.8.8.8", 9, 112, 25.2f),
            lineNoReply(System.currentTimeMillis() - 1100, 10),
            lineNoReply(System.currentTimeMillis() - 1000, 11),
            lineNoReply(System.currentTimeMillis() - 900, 12)
        );

        keepAlive.start();
        Mockito.verify(executor).execute(executorCaptor.capture());
        executorCaptor.getValue().run();
        Mockito.verifyZeroInteractions(callback);
    }

    @Test(timeout = 5000)
    public void testConnectionDead() throws InterruptedException {
        setupProcessOutput(
            lineReply(System.currentTimeMillis() - 2000, 64, "8.8.8.8", 1, 112, 25.2f),
            lineReply(System.currentTimeMillis() - 1900, 64, "8.8.8.8", 2, 112, 25.2f),
            lineReply(System.currentTimeMillis() - 1800, 64, "8.8.8.8", 3, 112, 25.2f),
            lineReply(System.currentTimeMillis() - 1700, 64, "8.8.8.8", 4, 112, 25.2f),
            lineNoReply(System.currentTimeMillis() - 1600, 5),
            lineNoReply(System.currentTimeMillis() - 1500, 6),
            lineNoReply(System.currentTimeMillis() - 1400, 7),
            lineNoReply(System.currentTimeMillis() - 1300, 8),
            lineNoReply(System.currentTimeMillis() - 1100, 9),
            lineNoReply(System.currentTimeMillis() - 1000, 10)
        );

        keepAlive.start();
        Mockito.verify(executor).execute(executorCaptor.capture());
        executorCaptor.getValue().run();
        Mockito.verify(callback).run();
    }

    @Test(timeout = 5000)
    public void testStop() throws InterruptedException, IOException {
        Semaphore processStartedSemaphore = new Semaphore(1);
        Semaphore processStoppedSemaphore = new Semaphore(1);
        processStartedSemaphore.acquire();
        processStoppedSemaphore.acquire();

        Mockito.when(process.getPid()).thenReturn(1000);
        Mockito.when(process.takeStdout()).then(im -> {
            processStartedSemaphore.release();
            processStoppedSemaphore.acquire();
            return null;
        });

        Mockito.when(scriptRunner.runScript(KILL_SCRIPT_NAME, "1000")).then(im -> {
            processStoppedSemaphore.release();
            return 0;
        });

        keepAlive.start();

        // run blocking mock process in extra thread
        Mockito.verify(executor).execute(executorCaptor.capture());
        Thread thread = new Thread(executorCaptor.getValue());
        thread.start();

        // wait for mock process to be run
        processStartedSemaphore.acquire();

        keepAlive.stop();

        // wait for extra thread to exit
        thread.join();
    }

    @Test(timeout = 5000)
    public void testStopAfterCallback() throws InterruptedException, IOException {
        setupProcessOutput(
            lineReply(System.currentTimeMillis() - 2000, 64, "8.8.8.8", 1, 112, 25.2f),
            lineReply(System.currentTimeMillis() - 1900, 64, "8.8.8.8", 2, 112, 25.2f),
            lineReply(System.currentTimeMillis() - 1800, 64, "8.8.8.8", 3, 112, 25.2f),
            lineReply(System.currentTimeMillis() - 1700, 64, "8.8.8.8", 4, 112, 25.2f),
            lineNoReply(System.currentTimeMillis() - 1600, 5),
            lineNoReply(System.currentTimeMillis() - 1500, 6),
            lineNoReply(System.currentTimeMillis() - 1400, 7),
            lineNoReply(System.currentTimeMillis() - 1300, 8),
            lineNoReply(System.currentTimeMillis() - 1200, 9)
        );
        keepAlive.start();
        Mockito.verify(executor).execute(executorCaptor.capture());
        executorCaptor.getValue().run();

        Mockito.verify(callback).run();
        Mockito.verify(scriptRunner).runScript(Mockito.eq(KILL_SCRIPT_NAME), Mockito.anyString());
    }

    @Test(timeout = 5000)
    public void testNoRepeatedCallbacks() throws InterruptedException, IOException {
        setupProcessOutput(
            lineReply(System.currentTimeMillis() - 2000, 64, "8.8.8.8", 1, 112, 25.2f),
            lineReply(System.currentTimeMillis() - 1900, 64, "8.8.8.8", 2, 112, 25.2f),
            lineReply(System.currentTimeMillis() - 1800, 64, "8.8.8.8", 3, 112, 25.2f),
            lineReply(System.currentTimeMillis() - 1700, 64, "8.8.8.8", 4, 112, 25.2f),
            lineNoReply(System.currentTimeMillis() - 1600, 5),
            lineNoReply(System.currentTimeMillis() - 1500, 6),
            lineNoReply(System.currentTimeMillis() - 1400, 7),
            lineNoReply(System.currentTimeMillis() - 1300, 8),
            lineNoReply(System.currentTimeMillis() - 1200, 9),
            lineReply(System.currentTimeMillis() - 1100, 64, "8.8.8.8", 10, 112, 25.2f),
            lineNoReply(System.currentTimeMillis() - 1000, 11),
            lineNoReply(System.currentTimeMillis() - 900, 12),
            lineNoReply(System.currentTimeMillis() - 800, 13),
            lineNoReply(System.currentTimeMillis() - 700, 14),
            lineNoReply(System.currentTimeMillis() - 600, 15)
        );

        keepAlive.start();
        Mockito.verify(executor).execute(executorCaptor.capture());
        executorCaptor.getValue().run();

        Mockito.verify(callback, Mockito.times(1)).run();
        Mockito.verify(scriptRunner).runScript(Mockito.eq(KILL_SCRIPT_NAME), Mockito.anyString());
    }

    // EB1-2387
    @Test(timeout = 5000)
    public void testStopTwice() throws InterruptedException, IOException {
        Mockito.doAnswer(im -> {
                keepAlive.stop();
                return null;
        }).when(callback).run();

        setupProcessOutput(
            lineReply(System.currentTimeMillis() - 2000, 64, "8.8.8.8", 1, 112, 25.2f),
            lineReply(System.currentTimeMillis() - 1900, 64, "8.8.8.8", 2, 112, 25.2f),
            lineReply(System.currentTimeMillis() - 1800, 64, "8.8.8.8", 3, 112, 25.2f),
            lineReply(System.currentTimeMillis() - 1700, 64, "8.8.8.8", 4, 112, 25.2f),
            lineNoReply(System.currentTimeMillis() - 1600, 5),
            lineNoReply(System.currentTimeMillis() - 1500, 6),
            lineNoReply(System.currentTimeMillis() - 1400, 7),
            lineNoReply(System.currentTimeMillis() - 1300, 8),
            lineNoReply(System.currentTimeMillis() - 1200, 9)
        );

        keepAlive.start();
        Mockito.verify(executor).execute(executorCaptor.capture());
        executorCaptor.getValue().run();

        Mockito.verify(scriptRunner).runScript(Mockito.eq(KILL_SCRIPT_NAME), Mockito.anyString());
    }

    private void setupProcessOutput(String... output) throws InterruptedException {
        final Holder<Integer> nextLine = new Holder<>(0);
        Mockito.when(process.takeStdout()).then(im -> nextLine.value == output.length ? null : output[nextLine.value++]);
    }

    private String lineReply(long timestamp, int bytes, String source, int icmpSequence, int ttl, float rtt) {
        return String.format(Locale.US, "[%.6f] %d bytes from %s: icmp_seq=%d ttl=%d time=%.1f ms", timestamp / 1000.0, bytes, source, icmpSequence, ttl, rtt);
    }

    private String lineNoReply(long timestamp, int icmpSequence) {
        return String.format(Locale.US, "[%.6f] no answer yet for icmp_seq=%d", timestamp / 1000.0, icmpSequence);
    }

}
