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
import com.google.inject.name.Named;
import org.eblocker.registration.ProductFeature;
import org.eblocker.server.common.RequireFeature;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.page.PageContext;
import org.eblocker.server.common.service.FeatureService;
import org.eblocker.server.common.service.FeatureServiceSubscriber;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.transaction.TransactionCache;
import org.eblocker.server.common.util.UrlUtils;
import org.eblocker.server.icap.filter.Category;
import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.FilterManager;
import org.eblocker.server.icap.filter.FilterResult;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@RequireFeature(ProductFeature.PRO)
@Singleton
public class EBlockerFilterProcessor implements TransactionProcessor {
    private static final Logger FILTER_LOG = LoggerFactory.getLogger("FILTER_LOG");
    private static final Logger log = LoggerFactory.getLogger(EBlockerFilterProcessor.class);

    private final FilterManager filterManager;
    private final TransactionCache transactionCache;
    private final DataSource dataSource;
    private final FeatureService featureService;
    private final String connectionTestPatternBlockerUrl;

    @Inject
    public EBlockerFilterProcessor(
            FilterManager filterManager,
            TransactionCache transactionCache,
            DataSource dataSource,
            FeatureServiceSubscriber featureService,
            @Named("connection.test.patternblocker.url") String connectionTestPatternBlockerUrl
    ) {
        this.filterManager = filterManager;
        this.transactionCache = transactionCache;
        this.dataSource = dataSource;
        this.featureService = featureService;
        this.connectionTestPatternBlockerUrl = connectionTestPatternBlockerUrl;
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

        PageContext pageContext = transaction.getPageContext();
        // is "eblocker-filter" ads or tracking filter ?
        if (!session.isBlockTrackings()
                || pageContext != null && (pageContext.getWhiteListConfig().isAds() || pageContext.getWhiteListConfig().isTrackers())) {
            return true;
        }

        Filter eBlockerFilter = filterManager.getFilter(Category.EBLOCKER);
        FilterResult result = eBlockerFilter.filter(transaction);
        transaction.setDecision(result.getDecision());
        transaction.setFilterResult(result);
        if (!result.getDecision().equals(Decision.NO_DECISION)) {
            FILTER_LOG.info("{}\tEB\t{}\t{}\t{}", session.getShortId(), result.getDecision(), transaction.getUrl(), result.getDecider());
        }
        switch (result.getDecision()) {
            case BLOCK:
                session.incrementBlockedTrackings(transaction);
                transaction.block();
                return false;

            case NO_CONTENT:
                if (featureService.getGoogleCaptivePortalRedirectorState() || isConnectionCheckUrl(transaction.getUrl())) {
                    transaction.noContent();
                    return false;
                } else {
                    return true;
                }

            case REDIRECT:
                session.incrementBlockedTrackings(transaction);
                if (result.getValue() != null) {
                    log.debug("Skipping tracking server. Immediate redirect to target URL {}", result.getValue());
                    transaction.redirect(result.getValue());
                } else {
                    transaction.setRedirectTarget(result.getValue());
                    UUID uuid = transactionCache.add(transaction);
                    String orginalDomain = UrlUtils.getDomain(UrlUtils.getHostname(transaction.getUrl()));
                    String baseURL = transaction.getBaseUrl();
                    FILTER_LOG.info("{}\tUS\t{}\t{}\t{}", session.getShortId(), Decision.ASK, transaction.getUrl(), "<<USER-NO-TARGET>>");
                    transaction.redirect(baseURL + "/dashboard/#!/redirect/" + uuid + "/" + orginalDomain);
                }
                return false;

            case ASK:
                return ask(transaction, session, result);

            default:
                return true;

        }
    }

    private boolean isConnectionCheckUrl(String url) {
        return url.endsWith(connectionTestPatternBlockerUrl);
    }

    private boolean ask(Transaction transaction, Session session, FilterResult result) {
        Decision userDecision = dataSource.getRedirectDecision(session.getSessionId(), transaction.getDomain());
        switch (userDecision) {

            case ASK:
            case NO_DECISION:
                transaction.setRedirectTarget(result.getValue());
                UUID uuid = transactionCache.add(transaction);
                String orginalDomain = UrlUtils.getDomain(UrlUtils.getHostname(transaction.getUrl()));
                String baseURL = transaction.getBaseUrl();
                if (result.getValue() == null) {
                    FILTER_LOG.info("{}\tUS\t{}\t{}\t{}", session.getShortId(), Decision.ASK, transaction.getUrl(), "<<USER-NO-TARGET>>");
                    transaction.redirect(baseURL + "/dashboard/#!/redirect/" + uuid + "/" + orginalDomain);
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
                return false;

            case REDIRECT:
                session.incrementBlockedTrackings(transaction);
                if (result.getValue() == null) {
                    FILTER_LOG.info("{}\tUS\t{}\t{}\t{}", session.getShortId(), userDecision, transaction.getUrl(), "<<USER-NO-TARGET>>");
                    transaction.block();
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
}
