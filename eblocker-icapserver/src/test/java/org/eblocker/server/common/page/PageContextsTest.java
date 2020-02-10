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
package org.eblocker.server.common.page;

import static org.junit.Assert.*;

import org.eblocker.server.common.data.IpAddress;
import org.junit.Test;

public class PageContextsTest {
	private static final IpAddress IP = IpAddress.parse("192.168.3.3");

	@Test
	public void test() {
		// Create cache with 4 slots
		PageContexts pageContexts = new PageContexts(4);
		
		// Fill all four slots
		PageContext p1 = pageContexts.add(null, "page-1", IP);
		PageContext p2 = pageContexts.add(null, "page-2", IP);
		PageContext p3 = pageContexts.add(null, "page-3", IP);
		PageContext p4 = pageContexts.add(null, "page-4", IP);


		//try adding a null pointer, should not do anything, but return null
		PageContext errorContext = pageContexts.add(null, null, IP);
		assertNull(errorContext);

		
		// Verify that all four page contexts can be retrieved.
		assertEquals(p1, pageContexts.get("page-1"));
		assertEquals(p2, pageContexts.get("page-2"));
		assertEquals(p3, pageContexts.get("page-3"));
		assertEquals(p4, pageContexts.get("page-4"));

		// Double check that we have no trivial identity
		assertNotEquals(p2, pageContexts.get("page-1"));
		assertNotEquals(p3, pageContexts.get("page-2"));
		assertNotEquals(p4, pageContexts.get("page-3"));
		assertNotEquals(p1, pageContexts.get("page-4"));

		// Confirm that page-5 and page-6 are not yet available
		assertNull(pageContexts.get("page-5"));
		assertNull(pageContexts.get("page-6"));
		
		// Store page-5 and page-6
		PageContext p5 = pageContexts.add(null, "page-5", IP);
		PageContext p6 = pageContexts.add(null, "page-6", IP);
		
		// Assert that page-1 and page-2 are now lost and that page-5 and page-6 can be retrieved
		assertNull(pageContexts.get("page-1"));
		assertNull(pageContexts.get("page-2"));
		assertEquals(p3, pageContexts.get("page-3"));
		assertEquals(p4, pageContexts.get("page-4"));
		assertEquals(p5, pageContexts.get("page-5"));
		assertEquals(p6, pageContexts.get("page-6"));
		
		// Store page-5 and page-6 again
		PageContext p5b = pageContexts.add(null, "page-5", IP);
		PageContext p6b = pageContexts.add(null, "page-6", IP);
		
		// Assert that no new entries have been created
		assertEquals(p5, p5b);
		assertEquals(p6, p6b);
		
		// Assert that the cache is in general unchanged
		assertEquals(p3, pageContexts.get("page-3"));
		assertEquals(p4, pageContexts.get("page-4"));
		assertEquals(p5, pageContexts.get("page-5"));
		assertEquals(p6, pageContexts.get("page-6"));
		
	}

}
