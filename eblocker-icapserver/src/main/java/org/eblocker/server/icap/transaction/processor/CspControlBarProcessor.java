/*
 * Copyright 2023 eBlocker Open Source UG (haftungsbeschraenkt)
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

import com.google.inject.Singleton;
import org.eblocker.server.common.data.ContentSecurityPolicy;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapts a Content-Security-Policy header of the page so the ControlBar (icons, iframe, XMLHttpRequests) can be loaded.
 */
@Singleton
public class CspControlBarProcessor implements TransactionProcessor {
    private static final Logger log = LoggerFactory.getLogger(CspControlBarProcessor.class);

    @Override
    public boolean process(Transaction transaction) {
        if (transaction.getControlBarUrl() != null && transaction.getCspNonce() != null) {
            String cspHeader = transaction.getResponse().headers().get("Content-Security-Policy");
            if (cspHeader != null) {
                ContentSecurityPolicy csp = ContentSecurityPolicy.from(cspHeader);
                csp.allowControlBar(transaction.getControlBarUrl(), transaction.getCspNonce());
                String newCspHeader = csp.toString();
                log.debug("Replacing CSP for {} \n    Old CSP: {}\n    New CSP: {}", transaction.getUrl(), cspHeader, newCspHeader);
                transaction.getResponse().headers().set("Content-Security-Policy", newCspHeader);
            }
        }
        return true;
    }
}
