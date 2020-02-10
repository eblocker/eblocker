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
import io.netty.handler.codec.http.HttpHeaders;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class SessionProcessorTest {

    private SessionStore sessionStore;
    private SessionProcessor processor;

    @Before
    public void setup() {
        sessionStore = Mockito.mock(SessionStore.class);
        processor = new SessionProcessor(sessionStore);
    }

    @Test
    public void testRequest() {
        Transaction transaction = Mockito.mock(Transaction.class);
        Mockito.when(transaction.isRequest()).thenReturn(true);

        Session session = Mockito.mock(Session.class);
        Mockito.when(session.getSessionId()).thenReturn("12355");
        Mockito.when(sessionStore.getSession(transaction)).thenReturn(session);

        processor.process(transaction);

        Mockito.verify(sessionStore).getSession(transaction);
        Mockito.verify(transaction).setSession(session);
    }

    @Test
    public void testResponseNoSessionHeader() {
        Transaction transaction = Mockito.mock(Transaction.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(transaction.isResponse()).thenReturn(true);
        Mockito.when(transaction.getRequest().headers()).thenReturn(Mockito.mock(HttpHeaders.class));

        Session session = Mockito.mock(Session.class);
        Mockito.when(session.getSessionId()).thenReturn("12355");
        Mockito.when(sessionStore.getSession(transaction)).thenReturn(session);

        processor.process(transaction);

        Mockito.verify(sessionStore, Mockito.never()).findSession(Mockito.any());
        Mockito.verify(sessionStore).getSession(transaction);
        Mockito.verify(transaction).setSession(session);
    }

    @Test
    public void testResponseSessionHeader() {
        HttpHeaders headers = Mockito.mock(HttpHeaders.class);
        Mockito.when(headers.get(SessionProcessor.HEADER)).thenReturn("12345");

        Transaction transaction = Mockito.mock(Transaction.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(transaction.isResponse()).thenReturn(true);
        Mockito.when(transaction.getRequest().headers()).thenReturn(headers);

        Session session = Mockito.mock(Session.class);
        Mockito.when(sessionStore.findSession("12345")).thenReturn(session);

        processor.process(transaction);

        Mockito.verify(sessionStore).findSession("12345");
        Mockito.verify(sessionStore, Mockito.never()).getSession(Mockito.any());
        Mockito.verify(transaction).setSession(session);
    }

    @Test
    public void testResponseSessionHeaderExpired() {
        HttpHeaders headers = Mockito.mock(HttpHeaders.class);
        Mockito.when(headers.get(SessionProcessor.HEADER)).thenReturn("12345");

        Transaction transaction = Mockito.mock(Transaction.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(transaction.isResponse()).thenReturn(true);
        Mockito.when(transaction.getRequest().headers()).thenReturn(headers);

        Session session = Mockito.mock(Session.class);
        Mockito.when(session.getSessionId()).thenReturn("12355");
        Mockito.when(sessionStore.getSession(transaction)).thenReturn(session);

        processor.process(transaction);

        Mockito.verify(sessionStore).findSession("12345");
        Mockito.verify(sessionStore).getSession(transaction);
        Mockito.verify(transaction).setSession(session);
    }
}
