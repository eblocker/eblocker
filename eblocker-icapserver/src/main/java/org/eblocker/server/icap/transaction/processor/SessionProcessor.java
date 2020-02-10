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

import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SessionProcessor implements TransactionProcessor {
    private static final Logger log = LoggerFactory.getLogger(SessionProcessor.class);

    public static final String HEADER = "X-eblocker-session-id";

    private final SessionStore sessionStore;

    @Inject
    public SessionProcessor(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    public boolean process(Transaction transaction) {
        HttpRequest request = transaction.getRequest();
        Session session = null;

        if (transaction.isRequest()) {
            session = sessionStore.getSession(transaction);
        } else {
            String sessionId = request.headers().get(HEADER);
            if (sessionId != null) {
                session = sessionStore.findSession(request.headers().get(HEADER));
            }
            if (session == null) {
                session = sessionStore.getSession(transaction);
            }
        }

        transaction.setSession(session);
        return true;
    }
}
