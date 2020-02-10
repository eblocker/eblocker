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
import org.eblocker.server.icap.filter.learning.SynchronousLearningFilter;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FilterStoreConcurrentModificationTest {

	private static final Logger LOG = LoggerFactory.getLogger(FilterStoreConcurrentModificationTest.class);

	@Test(timeout = 5000)
	public void test() throws InterruptedException, TimeoutException, ExecutionException {
		FilterDomainContainer container = new SynchronousLearningFilter(true);
		FilterStore store = new FilterStore(container);

		//
		// Add one filter to the store
		//
        store.update(Collections.singletonList(createBrokenFilter("to-be-removed")));

		//
		// Next update cycle - add another filter. The first one should be removed.
		//
		try {
            store.update(Collections.singletonList(createBrokenFilter("new-filter")));
		} catch (ConcurrentModificationException e) {
			// Ignore the exception!
			// In real life this would be in a different thread
			// and also effectively be ignored.
		}

		//
		// Try again to update the filter store
		// With the concurrency bug (EB1-679), this call would never return
        // Underlying bug should be fixed in (EB1-738) but we just ensure ConcurrentModificationExceptions
        // won't break update threads.
		//
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<?> future = executor.submit(() -> store.update(Collections.emptyList()));

		//
		// Make sure the executor has finished its task.
		//
		future.get(2000, TimeUnit.SECONDS);
	}

	/*
	 * The purpose of the broken filter is to simulate a ConcurrentModificationException, when looping over
	 * all filters to remove the filter from the list.
	 */
	private Filter createBrokenFilter(String definition) {
		Filter filter = new AbstractFilter(FilterPriority.DEFAULT, definition) {

			@Override
			protected FilterResult doFilter(TransactionContext context) {
				return null;
			}

			@Override
			public int hashCode() {
				LOG.error("###");
				throw new ConcurrentModificationException("fake exception");
			}

			@Override
			public boolean equals(Object o) {
				LOG.error("###");
				throw new ConcurrentModificationException("fake exception");
			}
		};
		return filter;
	}

}
