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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.eblocker.server.common.service.FeatureService;
import org.eblocker.server.common.service.FeatureServiceSubscriber;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Map.entry;

/**
 * Responds to captive portal check URLs if the feature "GoogleCaptivePortalRedirector" is enabled.
 *
 * If the feature is not enabled and the transaction is a response, stop further processing, so the
 * eBlocker icon is not injected into the HTML. This avoids triggering the client's
 * captive portal check service (e.g. on iOS 18.4 and later).
 */
@Singleton
public class CaptivePortalCheckProcessor implements TransactionProcessor {
    private final FeatureService featureService;
    private final Map<String, Supplier<FullHttpResponse>> captivePortalCheckResponders;

    // Responder for Android:
    private final Supplier<FullHttpResponse> generate204 = () -> {
        FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT);
        httpResponse.headers().add(HttpHeaders.Names.CONTENT_LENGTH, 0);
        httpResponse.headers().add("Cross-Origin-Resource-Policy", "cross-origin");
        return httpResponse;
    };

    // Responder for Ubuntu:
    private final Supplier<FullHttpResponse> generate204Ubuntu = () -> {
        FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT);
        httpResponse.headers().add("x-networkmanager-status", "online");
        return httpResponse;
    };

    // Create a responder for HTML:
    private Supplier<FullHttpResponse> htmlGenerator(String html) {
        return () -> {
            byte[] data = html.getBytes(StandardCharsets.UTF_8);
            FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(data));
            httpResponse.headers().add(HttpHeaders.Names.CONTENT_TYPE, "text/html");
            httpResponse.headers().add(HttpHeaders.Names.CONTENT_LENGTH, data.length);
            return httpResponse;
        };
    }

    // Responder for Apple:
    private final Supplier<FullHttpResponse> generateSuccessHtml = htmlGenerator("<HTML><HEAD><TITLE>Success</TITLE></HEAD><BODY>Success</BODY></HTML>\n");

    private final Supplier<FullHttpResponse> generateSuccessApple = () -> {
        FullHttpResponse httpResponse = generateSuccessHtml.get();
        httpResponse.headers().add(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        return httpResponse;
    };

    // Create a responder for plain text:
    private Supplier<FullHttpResponse> textGenerator(String text) {
        return () -> {
            byte[] data = text.getBytes(StandardCharsets.US_ASCII);
            FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(data));
            httpResponse.headers().add(HttpHeaders.Names.CONTENT_TYPE, "text/plain");
            httpResponse.headers().add(HttpHeaders.Names.CONTENT_LENGTH, data.length);
            return httpResponse;
        };
    }

    private Map<String, Supplier<FullHttpResponse>> mapUrlsToResponders(boolean simulateCaptivePortal) {
        Supplier<FullHttpResponse> generateMsftConnectTest = textGenerator("Microsoft Connect Test");
        Supplier<FullHttpResponse> generateSuccessFirefox = textGenerator("success\n");

        Map<String, Supplier<FullHttpResponse>> responders = Map.ofEntries(
                // Android:
                entry("http://clients3.google.com/generate_204", generate204),
                entry("http://connectivitycheck.gstatic.com/generate_204", generate204),
                // Apple:
                entry("http://captive.apple.com/hotspot-detect.html", generateSuccessApple),
                entry("http://www.apple.com/library/test/success.html", generateSuccessApple),
                // Microsoft:
                entry("http://www.msftconnecttest.com/connecttest.txt", generateMsftConnectTest),
                entry("http://ipv6.msftconnecttest.com/connecttest.txt", generateMsftConnectTest),
                entry("http://www.msftncsi.com/ncsi.txt", textGenerator("Microsoft NCSI")),
                // Firefox:
                entry("http://detectportal.firefox.com/canonical.html", htmlGenerator("<meta http-equiv=\"refresh\" content=\"0;url=https://support.mozilla.org/kb/captive-portal\"/>")),
                entry("http://detectportal.firefox.com/success.txt?ipv4", generateSuccessFirefox),
                entry("http://detectportal.firefox.com/success.txt?ipv6", generateSuccessFirefox),
                // Ubuntu:
                entry("http://connectivity-check.ubuntu.com/", generate204Ubuntu)
        );
        if (simulateCaptivePortal) {
            /*
            For testing how the client reacts to captive portals:
            - Go to "Settings / Blocker / Advanced Settings / Captive Portal Check" and enable "Block Requests..."
            - Set "captivePortal.simulate=true" in configuration.properties
            - Restart ICAP server.
            The eBlocker will now respond with an HTML page to all of the above URLs.
             */
            Supplier<FullHttpResponse> generateCaptivePortalResponse = htmlGenerator("<html><body><h1>eBlocker Captive Portal Simulator</h1><p>Welcome to eBlocker!</p></body></html>");
            responders = responders.keySet().stream().collect(Collectors.toUnmodifiableMap(Function.identity(), url -> generateCaptivePortalResponse));
        }
        return responders;
    }

    @Inject
    public CaptivePortalCheckProcessor(FeatureServiceSubscriber featureService, @Named("captivePortal.simulate") boolean simulateCaptivePortal) {
        this.featureService = featureService;
        this.captivePortalCheckResponders = mapUrlsToResponders(simulateCaptivePortal);
    }

    @Override
    public boolean process(Transaction transaction) {
        if (!captivePortalCheckResponders.containsKey(transaction.getUrl())) {
            return true;
        }

        if (transaction.isRequest()) {
            if (featureService.getGoogleCaptivePortalRedirectorState()) {
                generateResponse(transaction);
                return false;
            } else {
                return true; // allow request to captive portal check URL
            }
        } else {
            return false; // do not inject eBlocker icon (or other stuff) into response
        }
    }

    private void generateResponse(Transaction transaction) {
        Supplier<FullHttpResponse> responder = captivePortalCheckResponders.get(transaction.getUrl());
        transaction.setResponse(responder.get());
        transaction.setComplete(true);
    }
}
