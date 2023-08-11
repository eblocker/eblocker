/*
 * Copyright 2023 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.data;

import org.junit.Assert;
import org.junit.Test;

public class ContentSecurityPolicyTest {
    private static final String CONTROLBAR_URL = "https://controlbar.eblocker.org:3443";
    private static final String NONCE = "755c66b3f7674e309b0c09561c9c6b2c";

    @Test
    public void testFromString() {
        roundTrip("default-src 'self'");
        roundTrip("default-src 'self'; img-src 'self' data: *.example.com");

        // some normalization takes place:
        roundTrip("DEFAULT-src \t 'self'", "default-src 'self'");
    }

    @Test
    public void testInsertControlBarUrl() {
        String cspIn = "default-src 'self'; " +
                "script-src 'self' 'unsafe-inline' https://www.google.com/recaptcha/ https://www.gstatic.com/recaptcha/ https://cdnjs.cloudflare.com/; " +
                "frame-src 'self' https://www.google.com/recaptcha/; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' https://account.ycombinator.com; " +
                "frame-ancestors 'self'";
        String cspOut = "default-src 'self'; " +
                "script-src 'self' 'unsafe-inline' https://www.google.com/recaptcha/ https://www.gstatic.com/recaptcha/ https://cdnjs.cloudflare.com/; " +
                "frame-src 'self' https://www.google.com/recaptcha/ " + CONTROLBAR_URL + "; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' https://account.ycombinator.com " + CONTROLBAR_URL + "; " +
                "frame-ancestors 'self'; " +
                "connect-src 'self' " + CONTROLBAR_URL; // 'self' is copied from default-src
        insertControlBar(cspIn, cspOut);
    }

    @Test
    public void testInsertNonce() {
        // Nonce must be used to allow injected ControlBar JavaScript:
        String cspIn = "script-src 'self' https://*.example.com";
        String cspOut = "script-src 'self' https://*.example.com; script-src-elem 'self' https://*.example.com 'nonce-" + NONCE + "'";
        insertControlBar(cspIn, cspOut);

        cspIn  = "script-src 'self'; script-src-elem 'self' https://*.example.com";
        cspOut = "script-src 'self'; script-src-elem 'self' https://*.example.com 'nonce-" + NONCE + "'";
        insertControlBar(cspIn, cspOut);

        // Nonce may not be used if 'unsafe-inline' is allowed.
        // Modern browsers ignore 'unsafe-inline' if a nonce is specified, so the site would break.
        String csp = "script-src 'self' 'unsafe-inline'";
        insertControlBar(csp, csp);

        csp = "script-src-elem 'self' 'unsafe-inline'";
        insertControlBar(csp, csp);

        // But if the site itself has already set a nonce and has set 'unsafe-inline',
        // we also have to add our nonce, because modern browsers ignore the 'unsafe-inline'.
        cspIn  = "script-src-elem 'nonce-abcd' 'unsafe-inline'";
        cspOut = "script-src-elem 'nonce-abcd' 'unsafe-inline' 'nonce-" + NONCE + "'";
        insertControlBar(cspIn, cspOut);

        cspIn  = "script-src 'nonce-abcd' 'unsafe-inline'";
        cspOut = "script-src 'nonce-abcd' 'unsafe-inline'; " +
                "script-src-elem 'nonce-abcd' 'unsafe-inline' 'nonce-" + NONCE + "'";
        insertControlBar(cspIn, cspOut);
    }

    @Test
    public void testEmpty() {
        insertControlBar("", "");
        insertControlBar(";", "");
    }

    @Test
    public void testNoValue() {
        // This key has no value:
        String csp = "upgrade-insecure-requests";
        insertControlBar(csp, csp);
    }
    @Test
    public void testNoDefault() {
        String cspIn = "img-src 'self' https://account.ycombinator.com";
        String cspOut = "img-src 'self' https://account.ycombinator.com " + CONTROLBAR_URL;
        // note that connect-src and frame-src are not set, because setting them to CONTROLBAR_URL would restrict any other sources
        insertControlBar(cspIn, cspOut);
    }

    @Test
    public void testNone() {
        String cspIn = "frame-src 'none'";
        String cspOut = "frame-src " + CONTROLBAR_URL;
        insertControlBar(cspIn, cspOut);
    }

    private void insertControlBar(String cspIn, String cspOut) {
        ContentSecurityPolicy csp = ContentSecurityPolicy.from(cspIn);
        csp.allowControlBar(CONTROLBAR_URL, NONCE);
        Assert.assertEquals(cspOut, csp.toString());
    }

    private void roundTrip(String csp) {
        Assert.assertEquals(csp, ContentSecurityPolicy.from(csp).toString());
    }

    private void roundTrip(String csp, String expected) {
        Assert.assertEquals(expected, ContentSecurityPolicy.from(csp).toString());
    }
}
