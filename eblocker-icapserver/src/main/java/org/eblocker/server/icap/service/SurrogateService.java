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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.eblocker.server.common.util.ResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SurrogateService {

    private static final String SURROGATES_PATH = "surrogates/";
    private final Logger log = LoggerFactory.getLogger(SurrogateService.class);

    private Map<Pattern, String> urlToSurrogate = new HashMap<>();
    private final Map<String, byte[]> scriptCache = new ConcurrentHashMap<>();

    public SurrogateService() {
        readSurrogatesHJson();
    }

    public Optional<FullHttpResponse> surrogateForBlockedUrl(String url) {
        try {
            return surrogateForUrl(url).map(this::surrogateAsHttpResponse);
        } catch (Exception e) {
            log.error("Error while searching for surrogate", e);
            return Optional.empty();
        }
    }

    private Optional<String> surrogateForUrl(String url) {
        return urlToSurrogate.entrySet().stream()
                .filter(e -> matchesSurrogate(e.getKey(), url))
                .peek(e -> log.debug("Sending surrogate {} for {}", e.getValue(), url))
                .findFirst()
                .map(Map.Entry::getValue);
    }

    private boolean matchesSurrogate(Pattern surrogateRegexp, String url) {
        return surrogateRegexp.matcher(url).find();
    }

    private FullHttpResponse surrogateAsHttpResponse(String surrogate) {
        byte[] jsBytes = surrogateJavascript(surrogate);
        FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(jsBytes));
        httpResponse.headers().add(HttpHeaderNames.CONTENT_TYPE, "application/javascript");
        httpResponse.headers().add(HttpHeaderNames.CONTENT_LENGTH, jsBytes.length);
        return httpResponse;
    }

    private byte[] surrogateJavascript(String surrogate) {
        return scriptCache.computeIfAbsent(surrogate, this::loadScript);
    }

    private byte[] loadScript(String surrogate) {
        return ResourceUtil.loadResource(SURROGATES_PATH + surrogate).getBytes();
    }

    private void readSurrogatesHJson() {
        try {
            String js = ResourceUtil.loadResource("surrogates/mapping.json");
            ObjectMapper mapper = createObjectMapper();
            TypeReference<Map<String, List<SurrogateRule>>> typeReference = new TypeReference<>() {
            };
            Map<String, List<SurrogateRule>> surrogateInfos = mapper.readValue(js, typeReference);
            urlToSurrogate = surrogateInfos.values().stream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.toMap(l -> asPattern(l.regexRule), l -> l.surrogate));
        } catch (JsonProcessingException e) {
            log.error("Error while reading mapping.json", e);
        }
    }

    private Pattern asPattern(String surrogateRegexp) {
        return Pattern.compile(surrogateRegexp);
    }

    private ObjectMapper createObjectMapper() {
        return new ObjectMapper();
    }

    /* testing */ Map<Pattern, String> getUrlToSurrogate() {
        return urlToSurrogate;
    }
}
