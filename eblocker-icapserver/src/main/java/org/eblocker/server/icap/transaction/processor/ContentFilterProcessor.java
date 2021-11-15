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
import org.eblocker.registration.ProductFeature;
import org.eblocker.server.common.RequireFeature;
import org.eblocker.server.common.service.FilterStatisticsService;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.util.UrlUtils;
import org.eblocker.server.icap.filter.content.ContentFilterService;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;

@RequireFeature(ProductFeature.PRO)
@Singleton
public class ContentFilterProcessor implements TransactionProcessor {

    private final ContentFilterService contentFilterService;
    private final FilterStatisticsService filterStatisticsService;

    @Inject
    public ContentFilterProcessor(ContentFilterService contentFilterService,
                                  FilterStatisticsService filterStatisticsService) {
        this.contentFilterService = contentFilterService;
        this.filterStatisticsService = filterStatisticsService;
    }

    @Override
    public boolean process(Transaction transaction) {
        if (!transaction.isResponse()) {
            return true; // only makes sense for responses
        }

        if (transaction.isPreview()) {
            return true; // complete content needed for injecting HTML
        }

        if (!isHTML(transaction)) {
            return true;
        }

        Session session = transaction.getSession();
        if (session.isPatternFiltersEnabled()) {
            String hostname = UrlUtils.getHostname(transaction.getUrl());
            String html = contentFilterService.getHtmlToInject(hostname);
            if (!html.isEmpty()) {
                transaction.getInjections().inject(html);
                filterStatisticsService.countBlocked("pattern", transaction.getOriginalClientIP(), "CONTENT");
            }
        }
        return true;
    }

    /**
     * Check whether this response is HTML or not
     *
     * @param transaction
     * @return
     */
    private boolean isHTML(Transaction transaction) {
        String contentType = transaction.getContentType();
        return (contentType != null) && (contentType.contains("text/html") || contentType.contains("text/xhtml"));
    }
}
