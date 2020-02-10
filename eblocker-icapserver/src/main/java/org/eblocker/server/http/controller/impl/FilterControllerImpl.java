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
package org.eblocker.server.http.controller.impl;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eblocker.server.http.controller.FilterController;
import org.eblocker.server.common.page.PageContext;
import org.eblocker.server.common.page.PageContextStore;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.session.SessionStore;
import org.restexpress.Request;
import org.restexpress.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eblocker.server.http.server.SessionContextController;
import com.google.inject.Inject;

/**
 * Provides access to filter stats and configuration
 */
public class FilterControllerImpl extends SessionContextController implements FilterController {
    @SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(FilterControllerImpl.class);
	
	@Inject
	public FilterControllerImpl(SessionStore sessionStore, PageContextStore pageContextStore) {
		super(sessionStore, pageContextStore);
	}

	/**
	 * Returns the total number of blocked URLs for the badge at the eBlocker icon.
	 * @param request
	 * @param response
	 * @return
	 * @throws IOException
	 */
	@Override
	public Object getBadge(Request request, Response response) throws IOException {
		PageContext pageContext = getPageContext(request);
		int totalBlocked = 0;
		if (pageContext != null) {
			totalBlocked = pageContext.getBlockedAds() + pageContext.getBlockedTrackings();
		}
		response.addHeader("Cache-Control", "private, no-cache, no-store");
		response.addHeader("Access-Control-Allow-Origin", "*");
		return Collections.singletonMap("badge", totalBlocked);
	}

	@Override
	public Object getStats(Request request, Response response) {
		Map<String, Integer> result = new HashMap<String, Integer>();

		Session session = getSession(request);
		//log.info("Session (filter-controller): "+session.getSessionId()+" user-agent: "+session.getUserAgent()+" outgoing-user-agent: "+session.getOutgoingUserAgent());
		result.put("adsBlocked", session.getBlockedAds());
		result.put("trackingsBlocked", session.getBlockedTrackings());

		PageContext pageContext = getPageContext(request);
		result.put("adsBlockedOnPage", pageContext == null ? 0 : pageContext.getBlockedAds());
		result.put("trackingsBlockedOnPage", pageContext == null ? 0 : pageContext.getBlockedTrackings());
		return result;
	}
	
	@Override
	public Object getBlockedAdsSet(Request request, Response response){
		PageContext pageContext = getPageContext(request);
		//log.info("blocked ads on page:"+pageContext.getBlockedAds());
		if(pageContext == null)
			return null;
		return pageContext.getBlockedAdsSet();
	}
	
	@Override
	public Object getBlockedTrackingsSet(Request request, Response response){
		PageContext pageContext = getPageContext(request);
		if(pageContext == null)
			return null;
		return pageContext.getBlockedTrackingsSet();
	}
	
	@Override
	public Object getConfig(Request request, Response response) {
		Map<String, Boolean> result = new HashMap<String, Boolean>();
		result.put("blockAds", getSession(request).isBlockAds());
		result.put("blockTrackings", getSession(request).isBlockTrackings());
		return result;
	}
	
	@Override
	public Object putConfig(Request request, Response response) {
		@SuppressWarnings("unchecked")
		Map<String, Boolean> config = request.getBodyAs(Map.class);
		getSession(request).setBlockAds(config.get("blockAds"));
		getSession(request).setBlockTrackings(config.get("blockTrackings"));
		return config;
	}
}
