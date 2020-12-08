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

import com.google.inject.Singleton;
import org.eblocker.server.common.util.HtmlUtils;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * This class will collect all the content that is about to get injected into HTML responses
 */
@Singleton
public class HtmlInjectionProcessor implements TransactionProcessor {

    private static final Logger log = LoggerFactory.getLogger(HtmlInjectionProcessor.class);

    @Override
    public boolean process(Transaction transaction) {
        if (transaction.isPreview()) {
            log.debug("PREV - need complete content");
            transaction.setComplete(false);
            return false;
        }

        if (!isHTML(transaction)) {
            return true;
        }

        StringBuilder content = transaction.getContent();
        if (content == null) {
            return true;
        }


        inject(content, transaction.getInjections().getInjections());
        return true;
    }

    /**
     * Perform the injection before the last closing body tag
     *
     * @param transaction
     * @param content
     * @param injections
     */
    private void inject(StringBuilder content, List<String> injections) {
        StringBuilder injectionTotal = new StringBuilder();
        for (String injection : injections) {
            injectionTotal.append(injection);
        }

        //find last body tag and inject everything there
        HtmlUtils.insertBeforeBodyEnd(content, injectionTotal.toString());
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
