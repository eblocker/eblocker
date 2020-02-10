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
package org.eblocker.server.icap.filter.json;

import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.FilterPriority;
import org.eblocker.server.icap.filter.FilterStore;
import org.eblocker.server.icap.filter.FilterType;
import org.eblocker.server.icap.filter.learning.SynchronousLearningFilter;
import org.eblocker.server.icap.filter.url.StringMatchType;
import org.eblocker.server.icap.filter.url.UrlFilterFactory;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class JSONMarshallerTest {
	private static final Logger LOG = LoggerFactory.getLogger(JSONMarshallerTest.class);

	@Test
	public void test() {
		FilterStore store = createFilterStore();
		LOG.debug("store: {}", store);

		String json = marshall(store);
		LOG.debug("json: {}", json);

		FilterStore unmarshalled = unmarshall(json);
		LOG.debug("unmarshalled: {}", unmarshalled);

		assertEquals(store, unmarshalled);
	}

	private String marshall(FilterStore store) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		JSONMarshaller.marshall(store, out);
		return new String(out.toByteArray());
	}

	private FilterStore unmarshall(String json) {
		ByteArrayInputStream in = new ByteArrayInputStream(json.getBytes());
		return JSONMarshaller.unmarshall(in);
	}

	private FilterStore createFilterStore() {
		SynchronousLearningFilter learningFilter = new SynchronousLearningFilter(true);
		FilterStore store = new FilterStore(learningFilter);
		createLearningFilter(store);
		return store;
	}

	private void createLearningFilter(FilterStore store) {
	    List<Filter> filters = new ArrayList<>();
		filters.add(createUrlFilter(store, StringMatchType.REGEX, "hello.*world", null));
		filters.add(createUrlFilter(store, StringMatchType.REGEX, "foo.*bar", null));
		filters.add(createUrlFilter(store, StringMatchType.REGEX, "foo.*bar 2", null));
		filters.add(createUrlFilter(store, StringMatchType.REGEX, "hello.*world", "brightmammoth.com"));
		filters.add(createUrlFilter(store, StringMatchType.CONTAINS, "contains", "brightmammoth.com"));
		filters.add(createUrlFilter(store, StringMatchType.REGEX, "hello.*world", "etracker.com"));
        filters.add(createUrlFilter(store, StringMatchType.ENDSWITH, "endswith", "etracker.com"));

        store.update(filters);
	}

	private Filter createUrlFilter(FilterStore store, StringMatchType matchType, String matchString, String domain) {
		Filter filter = UrlFilterFactory.getInstance()
				.setStringMatchType(matchType)
				.setMatchString(matchString)
				.setType(FilterType.BLOCK)
				.setDefinition("definition::"+matchString)
				.setDomain(domain)
				.setPriority(FilterPriority.HIGH)
				.setRedirectParam("redirectUrl")
				.setReferrerDomainWhiteList(Arrays.asList("white1.com", "white2.com"))
				.setReferrerDomainBlackList(Arrays.asList("black1.com", "black2.com"))
				.build();
		return filter;
	}
}
