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
package org.eblocker.server.http.controller;

import org.restexpress.Request;
import org.restexpress.Response;

import java.io.IOException;
import java.util.Set;

public interface NetworkController {
    Object getConfiguration(Request request, Response response) throws IOException;

    Object getSetupPageInfo(Request request, Response response) throws IOException;

    Object updateConfiguration(Request request, Response response) throws IOException;

    boolean getDHCPActive(Request request, Response response);

    Set<String> getDhcpServers(Request request, Response response);

    Object getConfigurationIp6(Request request, Response response) throws IOException;

    Object updateConfigurationIp6(Request request, Response response) throws IOException;

}
