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

import java.util.List;

@RequireFeature(ProductFeature.PRO)
@Singleton
public class SetDntHeaderProcessor implements TransactionProcessor{
    private static final String DNT_HEADER = "DNT";
    private static final String DNT_VALUE = "1";
    private final FeatureService featureService;

    @Inject
    public SetDntHeaderProcessor(FeatureServiceSubscriber featureService){
        this.featureService = featureService;
    }

    @Override
    public boolean process(Transaction transaction) {
        if(featureService.getDntHeaderState()) {
            if (transaction.isResponse()) {
                // just for requests, because they contain the http header
                return true;
            }
            List<String> dntHeaders = transaction.getRequest().headers().getAll(DNT_HEADER);
            boolean dntHeaderSet = false;
            if (dntHeaders.size()!=1){
                // no header or multiple headers set
                dntHeaderSet = true;
            } else {
                // see if header already set
                if (!"1".equals(dntHeaders.get(0))) {
                    dntHeaderSet = true;
                }
            }

            if (dntHeaderSet) {
                transaction.getRequest().headers().set(DNT_HEADER, DNT_VALUE);
                transaction.setHeadersChanged(true);
                transaction.setComplete(true);
            }
        }
        return true;
    }
}
