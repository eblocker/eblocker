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

import io.netty.handler.codec.http.HttpResponseStatus;
import org.eblocker.server.http.controller.ConnectionCheckController;
import org.restexpress.Request;
import org.restexpress.Response;

public class ConnectionCheckControllerImpl implements ConnectionCheckController {

    public ConnectionCheckControllerImpl() {
        // No dependencies
    }

    @Override
    public void routingTest(Request request, Response response) {
        response.setResponseCode(HttpResponseStatus.NO_CONTENT.code());
        response.setResponseStatus(HttpResponseStatus.NO_CONTENT);
        response.addHeader("Access-Control-Allow-Origin", "*");
    }

}
