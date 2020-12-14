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
package org.eblocker.server.common.system.unix;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoggingProcessUnixTest {

    private ExecutorService executorService;

    @Before
    public void setUp() {
        executorService = Executors.newFixedThreadPool(3);  // 1 for process, 2 for stdout/stderr readers
    }

    @After
    public void tearDown() {
        executorService.shutdownNow();
    }

    @Test(timeout = 3000)
    public void testWaitFor() throws IOException, InterruptedException {
        LoggingProcessUnix process = new LoggingProcessUnix("testWaitFor", executorService);
        long start = System.currentTimeMillis();
        process.start(new String[]{ "/bin/bash", "-c", "sleep 1" });
        Assert.assertEquals(0, process.waitFor());
        long elapsed = System.currentTimeMillis() - start;
        Assert.assertTrue(elapsed >= 1000);
    }

    @Test
    public void testGetPid() throws IOException, InterruptedException {
        LoggingProcessUnix process = new LoggingProcessUnix("testGetPid", executorService);
        process.start(new String[]{ "/bin/bash", "-c", "echo $$" });
        Assert.assertEquals(0, process.waitFor());
        String output = process.pollStdout();
        Assert.assertNotNull(output);
        Assert.assertEquals(Integer.parseInt(output), process.getPid());
    }

    @Test(timeout = 5000)
    public void testPollStdout() throws IOException, InterruptedException {
        LoggingProcessUnix process = new LoggingProcessUnix("testPollStdout", executorService);
        process.start(new String[]{ "/bin/bash", "-c", "sleep 1; echo line 1; sleep 1; echo line 2" });
        Assert.assertNull(process.pollStdout());
        Thread.sleep(1200);
        Assert.assertEquals("line 1", process.pollStdout());
        Assert.assertNull(process.pollStdout());
        Thread.sleep(1200);
        Assert.assertEquals("line 2", process.pollStdout());
        Assert.assertNull(process.pollStdout());
    }

    @Test(timeout = 5000)
    public void testTakeStdout() throws IOException, InterruptedException {
        LoggingProcessUnix process = new LoggingProcessUnix("testTakeStdout", executorService);
        process.start(new String[]{ "/bin/bash", "-c", "sleep 1; echo line 1; sleep 1; echo line 2" });
        Assert.assertEquals("line 1", process.takeStdout());
        Assert.assertEquals("line 2", process.takeStdout());
        Assert.assertNull(process.takeStdout());
    }

}
