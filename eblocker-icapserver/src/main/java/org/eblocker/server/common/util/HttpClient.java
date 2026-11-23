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
package org.eblocker.server.common.util;

import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

// TODO: should be shared with eblocker-lists
public class HttpClient {

    private static final Logger log = LoggerFactory.getLogger(HttpClient.class);
    private static final int CONNECT_TIMEOUT_MS = 30000;
    private static final int SOCKET_TIMEOUT_MS = 60000;

    protected CloseableHttpClient createHttpClient() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setConnectionRequestTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();
        return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    public InputStream download(String url) throws IOException {
        log.debug("downloading {}", url);
        CloseableHttpClient client = createHttpClient();
        CloseableHttpResponse response = null;
        try {
            HttpGet request = new HttpGet(url);
            request.addHeader("Accept", "*/*");
            request.addHeader("Accept-Encoding", "gzip");
            response = client.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            log.debug("downloaded {}: {} {}", url, statusCode, response.getStatusLine().getReasonPhrase());
            if (statusCode != 200) {
                throw new IOException("Could not download " + url + ". Status " + statusCode + " returned");
            }
            HttpEntity entity = response.getEntity();
            if (entity == null || entity.getContent() == null) {
                throw new IOException("Empty response body when downloading " + url);
            }
            final CloseableHttpResponse finalResponse = response;
            final AtomicBoolean closed = new AtomicBoolean(false);
            return new FilterInputStream(entity.getContent()) {
                @Override
                public void close() throws IOException {
                    if (!closed.compareAndSet(false, true)) {
                        return;
                    }
                    try {
                        super.close();
                    } finally {
                        try {
                            finalResponse.close();
                        } finally {
                            client.close();
                        }
                    }
                }
            };
        } catch (IOException | RuntimeException e) {
            closeQuietly(response);
            closeQuietly(client);
            throw e;
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                log.debug("Ignoring close error", e);
            }
        }
    }
}
