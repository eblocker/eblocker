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
import org.eblocker.server.icap.transaction.Transaction;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

public class UserAgentSpoofProcessorTest {

    private UserAgentSpoofProcessor processor;

    @Before
    public void setup() {
        processor = new UserAgentSpoofProcessor();
    }

    @Test
    public void testSpoofing() {
        Session session = Mockito.mock(Session.class);

        Mockito.when(session.getOutgoingUserAgent()).thenReturn("mock user agent");
        Mockito.when(session.getSessionId()).thenReturn("12345");

        HttpHeaders headers = Mockito.mock(HttpHeaders.class);
        FullHttpRequest request = Mockito.mock(FullHttpRequest.class);
        Mockito.when(request.headers()).thenReturn(headers);

        Transaction transaction = Mockito.mock(Transaction.class);
        Mockito.when(transaction.isRequest()).thenReturn(true);
        Mockito.when(transaction.getRequest()).thenReturn(request);
        Mockito.when(transaction.getSession()).thenReturn(session);

        processor.process(transaction);

        InOrder inOrder = Mockito.inOrder(headers);
        inOrder.verify(headers).set(HttpHeaderNames.USER_AGENT, "mock user agent");

        Mockito.verify(headers).set(SessionProcessor.HEADER, "12345");
        Mockito.verify(transaction).setHeadersChanged(true);
    }

    @Test
    public void testNoSpoofing() {
        Session session = Mockito.mock(Session.class);

        Mockito.when(session.getSessionId()).thenReturn("12345");

        HttpHeaders headers = Mockito.mock(HttpHeaders.class);
        FullHttpRequest request = Mockito.mock(FullHttpRequest.class);
        Mockito.when(request.headers()).thenReturn(headers);

        Transaction transaction = Mockito.mock(Transaction.class);
        Mockito.when(transaction.isRequest()).thenReturn(true);
        Mockito.when(transaction.getRequest()).thenReturn(request);
        Mockito.when(transaction.getSession()).thenReturn(session);

        processor.process(transaction);

        Mockito.verifyZeroInteractions(headers);
        Mockito.verify(transaction, Mockito.never()).setHeadersChanged(Mockito.anyBoolean());
    }
}
