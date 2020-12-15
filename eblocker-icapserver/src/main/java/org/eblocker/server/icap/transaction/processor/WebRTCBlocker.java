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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.registration.ProductFeature;
import org.eblocker.server.common.RequireFeature;
import org.eblocker.server.common.service.FeatureService;
import org.eblocker.server.common.service.FeatureServiceSubscriber;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class aims to block WebRTC connections, because WebRTC leaks the IP address (the local network IP as well) to the public
 * <p>
 * The usual call to establish a connection is to the constructor: var pc = new RTCPeerConnection(x,y);
 * But there are also instances you can just get from the window object:
 * window.RTCPeerConnection, window.mozRTCPeerConnection,window.webkitRTCPeerConnection, window.msRTCPeerConnection
 * These also have to be 'destroyed' = overwritten
 * <p>
 * This class extends InsertToolbarProcessor to inherit the methods to get and set the content of a Transaction object!
 * <p>
 * NOTE: Squid has to direct also the following Javascript mimetypes to the Icapserver to make this work:
 * <p>
 * acl html_response rep_mime_type text/javascript
 * acl html_response rep_mime_type application/x-javascript
 * acl html_response rep_mime_type application/javascript
 * acl html_response rep_mime_type application/ecmascript
 * acl html_response rep_mime_type text/ecmascript
 */
@RequireFeature(ProductFeature.PRO)
@Singleton
public class WebRTCBlocker extends HtmlInjectionProcessor implements TransactionProcessor {

    private static final Logger log = LoggerFactory.getLogger(WebRTCBlocker.class);

    private final FeatureService featureService;

    //public static final String MIME_JAVASCRIPT = "text/javascript";
    private final String WEBRTC_UNIVERSAL_STRING = "RTCPeerConnection";
    //FIXME: Constructors like "new RTCPeerConnection(var1.get(), var); will not be recognized."
    private final String WEBRTC_CONSTRUCTOR_REGEX = "(new RTCPeerConnection\\s?[(](\\w*|,|\\s)*[)])\\s?";
    private final String WEBRTC_SUM_REGEX = "(\\w+\\.mozRTCPeerConnection)|(\\w+\\.RTCPeerConnection)|(\\w+\\.webkitRTCPeerConnection)|(\\w+\\.msRTCPeerConnection)";

    private final Pattern WEBRTC_CONSTRUCTOR = Pattern.compile(WEBRTC_CONSTRUCTOR_REGEX);
    private final Pattern WEBRTC_SUM_WO_CONSTRUCTOR = Pattern.compile(WEBRTC_SUM_REGEX);
    private final Pattern WEBRTC_CONTAINS = Pattern.compile(WEBRTC_UNIVERSAL_STRING, Pattern.LITERAL);

    /*private final String WEBRTC_WINDOW_REGEX = "[.]*(\\w+.RTCPeerConnection)[.]*";
    private final String WEBRTC_WINDOW_MOZ_REGEX = "[.]*(\\w+.mozRTCPeerConnection)[.]*";
    private final String WEBRTC_WINDOW_WEBKIT_REGEX = "[.]*(\\w+.webkitRTCPeerConnection)[.]*";
    private final String WEBRTC_WINDOW_MS_REGEX ="[.]*(\\w+.msRTCPeerConnection)[.]*";*/
    /*private final Pattern WEBRTC_WINDOW = Pattern.compile(WEBRTC_WINDOW_REGEX);
    //private final Pattern WEBRTC_WINDOW_MOZ = Pattern.compile(WEBRTC_WINDOW_MOZ_REGEX);
    //private final Pattern WEBRTC_WINDOW_WEBKIT = Pattern.compile(WEBRTC_WINDOW_WEBKIT_REGEX);
    //private final Pattern WEBRTC_WINDOW_MS = Pattern.compile(WEBRTC_WINDOW_MS_REGEX);*/

    private final Set<Pattern> patterns;
    private static final String REPLACE = "undefined";

    @Inject
    public WebRTCBlocker(FeatureServiceSubscriber featureService) {
        super();

        this.featureService = featureService;

        patterns = new HashSet<>();
        patterns.add(WEBRTC_CONSTRUCTOR);
        patterns.add(WEBRTC_SUM_WO_CONSTRUCTOR);
        /*patterns.add(WEBRTC_WINDOW);
        patterns.add(WEBRTC_WINDOW_MOZ);
        patterns.add(WEBRTC_WINDOW_WEBKIT);
        patterns.add(WEBRTC_WINDOW_MS);*/
    }

    @Override
    public boolean process(Transaction transaction) {
        if (featureService.getWebRTCBlockingState()) {
            if (transaction.isPreview())
                return false;

            StringBuilder content = transaction.getContent();

            log.debug("Looking for WebRTC connection attempt in : {}", transaction.getUrl());
            log.debug("Content type of transaction: {}", transaction.getContentType());

            if (content != null && containsWebRTC(content)) {//only start replacing, if the file contains the string WEBRTC_UNIVERSAL_STRING
                findAndReplace(content);
            }
        }
        return true;
    }

    private boolean containsWebRTC(StringBuilder content) {
        Matcher matcher = WEBRTC_CONTAINS.matcher(content);
        return matcher.find();
    }

    /**
     * Loop over all Regular Expressions and replace all their matches with REPLACE
     *
     * @return the content text with all the replaced substrings
     */
    private void findAndReplace(StringBuilder content) {
        patterns.forEach(pattern -> replace(content, pattern, REPLACE));
    }

    private void replace(StringBuilder sb, Pattern pattern, String replacement) {
        Matcher matcher = pattern.matcher(sb);
        int start = 0;
        while (matcher.find(start)) {
            sb.replace(matcher.start(), matcher.end(), replacement);
            start = matcher.start() + replacement.length();
        }
    }

}
