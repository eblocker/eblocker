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
package org.eblocker.server.http.security;

import io.netty.handler.codec.http.HttpHeaderNames;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restexpress.Request;
import org.restexpress.exception.UnauthorizedException;
import org.restexpress.route.Route;

public class SecurityProcessorTest {
    private JsonWebTokenHandler tokenHandler;
    private SecurityProcessor processor;
    private Request request;
    private Route route;

    @Before
    public void setUp() {
        tokenHandler = Mockito.mock(JsonWebTokenHandler.class);
        processor = new SecurityProcessor(tokenHandler);
        request = Mockito.mock(Request.class);
        route = Mockito.mock(Route.class);
        Mockito.when(request.getResolvedRoute()).thenReturn(route);
    }

    @Test
    public void testPublic() {
        Mockito.when(route.getName()).thenReturn("public.something");
        processor.process(request);
    }

    @Test(expected = UnauthorizedException.class)
    public void noToken() {
        Mockito.when(route.getName()).thenReturn("adminconsole.something");
        processor.process(request);
    }

    @Test(expected = UnauthorizedException.class)
    public void badToken() {
        Mockito.when(route.getName()).thenReturn("adminconsole.something");
        Mockito.when(request.getHeader(HttpHeaderNames.AUTHORIZATION.toString())).thenReturn("my token");
        processor.process(request);
    }

    @Test
    public void validToken() {
        Mockito.when(route.getName()).thenReturn("adminconsole.something");
        Mockito.when(request.getHeader(HttpHeaderNames.AUTHORIZATION.toString())).thenReturn("Bearer 1234");
        Mockito.when(tokenHandler.verifyToken("1234")).thenReturn(createTokenInfo(true));
        processor.process(request);
        Mockito.verify(tokenHandler).verifyToken("1234");
    }

    @Test(expected = UnauthorizedException.class)
    public void invalidToken() {
        Mockito.when(route.getName()).thenReturn("adminconsole.something");
        Mockito.when(request.getHeader(HttpHeaderNames.AUTHORIZATION.toString())).thenReturn("Bearer 666");
        Mockito.when(tokenHandler.verifyToken("666")).thenThrow(new UnauthorizedException());
        processor.process(request);
    }

    @Test
    public void unauthenticatedTokenNoAuthenticationRequired() {
        Mockito.when(route.getName()).thenReturn("adminconsole.something");
        Mockito.when(route.isFlagged(SecurityProcessor.NO_AUTHENTICATION_REQUIRED)).thenReturn(true);
        Mockito.when(request.getHeader(HttpHeaderNames.AUTHORIZATION.toString())).thenReturn("Bearer 1234");
        Mockito.when(tokenHandler.verifyToken("1234")).thenReturn(createTokenInfo(false));
        processor.process(request);
        Mockito.verify(tokenHandler).verifyToken("1234");
    }

    @Test(expected = UnauthorizedException.class)
    public void unauthenticatedTokenAuthenticationRequired() {
        Mockito.when(route.getName()).thenReturn("adminconsole.something");
        Mockito.when(request.getHeader(HttpHeaderNames.AUTHORIZATION.toString())).thenReturn("Bearer 1234");
        Mockito.when(tokenHandler.verifyToken("1234")).thenReturn(createTokenInfo(false));
        processor.process(request);
        Mockito.verify(tokenHandler).verifyToken("1234");
    }

    private TokenInfo createTokenInfo(boolean authenticated) {
        return new TokenInfo(AppContext.ADMINCONSOLE, 1000L, 1000L, authenticated);
    }
}
