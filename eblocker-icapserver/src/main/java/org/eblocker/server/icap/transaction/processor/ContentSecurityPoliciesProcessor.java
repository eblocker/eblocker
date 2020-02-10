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
package org.eblocker.server.icap.transaction.processor;

import org.eblocker.server.common.RequireFeature;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.icap.filter.Category;
import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.FilterManager;
import org.eblocker.server.icap.filter.FilterResult;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.eblocker.registration.ProductFeature;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@RequireFeature(ProductFeature.PRO)
@Singleton
public class ContentSecurityPoliciesProcessor implements TransactionProcessor {

    private final FilterManager filterManager;

    @Inject
    public ContentSecurityPoliciesProcessor(FilterManager filterManager) {
        this.filterManager = filterManager;
    }

	@Override
    public boolean process(Transaction transaction) {
        Session session = transaction.getSession();
        if (session.isPatternFiltersEnabled()) {
            Filter filter = filterManager.getFilter(Category.CONTENT_SECURITY_POLICIES);
            FilterResult result = filter.filter(transaction);

            if (Decision.SET_CSP_HEADER == result.getDecision()) {
                transaction.getResponse().headers().set("Content-Security-Policy", result.getValue());
                transaction.setHeadersChanged(true);
            }
        }
        return true;
    }
}
