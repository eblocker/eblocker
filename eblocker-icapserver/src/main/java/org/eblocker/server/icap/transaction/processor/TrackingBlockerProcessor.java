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
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.page.PageContext;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.transaction.TransactionCache;
import org.eblocker.server.common.util.UrlUtils;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.FilterManager;
import org.eblocker.server.icap.filter.FilterResult;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.eblocker.server.icap.transaction.processor.filter.PatternBlockerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@RequireFeature(ProductFeature.PRO)
@Singleton
public class TrackingBlockerProcessor implements TransactionProcessor {
    private static final Logger FILTER_LOG = LoggerFactory.getLogger("FILTER_LOG");

    private final DataSource dataSource;
    private final FilterManager filterManager;
    private final PatternBlockerUtils patternBlockerUtils;
    private final TransactionCache transactionCache;
    private final DeviceService deviceService;

    @Inject
    public TrackingBlockerProcessor(DataSource dataSource,
                                    FilterManager filterManager,
                                    PatternBlockerUtils patternBlockerUtils,
                                    TransactionCache transactionCache,
                                    DeviceService deviceService) {
        this.dataSource = dataSource;
        this.filterManager = filterManager;
        this.patternBlockerUtils = patternBlockerUtils;
        this.transactionCache = transactionCache;
        this.deviceService = deviceService;
    }

    private boolean askBlockOrRedirect(Transaction transaction, Session session) {
        Filter redirectFilter = filterManager.getFilter(org.eblocker.server.icap.filter.Category.TRACKER_REDIRECT);
        FilterResult result = redirectFilter.filter(transaction);
        // We do not want the redirect filter to overwrite the result of the tracking filter
        //transaction.setFilterResult(result);

        switch (result.getDecision()) {

            case NO_DECISION:
                // No decision here means: We follow the previously made "BLOCK" decision from the easylist tracking filter!
            case BLOCK:
                session.incrementBlockedTrackings(transaction);
                transaction.block();
                patternBlockerUtils.countBlockedDomain(Category.TRACKERS, transaction.getFilterResult(), session, transaction);
                return false;

            case REDIRECT:
                session.incrementBlockedTrackings(transaction);
                patternBlockerUtils.countBlockedDomain(Category.TRACKERS, transaction.getFilterResult(), session, transaction);
                if (result.getValue() != null) {
                    FILTER_LOG.info("{}\tRE\t{}\t{}\t{}", session.getShortId(), result.getDecision(), transaction.getUrl(), result.getDecider());
                    transaction.redirect(result.getValue());
                } else {
                    FILTER_LOG.info("{}\tRE\t{}\t{}\t{}", session.getShortId(), Decision.BLOCK, transaction.getUrl(), "<<REDIRECT-NO-TARGET>>");
                    transaction.block();
                }
                return false;

            case ASK:
                return ask(transaction, session, result);

            default:
                return true;

        }

    }

    private boolean ask(Transaction transaction, Session session, FilterResult result) {
        //
        // Do we already have a previously made user decision?
        //
        Decision userDecision = dataSource.getRedirectDecision(session.getSessionId(), transaction.getDomain());
        switch (userDecision) {

            //
            // No. No previously made user decision available.
            //
            case ASK:
            case NO_DECISION:
                FILTER_LOG.info("{}\tRE\t{}\t{}\t{}", session.getShortId(), result.getDecision(), transaction.getUrl(), result.getDecider());
                transaction.setRedirectTarget(result.getValue());
                UUID uuid = transactionCache.add(transaction);
                String orginalDomain = UrlUtils.getDomain(UrlUtils.getHostname(transaction.getUrl()));
                String baseURL = transaction.getBaseUrl();
                if (result.getValue() == null) {
                    if (isBlockingWithoutRedirectTarget()) {
                        session.incrementBlockedTrackings(transaction);
                        FILTER_LOG.info("{}\tUS\t{}\t{}\t{}", session.getShortId(), userDecision, transaction.getUrl(), "<<USER-NO-TARGET>>");
                        transaction.block();
                        patternBlockerUtils.countBlockedDomain(Category.TRACKERS, transaction.getFilterResult(), session, transaction);
                    } else {
                        FILTER_LOG.info("{}\tUS\t{}\t{}\t{}", session.getShortId(), Decision.ASK, transaction.getUrl(), "<<USER-NO-TARGET>>");
                        transaction.redirect(baseURL + "/dashboard/#!/redirect/" + uuid + "/" + orginalDomain);
                    }
                } else {
                    FILTER_LOG.info("{}\tUS\t{}\t{}\t{}", session.getShortId(), Decision.ASK, transaction.getUrl(), "<<USER-WITH-TARGET>>");
                    String targetDomain = UrlUtils.getDomain(UrlUtils.getHostname(result.getValue()));
                    transaction.redirect(baseURL + "/dashboard/#!/redirect/" + uuid + "/" + orginalDomain + "/" + targetDomain);
                }
                return false;

            case BLOCK:
                session.incrementBlockedTrackings(transaction);
                FILTER_LOG.info("{}\tUS\t{}\t{}\t{}", session.getShortId(), userDecision, transaction.getUrl(), "<<USER-NO-TARGET>>");
                transaction.block();
                patternBlockerUtils.countBlockedDomain(Category.TRACKERS, transaction.getFilterResult(), session, transaction);
                return false;

            case REDIRECT:
                session.incrementBlockedTrackings(transaction);
                if (result.getValue() == null) {
                    FILTER_LOG.info("{}\tUS\t{}\t{}\t{}", session.getShortId(), userDecision, transaction.getUrl(), "<<USER-NO-TARGET>>");
                    transaction.block();
                    patternBlockerUtils.countBlockedDomain(Category.TRACKERS, transaction.getFilterResult(), session, transaction);
                } else {
                    FILTER_LOG.info("{}\tUS\t{}\t{}\t{}", session.getShortId(), userDecision, transaction.getUrl(), "<<USER>>");
                    transaction.redirect(result.getValue());
                }
                return false;

            case PASS:
            default:
                FILTER_LOG.info("{}\tUS\t{}\t{}\t{}", session.getShortId(), userDecision, transaction.getUrl(), "<<USER>>");
                return true;

        }
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

        if (!deviceService.getDeviceById(session.getDeviceId()).isFilterTrackersEnabled()) {
            return true;
        }

        PageContext pageContext = transaction.getPageContext();
        if (!session.isBlockTrackings() || pageContext != null && pageContext.getWhiteListConfig().isTrackers()) {
            return true;
        }

        Filter trackingFilter = filterManager.getFilter(org.eblocker.server.icap.filter.Category.TRACKER_BLOCKER);
        FilterResult result = trackingFilter.filter(transaction);
        transaction.setFilterResult(result);
        if (!result.getDecision().equals(Decision.NO_DECISION)) {
            FILTER_LOG.info("{}\tTR\t{}\t{}\t{}", session.getShortId(), result.getDecision(), transaction.getUrl(), result.getDecider());
        }
        switch (result.getDecision()) {

            case BLOCK:
                return askBlockOrRedirect(transaction, session);

            default:
                return true;

        }
    }

    /**
     * How should the filter behave, when we cannot find a redirect target URL in the original URL?
     */
    private boolean isBlockingWithoutRedirectTarget() {
        //
        // TODO: Returning "true" should resemble the previous behaviour.
        //       But we can do better: Find out if the request is for an HTML page.
        //       If so, we can always return a redirect and find out (with JavaScript), if we are in a top level frame.
        //
        return true;
    }

}
