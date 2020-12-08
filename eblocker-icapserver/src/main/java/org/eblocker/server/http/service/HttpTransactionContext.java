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
package org.eblocker.server.http.service;

import io.netty.handler.codec.http.HttpRequest;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.transaction.TransactionContext;
import org.eblocker.server.common.util.UrlUtils;

public class HttpTransactionContext implements TransactionContext {

    private final Session session;
    private final HttpRequest request;

    public HttpTransactionContext(Session session, HttpRequest request) {
        this.session = session;
        this.request = request;
    }

    @Override
    public String getSessionId() {
        return session.getSessionId();
    }

    @Override
    public String getUrl() {
        return request.uri();
    }

    @Override
    public String getDomain() {
        return UrlUtils.getHostname(request.uri());
    }

    @Override
    public String getReferrer() {
        return request.headers().get("Referer");
    }

    @Override
    public String getReferrerHostname() {
        String referrer = getReferrer();
        return referrer != null ? UrlUtils.getHostname(getReferrer()) : null;
    }

    @Override
    public String getAccept() {
        return request.headers().get("Accept");
    }

    @Override
    public Decision getDecision() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getRedirectTarget() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isThirdParty() {
        return !UrlUtils.isSameDomain(getDomain(), getReferrerHostname());
    }
}
