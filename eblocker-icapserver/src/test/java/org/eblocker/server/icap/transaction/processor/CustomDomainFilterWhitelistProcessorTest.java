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

import org.eblocker.server.common.blacklist.CollectionFilter;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.icap.filter.FilterResult;
import org.eblocker.server.icap.service.CustomDomainFilterWhitelistService;
import org.eblocker.server.icap.transaction.Transaction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;

public class CustomDomainFilterWhitelistProcessorTest {

    private CustomDomainFilterWhitelistService customDomainFilterWhitelistService;
    private CustomDomainFilterWhitelistProcessor processor;

    private Session session;
    private Transaction transaction;

    @Before
    public void setUp() {
        customDomainFilterWhitelistService = Mockito.mock(CustomDomainFilterWhitelistService.class);
        Mockito.when(customDomainFilterWhitelistService.getWhitelistFilter(1)).thenReturn(new CollectionFilter<>(1, Collections.singleton("evil.tracker.com")));

        session = Mockito.mock(Session.class);
        Mockito.when(session.isPatternFiltersEnabled()).thenReturn(true);
        Mockito.when(session.getUserId()).thenReturn(1);

        transaction = Mockito.mock(Transaction.class);
        Mockito.when(transaction.getSession()).thenReturn(session);

        processor = new CustomDomainFilterWhitelistProcessor(customDomainFilterWhitelistService);
    }

    @Test
    public void testNotWhitelistedDomain() {
        Mockito.when(transaction.getUrl()).thenReturn("https://rainbow.com/unicorn.js");

        Assert.assertTrue(processor.process(transaction));
        Mockito.verify(transaction, Mockito.never()).setFilterResult(Mockito.any(FilterResult.class));
    }

    @Test
    public void testWhitelistedDomain() {
        Mockito.when(transaction.getUrl()).thenReturn("https://evil.tracker.com/dubious.js");

        Assert.assertTrue(processor.process(transaction));

        ArgumentCaptor<FilterResult> captor = ArgumentCaptor.forClass(FilterResult.class);
        Mockito.verify(transaction).setFilterResult(captor.capture());
        Assert.assertEquals(Decision.PASS, captor.getValue().getDecision());
    }

    @Test
    public void testAdvancedFilterDisabled() {
        Mockito.when(session.isPatternFiltersEnabled()).thenReturn(false);
        Mockito.when(transaction.getUrl()).thenReturn("https://evil.tracker.com/dubious.js");

        Assert.assertTrue(processor.process(transaction));
        Mockito.verify(transaction, Mockito.never()).setFilterResult(Mockito.any(FilterResult.class));
    }

}
