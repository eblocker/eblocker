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
import org.eblocker.server.icap.filter.FilterStore;
import org.eblocker.server.icap.filter.TestContext;
import org.eblocker.server.icap.filter.TestFilter;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;

public class LearningFilterTest {

	private Filter highest_a = new TestFilter("aa", false, FilterPriority.HIGHEST);
	private Filter high_a = new TestFilter("a", false, FilterPriority.HIGH);
	private Filter high_b = new TestFilter("bb", false, FilterPriority.HIGH);
	private Filter medium_b = new TestFilter("b", false, FilterPriority.MEDIUM);


	private TransactionContext aa = new TestContext("http://xxx.yyy.zzz/aa");
	private TransactionContext bb = new TestContext("http://xxx.yyy.zzz/bb");
	private TransactionContext kk = new TestContext("http://xxx.yyy.zzz/kk");

	private TransactionContext aa2 = new TestContext("http://lll.mmm.nnn/aa");
	private TransactionContext bb2 = new TestContext("http://lll.mmm.nnn/bb");
	private TransactionContext kk2 = new TestContext("http://lll.mmm.nnn/kk");

	@Test
	public void test() {
		LearningFilter learningFilter = new TestLearningFilter();

        learningFilter.add(null, highest_a);
		learningFilter.add(null, high_a);
		learningFilter.add(null, high_b);
		learningFilter.add(null, medium_b);

		//
		// Filter list for hostname should contain only the default PASS-BY-DEFAULT filter.
		// Filter result is "PASS" by default.
		//
		assertEquals(Decision.NO_DECISION, learningFilter.filter(aa).getDecision());
		assertEquals(Decision.NO_DECISION, learningFilter.filter(kk).getDecision());

		//
		// Process queue --> Filter for "aa" should be added to map; filter for "bb" should still be missing!
		//
		learningFilter.learn(null, aa);
		learningFilter.learn(null, kk);

		//
		// Matching request should now be blocked.
		//
		assertEquals(Decision.BLOCK, learningFilter.filter(aa).getDecision());
		assertEquals(Decision.NO_DECISION, learningFilter.filter(bb).getDecision());
		assertEquals(Decision.NO_DECISION, learningFilter.filter(kk).getDecision());

		//
		// Process queue --> Filter for "bb" should now also be added
		//
		learningFilter.learn(null, bb);

		//
		// Matching request should now be blocked.
		//
		assertEquals(Decision.BLOCK, learningFilter.filter(aa).getDecision());
		assertEquals(Decision.BLOCK, learningFilter.filter(bb).getDecision());
		assertEquals(Decision.NO_DECISION, learningFilter.filter(kk).getDecision());

		//
		// Filter for other hostname should still be unknown
		//
		assertEquals(Decision.NO_DECISION, learningFilter.filter(aa2).getDecision());
		assertEquals(Decision.NO_DECISION, learningFilter.filter(bb2).getDecision());
		assertEquals(Decision.NO_DECISION, learningFilter.filter(kk2).getDecision());

		//
		// Process queue --> Filter for other hostname should now also be added
		//
		learningFilter.learn(null, aa2);
		learningFilter.learn(null, bb2);
		learningFilter.learn(null, kk2);

		//
		// Filter for other hostname should still be unknown
		//
		assertEquals(Decision.BLOCK, learningFilter.filter(aa2).getDecision());
		assertEquals(Decision.BLOCK, learningFilter.filter(bb2).getDecision());
		assertEquals(Decision.NO_DECISION, learningFilter.filter(kk2).getDecision());
	}

    @Test
    public void testLearnResultWithHigherPriority() {
        Filter mockFilter = createMockFilter(FilterPriority.HIGHEST);
        LearningFilter learningFilter = new TestLearningFilter();
        learningFilter.add(high_a);
        FilterResult learnResult = learningFilter.learn(FilterResult.pass(mockFilter), aa);
        assertEquals(Decision.PASS, learnResult.getDecision());
        assertEquals(mockFilter, learnResult.getDecider());
    }


    @Test
    public void testLearnResultWithLowerPriority() {
        Filter mockFilter = createMockFilter(FilterPriority.DEFAULT);
        LearningFilter learningFilter = new TestLearningFilter();
        learningFilter.add(high_a);
        FilterResult learnResult = learningFilter.learn(FilterResult.pass(mockFilter), aa);
        assertEquals(Decision.BLOCK, learnResult.getDecision());
        assertEquals(high_a, learnResult.getDecider());
    }

    @Test
    public void testLearnResultWithSamePriority() {
        Filter mockFilter = createMockFilter(FilterPriority.HIGH);
        LearningFilter learningFilter = new TestLearningFilter();
        learningFilter.add(high_a);
        FilterResult learnResult = learningFilter.learn(FilterResult.pass(mockFilter), aa);
        assertEquals(Decision.BLOCK, learnResult.getDecision());
        assertEquals(high_a, learnResult.getDecider());
    }

    @Test
    public void testResolveReferences() {
	    TransactionContext transactionContext = Mockito.mock(TransactionContext.class);
        Filter filter = createMockFilter(FilterPriority.HIGH);
        Mockito.when(filter.getDefinition()).thenReturn("definition");
        Mockito.when(filter.filter(transactionContext)).thenReturn(FilterResult.noDecision(filter));
        LearningFilter learningFilter = new TestLearningFilter();
        learningFilter.add("domain.com", filter);

        Assert.assertNotNull(learningFilter.get("domain.com", false));
        learningFilter.get("domain.com", false).doFilter(transactionContext);
        Mockito.verify(filter).filter(transactionContext);

        Filter filter2 = createMockFilter(FilterPriority.HIGH);
        Mockito.when(filter2.getDefinition()).thenReturn("definition");
        Mockito.when(filter2.filter(transactionContext)).thenReturn(FilterResult.noDecision(filter2));
        FilterStore store = Mockito.mock(FilterStore.class);
        Mockito.when(store.get("definition")).thenReturn(filter2);
        learningFilter.resolveReferences(store);

        Assert.assertNotNull(learningFilter.get("domain.com", false));
        learningFilter.get("domain.com", false).doFilter(transactionContext);
        Mockito.verify(filter2).filter(transactionContext);

        Mockito.reset(store);
        learningFilter.resolveReferences(store);
        Assert.assertNull(learningFilter.get("domain.com", false));
    }

    private class TestLearningFilter extends LearningFilter {
        public TestLearningFilter() {
            super("<<TEST-LEARNING-FILTER>>", true);
        }

        @Override
        protected FilterResult doLearn(FilterResult result, TransactionContext context) {
            return result;
        }
    }

    private Filter createMockFilter(FilterPriority priority) {
        Filter filter = Mockito.mock(Filter.class);
        Mockito.when(filter.getPriority()).thenReturn(priority);
        return filter;
    }
}
