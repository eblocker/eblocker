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

import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.transaction.TransactionContext;
import org.eblocker.server.icap.filter.learning.SynchronousLearningFilter;
import org.eblocker.server.icap.filter.url.StringMatchType;
import org.eblocker.server.icap.filter.url.UrlFilterFactory;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class FilterStoreTest {

	@Test
	public void test() {
		FilterDomainContainer container = new SynchronousLearningFilter(true);
		FilterStore store = new FilterStore(container);
		store.update(Collections.singletonList(createUrlFilter(StringMatchType.CONTAINS, "to-be-removed")));

		FilterDomainContainer container2 = new SynchronousLearningFilter(true);
		FilterStore store2 = new FilterStore(container2);
        store2.update(Collections.singletonList(createUrlFilter(StringMatchType.CONTAINS, "to-be-removed")));

		assertEquals(store, store2);

        store.update(Collections.singletonList(createUrlFilter(StringMatchType.CONTAINS, "newly-added")));
		createFilters(store, "newly-added");

		assertNotEquals(store, store2);
	}

	@Test
    public void testRemovingLearnedFilter() {
        FilterDomainContainer container = new SynchronousLearningFilter(true);
        FilterStore store = new FilterStore(container);
        store.update(Collections.singletonList(createUrlFilter(StringMatchType.CONTAINS, "blah")));
        createFilters(store, "blah");

        TransactionContext context = new TestContext("http://etracker.com/blah", null, null);
        Assert.assertEquals(Decision.BLOCK, container.filter(context).getDecision());

        store.update(Collections.singletonList(createUrlFilter(StringMatchType.CONTAINS, "blub")));
        Assert.assertEquals(Decision.NO_DECISION, container.filter(context).getDecision());
    }

    @Test
    public void testUpdateLearnedFilter() {
	    Filter originalFilter = createUrlFilter(StringMatchType.CONTAINS, "blah");
        Filter updatedFilter = createUrlFilter(StringMatchType.CONTAINS, "blah");
        FilterDomainContainer container = new SynchronousLearningFilter(true);
        FilterStore store = new FilterStore(container);
        store.update(Collections.singletonList(originalFilter));
        createFilters(store, "blah");

        TransactionContext context = new TestContext("http://etracker.com/blah", null, null);
        FilterResult result = container.filter(context);
        Assert.assertEquals(Decision.BLOCK, result.getDecision());
        assertEquals(originalFilter, result.getDecider());

        store.update(Collections.singletonList(updatedFilter));
        result = container.filter(context);
        Assert.assertEquals(Decision.BLOCK, result.getDecision());
        assertEquals(updatedFilter, result.getDecider());
    }

	@SuppressWarnings("unused")
	private void createFilters(FilterStore store, String definition) {
		store.update(Collections.singletonList(createUrlFilter(StringMatchType.CONTAINS, definition)));
	}

    private Filter createUrlFilter(StringMatchType matchType, String matchString) {
        return UrlFilterFactory.getInstance()
            .setStringMatchType(matchType)
            .setMatchString(matchString)
            .setType(FilterType.BLOCK)
            .setDefinition("definition::"+matchString)
            .setDomain(null)
            .setPriority(FilterPriority.HIGH)
            .setRedirectParam("redirectUrl")
            .build();
	}

}
