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

import org.eblocker.server.common.page.PageContext;
import org.eblocker.server.common.page.PageContextStore;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.icap.transaction.Transaction;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class PageContextProcessorTest {

    private static final String REFERRER = "referrer";
    private static final String URL = "url";

    private PageContext pageContext;
    private PageContext childPageContext;
    private Session session;
    private PageContextStore pageContextStore;
    private PageContextProcessor processor;
    private Transaction transaction;

    @Before
    public void setup() {
        session = Mockito.mock(Session.class);

        pageContext = Mockito.mock(PageContext.class);
        childPageContext = Mockito.mock(PageContext.class);
        pageContextStore = Mockito.mock(PageContextStore.class);

        Mockito.when(pageContextStore.find(session, REFERRER)).thenReturn(pageContext);
        Mockito.when(pageContextStore.create(pageContext, session, URL)).thenReturn(childPageContext);

        processor = new PageContextProcessor(pageContextStore);

        transaction = Mockito.mock(Transaction.class);
        Mockito.when(transaction.getSession()).thenReturn(session);
        Mockito.when(transaction.getUrl()).thenReturn(URL);
        Mockito.when(transaction.getReferrer()).thenReturn(REFERRER);
    }

    @Test
    public void testRequest() {
        Mockito.when(transaction.isRequest()).thenReturn(true);

        processor.process(transaction);

        Mockito.verify(transaction).setPageContext(pageContext);
    }

    @Test
    public void testResponse() {
        Mockito.when(transaction.isResponse()).thenReturn(true);

        processor.process(transaction);

        Mockito.verify(transaction).setPageContext(childPageContext);
    }
}
