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
package org.eblocker.server.http.controller.impl;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.network.BaseURLs;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.common.transaction.TransactionCache;
import org.eblocker.server.common.transaction.TransactionContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restexpress.Request;
import org.restexpress.Response;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.startsWith;
import static org.mockito.Mockito.when;

public class RedirectControllerImplTest {
    private RedirectControllerImpl controller;
    private TransactionCache transactionCache;
    private String firefoxUserAgent = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1";
    private BaseURLs baseUrls = Mockito.mock(BaseURLs.class);

    @Before
    public void setUp() throws Exception {
        DataSource dataSource = Mockito.mock(DataSource.class);
        transactionCache = new TransactionCache(3);
        SessionStore sessionStore = Mockito.mock(SessionStore.class);
        controller = new RedirectControllerImpl(sessionStore, transactionCache, dataSource, baseUrls);

        Session session = Mockito.mock(Session.class);
        when(sessionStore.getSession(any())).thenReturn(session);
    }

    @Test
    public void redirectToSSL() {
        final String originalURL = "https://ad.tracker.com/12345?id1=432&id2=987";
        final String encodedURL = "https%3A%2F%2Fad.tracker.com%2F12345%3Fid1%3D432%26id2%3D987";

        when(baseUrls.selectURLForPage(startsWith("https:"))).thenReturn("https://192.168.1.2:3443");
        when(baseUrls.selectURLForPage(startsWith("http:"))).thenReturn("http://192.168.1.2:3000");

        Request request = ControllerTestUtils.createRequest("", HttpMethod.GET, "https://192.168.1.2:3443/redirect/prepare?url=" + encodedURL);
        request.addHeader("User-Agent", firefoxUserAgent);
        Response response = new Response();
        controller.prepare(request, response);

        // Did we get a redirect?
        assertEquals(HttpResponseStatus.MOVED_PERMANENTLY, response.getResponseStatus());

        // Check the redirect location:
        String location = response.getHeader(HttpHeaderNames.LOCATION.toString());
        String patternStr = "https://192.168.1.2:3443/dashboard/#!/redirect/([0-9a-f\\-]+)/tracker.com";
        assertTrue("Redirect location '" + location + "' does not match the expected pattern",
                location.matches(patternStr));

        // Get UUID:
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(location);
        assertTrue(matcher.matches());
        String uuid = matcher.group(1);

        // Can we retrieve the original URL from the transaction cache?
        TransactionContext transaction = transactionCache.get(UUID.fromString(uuid));
        assertEquals(originalURL, transaction.getUrl());
    }
}
