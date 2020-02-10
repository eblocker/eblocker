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
package org.eblocker.server.common.status;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.eblocker.server.common.system.ScriptRunner;

import redis.clients.jedis.exceptions.JedisConnectionException;

public class StartupStatusReporterTest {
    private StartupStatusReporter reporter;
    private ScriptRunner scriptRunner;
    private DataSource dataSource;
    private NetworkInterfaceWrapper networkInterface;
    private ArgumentCaptor<String> tempFileName;
    private Clock clock;
    private static final long NOW = 1481648409409L; // Tue Dec 13 18:00:09 CET 2016
    
    @Before
    public void setUp() {
        scriptRunner = Mockito.mock(ScriptRunner.class);
        dataSource = Mockito.mock(DataSource.class);
        networkInterface = Mockito.mock(NetworkInterfaceWrapper.class);
        clock = Mockito.mock(Clock.class);
        Mockito.when(clock.getZone()).thenReturn(ZoneId.of("CET"));
        Mockito.when(clock.instant()).thenReturn(Instant.ofEpochMilli(NOW));

        reporter = new StartupStatusReporter("3.1.4", "mycommand", scriptRunner, networkInterface, clock);
        tempFileName = ArgumentCaptor.forClass(String.class);
    }
    
    @Test
    public void testSuccess() throws IOException, InterruptedException, DatabaseFailureException {
        when(dataSource.getVersion()).thenReturn("23");
        when(networkInterface.isUp()).thenReturn(true);
        reporter.consoleStarted();
        reporter.testDatabaseConnection(dataSource);
        reporter.testNetworkInterface();
        reporter.portConnected("HTTP", "http://192.168.1.2:3000/");
        reporter.portConnected("HTTPS", "https://192.168.1.2:3443/");
        reporter.startupCompleted();
        verify(scriptRunner).runScript(eq("mycommand"), tempFileName.capture());
        compareFileWithResource(tempFileName.getValue(), "test-data/startup-status/eblocker-status-success.html");
    }

    @Test
    public void databaseFailure() throws IOException, InterruptedException {
        when(dataSource.getVersion()).thenThrow(new JedisConnectionException("Could not get a resource from the pool", null));
        reporter.consoleStarted();
        try {
            reporter.testDatabaseConnection(dataSource);
        } catch (DatabaseFailureException e) {
            reporter.startupFailed(e);            
        }
        verify(scriptRunner).runScript(eq("mycommand"), tempFileName.capture());
        compareFileWithResource(tempFileName.getValue(), "test-data/startup-status/eblocker-status-database-failure.html");
    }
    
    @Test
    public void genericFailure() throws IOException, InterruptedException {
        reporter.consoleStarted();
        reporter.startupFailed(new Exception("Something went terribly wrong!"));
        verify(scriptRunner).runScript(eq("mycommand"), tempFileName.capture());
        compareFileWithResource(tempFileName.getValue(), "test-data/startup-status/eblocker-status-failure.html");
    }
    
    @Test
    public void networkDown() throws IOException, InterruptedException, DatabaseFailureException {
        when(dataSource.getVersion()).thenReturn("23");
        when(networkInterface.isUp()).thenReturn(false);
        reporter.consoleStarted();
        reporter.testDatabaseConnection(dataSource);
        reporter.testNetworkInterface();
        reporter.portConnected("HTTP", "http://169.254.94.109:3000/");
        reporter.startupCompleted();
        verify(scriptRunner).runScript(eq("mycommand"), tempFileName.capture());
        compareFileWithResource(tempFileName.getValue(), "test-data/startup-status/eblocker-status-network-down.html");
    }
    
    private void compareFileWithResource(String file, String resource) throws IOException {
        String result = FileUtils.readFileToString(new File(file));
        String expected = IOUtils.toString(ClassLoader.getSystemResource(resource));
        assertEquals(expected, result);
    }
}
