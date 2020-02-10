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

import org.eblocker.server.common.network.ConfigurationTestBase;
import org.junit.Before;
import org.junit.Test;

public class NetworkInterfaceConfigurationTest extends ConfigurationTestBase {
	NetworkInterfaceConfiguration configuration;
	
	@Before
	public void setUp() {
		configuration = new NetworkInterfaceConfiguration(getOutFilePath(), "eth0", "169.254.94.109", "255.255.0.0", 
				                                          "169.254.7.53/32", "255.255.0.0");
	}

	@Test
	public void testEnableDhcp() throws Exception {
		configuration.enableDhcp();
		compareOutFileWith("test-data/interfaces-dhcp.conf");
	}

	@Test
	public void testEnableStatic() throws Exception {
		configuration.enableStatic("192.168.0.2", "255.255.255.0", "192.168.0.1");
		compareOutFileWith("test-data/interfaces-static.conf");
	}
}
