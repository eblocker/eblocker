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
package org.eblocker.server.common.data;

import org.eblocker.server.common.data.RecordedUrl.WhitelistRecommendation;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

public class RecordedUrlTest {

    // Test getter, setter, constructor
    @Test
    public void testConstructors() {
        String ip = "1.2.3.4";
        String domain = "www.doma.in";
        RecordedSSLHandshake handshake = Mockito.mock(RecordedSSLHandshake.class);
        when(handshake.getIP()).thenReturn(ip);
        when(handshake.getServername()).thenReturn(domain);

        RecordedUrl url = new RecordedUrl(handshake);
        assertNotNull(url);
        assertEquals(ip, url.getRecordedIp());
        assertEquals(domain, url.getRecordedDomain());

        RecordedUrl urlSecond = new RecordedUrl(ip, domain);
        assertNotNull(urlSecond);
        assertEquals(ip, urlSecond.getRecordedIp());
        assertEquals(domain, urlSecond.getRecordedDomain());
    }

    @Test
    public void adjustWhitelistRecommendationCorrespondingDataObsevered() {
        RecordedUrl url = new RecordedUrl("1.2.3.4", "www.doma.in");
        assertEquals(WhitelistRecommendation.RECOMMENDATION_NONE, url.getWhitelistRecommendation());
        url.adjustWhitelistRecommendation(true);
        assertEquals(WhitelistRecommendation.RECOMMENDATION_BUMP, url.getWhitelistRecommendation());
        url.adjustWhitelistRecommendation(false);
        assertEquals(WhitelistRecommendation.RECOMMENDATION_WHITELIST, url.getWhitelistRecommendation());
    }

    @Test
    public void adjustWhitelistRecommendationNoCorrespondingDataObserved() {
        RecordedUrl url = new RecordedUrl("1.2.3.4", "www.doma.in");
        assertEquals(WhitelistRecommendation.RECOMMENDATION_NONE, url.getWhitelistRecommendation());
        url.adjustWhitelistRecommendation(false);
        assertEquals(WhitelistRecommendation.RECOMMENDATION_WHITELIST, url.getWhitelistRecommendation());
    }

}
