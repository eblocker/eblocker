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

import com.google.common.io.ByteStreams;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

// TODO: should be shared with eblocker-lists
public class HttpClient {

    private static final Logger log = LoggerFactory.getLogger(HttpClient.class);

    public InputStream download(String url) throws IOException {
        log.debug("downloading {}", url);
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.addHeader("Accept", "*/*");
            request.addHeader("Accept-Encoding", "gzip");
            HttpResponse response = client.execute(request);
            log.debug("downloaded {}: {} {}", url, response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new IOException("Could not download " + url + ". Status " + response.getStatusLine().getStatusCode() + " returned");
            }
            return new ByteArrayInputStream(ByteStreams.toByteArray(response.getEntity().getContent()));
        }
    }
}
