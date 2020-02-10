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
package org.eblocker.server.icap.filter;

import static org.junit.Assert.*;

import org.junit.Test;

import org.eblocker.server.common.transaction.Decision;

public class FilterTest {

	@Test
	public void test() {
		Filter filter = new TestFilter("ello");
		FilterResult result = filter.filter(new TestContext("Hello World"));
		assertEquals(Decision.BLOCK, result.getDecision());
		assertEquals(filter, result.getDecider());
		
		filter = new TestFilter("ello");
		result = filter.filter(new TestContext("Hallo Welt"));
		assertEquals(Decision.NO_DECISION, result.getDecision());
		assertEquals(filter, result.getDecider());
		
		filter = new TestFilter("allo", true);
		result = filter.filter(new TestContext("Hallo Welt"));
		assertEquals(Decision.PASS, result.getDecision());
		assertEquals(filter, result.getDecider());
		
		Filter clone = new TestFilter((TestFilter) filter);
		assertEquals(filter, clone);
		
		Filter other = new TestFilter("foobar", true);
		assertNotEquals(filter, other);
		
		assertNotEquals(filter, null);
		assertNotEquals(filter, new Object());
	}

	@Test
	public void testCompare() {
		Filter high1 = new TestFilter("high1", false, FilterPriority.HIGH);
		Filter high2 = new TestFilter("high2", false, FilterPriority.HIGH);
		Filter low1  = new TestFilter("low1",  false, FilterPriority.LOW);
		Filter low2  = new TestFilter("low2",  false, FilterPriority.LOW);
		
		//
		// From an ordering perspective, "high" prio comes before "low" prio.
		//
		assertTrue(high1.compareTo(low2) < 0);
		assertTrue(low1.compareTo(high2) > 0);
		
		assertTrue(high1.compareTo(high1) == 0);
	}
	
}
