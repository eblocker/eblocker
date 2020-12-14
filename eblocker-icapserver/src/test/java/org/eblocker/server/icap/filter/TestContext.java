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
import org.eblocker.server.common.util.UrlUtils;

public class TestContext implements TransactionContext {

    private final String url;

    private final String referrer;

    private final String accept;

    public TestContext(String url) {
        this.url = url;
        this.referrer = null;
        this.accept = null;
    }

    public TestContext(String url, String referrer, String accept) {
        this.url = url;
        this.referrer = referrer;
        this.accept = accept;
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
    public String getAccept() {
        return accept;
    }

    @Override
    public String getDomain() {
        if (url == null)
            return null;
        return UrlUtils.getDomain(UrlUtils.getHostname(url));
    }

    @Override
    public String getReferrerHostname() {
        if (referrer == null)
            return null;
        return UrlUtils.getHostname(referrer);
    }

    @Override
    public String getSessionId() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Decision getDecision() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getRedirectTarget() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isThirdParty() {
        return !UrlUtils.isSameDomain(getDomain(), getReferrerHostname());
    }

}
