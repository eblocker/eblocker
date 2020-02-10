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

import org.eblocker.server.common.network.BaseURLs;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Date;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class JsonWebTokenHandlerTest {

    private static final String HTTPS_URL = "my-https-url";

    private BaseURLs baseURLs;

    @Before
    public void init() {
        baseURLs = Mockito.mock(BaseURLs.class);
        when(baseURLs.getHttpsURL()).thenReturn(HTTPS_URL);
    }
    @Test
    public void test() {
        AppContext appContext = AppContext.CONSOLE;
        long validity = 3600;
        Date now = new Date();
        long expectedExpiry = now.getTime() / 1000L + validity;


        JsonWebTokenHandler jsonWebTokenHandler = new JsonWebTokenHandler(
            600,
                600,
                baseURLs
        );

        JsonWebToken jsonWebToken = jsonWebTokenHandler.generateToken(true, appContext, validity, true);

        assertNotNull(jsonWebToken);
        assertFalse(jsonWebToken.getToken().isEmpty());
        assertEquals(appContext, jsonWebToken.getAppContext());
        assertEquals(true, jsonWebToken.isPasswordRequired());
        // This test method should not take more than 5 seconds...
        assertTrue(Math.abs(expectedExpiry - jsonWebToken.getExpiresOn()) < 5);


        TokenInfo tokenInfo = jsonWebTokenHandler.verifyToken(jsonWebToken.getToken());

        assertNotNull(tokenInfo);
        assertEquals(appContext, tokenInfo.getAppContext());
        assertEquals(validity, tokenInfo.getTokenValiditySeconds());
        // This test method should not take more than 5 seconds...
        assertTrue(Math.abs(expectedExpiry - tokenInfo.getExpiresOn()) < 5);

    }

    @Test
    public void testSpecialTokens() {
        long systemTokenValidity = 1234L;
        long squidTokenValidity = 5678L;

        JsonWebTokenHandler jsonWebTokenHandler = new JsonWebTokenHandler(
                squidTokenValidity,
                systemTokenValidity,
                baseURLs
        );

        JsonWebToken jsonWebToken = jsonWebTokenHandler.generateSystemToken();
        TokenInfo tokenInfo = jsonWebTokenHandler.verifyToken(jsonWebToken.getToken());

        assertEquals(AppContext.SYSTEM, tokenInfo.getAppContext());
        assertEquals(systemTokenValidity, tokenInfo.getTokenValiditySeconds());


        jsonWebToken = jsonWebTokenHandler.generateSquidToken();
        tokenInfo = jsonWebTokenHandler.verifyToken(jsonWebToken.getToken());

        assertEquals(AppContext.SQUID_ERROR, tokenInfo.getAppContext());
        assertEquals(squidTokenValidity, tokenInfo.getTokenValiditySeconds());
    }
}
