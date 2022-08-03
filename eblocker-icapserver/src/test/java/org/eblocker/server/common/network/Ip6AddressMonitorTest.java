/*
 * Copyright 2022 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.network;

import org.eblocker.server.common.system.LoggingProcess;
import org.eblocker.server.common.system.ScriptRunner;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;

import static org.junit.Assert.*;

public class Ip6AddressMonitorTest {
    private Ip6AddressMonitor monitor;
    private ScriptRunner scriptRunner;
    private NetworkInterfaceWrapper networkInterface;
    private LoggingProcess process;

    @Before
    public void setUp() throws Exception {
        scriptRunner = Mockito.mock(ScriptRunner.class);
        networkInterface = Mockito.mock(NetworkInterfaceWrapper.class);
        process = Mockito.mock(LoggingProcess.class);

        Mockito.when(networkInterface.getInterfaceName()).thenReturn("eth0");
        Mockito.when(scriptRunner.startScript("ip-monitor", "eth0")).thenReturn(process);

        monitor = new Ip6AddressMonitor("ip-monitor", "kill-process", scriptRunner, networkInterface);
    }

    @Test
    public void testNotify() throws Exception {
        Mockito.when(process.takeStdout())
                .thenReturn("[2022-07-26T08:08:33.619958] Deleted 2: eth0    inet6 2001:47:11:1:41e2:2759:b8e8:8c94/128 scope global")
                .thenReturn(null);
        monitor.run();
        Mockito.verify(networkInterface).notifyIp6AddressChanged();
    }

    @Test
    public void testDontNotify() throws Exception {
        Mockito.when(process.takeStdout())
                .thenReturn("       valid_lft 86400sec preferred_lft 14400sec")
                .thenReturn(null);
        monitor.run();
        Mockito.verify(networkInterface, Mockito.never()).notifyIp6AddressChanged();
    }
}
