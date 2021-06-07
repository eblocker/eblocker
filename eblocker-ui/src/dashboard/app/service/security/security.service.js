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
export default function SecurityService(logger, $http, $q, $localStorage, Idle, APP_CONTEXT) {
    'ngInject';
    'use strict';

    let securityContext = {};

    const PATH = '/api/token/';
    const LOGIN_PATH = '/api/adminconsole/authentication/login/';
    const RENEW_PATH = '/api/admindashboard/authentication/renew/';
    const httpConfig = {timeout: 3000};

    function init() {
        loadSecurityContext();
    }

    init();

    /*
      If an unexpired admin security context could be loaded from local storage, try to use this.
      (The user might have logged in as admin and then reloaded the page.)
      Otherwise, request a "normal" token.
     */
    function requestInitialToken() {
        if (isLoggedInAsAdmin()) {
            return $q.resolve(securityContext.token);
        } else {
            return requestToken(APP_CONTEXT.name);
        }
    }

    function requestToken(appContext) {
        return $http.get(PATH + appContext, httpConfig).then(function(response) {
            // Start watching for idleness, if not yet doing so.
            // We do this, even when we do not need a password, to start the keepalive process.
            // Because the JWT must be renewed even when there is no password!
            if (!Idle.running()) {
                Idle.watch();
            }
            storeSecurityContext(response.data);
            return response.data.token;
        }, function(response) {
            logger.error('Login failed with status ' + response.status + ' - ' + response.data);
            return $q.reject(response.data);
        });
    }

    function requestAdminToken(appContext, password) {
        return $http.post(LOGIN_PATH + appContext, {currentPassword: password}, httpConfig)
            .then(function(response) {
                storeSecurityContext(response.data);
                return response.data.token;
            }, function(response) {
                logger.error('Login failed with status ' + response.status + ' - ' + response.data);
                return $q.reject(response.data);
            });
    }

    function renewToken() {
        if (isLoggedInAsAdmin()) {
            return $http.get(RENEW_PATH + securityContext.appContext, httpConfig).then(function(response) {
                storeSecurityContext(response.data);
                return response.data.token;
            }, function(response) {
                logger.error('Renew token failed with status ' + response.status, response);
                return $q.reject(response);
            });
        } else {
            return requestToken(securityContext.appContext);
        }
    }

    function loadSecurityContext() {
        let storedContext = $localStorage.securityContextDashboard;
        if (angular.isDefined(storedContext)) {
            if (storedContext.expiresOn > Date.now()/1000) {
                storeSecurityContext(storedContext);
            } else {
                clearSecurityContext();
            }
        }
    }

    function storeSecurityContext(data) {
        securityContext.token = data.token;
        securityContext.appContext = data.appContext;
        securityContext.expiresOn = data.expiresOn;
        securityContext.passwordRequired = data.passwordRequired;

        $localStorage.securityContextDashboard = data;

        $http.defaults.headers.common.Authorization = 'Bearer ' + data.token;
    }

    function clearSecurityContext() {
        securityContext = {};

        $localStorage.securityContextDashboard = undefined;

        $http.defaults.headers.common.Authorization = undefined;
    }

    function isLoggedInAsAdmin() {
        return securityContext.appContext === APP_CONTEXT.adminAppContextName;
    }

    function logoutAdmin() {
        clearSecurityContext();
        return requestToken(APP_CONTEXT.name);
    }

    return {
        requestToken: requestToken,
        requestInitialToken: requestInitialToken,
        requestAdminToken: requestAdminToken,
        renewToken: renewToken,
        isLoggedInAsAdmin: isLoggedInAsAdmin,
        logoutAdmin: logoutAdmin
    };
}
