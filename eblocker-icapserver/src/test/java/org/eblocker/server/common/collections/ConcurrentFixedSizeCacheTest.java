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
package org.eblocker.server.common.collections;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ConcurrentFixedSizeCacheTest {

	@Test
	public void test() {
		Map<String,String> map = new ConcurrentFixedSizeCache<String, String>(5);
		
		map.put("key-1", "value-1");
		map.put("key-2", "value-2");
		map.put("key-3", "value-3");
		map.put("key-4", "value-4");
		map.put("key-5", "value-5");
		
		assertEquals("value-1", map.get("key-1"));
		assertEquals("value-2", map.get("key-2"));
		assertEquals("value-3", map.get("key-3"));
		assertEquals("value-4", map.get("key-4"));
		assertEquals("value-5", map.get("key-5"));
		
		assertNull(map.get("key-6"));
		assertNull(map.get("key-7"));
		
		map.put("key-6", "value-6");
		map.put("key-7", "value-7");
		
		assertNull(map.get("key-1"));
		assertNull(map.get("key-2"));

		assertEquals("value-3", map.get("key-3"));
		assertEquals("value-4", map.get("key-4"));
		assertEquals("value-5", map.get("key-5"));
		assertEquals("value-6", map.get("key-6"));
		assertEquals("value-7", map.get("key-7"));
		
		map.put("key-6", "alter-6");
		map.put("key-7", "alter-7");
		
		assertEquals("value-3", map.get("key-3"));
		assertEquals("value-4", map.get("key-4"));
		assertEquals("value-5", map.get("key-5"));
		assertEquals("alter-6", map.get("key-6"));
		assertEquals("alter-7", map.get("key-7"));
		
	}

	@Test
	public void lruEviction() {
		Map<String,String> map = new ConcurrentFixedSizeCache<>(3, true);
		map.put("key-1", "value-1");
		map.put("key-2", "value-2");
		map.put("key-3", "value-3");

		// access keys in reversed order
		assertEquals("value-3", map.get("key-3"));
		assertEquals("value-2", map.get("key-2"));
		assertEquals("value-1", map.get("key-1"));

		// insert additonal element forcing eviction of least recently access key (key-3)
		map.put("key-4", "value-4");
		assertEquals(3, map.size());
		assertEquals("value-4", map.get("key-4"));
		assertEquals("value-2", map.get("key-2"));
		assertEquals("value-1", map.get("key-1"));
	}

}
