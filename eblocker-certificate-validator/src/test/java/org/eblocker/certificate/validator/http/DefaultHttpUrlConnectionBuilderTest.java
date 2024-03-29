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
package org.eblocker.certificate.validator.http;

import com.google.common.io.ByteStreams;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.NottableString;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultHttpUrlConnectionBuilderTest {

    private static ClientAndServer SERVER;

    private DefaultHttpUrlConnectionBuilder builder;

    @BeforeAll
    public static void beforeClass() {
        SERVER = new ClientAndServer(18080);
    }

    @AfterAll
    public static void afterClass() {
        SERVER.stop();
    }

    @BeforeEach
    public void setUp() {
        builder = new DefaultHttpUrlConnectionBuilder();
    }

    @AfterEach
    public void tearDown() {
        SERVER.reset();
    }

    @Test
    void testGetRequest() throws IOException {
        byte[] responseBody = "<xml></xml>".getBytes();
        SERVER
                .when(HttpRequest.request()
                        .withMethod("GET")
                        .withHeader("Accept", "application/xml")
                        .withPath("/get"))
                .respond(HttpResponse.response()
                        .withStatusCode(200)
                        .withBody(responseBody));

        HttpURLConnection connection = builder
                .setUrl("http://127.0.0.1:18080/get")
                .setRequestProperty("Accept", "application/xml")
                .get();

        assertEquals(200, connection.getResponseCode());
        assertArrayEquals(responseBody, ByteStreams.toByteArray(connection.getInputStream()));
    }

    @Test
    void testPostRequest() throws IOException {
        byte[] requestBody = "test test test".getBytes();
        byte[] responseBody = "<xml></xml>".getBytes();
        SERVER
                .when(HttpRequest.request()
                        .withMethod("POST")
                        .withHeader("Accept", "application/xml")
                        .withHeader("Content-Type", "application/text")
                        .withBody(requestBody)
                        .withPath("/post"))
                .respond(HttpResponse.response()
                        .withStatusCode(200)
                        .withBody(responseBody));

        HttpURLConnection connection = builder
                .setUrl("http://127.0.0.1:18080/post")
                .setRequestProperty("Accept", "application/xml")
                .setRequestProperty("Content-Type", "application/text")
                .post(requestBody);

        assertEquals(200, connection.getResponseCode());
        assertArrayEquals(responseBody, ByteStreams.toByteArray(connection.getInputStream()));
    }

    @Test
    void testIfModifiedSince() throws IOException {
        SERVER
                .when(HttpRequest.request()
                        .withMethod("GET")
                        .withPath("/get")
                        .withHeader(NottableString.not("If-Modified-Since"), NottableString.string(".*")))
                .respond(HttpResponse.response()
                        .withStatusCode(200)
                        .withBody("header-not-present"));

        SERVER
                .when(HttpRequest.request()
                        .withMethod("GET")
                        .withPath("/get")
                        .withHeader("If-Modified-Since", ""))
                .respond(HttpResponse.response()
                        .withStatusCode(200)
                        .withBody("header-present"));

        HttpURLConnection connectionWithoutIfModifiedSince = builder.setUrl("http://127.0.0.1:18080/get").get();
        HttpURLConnection connectionWithIfModifiedSince = builder.setUrl("http://127.0.0.1:18080/get").setIfModifiedSince(System.currentTimeMillis()).get();

        assertEquals(200, connectionWithoutIfModifiedSince.getResponseCode());
        assertEquals("header-not-present", new String(ByteStreams.toByteArray(connectionWithoutIfModifiedSince.getInputStream())));
        assertEquals(200, connectionWithIfModifiedSince.getResponseCode());
        assertEquals("header-present", new String(ByteStreams.toByteArray(connectionWithIfModifiedSince.getInputStream())));
    }

    @Test
    void testReadTimeout() throws IOException {
        SERVER
                .when(HttpRequest.request()
                        .withMethod("GET")
                        .withPath("/get"))
                .respond(HttpResponse.response()
                        .withDelay(TimeUnit.SECONDS, 1)
                        .withStatusCode(204));

        HttpURLConnection connection = builder.setUrl("http://127.0.0.1:18080/get").setReadTimeout(50).get();
        Assertions.assertThrows(SocketTimeoutException.class, connection::getResponseCode);
    }

    @Test
    void testConnectionTimeout() throws IOException {
        // 192.0.2.0/24 is reserved for documentation according to RFC 5737
        HttpURLConnection connection = builder.setUrl("http://192.0.2.1:18080/get").setConnectionTimeout(50).get();
        Assertions.assertThrows(SocketTimeoutException.class, connection::getResponseCode);
    }
}
