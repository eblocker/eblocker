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
package org.eblocker.server.http.controller.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.IpAddressModule;
import org.eblocker.server.http.server.HttpTransactionIdentifier;
import org.mockito.Mockito;
import org.restexpress.Request;
import org.restexpress.response.RawResponseWrapper;
import org.restexpress.route.RouteMapping;
import org.restexpress.route.RouteResolver;
import org.restexpress.serialization.AbstractSerializationProvider;
import org.restexpress.serialization.json.JacksonJsonProcessor;

import java.net.URI;
import java.nio.charset.Charset;

import static org.mockito.Mockito.when;

/**
 * Utility functions for testing controller actions.
 */
public class ControllerTestUtils {

    private static ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.registerModule(new IpAddressModule());
    }

    public static String toJSON(Object object) throws JsonProcessingException {
        return mapper.writeValueAsString(object);
    }

    public static Request createRequest(String content, HttpMethod method, String uriStr) {
        ByteBuf contentBuffer = Unpooled.copiedBuffer(content, Charset.defaultCharset());

        FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uriStr, contentBuffer);

        URI uri = URI.create(uriStr);
        String host = uri.getHost();
        if (host != null) {
            if (uri.getPort() != -1) {
                host += ":" + uri.getPort();
            }
            HttpHeaders.setHost(httpRequest, host);
        }

        Request request = wrapHttpRequest(httpRequest);
        return request;
    }

    public static Request wrapHttpRequest(FullHttpRequest httpRequest) {
        org.restexpress.serialization.SerializationProvider serializationProvider = new SerializationProvider();
        RouteMapping routeMapping = new RouteMapping();
        RouteResolver routeResolver = new RouteResolver(routeMapping);
        Request request = new Request(httpRequest, routeResolver, serializationProvider);
        return request;
    }

    /**
     * Mock a request coming from a specific device. Unfortunately, we can only use
     * mocked requests, because there is no way to set the remote IP address of a
     * request that is created from a HttpRequest.
     */
    public static Request mockRequestByDevice(Device device) {
        IpAddress deviceIp = device.getIpAddresses().get(0);

        Request request = Mockito.mock(Request.class);
        setIpAddressOfMockRequest(deviceIp, request);
        return request;
    }

    public static void setIpAddressOfMockRequest(IpAddress ipAddress, Request request) {
        HttpTransactionIdentifier identifier = Mockito.mock(HttpTransactionIdentifier.class);
        when(identifier.getOriginalClientIP()).thenReturn(ipAddress);
        when(request.getAttachment("transactionIdentifier")).thenReturn(identifier);
    }

    private static class SerializationProvider extends AbstractSerializationProvider {
        SerializationProvider() {
            super();
            add(new JacksonJsonProcessor() {
                @Override
                protected void initializeMapper(ObjectMapper mapper) {
                    super.initializeMapper(mapper);
                    mapper.registerModule(new IpAddressModule());
                }
            }, new RawResponseWrapper(), true);
        }
    }
}
