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
package org.eblocker.server.icap.transaction.processor.filter;

import org.eblocker.server.common.page.PageContext;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.icap.filter.Category;
import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.FilterManager;
import org.eblocker.server.icap.filter.FilterResult;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseFilterProcessor implements TransactionProcessor {

    private final static Logger log = LoggerFactory.getLogger(BaseFilterProcessor.class);
    private final static Logger FILTER_LOG = LoggerFactory.getLogger("FILTER_LOG");

    private final Filter filter;

    public BaseFilterProcessor(FilterManager filterManager) {
        filter = filterManager.getFilter(getFilterCategory());
    }

    protected abstract Category getFilterCategory();

    protected abstract String getFilterShortName();

    protected abstract boolean isFilterActive(Session session, PageContext pageContext, Transaction transaction);

    protected abstract boolean isPassFinal(Session session, PageContext pageContext, Transaction transaction);

    protected abstract boolean isBlockFinal(Session session, PageContext pageContext,Transaction transaction);

    protected abstract boolean isRedirectFinal(Session session, PageContext pageContext,Transaction transaction);

    protected abstract void countBlockedTransaction(Transaction transaction, Session session);

    protected abstract void countRedirectedTransaction(Transaction transaction, Session session);

    @Override
    public boolean process(Transaction transaction) {
        Session session = transaction.getSession();
        PageContext pageContext = transaction.getPageContext();
        if (!isFilterActive(session, pageContext, transaction)) {
            return true;
        }

        FilterResult result = filter.filter(transaction);
        transaction.setFilterResult(result);
        if (result.getDecision() == Decision.NO_DECISION) {
            return true;
        }

        FILTER_LOG.info("{}\t{}\t{}\t{}\t{}", getFilterShortName(), session.getShortId(), result.getDecision(), transaction.getUrl(), result.getDecider());
        switch (result.getDecision()) {

            case PASS:
                if (isPassFinal(session, pageContext, transaction)) {
                    transaction.setComplete(true);
                    return false;
                }
                break;

            case BLOCK:
                if (isBlockFinal(session, pageContext, transaction)) {
                    countBlockedTransaction(transaction, session);
                    transaction.block();
                    return false;
                }
                break;

            case REDIRECT:
                if (isRedirectFinal(session, pageContext, transaction)) {
                    if (result.getValue() != null) {
                        countRedirectedTransaction(transaction, session);
                        log.info("Skipping tracking destination. Immediate redirecting to target URL {}", result.getValue());
                        transaction.redirect(result.getValue());
                    } else {
                        countBlockedTransaction(transaction, session);
                        transaction.block();
                    }
                }
                break;

            case ASK:
                break;

            default:
                break;

        }

        //
        // If we get here, we have not really executed the decision.
        // So at least, we store it, so that subsequent processors can make use of it.
        //
        transaction.setDecision(result.getDecision());
        return true;
    }

}

