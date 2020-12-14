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
package org.eblocker.server.icap.filter.url;

import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.transaction.TransactionContext;
import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.FilterResult;
import org.eblocker.server.icap.filter.FilterType;
import org.eblocker.server.icap.filter.TestContext;
import org.junit.Assert;
import org.junit.Test;

public class ContentSecurityPoliciesUrlFilterTest {

    @Test
    public void test() {
        assertFilterResult(Decision.SET_CSP_HEADER, "script-src 'self' * 'unsafe-inline",
            filter(StringMatchType.REGEX, "^http(s)?://[a-zA-Z0-9._-]*merriam-webster\\.com([^a-zA-Z0-9_.%-]|$).*$", FilterType.BLOCK, "script-src 'self' * 'unsafe-inline", "https://www.merriam-webster.com/index.html"));
        assertFilterResult(Decision.PASS, null, filter(StringMatchType.REGEX, "^http(s)?://[a-zA-Z0-9._-]*merriam-webster\\.com([^a-zA-Z0-9_.%-]|$).*$", FilterType.PASS, "script-src 'self' * 'unsafe-inline", "https://www.merriam-webster.com/index.html"));
        assertFilterResult(Decision.NO_DECISION, null, filter(StringMatchType.REGEX, "^http(s)?://[a-zA-Z0-9._-]*merriam-webster\\.com([^a-zA-Z0-9_.%-]|$).*$", FilterType.BLOCK, "script-src 'self' * 'unsafe-inline", "https://www.xkcd.com/index.html"));
    }

    private void assertFilterResult(Decision expectedDecision, String expectedValue, FilterResult result) {
        Assert.assertEquals(expectedDecision, result.getDecision());
        Assert.assertEquals(expectedValue, result.getValue());
    }

    private FilterResult filter(StringMatchType matchType, String substring, FilterType type, String contentSecurityPolicies, String url) {
        TransactionContext context = new TestContext(url);
        Filter filter = UrlFilterFactory.getInstance()
            .setStringMatchType(matchType)
            .setMatchString(substring)
            .setType(type)
            .setContentSecurityPolicies(contentSecurityPolicies)
            .build();
        return filter.filter(context);
    }

}
