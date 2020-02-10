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

import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.FilterManager;
import org.eblocker.server.icap.filter.FilterResult;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.processor.filter.PatternBlockerUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class AdBlockerProcessorTest {

    private Filter filter;
    private FilterManager filterManager;
    private PatternBlockerUtils patternBlockerUtils;
    private Session session;
    private Transaction transaction;
    private AdBlockerProcessor processor;

    @Before
    public void setUp() {
        session = Mockito.mock(Session.class);
        Mockito.when(session.getDeviceId()).thenReturn("device:1234");
        Mockito.when(session.isBlockAds()).thenReturn(true);
        Mockito.when(session.isPatternFiltersEnabled()).thenReturn(true);

        transaction = Mockito.mock(Transaction.class);
        Mockito.when(transaction.getSession()).thenReturn(session);
        Mockito.when(transaction.getUrl()).thenReturn("https://evil.org/analytics.js");
        Mockito.when(transaction.getOriginalClientIP()).thenReturn(IpAddress.parse("192.168.3.3"));

        filter = Mockito.mock(Filter.class);
        Mockito.when(filter.filter(transaction)).thenReturn(FilterResult.block(filter));

        filterManager = Mockito.mock(FilterManager.class);
        Mockito.when(filterManager.getFilter(Mockito.any(org.eblocker.server.icap.filter.Category.class))).thenReturn(filter);

        patternBlockerUtils = Mockito.mock(PatternBlockerUtils.class);

        processor = new AdBlockerProcessor(filterManager, patternBlockerUtils);
    }

    @Test
    public void testFilterDisabled() {
        Mockito.when(session.isPatternFiltersEnabled()).thenReturn(false);
        Assert.assertTrue(processor.process(transaction));
        Mockito.verify(transaction, Mockito.never()).block();
        Mockito.verifyZeroInteractions(patternBlockerUtils);
    }

    @Test
    public void testFilterBlock() {
        Assert.assertFalse(processor.process(transaction));
        Mockito.verify(transaction).block();
        Mockito.verify(patternBlockerUtils).countBlockedDomain(Mockito.eq(Category.ADS), Mockito.any(FilterResult.class), Mockito.eq(session), Mockito.eq(transaction));
    }

    @Test
    public void testFilterNoDecision() {
        Mockito.when(filter.filter(transaction)).thenReturn(FilterResult.noDecision(filter));
        Assert.assertTrue(processor.process(transaction));
        Mockito.verify(transaction, Mockito.never()).block();
        Mockito.verifyZeroInteractions(patternBlockerUtils);
    }

    @Test
    public void testFilterPass() {
        Mockito.when(filter.filter(transaction)).thenReturn(FilterResult.pass(filter));
        Assert.assertTrue(processor.process(transaction));
        Mockito.verify(transaction, Mockito.never()).block();
        Mockito.verifyZeroInteractions(patternBlockerUtils);
    }

    @Test
    public void testDoNotOverridePass() {
        Mockito.when(transaction.getDecision()).thenReturn(Decision.PASS);
        Assert.assertTrue(processor.process(transaction));
        Mockito.verify(transaction, Mockito.never()).block();
        Mockito.verifyZeroInteractions(patternBlockerUtils);
    }

}
