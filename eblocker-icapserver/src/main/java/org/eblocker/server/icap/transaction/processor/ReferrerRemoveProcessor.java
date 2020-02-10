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

import org.eblocker.server.common.RequireFeature;
import org.eblocker.server.common.service.FeatureService;
import org.eblocker.server.common.service.FeatureServiceSubscriber;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.eblocker.registration.ProductFeature;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**This processor will remove the HTTP-Referrer-Header from the Requests
 */

@RequireFeature(ProductFeature.PRO)
@Singleton
public class ReferrerRemoveProcessor implements TransactionProcessor{
    private static final Logger log = LoggerFactory.getLogger(ReferrerRemoveProcessor.class);
    private static final String REFERRER_HEADER = "Referer";
    private final FeatureService featureService;

    @Inject
    public ReferrerRemoveProcessor(FeatureServiceSubscriber featureService){
        this.featureService = featureService;
    }

    @Override
    public boolean process(Transaction transaction) {
        if(featureService.getHTTPRefererRemovingState()) {
            if (transaction.isResponse())//just for requests, because they contain the http header
                return false;

            FullHttpRequest request = transaction.getRequest();
            //get HTTP Header
            HttpHeaders headers = request.headers();

			if (headers.get(REFERRER_HEADER) != null // Referrer Header exists
					&& transaction.isThirdParty()) {
                //remove Referrer Header
                headers.remove(REFERRER_HEADER);
                log.debug("Removed HTTP Referrer Header!");
                //finish
                transaction.setRequest(request);
            }
        }
        return true;
    }
}
