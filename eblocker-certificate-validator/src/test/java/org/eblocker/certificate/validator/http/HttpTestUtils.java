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

import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;

public class HttpTestUtils {

    public static HttpUrlConnectionBuilder createConnectionBuilderMock() throws MalformedURLException {
        HttpUrlConnectionBuilder connectionBuilder = Mockito.mock(HttpUrlConnectionBuilder.class);
        Mockito.when(connectionBuilder.setUrl(Mockito.anyString())).thenReturn(connectionBuilder);
        Mockito.when(connectionBuilder.setRequestProperty(Mockito.anyString(), Mockito.anyString())).thenReturn(connectionBuilder);
        Mockito.when(connectionBuilder.setIfModifiedSince(Mockito.any())).thenReturn(connectionBuilder);
        Mockito.when(connectionBuilder.setConnectionTimeout(Mockito.anyInt())).thenReturn(connectionBuilder);
        Mockito.when(connectionBuilder.setReadTimeout(Mockito.anyInt())).thenReturn(connectionBuilder);
        return connectionBuilder;
    }

    public static HttpURLConnection createMockResponse(int code) throws IOException {
        HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(connection.getResponseCode()).thenReturn(code);
        return connection;
    }

    public static HttpURLConnection createMockResponse(int code, byte[] content) throws IOException {
        HttpURLConnection connection = createMockResponse(code);
        Mockito.when(connection.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        return connection;
    }

}
