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
package org.eblocker.server.icap.filter.url;

import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.transaction.TransactionContext;
import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.FilterResult;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class UrlLineParserTest {

    private UrlLineParser parser;

    @Before
    public void setUp() {
        parser = new UrlLineParser();
    }

    @Test
    public void testParseHttpRule() {
        Filter filter = parser.parseLine("http://www.eblocker.com/index.html");
        Assert.assertEquals("eblocker.com", filter.getDomain());
        Assert.assertEquals(Decision.BLOCK, filterUrl(filter, "http://www.eblocker.com/index.html"));
        Assert.assertEquals(Decision.BLOCK, filterUrl(filter, "https://www.eblocker.com/index.html"));
        Assert.assertEquals(Decision.NO_DECISION, filterUrl(filter, "http://www.eblocker.com/index2.html"));
        Assert.assertEquals(Decision.NO_DECISION, filterUrl(filter, "http://eblocker.com"));
        Assert.assertEquals(Decision.NO_DECISION, filterUrl(filter, "https://www.eblocker.com"));
        Assert.assertEquals(Decision.NO_DECISION, filterUrl(filter, "https://www.eblocker2.com"));
        Assert.assertEquals(Decision.NO_DECISION, filterUrl(filter, "https://www.eblocker.com.ru"));

    }

    @Test
    public void testParseHttpsRule() {
        Filter filter = parser.parseLine("https://www.eblocker.com/index.html");
        Assert.assertEquals("eblocker.com", filter.getDomain());
        Assert.assertEquals(Decision.BLOCK, filterUrl(filter, "http://www.eblocker.com/index.html"));
        Assert.assertEquals(Decision.BLOCK, filterUrl(filter, "https://www.eblocker.com/index.html"));
        Assert.assertEquals(Decision.NO_DECISION, filterUrl(filter, "http://www.eblocker.com/index2.html"));
        Assert.assertEquals(Decision.NO_DECISION, filterUrl(filter, "http://eblocker.com"));
        Assert.assertEquals(Decision.NO_DECISION, filterUrl(filter, "https://www.eblocker.com"));
        Assert.assertEquals(Decision.NO_DECISION, filterUrl(filter, "https://www.eblocker2.com"));
        Assert.assertEquals(Decision.NO_DECISION, filterUrl(filter, "https://www.eblocker.com.ru"));

    }

    @Test
    public void testParseNoSchemeRule() {
        Filter filter = parser.parseLine("www.eblocker.com/index.html");
        Assert.assertEquals("eblocker.com", filter.getDomain());
        Assert.assertEquals(Decision.BLOCK, filterUrl(filter, "http://www.eblocker.com/index.html"));
        Assert.assertEquals(Decision.BLOCK, filterUrl(filter, "https://www.eblocker.com/index.html"));
        Assert.assertEquals(Decision.NO_DECISION, filterUrl(filter, "http://www.eblocker.com/index2.html"));
        Assert.assertEquals(Decision.NO_DECISION, filterUrl(filter, "http://eblocker.com"));
        Assert.assertEquals(Decision.NO_DECISION, filterUrl(filter, "https://www.eblocker.com"));
        Assert.assertEquals(Decision.NO_DECISION, filterUrl(filter, "https://www.eblocker2.com"));
        Assert.assertEquals(Decision.NO_DECISION, filterUrl(filter, "https://www.eblocker.com.ru"));
    }

    @Test
    public void testParseSubDomain() {
        Filter filter = parser.parseLine("www.eblocker.com");
        Assert.assertEquals("eblocker.com", filter.getDomain());
        Assert.assertEquals(Decision.BLOCK, filterUrl(filter, "http://www.eblocker.com/index.html"));
        Assert.assertEquals(Decision.BLOCK, filterUrl(filter, "https://www.eblocker.com/index.html"));
        Assert.assertEquals(Decision.BLOCK, filterUrl(filter, "http://www.eblocker.com/index2.html"));
        Assert.assertEquals(Decision.NO_DECISION, filterUrl(filter, "http://eblocker.com"));
        Assert.assertEquals(Decision.BLOCK, filterUrl(filter, "https://www.eblocker.com"));
        Assert.assertEquals(Decision.NO_DECISION, filterUrl(filter, "https://www.eblocker2.com"));
        Assert.assertEquals(Decision.NO_DECISION, filterUrl(filter, "https://www.eblocker.com.ru"));
    }

    @Test
    public void testParseDomain() {
        Filter filter = parser.parseLine("eblocker.com");
        Assert.assertEquals("eblocker.com", filter.getDomain());
        Assert.assertEquals(Decision.BLOCK, filterUrl(filter, "http://www.eblocker.com/index.html"));
        Assert.assertEquals(Decision.BLOCK, filterUrl(filter, "https://www.eblocker.com/index.html"));
        Assert.assertEquals(Decision.BLOCK, filterUrl(filter, "http://www.eblocker.com/index2.html"));
        Assert.assertEquals(Decision.BLOCK, filterUrl(filter, "http://eblocker.com"));
        Assert.assertEquals(Decision.BLOCK, filterUrl(filter, "https://www.eblocker.com"));
        Assert.assertEquals(Decision.NO_DECISION, filterUrl(filter, "https://www.eblocker2.com"));
        Assert.assertEquals(Decision.NO_DECISION, filterUrl(filter, "https://www.eblocker.com.ru"));
    }

    private Decision filterUrl(Filter filter, String url) {
        FilterResult result = filter.filter(createMockContext(url));
        return result.getDecision();
    }

    private TransactionContext createMockContext(String url) {
        TransactionContext context = Mockito.mock(TransactionContext.class);
        Mockito.when(context.getUrl()).thenReturn(url);
        return context;
    }

}
