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

public interface AnonymousController {
    Object getConfig(Request request, Response response);

    Object getConfigById(Request request, Response response);

    Object putConfig(Request request, Response response);

    Object putConfigById(Request request, Response response);

    boolean isTorConnected(Request req, Response resp);

    void getNewTorIdentity(Request req, Response resp);

    Object getTorCountries(Request req, Response resp);

    void setTorExitNodeCountries(Request req, Response resp);

    Object getCurrentTorExitNodeCountries(Request req, Response resp);

    Object getTorCheckServices(Request request, Response response);

    void setWebRTCBlockingState(Request request, Response response);

    boolean isWebRTCBlockingEnabled(Request request, Response response);

    void setHTTPRefererRemovingState(Request request, Response response);

    boolean isHTTPRefererRemovingEnabled(Request request, Response response);

    boolean getGoogleCaptivePortalRedirectState(Request req, Response resp);

    void setGoogleCaptivePortalRedirectState(Request req, Response resp);

    boolean getDntHeaderState(Request request, Response response);

    void setDntHeaderState(Request request, Response response);
}
