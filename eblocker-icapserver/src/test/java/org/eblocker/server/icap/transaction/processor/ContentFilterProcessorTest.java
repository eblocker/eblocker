/* SPDX-License-Identifier: EUPL-1.2 */
package org.eblocker.server.icap.transaction.processor;

import org.eblocker.server.common.service.FilterStatisticsService;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.icap.filter.content.ContentFilterService;
import org.eblocker.server.icap.transaction.Injections;
import org.eblocker.server.icap.transaction.Transaction;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class ContentFilterProcessorTest {
    @Test
    public void earlyOnlyScriptletIsInjectedAndCounted() {
        ContentFilterService filters = Mockito.mock(ContentFilterService.class);
        FilterStatisticsService statistics = Mockito.mock(FilterStatisticsService.class);
        Session session = Mockito.mock(Session.class);
        Transaction transaction = Mockito.mock(Transaction.class);
        Injections injections = new Injections();
        Mockito.when(transaction.isResponse()).thenReturn(true);
        Mockito.when(transaction.getContentType()).thenReturn("text/html");
        Mockito.when(transaction.getSession()).thenReturn(session);
        Mockito.when(transaction.getUrl()).thenReturn("https://example.com/page");
        Mockito.when(transaction.getInjections()).thenReturn(injections);
        Mockito.when(session.isPatternFiltersEnabled()).thenReturn(true);
        Mockito.when(filters.getHtmlToInject("example.com")).thenReturn("");
        Mockito.when(filters.getEarlyHtmlToInject("example.com")).thenReturn("<script type=\"text/javascript\">window.testFlag = true;</script>");

        Assert.assertTrue(new ContentFilterProcessor(filters, statistics).process(transaction));

        Assert.assertTrue(injections.getInjections().isEmpty());
        Assert.assertEquals(1, injections.getEarlyInjections().size());
        String nonce = Mockito.mockingDetails(transaction).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("setCspNonce"))
                .map(invocation -> (String) invocation.getArgument(0))
                .findFirst().orElseThrow(AssertionError::new);
        Assert.assertTrue(injections.getEarlyInjections().get(0).contains("nonce=\"" + nonce + "\""));
        Mockito.verify(statistics).countBlocked("pattern", null, "CONTENT");
    }
}
