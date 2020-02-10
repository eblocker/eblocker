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

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

class DefaultHttpUrlConnectionBuilder implements HttpUrlConnectionBuilder {
    private int connectionTimeout = 10000;
    private int readTimeout = 10000;
    private Long ifModifiedSince;
    private Map<String, String> requestProperties = new HashMap<>();
    private URL url;

    DefaultHttpUrlConnectionBuilder() {
        this.requestProperties.put("Connection", "close");
    }

    public DefaultHttpUrlConnectionBuilder setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
        return this;
    }

    public DefaultHttpUrlConnectionBuilder setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    public DefaultHttpUrlConnectionBuilder setUrl(String url) throws MalformedURLException {
        this.url = new URL(url);
        return this;
    }

    public DefaultHttpUrlConnectionBuilder setRequestProperty(String key, String value) {
        requestProperties.put(key, value);
        return this;
    }

    public DefaultHttpUrlConnectionBuilder setIfModifiedSince(Long ifModifiedSince) {
        this.ifModifiedSince = ifModifiedSince;
        return this;
    }

    public HttpURLConnection get() throws IOException {
        return build();
    }

    public HttpURLConnection post(byte[] content) throws IOException {
        HttpURLConnection connection = build();
        connection.setDoOutput(true);
        OutputStream out = connection.getOutputStream();
        out.write(content);
        out.flush();
        out.close();
        return connection;
    }

    private HttpURLConnection build() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(connectionTimeout);
        connection.setReadTimeout(readTimeout);
        if (ifModifiedSince != null) {
            connection.setIfModifiedSince(ifModifiedSince);
        }
        requestProperties.forEach((k, v) -> connection.setRequestProperty(k, v));
        return connection;
    }
}
