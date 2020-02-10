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

import org.junit.Test;

import static org.junit.Assert.*;

public class SSLWhitelistUrlTest {

	@Test
	public void domainExtraction() {

		// Correct case
		String domain = "bla.de";
		String domainExpected = "bla.de";
		String name = "name";
		SSLWhitelistUrl url = new SSLWhitelistUrl(name, domain);
		assertEquals(domainExpected, url.getUrl());

		// Catched by bool SSLWhitelistService.isInvalidDomain()
		domain = ".";
		domainExpected = ".";
		name = "name";
		url = new SSLWhitelistUrl(name, domain);
		assertEquals(domainExpected, url.getUrl());

		// Catched by bool SSLWhitelistService.isInvalidDomain()
		domain = "de";
		domainExpected = "de";
		name = "name";
		url = new SSLWhitelistUrl(name, domain);
		assertEquals(domainExpected, url.getUrl());

		// Correct case
		domain = "http://www.bla.de";
		domainExpected = "www.bla.de";
		name = "name";
		url = new SSLWhitelistUrl(name, domain);
		assertEquals(domainExpected, url.getUrl());

		domain = "http://www.bla.de/";
		domainExpected = "www.bla.de";
		name = "name";
		url = new SSLWhitelistUrl(name, domain);
		assertEquals(domainExpected, url.getUrl());

		domain = "bla.de/blubb";
		domainExpected = "bla.de";
		name = "name";
		url = new SSLWhitelistUrl(name, domain);
		assertEquals(domainExpected, url.getUrl());

		domain = "http://www.bla.de/blubb.html#fasel";
		domainExpected = "www.bla.de";
		name = "name";
		url = new SSLWhitelistUrl(name, domain);
		assertEquals(domainExpected, url.getUrl());

		domain = "http://user:pass@bla.de/blubb";
		domainExpected = "bla.de";
		name = "name";
		url = new SSLWhitelistUrl(name, domain);
		assertEquals(domainExpected, url.getUrl());

		domain = "https://user:pass@bla.de/blubb.php?param1=foo&param2=bar";
		domainExpected = "bla.de";
		name = "name";
		url = new SSLWhitelistUrl(name, domain);
		assertEquals(domainExpected, url.getUrl());
	}
}
