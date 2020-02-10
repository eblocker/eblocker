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

import org.eblocker.server.common.transaction.TransactionContext;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FilterListTest {
	private final static Logger log = LoggerFactory.getLogger(FilterListTest.class);

	private Filter highest_a = new TestFilter("aa", false, FilterPriority.HIGHEST);
	private Filter high_a = new TestFilter("a", false, FilterPriority.HIGH);
	private Filter high_b = new TestFilter("bb", false, FilterPriority.HIGH);
	private Filter medium_b = new TestFilter("b", false, FilterPriority.MEDIUM);
	private Filter medium_c = new TestFilter("cc", false, FilterPriority.MEDIUM);
	private Filter low_c = new TestFilter("c", false, FilterPriority.LOW);
	private Filter low_d = new TestFilter("dd", false, FilterPriority.LOW);
	private Filter lowest_d = new TestFilter("d", false, FilterPriority.LOWEST);

	private TransactionContext aa = new TestContext("aa");
	private TransactionContext bb = new TestContext("bb");
	private TransactionContext cc = new TestContext("cc");
	private TransactionContext dd = new TestContext("dd");
	private TransactionContext ax = new TestContext("ax");
	private TransactionContext bx = new TestContext("bx");
	private TransactionContext cx = new TestContext("cx");
	private TransactionContext dx = new TestContext("dx");
	private TransactionContext xx = new TestContext("xx");

	@Test
	public void test() {
		FilterList filters = createFilterList(highest_a, high_a, high_b, medium_b, medium_c, low_c, low_d, lowest_d);
		assertEquals(highest_a, filters.filter(aa).getDecider());
		assertEquals(high_b, filters.filter(bb).getDecider());
		assertEquals(medium_c, filters.filter(cc).getDecider());
		assertEquals(low_d, filters.filter(dd).getDecider());

		assertEquals(high_a, filters.filter(ax).getDecider());
		assertEquals(medium_b, filters.filter(bx).getDecider());
		assertEquals(low_c, filters.filter(cx).getDecider());
		assertEquals(lowest_d, filters.filter(dx).getDecider());

		filters = createFilterList(lowest_d, low_d, low_c, medium_c, medium_b, high_b, high_a, highest_a);
		assertEquals(highest_a, filters.filter(aa).getDecider());
		assertEquals(high_b, filters.filter(bb).getDecider());
		assertEquals(medium_c, filters.filter(cc).getDecider());
		assertEquals(low_d, filters.filter(dd).getDecider());

		assertEquals(high_a, filters.filter(ax).getDecider());
		assertEquals(medium_b, filters.filter(bx).getDecider());
		assertEquals(low_c, filters.filter(cx).getDecider());
		assertEquals(lowest_d, filters.filter(dx).getDecider());

		filters = createFilterList(lowest_d, low_c, medium_b,high_a);
		assertEquals(high_a, filters.filter(aa).getDecider());
		assertEquals(medium_b, filters.filter(bb).getDecider());
		assertEquals(low_c, filters.filter(cc).getDecider());
		assertEquals(lowest_d, filters.filter(dd).getDecider());

		assertEquals(high_a, filters.filter(ax).getDecider());
		assertEquals(medium_b, filters.filter(bx).getDecider());
		assertEquals(low_c, filters.filter(cx).getDecider());
		assertEquals(lowest_d, filters.filter(dx).getDecider());

		filters = createFilterList(low_d,  medium_c, high_b, highest_a);
		assertEquals(highest_a, filters.filter(aa).getDecider());
		assertEquals(high_b, filters.filter(bb).getDecider());
		assertEquals(medium_c, filters.filter(cc).getDecider());
		assertEquals(low_d, filters.filter(dd).getDecider());

		assertNull(filters.filter(ax).getDecider());
		assertNull(filters.filter(bx).getDecider());
		assertNull(filters.filter(cx).getDecider());
		assertNull(filters.filter(dx).getDecider());

	}

	@Test
	public void testNoDuplicates() {
		FilterList filters = createFilterList(highest_a, high_a, high_b, medium_b, medium_c, low_c, low_d, lowest_d);
		assertEquals(8, filters.size());

		filters = createFilterList(highest_a, high_a, high_b, medium_b, medium_c, low_c, low_d, lowest_d, highest_a, high_a, high_b, medium_b, medium_c, low_c, low_d, lowest_d);
		assertEquals(8, filters.size());

	}

	@Test
	public void testMatchesCount() {
		FilterList filters = createFilterList(highest_a, high_a, high_b, medium_b, medium_c, low_c, low_d, lowest_d);
		assertEquals(highest_a, filters.filter(aa).getDecider());
		assertEquals(high_b, filters.filter(bb).getDecider());
		assertEquals(medium_c, filters.filter(cc).getDecider());
		assertEquals(low_d, filters.filter(dd).getDecider());

		assertNull(filters.filter(xx).getDecider());

		assertEquals(4, filters.getMatches());
		log.info("Filter List: {}", filters);
	}

	@Test
    public void testResolveReferences() {
	    FilterStore store = Mockito.mock(FilterStore.class);
	    Mockito.when(store.get("aa")).thenReturn(highest_a);
        Mockito.when(store.get("a")).thenReturn(high_a);

	    FilterList filters = createFilterList(FilterWrapper.wrap(highest_a), FilterWrapper.wrap(high_a));

	    filters.resolveReferences(store);

        assertEquals(highest_a, filters.filter(aa).getDecider());
        assertEquals(high_a, filters.filter(ax).getDecider());
    }

    @Test
    public void testResolveReferencesRemoveOrphaned() {
        FilterStore store = Mockito.mock(FilterStore.class);
        Mockito.when(store.get("aa")).thenReturn(highest_a);

        FilterList filters = createFilterList(FilterWrapper.wrap(highest_a), FilterWrapper.wrap(high_a));

        Assert.assertTrue(filters.resolveReferences(store));

        assertEquals(highest_a, filters.filter(aa).getDecider());
        assertEquals(FilterResult.NO_DECISION, filters.filter(ax));
    }

    @Test
    public void testResolveReferencesRemoveAllOrphaned() {
        FilterStore store = Mockito.mock(FilterStore.class);

        FilterList filters = createFilterList(FilterWrapper.wrap(highest_a), FilterWrapper.wrap(high_a));

        Assert.assertFalse(filters.resolveReferences(store));

        assertEquals(FilterResult.NO_DECISION, filters.filter(aa));
        assertEquals(FilterResult.NO_DECISION, filters.filter(ax));
    }

	private FilterList createFilterList(Filter...filters) {
		FilterList filterList = new FilterList();
		for (Filter filter: filters) {
			filterList.add(filter);
		}
		return filterList;
	}

}
