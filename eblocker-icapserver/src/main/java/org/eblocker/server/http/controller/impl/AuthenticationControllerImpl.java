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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.page.PageContextStore;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.http.controller.AuthenticationController;
import org.eblocker.server.http.security.AppContext;
import org.eblocker.server.http.security.Credentials;
import org.eblocker.server.http.security.JsonWebToken;
import org.eblocker.server.http.security.PasswordResetToken;
import org.eblocker.server.http.security.SecurityService;
import org.eblocker.server.http.security.TokenInfo;
import org.eblocker.server.http.server.SessionContextController;
import org.eblocker.server.http.utils.ControllerUtils;
import org.restexpress.Request;
import org.restexpress.Response;

@Singleton
public class AuthenticationControllerImpl extends SessionContextController implements AuthenticationController {
    private final SecurityService securityService;

    @Inject
    public AuthenticationControllerImpl(SecurityService securityService, SessionStore sessionStore,
                                        PageContextStore pageContextStore) {
        super(sessionStore, pageContextStore);
        this.securityService = securityService;
    }

    @Override
    public JsonWebToken login(Request request, Response response) {
        IpAddress ip = ControllerUtils.getRequestIPAddress(request);
        String appContextName = request.getHeader("appContext");
        AppContext appContext = AppContext.nullSafeValue(appContextName);
        return securityService.generateToken(request.getBodyAs(Credentials.class), ip, appContext);
    }

    @Override
    public long passwordEntryInSeconds(Request request, Response response) {
        IpAddress ip = ControllerUtils.getRequestIPAddress(request);
        return securityService.passwordEntryInSeconds(ip);
    }

    @Override
    public JsonWebToken generateToken(Request request, Response response) {
        String appContextName = request.getHeader("appContext");
        AppContext appContext = AppContext.nullSafeValue(appContextName);
        return securityService.generateToken(appContext);
    }

    @Override
    public JsonWebToken generateConsoleToken(Request request, Response response) {
        String appContextName = request.getHeader("appContext");
        AppContext appContext = AppContext.nullSafeValue(appContextName);
        return securityService.generateConsoleToken(appContext);
    }

    @Override
    public JsonWebToken renew(Request request, Response response) {
        JsonWebToken token = securityService.renewToken((TokenInfo) request.getAttachment("jwt"));
        if (token == null) {
            response.setResponseCode(HttpResponseStatus.NO_CONTENT.code());
        }
        return token;
    }

    @Override
    public JsonWebToken renewToken(Request request, Response response) {
        String appContextName = request.getHeader("appContext");
        AppContext appContext = AppContext.nullSafeValue(appContextName);
        JsonWebToken token = securityService.renewToken(appContext);
        if (token == null) {
            response.setResponseCode(HttpResponseStatus.NO_CONTENT.code());
        }
        return token;
    }

    @Override
    public void enable(Request request, Response response) {
        IpAddress ipAddress = getSession(request).getIp();
        securityService.setPassword(request.getBodyAs(Credentials.class), ipAddress);
    }

    @Override
    public void disable(Request request, Response response) {
        IpAddress ipAddress = getSession(request).getIp();
        securityService.removePassword(request.getBodyAs(Credentials.class), ipAddress);
    }

    @Override
    public PasswordResetToken initiateReset(Request request, Response response) {
        return securityService.initiateReset();
    }

    @Override
    public void executeReset(Request request, Response response) {
        IpAddress ipAddress = getSession(request).getIp();
        securityService.executeReset(request.getBodyAs(PasswordResetToken.class), ipAddress);
    }

    @Override
    public void cancelReset(Request request, Response response) {
        securityService.cancelReset(request.getBodyAs(PasswordResetToken.class));
    }

}
