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
package org.eblocker.server.icap.filter.learning;

import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.transaction.TransactionContext;
import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.FilterPriority;
import org.eblocker.server.icap.filter.FilterResult;
import org.eblocker.server.icap.filter.TestContext;
import org.eblocker.server.icap.filter.TestFilter;
import org.junit.Assert;
import org.junit.Test;

import javax.xml.ws.Holder;
import java.util.ConcurrentModificationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class AsynchronousLearningFilterTest {

	private Filter highest_a = new TestFilter("aa", false, FilterPriority.HIGHEST);
	private Filter high_a = new TestFilter("a", false, FilterPriority.HIGH);
	private Filter high_b = new TestFilter("bb", false, FilterPriority.HIGH);
	private Filter medium_b = new TestFilter("b", false, FilterPriority.MEDIUM);

	private String domain = "yyy.zzz";
	private TransactionContext aa = new TestContext("http://xxx.yyy.zzz/aa");
	private TransactionContext bb = new TestContext("http://xxx.yyy.zzz/bb");
	private TransactionContext kk = new TestContext("http://xxx.yyy.zzz/kk");

	@Test
	public void test() {
		AsynchronousLearningFilter learningFilter = new AsynchronousLearningFilter(true);

		learningFilter.add(null, highest_a);
		learningFilter.add(null, high_a);
		learningFilter.add(null, high_b);
		learningFilter.add(null, medium_b);

		//
		// Filter list for hostname should be empty.
		// Filter result is "NO_DECISION" by default.
		//
		assertEquals(0, learningFilter.get(domain).size());
		assertEquals(Decision.NO_DECISION, learningFilter.get(domain).filter(aa).getDecision());
		assertEquals(Decision.NO_DECISION, learningFilter.get(domain).filter(bb).getDecision());
		assertEquals(Decision.NO_DECISION, learningFilter.get(domain).filter(kk).getDecision());

		//
		// Add transaction to learning queue and process queue
		//
		learningFilter.filter(aa);
		learningFilter.run();

		//
		// Filter list should now have one entry.
		// Matching request should now be blocked.
		//
		assertEquals(1, learningFilter.get(domain).size());
		assertEquals(Decision.BLOCK, learningFilter.get(domain).filter(aa).getDecision());
		assertEquals(Decision.NO_DECISION, learningFilter.get(domain).filter(bb).getDecision());
		assertEquals(Decision.NO_DECISION, learningFilter.get(domain).filter(kk).getDecision());

		//
		// Add transaction to queue and process queue
		//
		learningFilter.filter(bb);
		learningFilter.run();

		//
		// Filter list should now have two entries .
		// Matching request should now be blocked.
		//
		assertEquals(2, learningFilter.get(domain).size());
		assertEquals(Decision.BLOCK, learningFilter.get(domain).filter(aa).getDecision());
		assertEquals(Decision.BLOCK, learningFilter.get(domain).filter(bb).getDecision());
		assertEquals(Decision.NO_DECISION, learningFilter.get(domain).filter(kk).getDecision());

	}

	@Test
	public void testConcurrentModificationException() throws InterruptedException {
		Holder<Integer> counter = new Holder<>(0);
		AsynchronousLearningFilter learningFilter = new AsynchronousLearningFilter(true) {
			@Override
			public FilterResult learn(FilterResult result, TransactionContext context) {
				++counter.value;
				throw new ConcurrentModificationException("fake exception");
			}
		};

		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		executor.scheduleAtFixedRate(() -> learningFilter.filter(aa), 0, 1000, TimeUnit.MILLISECONDS);
		executor.scheduleAtFixedRate(learningFilter,  500,1000, TimeUnit.MILLISECONDS);

		Thread.sleep(3000);
		Assert.assertTrue("update has not run after first exception", counter.value > 1);

		executor.shutdown();
		executor.awaitTermination(1, TimeUnit.SECONDS);
	}

}
