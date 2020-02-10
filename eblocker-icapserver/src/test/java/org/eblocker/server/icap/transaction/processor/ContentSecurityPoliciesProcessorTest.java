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
import org.eblocker.server.common.transaction.TransactionContext;
import org.eblocker.server.icap.filter.Category;
import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.FilterManager;
import org.eblocker.server.icap.filter.FilterResult;
import org.eblocker.server.icap.transaction.Transaction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class ContentSecurityPoliciesProcessorTest {

    private Session session;
    private Transaction transaction;
    private Filter filter;
    private ContentSecurityPoliciesProcessor processor;

    @Before
    public void setUp() {
        session = Mockito.mock(Session.class);
        Mockito.when(session.isPatternFiltersEnabled()).thenReturn(true);

        transaction = Mockito.mock(Transaction.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(transaction.getSession()).thenReturn(session);

        filter = Mockito.mock(Filter.class);

        FilterManager filterManager = Mockito.mock(FilterManager.class);
        Mockito.when(filterManager.getFilter(Category.CONTENT_SECURITY_POLICIES)).thenReturn(filter);

        processor = new ContentSecurityPoliciesProcessor(filterManager);
    }

    @Test
    public void testSetCspHeader() {
        Mockito.when(filter.filter(Mockito.any(TransactionContext.class))).thenReturn(FilterResult.setCspHeader(filter, "script-src 'none' *"));

        Assert.assertTrue(processor.process(transaction));
        Mockito.verify(transaction).setHeadersChanged(true);
        Mockito.verify(transaction.getResponse().headers()).set("Content-Security-Policy", "script-src 'none' *");
    }

    @Test
    public void testPass() {
        Mockito.when(filter.filter(Mockito.any(TransactionContext.class))).thenReturn(FilterResult.pass(filter));

        Assert.assertTrue(processor.process(transaction));
        Mockito.verify(transaction, Mockito.never()).setHeadersChanged(Mockito.anyBoolean());
        Mockito.verify(transaction, Mockito.never()).getResponse();
    }

    @Test
    public void testNoDecision() {
        Mockito.when(filter.filter(Mockito.any(TransactionContext.class))).thenReturn(FilterResult.noDecision(filter));

        Assert.assertTrue(processor.process(transaction));
        Mockito.verify(transaction, Mockito.never()).setHeadersChanged(Mockito.anyBoolean());
        Mockito.verify(transaction, Mockito.never()).getResponse();
    }

    @Test
    public void testPatternFilterDisabled() {
        Mockito.when(session.isPatternFiltersEnabled()).thenReturn(false);

        Assert.assertTrue(processor.process(transaction));
        Mockito.verifyZeroInteractions(filter);
        Mockito.verify(transaction, Mockito.never()).setHeadersChanged(Mockito.anyBoolean());
    }

}
