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
package org.eblocker.server.common.network.unix;

//import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.HashSet;

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DhcpRange;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.network.ConfigurationTestBase;
import org.eblocker.server.common.network.DhcpServerConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import org.eblocker.server.common.system.ScriptRunner;

public class IscDhcpServerTest extends ConfigurationTestBase {
	private IscDhcpServer server;
	private ScriptRunner scriptRunner;
	private DhcpServerConfiguration configuration;

	@Before
	public void setUp() throws Exception {
		scriptRunner = Mockito.mock(ScriptRunner.class);
		
		server = new IscDhcpServer(scriptRunner, getOutFilePath(), "dhcpd-apply-config", "dhcpd-enable", "dhcpd-disable");

		configuration = new DhcpServerConfiguration();
		configuration.setIpAddress("192.168.0.2");
		configuration.setNetmask("255.255.255.0");
		configuration.setGateway("192.168.0.1");
		configuration.setRange(new DhcpRange("192.168.0.100", "192.168.0.200"));
		configuration.setDevices(new HashSet<Device>());
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void setConfiguration() throws Exception {
		server.setConfiguration(configuration);
		compareOutFileWith("test-data/dhcpd-extended-minimum.conf");
		verify(scriptRunner).runScript("dhcpd-apply-config");
	}

    @Test
    public void enableServerStart() throws Exception {
        when(scriptRunner.runScript("dhcpd-enable", "start")).thenReturn(0);
        server.enable(true);
        verify(scriptRunner).runScript("dhcpd-enable", "start");
    }

    @Test
    public void enableServer() throws Exception {
        when(scriptRunner.runScript("dhcpd-enable")).thenReturn(0);
        server.enable(false);
        verify(scriptRunner).runScript("dhcpd-enable");
    }

    @Test(expected= EblockerException.class)
    public void enableFailureStart() throws Exception {
        when(scriptRunner.runScript("dhcpd-enable", "start")).thenReturn(1);        
        server.enable(true);
    }

    @Test(expected= EblockerException.class)
    public void enableFailure() throws Exception {
        when(scriptRunner.runScript("dhcpd-enable")).thenReturn(1);        
        server.enable(false);
    }

	@Test
	public void disableServer() throws Exception {
		when(scriptRunner.runScript("dhcpd-disable")).thenReturn(0);
		server.disable();
		verify(scriptRunner).runScript("dhcpd-disable");
	}

	@Test(expected= EblockerException.class)
	public void disableFailure() throws Exception {
		when(scriptRunner.runScript("dhcpd-disable")).thenReturn(1);		
		server.disable();
	}
}
