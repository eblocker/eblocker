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

import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.transaction.TransactionIdentifier;
import org.restexpress.Request;

public class HttpTransactionIdentifier implements TransactionIdentifier {
    private final String userAgent;
    private final IpAddress clientIP;

    public HttpTransactionIdentifier(Request request) {
        userAgent = request.getHeader("User-Agent");

        String xClientIp = request.getHeader("X-Forwarded-For");
        if (xClientIp != null) {
            clientIP = IpAddress.parse(xClientIp);
        } else {
            clientIP = IpAddress.of(request.getRemoteAddress().getAddress());
        }
    }

    @Override
    public String getUserAgent() {
        return userAgent;
    }

    @Override
    public IpAddress getOriginalClientIP() {
        return clientIP;
    }
}
