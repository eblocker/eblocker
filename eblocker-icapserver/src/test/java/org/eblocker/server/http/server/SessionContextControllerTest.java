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
package org.eblocker.server.http.server;

import com.google.common.net.HttpHeaders;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.page.PageContext;
import org.eblocker.server.common.page.PageContextStore;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.session.SessionStore;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restexpress.Request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SessionContextControllerTest {
    private SessionContextController controller;
    private SessionStore sessionStore;
    private PageContextStore pageContextStore;

    @Before
    public void setUp() {
        sessionStore = Mockito.mock(SessionStore.class);
        pageContextStore = Mockito.mock(PageContextStore.class);

        controller = new SessionContextController(sessionStore, pageContextStore);
    }

    @Test
    public void getPageContext() {
        // create request
        HttpTransactionIdentifier identifier = Mockito.mock(HttpTransactionIdentifier.class);
        IpAddress ip = IpAddress.parse("10.2.3.4");
        when(identifier.getOriginalClientIP()).thenReturn(ip);

        Request getRequest = Mockito.mock(Request.class);
        when(getRequest.getAttachment("transactionIdentifier")).thenReturn(identifier);
        when(getRequest.getHeader("pageContextId")).thenReturn(null);
        String url = "http://www.where.did/i/come/from/";
        when(getRequest.getHeader(HttpHeaders.REFERER)).thenReturn(url);

        Session session = Mockito.mock(Session.class);
        when(sessionStore.getSession(identifier)).thenReturn(session);

        when(pageContextStore.get((String) null)).thenReturn(null);

        PageContext context = new PageContext(null, url, ip);

        when(pageContextStore.create(Mockito.isNull(), any(Session.class), eq(url))).thenReturn(context);
        when(pageContextStore.get(context.getId())).thenReturn(context);

        // If no context is present, one is created
        Object returnValue = controller.getPageContext(getRequest);
        assertNotNull(returnValue);
        assertEquals(context, returnValue);
        verify(pageContextStore).create(Mockito.isNull(), any(Session.class), eq(url));

        // One has been created previously
        Object returnValueSecond = controller.getPageContext(getRequest);
        assertNotNull(returnValueSecond);
        assertEquals(context, returnValueSecond);
    }

    @Test
    public void isPageContextValid() {
        // null-Request is never a valid PageContext
        assertFalse(controller.isPageContextValid(null));

        // Create request without transactionIdentifier
        Request emptyRequest = Mockito.mock(Request.class);
        when(emptyRequest.getAttachment("transactionIdentifier")).thenReturn(null);

        // Request without HttpTransactionIdentifier - invalid as well
        assertFalse(controller.isPageContextValid(emptyRequest));

        //
        // Request with valid PageContext
        //
        HttpTransactionIdentifier identifier = Mockito.mock(HttpTransactionIdentifier.class);
        IpAddress ip = IpAddress.parse("10.2.3.4");
        when(identifier.getOriginalClientIP()).thenReturn(ip);

        // Mocked page context
        String url = "http://www.where.did/i/come/from/";
        PageContext context = new PageContext(null, url, ip);
        // Mocked request
        Request validRequest = Mockito.mock(Request.class);
        when(validRequest.getAttachment("transactionIdentifier")).thenReturn(identifier);
        when(validRequest.getHeader("pageContextId")).thenReturn(context.getId());
        // Mocked pageContextStore
        when(pageContextStore.get(context.getId())).thenReturn(context);

        boolean returnValue = controller.isPageContextValid(validRequest);
        assertTrue(returnValue);

        //
        // Request with invalid PageContext (contextId wrong)
        //
        // Mocked request
        Request invalidContextIdRequest = Mockito.mock(Request.class);
        when(invalidContextIdRequest.getAttachment("transactionIdentifier")).thenReturn(identifier);
        String invalidContext = context.getId().substring(1, 2);
        when(invalidContextIdRequest.getHeader("pageContextId")).thenReturn(invalidContext);
        // Mocked pageContextStore
        when(pageContextStore.get(context.getId())).thenReturn(context);

        boolean returnValueInvalidFirst = controller.isPageContextValid(invalidContextIdRequest);
        assertFalse(returnValueInvalidFirst);

        //
        // Request with invalid PageContext (IP wrong)
        //
        // Mocked page context
        HttpTransactionIdentifier invalidIdentifier = Mockito.mock(HttpTransactionIdentifier.class);
        IpAddress invalidIp = IpAddress.parse("10.20.30.40");
        when(invalidIdentifier.getOriginalClientIP()).thenReturn(invalidIp);
        // Mocked request
        Request invalidIpRequest = Mockito.mock(Request.class);
        when(invalidIpRequest.getAttachment("transactionIdentifier")).thenReturn(invalidIdentifier);
        when(invalidIpRequest.getHeader("pageContextId")).thenReturn(context.getId());
        // Mocked pageContextStore
        when(pageContextStore.get(context.getId())).thenReturn(context);

        boolean returnValueInvalidSecond = controller.isPageContextValid(invalidIpRequest);
        assertFalse(returnValueInvalidSecond);
    }
}
