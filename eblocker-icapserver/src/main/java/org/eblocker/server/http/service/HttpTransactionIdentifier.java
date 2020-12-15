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
package org.eblocker.server.http.service;

import io.netty.handler.codec.http.HttpRequest;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.transaction.TransactionIdentifier;

public class HttpTransactionIdentifier implements TransactionIdentifier {

    private final HttpRequest request;
    private final IpAddress remoteAddress;

    public HttpTransactionIdentifier(HttpRequest request, IpAddress remoteAddress) {
        this.request = request;
        this.remoteAddress = remoteAddress;
    }

    @Override
    public String getUserAgent() {
        return request.headers().get("User-Agent");
    }

    @Override
    public IpAddress getOriginalClientIP() {
        String xClientIp = request.headers().get("X-Forwarded-For");
        return xClientIp != null ? IpAddress.parse(xClientIp) : remoteAddress;
    }
}
