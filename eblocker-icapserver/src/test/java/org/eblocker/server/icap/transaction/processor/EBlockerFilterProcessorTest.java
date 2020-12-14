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

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.service.FeatureServiceSubscriber;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.transaction.TransactionCache;
import org.eblocker.server.icap.filter.Category;
import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.FilterManager;
import org.eblocker.server.icap.filter.FilterResult;
import org.eblocker.server.icap.transaction.Transaction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class EBlockerFilterProcessorTest {

    private Filter filter;
    private FilterManager filterManager;
    private TransactionCache transactionCache;
    private DataSource dataSource;
    private FeatureServiceSubscriber featureServiceSubscriber;
    private Session session;
    private String connectionTestPatternBlockerUrl = "/_check_/pattern-blocker";

    private Transaction transaction;
    private EBlockerFilterProcessor processor;

    @Before
    public void setUp() {
        session = Mockito.mock(Session.class);
        Mockito.when(session.getDeviceId()).thenReturn("device:1234");
        Mockito.when(session.isBlockAds()).thenReturn(true);
        Mockito.when(session.isBlockTrackings()).thenReturn(true);
        Mockito.when(session.isPatternFiltersEnabled()).thenReturn(true);

        transaction = Mockito.mock(Transaction.class);
        Mockito.when(transaction.getSession()).thenReturn(session);
        Mockito.when(transaction.getUrl()).thenReturn("https://www.eblocker.com/wp-content/affiliate-wp/assets/js/tracking.min.js?ver=2.0.8");
        Mockito.when(transaction.getOriginalClientIP()).thenReturn(IpAddress.parse("192.168.3.3"));

        filter = Mockito.mock(Filter.class);

        filterManager = Mockito.mock(FilterManager.class);
        Mockito.when(filterManager.getFilter(Mockito.any(Category.class))).thenReturn(filter);

        processor = new EBlockerFilterProcessor(filterManager, transactionCache, dataSource, featureServiceSubscriber,
            connectionTestPatternBlockerUrl);
    }

    @Test
    public void testFilterDisabled() {
        Mockito.when(session.isPatternFiltersEnabled()).thenReturn(false);
        Assert.assertTrue(processor.process(transaction));
        Mockito.verifyZeroInteractions(filter);
    }

    @Test
    public void testFilterBlock() {
        FilterResult result = FilterResult.block(filter);
        Mockito.when(filter.filter(transaction)).thenReturn(result);

        Assert.assertFalse(processor.process(transaction));

        Mockito.verify(transaction).block();
        Mockito.verify(transaction).setDecision(Decision.BLOCK);
        Mockito.verify(transaction).setFilterResult(result);
    }

    @Test
    public void testFilterNoDecision() {
        FilterResult result = FilterResult.noDecision(filter);
        Mockito.when(filter.filter(transaction)).thenReturn(result);

        Assert.assertTrue(processor.process(transaction));

        Mockito.verify(transaction, Mockito.never()).block();
        Mockito.verify(transaction).setDecision(Decision.NO_DECISION);
        Mockito.verify(transaction).setFilterResult(result);
    }

    @Test
    public void testFilterPass() {
        FilterResult result = FilterResult.pass(filter);
        Mockito.when(filter.filter(transaction)).thenReturn(result);

        Assert.assertTrue(processor.process(transaction));

        Mockito.verify(transaction, Mockito.never()).block();
        Mockito.verify(transaction).setDecision(Decision.PASS);
        Mockito.verify(transaction).setFilterResult(result);
    }

    @Test
    public void testDoNotOverridePass() {
        Mockito.when(transaction.getDecision()).thenReturn(Decision.PASS);

        Assert.assertTrue(processor.process(transaction));

        Mockito.verify(transaction, Mockito.never()).block();
        Mockito.verifyZeroInteractions(filter);
        Mockito.verify(transaction, Mockito.never()).setDecision(Mockito.any());
        Mockito.verify(transaction, Mockito.never()).setFilterResult(Mockito.any());
    }

}
