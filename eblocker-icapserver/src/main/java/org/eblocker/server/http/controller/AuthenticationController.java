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

import org.eblocker.server.http.security.JsonWebToken;
import org.eblocker.server.http.security.PasswordResetToken;
import org.restexpress.Request;
import org.restexpress.Response;

public interface AuthenticationController {
    JsonWebToken login(Request request, Response response);

    JsonWebToken generateToken(Request request, Response response);

    JsonWebToken generateConsoleToken(Request request, Response response);

    JsonWebToken renew(Request request, Response response);

    JsonWebToken renewToken(Request request, Response response);

    void enable(Request request, Response response);

    void disable(Request request, Response response);

    PasswordResetToken initiateReset(Request request, Response response);

    void executeReset(Request request, Response response);

    void cancelReset(Request request, Response response);

    long passwordEntryInSeconds(Request request, Response response);
}
