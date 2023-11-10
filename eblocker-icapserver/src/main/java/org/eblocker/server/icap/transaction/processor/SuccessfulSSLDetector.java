/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
import org.eblocker.server.common.util.UrlUtils;
import org.eblocker.server.http.service.AutoTrustAppService;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SuccessfulSSLDetector implements TransactionProcessor {

    private final AutoTrustAppService autoTrustAppService;
    private static final Logger log = LoggerFactory.getLogger(SuccessfulSSLDetector.class);

    @Inject
    public SuccessfulSSLDetector(AutoTrustAppService autoTrustAppService) {
        this.autoTrustAppService = autoTrustAppService;
    }

    @Override
    public boolean process(Transaction transaction) {
        String url = transaction.getUrl();
        if (url != null && url.startsWith("https")) {
            log.debug("Processing HTTPS URL {}", url);
            autoTrustAppService.recordSuccessfulSSL(UrlUtils.getHostname(url));
        }
        return true;
    }
}
