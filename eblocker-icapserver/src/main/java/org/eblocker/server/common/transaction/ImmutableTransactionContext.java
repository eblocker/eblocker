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
package org.eblocker.server.common.transaction;


public class ImmutableTransactionContext implements TransactionContext {

	private final String sessionId;
	private final String url;
	private final String referrer;
	private final String domain;
	private final String accept;
	private final String referrerHostname;
	private final Decision decision;
	private final String redirectTarget;
	private final boolean thirdParty;

	public ImmutableTransactionContext(String sessionId, String url, String referrer, String domain, String referrerHostname, String accept, Decision decision, String redirectTarget, boolean thirdParty) {
		this.sessionId = sessionId;
		this.url = url;
		this.referrer = referrer;
		this.domain = domain;
		this.referrerHostname = referrerHostname;
		this.accept = accept;
		this.decision = decision;
		this.redirectTarget = redirectTarget;
		this.thirdParty = thirdParty;
	}

	public ImmutableTransactionContext(TransactionContext context) {
		sessionId = context.getSessionId();
		url = context.getUrl();
		referrer = context.getReferrer();
		domain = context.getDomain();
		referrerHostname = context.getReferrerHostname();
		accept = context.getAccept();
		decision = context.getDecision();
		redirectTarget = context.getRedirectTarget();
		this.thirdParty = context.isThirdParty();
	}
	
	@Override
	public String getUrl() {
		return url;
	}

	@Override
	public String getReferrer() {
		return referrer;
	}

	@Override
	public String getDomain() {
		return domain;
	}

	@Override
	public String getReferrerHostname() {
		return referrerHostname;
	}

	@Override
	public String getAccept() {
		return accept;
	}

	@Override
	public String getSessionId() {
		return sessionId;
	}

	@Override
	public Decision getDecision() {
		return decision;
	}

	@Override
	public String getRedirectTarget() {
		return redirectTarget;
	}

	@Override
	public boolean isThirdParty() {
		return thirdParty;
	}

}
