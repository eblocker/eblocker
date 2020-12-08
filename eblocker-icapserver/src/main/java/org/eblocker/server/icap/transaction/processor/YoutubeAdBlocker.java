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
import org.eblocker.server.common.page.PageContext;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class will tell inject some javascript into youtube.com website, to block invideo advertisements
 * and provide a way to further customize the youtube experience
 * (e.g. disable annoying annotations or autoplay -> the customization has to
 * be triggered manually at the moment by calling 'youtubeDisable()' in your browser console when using the eBlocker and youtube.com)
 */
@RequireFeature(ProductFeature.PRO)
@Singleton
public class YoutubeAdBlocker implements TransactionProcessor {

    private static final Logger log = LoggerFactory.getLogger(YoutubeAdBlocker.class);

    private String youtubeTemplate;

    @Inject
    public YoutubeAdBlocker(@Named("toolbarYoutubeInlayTemplate") String youtubeTemplate) {
        this.youtubeTemplate = youtubeTemplate;
    }

    @Override
    public boolean process(Transaction transaction) {
        if (!transaction.isResponse()) return true;//only makes sense for responses

        PageContext pageContext = transaction.getPageContext();

        if (isYoutubeWebsite(pageContext)) {
            log.debug("Injecting youtube template! {}", pageContext.getUrl());
            transaction.getInjections().inject(youtubeTemplate);
        }
        return true;
    }

    private boolean isYoutubeWebsite(PageContext pageContext) {
        //FIXME improve recognition (make more precise) -> this might be wildcard
        return (pageContext.getUrl().contains("youtube.com"));
    }
}
