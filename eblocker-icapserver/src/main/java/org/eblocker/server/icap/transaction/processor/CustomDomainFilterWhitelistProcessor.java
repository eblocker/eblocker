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
import org.eblocker.server.common.blacklist.DomainFilter;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.transaction.TransactionContext;
import org.eblocker.server.common.util.UrlUtils;
import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.FilterPriority;
import org.eblocker.server.icap.filter.FilterResult;
import org.eblocker.server.icap.service.CustomDomainFilterWhitelistService;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.eblocker.registration.ProductFeature;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Processor to set transactions decision to PASS if hostname belongs to a user's custom domain whitelist.
 */
@RequireFeature(ProductFeature.PRO)
@Singleton
public class CustomDomainFilterWhitelistProcessor implements TransactionProcessor {
    private final CustomFilter customFilter = new CustomFilter();

    private final CustomDomainFilterWhitelistService customDomainFilterWhitelistService;

    @Inject
    public CustomDomainFilterWhitelistProcessor(CustomDomainFilterWhitelistService customDomainFilterWhitelistService) {
        this.customDomainFilterWhitelistService = customDomainFilterWhitelistService;
    }

    @Override

    public boolean process(Transaction transaction) {
        Session session = transaction.getSession();
        if (session.isPatternFiltersEnabled()) {
            String hostname = UrlUtils.getHostname(transaction.getUrl());
            DomainFilter<String> filter = customDomainFilterWhitelistService.getWhitelistFilter(session.getUserId());
            if (filter.isBlocked(hostname).isBlocked()) {
                transaction.setDecision(Decision.PASS);
                transaction.setFilterResult(FilterResult.pass(customFilter));
            }
        }
        return true;
    }

    public class CustomFilter implements Filter {
        @Override
        public FilterResult filter(TransactionContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FilterPriority getPriority() {
            return null;
        }

        @Override
        public int getMatches() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getDefinition() {
            return "custom-ads-trackers";
        }

        @Override
        public String getDomain() {
            return null;
        }

        @Override
        public int compareTo(Filter o) {
            throw new UnsupportedOperationException();
        }
    }

}
