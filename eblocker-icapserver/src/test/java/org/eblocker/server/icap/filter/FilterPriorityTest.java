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

public class FilterPriorityTest {

	@Test
	public void test() {
		assertTrue(FilterPriority.HIGHEST.isHigher(FilterPriority.HIGH));
		assertFalse(FilterPriority.HIGH.isHigher(FilterPriority.HIGH));
		assertFalse(FilterPriority.LOW.isHigher(FilterPriority.HIGH));
		
		assertTrue(FilterPriority.HIGHEST.isHigherOrEqual(FilterPriority.HIGH));
		assertTrue(FilterPriority.HIGH.isHigherOrEqual(FilterPriority.HIGH));
		assertFalse(FilterPriority.LOW.isHigherOrEqual(FilterPriority.HIGH));
		
		assertFalse(FilterPriority.HIGHEST.isLower(FilterPriority.HIGH));
		assertFalse(FilterPriority.HIGH.isLower(FilterPriority.HIGH));
		assertTrue(FilterPriority.LOW.isLower(FilterPriority.HIGH));
		
		assertFalse(FilterPriority.HIGHEST.isLowerOrEqual(FilterPriority.HIGH));
		assertTrue(FilterPriority.HIGH.isLowerOrEqual(FilterPriority.HIGH));
		assertTrue(FilterPriority.LOW.isLowerOrEqual(FilterPriority.HIGH));
		
	}

}
