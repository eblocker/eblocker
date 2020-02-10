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
package org.eblocker.server.http.server;

import org.eblocker.server.common.data.IpAddress;
import com.google.common.net.HttpHeaders;

import org.eblocker.server.common.page.PageContext;
import org.eblocker.server.common.page.PageContextStore;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.common.transaction.TransactionIdentifier;
import org.restexpress.Request;

import org.eblocker.server.http.utils.ControllerUtils;

public class SessionContextController {

	private SessionStore sessionStore;
	private PageContextStore pageContextStore;

	public SessionContextController(SessionStore sessionStore, PageContextStore pageContextStore) {
		this.sessionStore = sessionStore;
		this.pageContextStore = pageContextStore;
	}

	protected Session getSession(Request request) {
		return sessionStore.getSession((TransactionIdentifier) request.getAttachment("transactionIdentifier"));
	}

	protected PageContext getPageContext(Request request) {
		return getPageContext(request, true);
	}

    protected boolean isPageContextValid(Request request) {
        IpAddress reqIp = ControllerUtils.getRequestIPAddress(request);
        if (reqIp != null) {
            // IP of request
            PageContext context = this.getPageContext(request, false);
            // Check to see if the requests IP matches the pageContexts IP
            return (context != null && reqIp.equals(context.getIpAddress()));
        }
        return false;
    }

	protected PageContext getPageContext(Request request, boolean autoCreate) {
		PageContext pageContext = pageContextStore.get(request.getHeader("pageContextId"));
		if (pageContext == null && autoCreate) {
			pageContext = pageContextStore.create(null, getSession(request), request.getHeader(HttpHeaders.REFERER));
			if (pageContext == null) {
				// Try again with request URL - in case there is no referrer
				// This is only really needed in standalone test environments.
				// In all other cases - where the icon was embedded by a real ICAP server -,
				// there should either be a valid page context or a referrer.
				pageContext = pageContextStore.create(null, getSession(request), request.getUrl());
			}
		}
		if (pageContext == null && !autoCreate) {
			return null;
		}
		return pageContext;
	}

}
