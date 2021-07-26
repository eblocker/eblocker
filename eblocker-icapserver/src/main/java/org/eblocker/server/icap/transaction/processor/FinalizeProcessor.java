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
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.eblocker.server.icap.transaction.processor.filter.PatternBlockerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FinalizeProcessor implements TransactionProcessor {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(FinalizeProcessor.class);
    private final static Logger FILTER_LOG = LoggerFactory.getLogger("FILTER_LOG");
    private final PatternBlockerUtils patternBlockerUtils;

    @Inject
    public FinalizeProcessor(PatternBlockerUtils patternBlockerUtils) {
        this.patternBlockerUtils = patternBlockerUtils;
    }

    @Override
    public boolean process(Transaction transaction) {
        FILTER_LOG.info("{}\tFI\t{}\t{}\t{}", transaction.getSession().getShortId(), Decision.PASS, transaction.getUrl(), "DEFAULT");
        transaction.setComplete(true);

        if (transaction.isRequest()) {
            // No other processor has terminated this request, so we assume it passed:
            patternBlockerUtils.countPassedDomain(transaction.getSession(), transaction);
        }

        return false;
    }
}
