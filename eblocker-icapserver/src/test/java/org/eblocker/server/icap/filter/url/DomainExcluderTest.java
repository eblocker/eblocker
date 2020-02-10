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
package org.eblocker.server.icap.filter.url;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class DomainExcluderTest {

	@Test
	public void test() {
		List<String> empty = Collections.emptyList();
		List<String> domains = Arrays.asList(new String[]{"brightmammoth.com", "eblocker.com"});
		
		assertFalse(new DomainExcluder(empty, true).isExcluded("www.brightmammoth.com"));
		assertFalse(new DomainExcluder(empty, false).isExcluded("www.brightmammoth.com"));
		
		assertFalse(new DomainExcluder(domains, true).isExcluded("www.brightmammoth.com"));
		assertTrue(new DomainExcluder(domains, false).isExcluded("www.brightmammoth.com"));
		
		assertFalse(new DomainExcluder(domains, true).isExcluded("www.eblocker.com"));
		assertTrue(new DomainExcluder(domains, false).isExcluded("www.eblocker.com"));
		
		assertFalse(new DomainExcluder(domains, true).isExcluded("brightmammoth.com"));
		assertTrue(new DomainExcluder(domains, false).isExcluded("brightmammoth.com"));
		
		assertFalse(new DomainExcluder(domains, true).isExcluded("some.subdomain.brightmammoth.com"));
		assertTrue(new DomainExcluder(domains, false).isExcluded("some.subdomain.brightmammoth.com"));
		
		assertTrue(new DomainExcluder(domains, true).isExcluded("other.host.org"));
		assertFalse(new DomainExcluder(domains, false).isExcluded("other.host.org"));
		
		assertTrue(new DomainExcluder(domains, true).isExcluded(null));
		assertFalse(new DomainExcluder(domains, false).isExcluded(null));
		
		assertFalse(new DomainExcluder(empty, true).isExcluded(null));
		assertFalse(new DomainExcluder(empty, false).isExcluded(null));
		
	}

}
