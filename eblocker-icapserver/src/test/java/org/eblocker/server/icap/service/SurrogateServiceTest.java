/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.icap.service;

import org.junit.Test;

import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SurrogateServiceTest {

    SurrogateService surrogateService = new SurrogateService();

    @Test
    public void testReadSurrogates() {
        Map<Pattern, String> urlToSurrogate = surrogateService.getUrlToSurrogate();
        assertTrue(urlToSurrogate.size() > 5);
    }

    @Test
    public void testNoMatch() {
        assertFalse(surrogateService.surrogateForBlockedUrl("foo.com").isPresent());
    }

    @Test
    public void testExactMatch() {
        assertTrue(surrogateService.surrogateForBlockedUrl("amazon-adsystem.com/aax2/amzn_ads.js").isPresent());
        assertTrue(surrogateService.surrogateForBlockedUrl("scorecardresearch.com/beacon.js").isPresent());
    }

    @Test
    public void testSubdomainMatch() {
        assertTrue(surrogateService.surrogateForBlockedUrl("something.amazon-adsystem.com/aax2/amzn_ads.js").isPresent());
    }

    @Test
    public void testQueryMatch() {
        assertTrue(surrogateService.surrogateForBlockedUrl("googletagmanager.com/gtm.js?id=GTM-W9MC85").isPresent());
    }

    @Test
    public void testRegexpMatch() {
        assertTrue(surrogateService.surrogateForBlockedUrl("facebook.net/something/sdk.js").isPresent());
        assertTrue(surrogateService.surrogateForBlockedUrl("https://www.facebook.net/something/sdk.js").isPresent());
    }

    @Test
    public void testHandlesNullUrl() {
        assertFalse(surrogateService.surrogateForBlockedUrl(null).isPresent());
    }
}
