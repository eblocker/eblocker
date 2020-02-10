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
package org.eblocker.server.http.service;

import org.eblocker.server.common.network.unix.DhcpDiscoveryService;
import org.eblocker.server.common.exceptions.DhcpDiscoveryException;
import org.eblocker.server.common.system.LoggingProcess;
import org.eblocker.server.common.system.ScriptRunner;
import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Set;

public class DhcpDiscoveryServiceTest {

    private static final String INTERFACE_NAME = "eth5";
    private static final String DISCOVERY_COMMAND = "test_discovery";
    private static final int TIMEOUT = 23;

    private DeviceService deviceService;
    private ScriptRunner scriptRunner;
    private DhcpDiscoveryService service;

    @Before
    public void setUp() {
        deviceService = Mockito.mock(DeviceService.class);
        scriptRunner = Mockito.mock(ScriptRunner.class);
        service = new DhcpDiscoveryService(INTERFACE_NAME, DISCOVERY_COMMAND, TIMEOUT, deviceService, scriptRunner);
    }

    @Test
    public void testDiscovery() throws DhcpDiscoveryException, IOException {
        LoggingProcess process = Mockito.mock(LoggingProcess.class);
        Mockito.when(process.pollStdout()).thenReturn(
            "...",
            "offer from 10.10.10.10: 10.10.10.101 / 255.255.255.0",
            "offer from 10.10.10.100: 10.10.10.234 / 255.255.252.0",
            "...",
            null
        );

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Mockito.when(scriptRunner.startScript(Mockito.eq(DISCOVERY_COMMAND), captor.capture())).thenReturn(process);

        Set<String> dhcpServers = service.getDhcpServers();

        // check arguments
        Assert.assertEquals(3, captor.getAllValues().size());
        Assert.assertTrue(captor.getAllValues().stream().anyMatch(arg -> arg.equals("-w " + TIMEOUT)));
        Assert.assertTrue(captor.getAllValues().stream().anyMatch(arg -> arg.matches("-h [0-9a-f]{12}")));
        Assert.assertEquals(INTERFACE_NAME, captor.getAllValues().get(2));

        Mockito.verify(deviceService).getDeviceById(Mockito.anyString());

        // check found dhcp servers
        Assert.assertEquals(2, dhcpServers.size());
        Assert.assertEquals(Sets.newHashSet("10.10.10.10", "10.10.10.100"), dhcpServers);
    }

    @Test(expected = DhcpDiscoveryException.class)
    public void testDiscoveryProcessException() throws DhcpDiscoveryException, IOException {
        Mockito.when(scriptRunner.startScript(Mockito.eq(DISCOVERY_COMMAND), Mockito.any())).thenThrow(IOException.class);

        service.getDhcpServers();
    }

    @Test(expected = DhcpDiscoveryException.class)
    public void testDiscoveryError() throws DhcpDiscoveryException, IOException, InterruptedException {
        LoggingProcess process = Mockito.mock(LoggingProcess.class);
        Mockito.when(process.waitFor()).thenReturn(1);
        Mockito.when(scriptRunner.startScript(Mockito.eq(DISCOVERY_COMMAND), Mockito.any())).thenReturn(process);

        service.getDhcpServers();
    }

}
