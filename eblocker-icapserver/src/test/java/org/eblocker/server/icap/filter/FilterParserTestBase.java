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
import org.eblocker.server.icap.filter.easylist.EasyListLineParser;
import org.eblocker.server.icap.filter.learning.LearningFilter;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

abstract public class FilterParserTestBase {
	@SuppressWarnings("unused")
	private final static Logger log = LoggerFactory.getLogger(FilterParserTestBase.class);

	private LearningFilter learningFilter;

	@Before
	public void load() throws IOException {
		learningFilter = new LearningFilter(null, true) {
			@Override
			protected FilterResult doLearn(FilterResult result,	TransactionContext context) {
				return result;
			}

            @Override
            public String toString() {
                return "123";
            }
        };

		FilterParser parser = new FilterParser(EasyListLineParser::new);

        List<Filter> filters = new ArrayList<>();
		for (InputStream in: getInputStreams()) {
		    filters.addAll(parser.parse(in));
		}

        FilterStore store = new FilterStore(learningFilter);
		store.update(filters);
	}

	abstract protected InputStream[] getInputStreams();

	protected void assertFilterResult_beforeLearning(Decision decision, String definition, String url, String referrer) {
		TransactionContext context = new TestContext(url, referrer, null);

		FilterResult result = learningFilter.filter(context);

		assertEquals(decision, result.getDecision());
		if (definition != null) {
			assertEquals(definition, result.getDecider().getDefinition());
		}
	}

	protected void assertFilterResult_afterLearning(Decision decision, String definition, String url, String referrer) {
		TransactionContext context = new TestContext(url, referrer, null);

		learningFilter.learn(null, context);
		FilterResult result = learningFilter.filter(context);

		assertEquals(decision, result.getDecision());
		if (definition != null) {
			assertEquals(definition, result.getDecider().getDefinition());
		}
	}
}
