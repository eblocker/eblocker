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
package org.eblocker.server.common.network;

import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.network.BaseURLs;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class BaseURLsTest {
	private NetworkInterfaceWrapper networkInterface;

	@Before
	public void setUp() throws Exception {
		networkInterface = Mockito.mock(NetworkInterfaceWrapper.class);
		when(networkInterface.getFirstIPv4Address()).thenReturn(Ip4Address.parse("192.168.1.2"));
		when(networkInterface.getVpnIpv4Address()).thenReturn(Ip4Address.parse("10.8.0.1"));
	}

	@Test
	public void test() {
        BaseURLs baseURLs = new BaseURLs(networkInterface, 3000, 3443, "http://setup.eblocker.com/", "controlbar.eblocker.com");
		assertEquals("http://192.168.1.2:3000", baseURLs.getHttpURL());
		assertEquals("https://192.168.1.2:3443", baseURLs.getHttpsURL());

        assertEquals("http://controlbar.eblocker.com:3000", baseURLs.selectURLForPage("http://www.example.com/path"));
        assertEquals("https://controlbar.eblocker.com:3443", baseURLs.selectURLForPage("https://www.example.com/path"));

		assertTrue(baseURLs.matchesAny("http://192.168.1.2:3000/a/b/c"));
		assertTrue(baseURLs.matchesAny("https://192.168.1.2:3443/c/d/e"));
		assertFalse(baseURLs.matchesAny("http://192.168.1.2:3443/foo"));
		assertFalse(baseURLs.matchesAny("http://some.other.host/bla"));
	}

	@Test
	public void testServerMode() {
        BaseURLs baseURLs = new BaseURLs(networkInterface, 3000, 3443, "http://setup.eblocker.com/", "controlbar.eblocker.com");

        assertEquals("http://controlbar.eblocker.com:3000", baseURLs.selectURLForPage("http://www.example.com/path"));
        assertEquals("https://controlbar.eblocker.com:3443", baseURLs.selectURLForPage("https://www.example.com/path"));
	}

	@Test
	public void testIpBaseUrls() {
	    BaseURLs baseURLs = new BaseURLs(networkInterface, 3000, 3443, "http://setup.eblocker.com/", "controlbar.eblocker.com");
        assertEquals("https://192.168.1.2:3443", baseURLs.selectIpForPage(false, "https"));

        baseURLs = new BaseURLs(networkInterface, 3000, 3443, "http://setup.eblocker.com/", "controlbar.eblocker.com");
        assertEquals("http://192.168.1.2:3000", baseURLs.selectIpForPage(false, "http"));

        baseURLs = new BaseURLs(networkInterface, 3000, 3443, "http://setup.eblocker.com/", "controlbar.eblocker.com");
        assertEquals("https://10.8.0.1:3443", baseURLs.selectIpForPage(true, "https"));

        baseURLs = new BaseURLs(networkInterface, 3000, 3443, "http://setup.eblocker.com/", "controlbar.eblocker.com");
        assertEquals("http://10.8.0.1:3000", baseURLs.selectIpForPage(true, "http"));
	}
}
