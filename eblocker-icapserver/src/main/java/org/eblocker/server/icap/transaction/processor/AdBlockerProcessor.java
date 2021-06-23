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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.registration.ProductFeature;
import org.eblocker.server.common.RequireFeature;
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.page.PageContext;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.FilterManager;
import org.eblocker.server.icap.filter.FilterResult;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.eblocker.server.icap.transaction.processor.filter.PatternBlockerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequireFeature(ProductFeature.PRO)
@Singleton
public class AdBlockerProcessor implements TransactionProcessor {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(AdBlockerProcessor.class);
    private static final Logger FILTER_LOG = LoggerFactory.getLogger("FILTER_LOG");

    private final FilterManager filterManager;
    private final PatternBlockerUtils patternBlockerUtils;
    private final DeviceService deviceService;

    @Inject
    public AdBlockerProcessor(FilterManager filterManager,
                              PatternBlockerUtils patternBlockerUtils,
                              DeviceService deviceService) {
        this.filterManager = filterManager;
        this.patternBlockerUtils = patternBlockerUtils;
        this.deviceService = deviceService;
    }

    @Override
    public boolean process(Transaction transaction) {
        if (Decision.PASS.equals(transaction.getDecision())) {
            return true;
        }

        Session session = transaction.getSession();
        if (!session.isPatternFiltersEnabled()) {
            return true;
        }

        if (!deviceService.getDeviceById(session.getDeviceId()).isFilterAdsEnabled()) {
            return true;
        }

        PageContext pageContext = transaction.getPageContext();
        if (!session.isBlockAds() || pageContext != null && pageContext.getWhiteListConfig().isAds()) {
            return true;
        }

        Filter filter = filterManager.getFilter(org.eblocker.server.icap.filter.Category.ADS);
        FilterResult result = filter.filter(transaction);
        transaction.setFilterResult(result);
        if (!result.getDecision().equals(Decision.NO_DECISION)) {
            FILTER_LOG.info("{}\tAD\t{}\t{}\t{}", session.getShortId(), result.getDecision(), transaction.getUrl(), result.getDecider());
        }

        switch (result.getDecision()) {
            case BLOCK:
                transaction.block();
                session.incrementBlockedAds(transaction);
                patternBlockerUtils.countBlockedDomain(Category.ADS, result, session, transaction);
                return false;

            default:
                return true;

        }
    }
}
