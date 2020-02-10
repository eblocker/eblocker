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
package org.eblocker.server.common.page;

import org.eblocker.server.common.session.Session;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

public class PageContextStore {

	private final Cache<String, PageContext> store;

	@Inject
	public PageContextStore(@Named("pagecontext.cache.size") int size) {
		store = CacheBuilder.newBuilder().maximumSize(size).build();
	}

	public PageContext get(UUID id) {
		if (id == null) {
			return null;
		}

		PageContext pageContext = store.getIfPresent(id.toString());

		// From now on, we know the corresponding page context
		setLoggingContext(pageContext);

		return pageContext;
	}

	public PageContext get(String id) {
		if (id == null) {
			return null;
		}

		PageContext pageContext = store.getIfPresent(id);

		// From now on, we know the corresponding page context
		setLoggingContext(pageContext);

		return pageContext;
	}

	public PageContext create(PageContext parentContext, Session session, String url) {
		PageContext pageContext = session.createPageContext(parentContext, url);

		if(pageContext == null)
			return null;

		pageContext.reset();

		// From now on, we know the corresponding page context
		setLoggingContext(pageContext);

		store.put(pageContext.getId(), pageContext);
		return pageContext;
	}

	public PageContext find(Session session, String url) {
		PageContext pageContext = session.getPageContext(url);

		// From now on, we know the corresponding page context
		setLoggingContext(pageContext);

		return pageContext;
	}

	public Collection<PageContext> getContexts() {
		return new ArrayList<>(store.asMap().values());
	}

	private void setLoggingContext(PageContext pageContext) {
		// From now on, we know the corresponding page context
		MDC.put("PAGE", pageContext == null ? "--------" : pageContext.getShortId());
	}

}
