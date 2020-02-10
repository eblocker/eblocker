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
import org.eblocker.server.common.session.Session;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.eblocker.registration.ProductFeature;
import com.google.inject.Singleton;
import io.netty.handler.codec.http.HttpHeaderNames;

@RequireFeature(ProductFeature.PRO)
@Singleton
public class UserAgentSpoofProcessor implements TransactionProcessor {

    @Override
    public boolean process(Transaction transaction) {
        Session session = transaction.getSession();
        String outgoingUserAgent = session.getOutgoingUserAgent();

        if (outgoingUserAgent != null) {
            // save session id to restore correct session on response despite spoofed UA
            transaction.getRequest().headers().set(SessionProcessor.HEADER, session.getSessionId());
            transaction.getRequest().headers().set(HttpHeaderNames.USER_AGENT, outgoingUserAgent);
            transaction.setHeadersChanged(true);
            transaction.setComplete(true);
        }

        return true;
    }
}
