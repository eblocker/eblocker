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
package org.eblocker.server.common.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NetworkConfigurationTest {
	NetworkConfiguration config1;
	NetworkConfiguration config2;

	@Before
	public void setUp() throws Exception {
		config1 = makeNetworkConfiguration();
		config2 = makeNetworkConfiguration();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testEquals() {
		assertEquals(config1, config1);
		assertEquals(config1, config2);
		assertEquals(config2, config1);
		assertEquals(config2, config2);
		
		config2.setIpAddress("192.168.0.22");
		assertNotEquals(config1, config2);
	}
	
	private NetworkConfiguration makeNetworkConfiguration() {
		NetworkConfiguration c = new NetworkConfiguration();
		c.setAutomatic(false);
		c.setIpAddress("192.168.0.23");
		c.setNetworkMask("255.255.255.0");
		c.setGateway("192.168.0.1");
		c.setNameServerPrimary("192.168.3.20");
		c.setDhcp(true);
		c.setDhcpRangeFirst("192.168.0.100");
		c.setDhcpRangeLast("192.168.0.199");
		return c;
	}
}
