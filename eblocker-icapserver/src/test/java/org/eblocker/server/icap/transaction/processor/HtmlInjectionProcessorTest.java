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

import ch.mimo.netty.handler.codec.icap.DefaultIcapRequest;
import ch.mimo.netty.handler.codec.icap.IcapMethod;
import ch.mimo.netty.handler.codec.icap.IcapRequest;
import ch.mimo.netty.handler.codec.icap.IcapVersion;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.page.PageContext;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.icap.ch.mimo.icap.IcapTransaction;
import org.eblocker.server.icap.transaction.ContentEncoding;
import org.eblocker.server.icap.transaction.Injections;
import org.eblocker.server.icap.transaction.Transaction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class HtmlInjectionProcessorTest {
    private static final String html = "<html><body></body></html>";
    private static final String injection1 = "template";
    private static final String injection2 = "funkyscript";
    private static final String htmlWithInsertedToolbar = "<html><body>templatefunkyscript</body></html>";
    private static final String uri = "uri";
    private static final IpAddress IP_ADDRESS = IpAddress.parse("1.2.3.4");

    private HtmlInjectionProcessor processor;
    private Session session;
    private Injections injections;

    @Before
    public void setUp() throws Exception {
        processor = new HtmlInjectionProcessor();

        injections = new Injections();
        injections.inject(injection1);
        injections.inject(injection2);

        session = Mockito.mock(Session.class);
        Mockito.when(session.getIp()).thenReturn(IpAddress.parse("192.168.2.11"));

        PageContext pageContext = new PageContext(null, uri, IP_ADDRESS);
        Mockito.when(session.createPageContext(null, uri)).thenReturn(pageContext);
    }

    private Transaction makeTransaction(StringBuilder content, ContentEncoding contentEncoding, Session session, PageContext pageContext, Injections injections) {
        IcapRequest request = new DefaultIcapRequest(IcapVersion.ICAP_1_0, IcapMethod.RESPMOD, uri, "myhost");

        Transaction transaction = new IcapTransaction(request);
        FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

        httpResponse.headers().add(HttpHeaders.Names.CONTENT_TYPE, "text/html");

        if (contentEncoding != null) {
            httpResponse.headers().add("Content-Encoding", contentEncoding);
        }

        FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        transaction.setRequest(httpRequest);
        transaction.setResponse(httpResponse);

        transaction.setSession(session);
        transaction.setPageContext(pageContext);
        transaction.setInjections(injections);
        transaction.setContentEncoding(contentEncoding);
        transaction.setContent(content);

        return transaction;
    }

    @Test
    public void plainContent() {
        PageContext pageContext = new PageContext(null, "http://foo.bar", IP_ADDRESS);
        Transaction transaction = makeTransaction(new StringBuilder(html), null, session, pageContext, injections);
        processor.process(transaction);
        Assert.assertEquals(htmlWithInsertedToolbar, transaction.getContent().toString());
    }

    @Test
    public void plainContentImplicitEndOfBody() {
        String html = "<html><div>hello</div></html>";
        PageContext pageContext = new PageContext(null, "http://foo.bar", IP_ADDRESS);
        Transaction transaction = makeTransaction(new StringBuilder(html), null, session, pageContext, injections);
        processor.process(transaction);
        Assert.assertEquals("<html><div>hello</div>templatefunkyscript</html>", transaction.getContent().toString());
    }

}
