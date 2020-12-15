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
package org.eblocker.server.http.server;

import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.pipeline.Postprocessor;

/**
 * Sets a cache-control header if the response does not have one already.
 * The cache-control header should prevent the browser from caching JSON data.
 * (MS Edge was observed to cache JSON when no cache-control header is set.
 */
public class CacheControlPostProcessor implements Postprocessor {

    @Override
    public void process(Request request, Response response) {
        if (!response.hasHeader("Cache-Control")) {
            response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        }
    }
}
