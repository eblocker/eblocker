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

public class PageContextTest {
	private static final IpAddress IP = IpAddress.parse("192.168.4.4");

	@Test
	public void test() {
		PageContext p1 = new PageContext(null, "foo", IP);
		PageContext p2 = new PageContext(null, "bar", IP);
		assertNotNull(p1.getId());
		assertNotNull(p2.getId());
		assertNotEquals(p1.getId(), p2.getId());
		assertNotEquals(p1, p2);
		assertEquals(0, p1.getBlockedAds());
		assertEquals(0, p1.getBlockedTrackings());
		assertEquals(0, p2.getBlockedAds());
		assertEquals(0, p2.getBlockedTrackings());
		
		for (int i = 0; i < 10; i++) {
			p1.incrementBlockedAds("a"+i);
			p1.incrementBlockedTrackings("a"+i);
			p2.incrementBlockedAds("a"+i);
			p2.incrementBlockedTrackings("a"+i);
		}
		for (int i = 0; i < 10; i++) {
			p1.incrementBlockedAds("b"+i);
			p1.incrementBlockedTrackings("b"+i);
			p2.incrementBlockedAds("b"+i);
		}
		for (int i = 0; i < 10; i++) {
			p1.incrementBlockedAds("c"+i);
			p1.incrementBlockedTrackings("c"+i);
		}
		for (int i = 0; i < 10; i++) {
			p1.incrementBlockedAds("a"+i);
		}
		assertEquals(10, p2.getBlockedTrackings());
		assertEquals(20, p2.getBlockedAds());
		assertEquals(30, p1.getBlockedTrackings());
		assertEquals(30, p1.getBlockedAds());
	}

}
