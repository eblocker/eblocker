/*
 * Copyright 2025 eBlocker Open Source UG (haftungsbeschraenkt)
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
import org.eblocker.server.common.service.FeatureServiceSubscriber;
import org.eblocker.server.icap.ch.mimo.icap.IcapTransaction;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CaptivePortalCheckProcessorTest {
    private TransactionProcessor processor;
    private FeatureServiceSubscriber featureService;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        featureService = Mockito.mock(FeatureServiceSubscriber.class);
        processor = new CaptivePortalCheckProcessor(featureService);
    }

    @Test
    void noMatch() {
        enableFeature(true);
        makeTransaction("http://not.a/captive/portal/check", true);
        assertTrue(processor.process(transaction));
    }

    @Test
    void noMatchDisabled() {
        enableFeature(false);
        makeTransaction("http://not.a/captive/portal/check", true);
        assertTrue(processor.process(transaction));
    }

    @Test
    void matchGoogle() {
        enableFeature(true);
        makeTransaction("http://connectivitycheck.gstatic.com/generate_204", true);
        assertFalse(processor.process(transaction));
        assertTrue(transaction.isComplete());
        assertTrue(transaction.isResponse());
    }

    @Test
    void matchGoogleDisabled() {
        enableFeature(false);
        makeTransaction("http://connectivitycheck.gstatic.com/generate_204", true);
        assertTrue(processor.process(transaction));
        assertFalse(transaction.isComplete());
        assertFalse(transaction.isResponse());
    }

    @Test
    void matchApple() {
        enableFeature(true);
        makeTransaction("http://captive.apple.com/hotspot-detect.html", true);
        assertFalse(processor.process(transaction));
        assertTrue(transaction.isComplete());
        assertTrue(transaction.isResponse());
        assertEquals("<HTML><HEAD><TITLE>Success</TITLE></HEAD><BODY>Success</BODY></HTML>\n",
                transaction.getResponse().content().toString(StandardCharsets.US_ASCII));
    }

    @Test
    void dontInjectIfDisabled() {
        enableFeature(false);
        makeTransaction("http://captive.apple.com/hotspot-detect.html", false);

        // Important for iOS 18.4 and later:
        // Stop processing, so no icon is injected into the HTML response.
        // (An icon would trigger the captive portal dialog.)
        assertFalse(processor.process(transaction));
    }

    private void makeTransaction(String url, boolean isRequest) {
        IcapRequest request = new DefaultIcapRequest(IcapVersion.ICAP_1_0, IcapMethod.RESPMOD, url, "someHost");

        transaction = new IcapTransaction(request);
        FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, url);
        transaction.setRequest(httpRequest);

        if (!isRequest) {
            // Response can be empty, because the CaptivePortalCheckProcessor doesn't do anything with it:
            FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            httpResponse.headers().add(HttpHeaders.Names.CONTENT_TYPE, "text/html");
            transaction.setResponse(httpResponse);
        }
    }

    private void enableFeature(boolean enabled) {
        Mockito.when(featureService.getGoogleCaptivePortalRedirectorState()).thenReturn(enabled);
    }
}
