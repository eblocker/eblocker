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
export default function SecurityService(logger, $http, $q, $localStorage, APP_CONTEXT) {
    'ngInject';
    'use strict';

    const PATH = '/api/adminconsole/authentication';
    const PATH_INIT_TOKEN = PATH + '/token/';
    const PATH_RENEW_TOKEN = PATH +  '/renew/';
    const PATH_LOGIN = PATH + '/login/';
    const PATH_LOGIN_COUNTDOWN = PATH + '/wait';

    const securityContext = {};

    function getToken() {
        return securityContext.token;
    }

    function requestInitToken() {
        return $http.get(PATH_INIT_TOKEN + APP_CONTEXT.name, {timeout: 3000}).then(function (response) {
            logger.debug('Successfully loaded initial token from server.');
            return response;
        }, function (response) {
            logger.error('Failed to load initial token from server with status ' + response.status + '.', response);
            return $q.reject(response.data);
        });
    }

    function init() {
        if (angular.isDefined($localStorage.securityContextAdminConsole)) {
            logger.info('Found security context in local storage.');
            storeSecurityContext($localStorage.securityContextAdminConsole);
        } else {
            logger.info('No security context found in local storage.');
        }
    }

    init();

    function login(password) {
        return $http.post(PATH_LOGIN + APP_CONTEXT.name, {
            currentPassword: password
        }, { timeout: 3000 }).then(function success(response) {
            logger.info('Received login token from server.');
            return response;
        }, function error(response) {
            if (response.status === 401) {
                logger.error('Authentication failed - did not receive valid JWT: ', response.data);
                return $q.reject(response);
            } else {
                logger.error('Login failed with status ' + response.status + ' - ' + response.data);
                // notificationService.error('ERROR_LOGIN_NOT_POSSIBLE', response);
                return $q.reject(response);
            }
        });
    }

    function renewToken() {
        return $http.get(PATH_RENEW_TOKEN + APP_CONTEXT.name, {timeout: 3000}).then(function(response) {
            return response;
        }, function(response) {
            logger.error('Renew token failed with status ' + response.status, response);
            return $q.reject(response);
        });
    }

    function isLoginAvailable() {
        return $http.get(PATH_LOGIN_COUNTDOWN, {timeout: 3000}).then(function success (response) {
            return response;
        }, function error(response){
            logger.error('Getting login status failed with status ' + response.status, response);
            return response;
        });
    }

    function storeSecurityContext(data) {
        securityContext.token = data.token;
        securityContext.appContext = data.appContext;
        securityContext.expiresOn = data.expiresOn;
        securityContext.passwordRequired = data.passwordRequired;
        securityContext.isLoggedIn = data.isLoggedIn;

        $localStorage.securityContextAdminConsole = data;

        $http.defaults.headers.common.Authorization = 'Bearer ' + data.token;
    }

    function logout() {
        // ** Do not redirect here!
        // Logout due to idleness redirects to expired state
        // Logout when user clicks on button, redirects to LOGOUT
        securityContext.token = undefined;
        securityContext.appContext = undefined;
        securityContext.expiresOn = undefined;
        securityContext.passwordRequired = undefined;
        securityContext.isLoggedIn = undefined;

        $localStorage.securityContextAdminConsole = undefined;

        $http.defaults.headers.common.Authorization = undefined;
    }

    function isAuthenticated() {
        return angular.isDefined(securityContext.token) &&
            (!isPasswordRequired() || (isPasswordRequired() && securityContext.isLoggedIn === true));
    }

    function isPasswordRequired(bool) {
        if (angular.isDefined(bool)) {
            securityContext.passwordRequired = bool;
            $localStorage.securityContextAdminConsole.passwordRequired = bool;
        }
        return securityContext.passwordRequired === true;
        // return angular.isDefined(securityContext.token) &&
        //     angular.isDefined(securityContext.passwordRequired) &&
        //     securityContext.passwordRequired;
    }

    return {
        isAuthenticated: isAuthenticated,
        init: init,
        storeSecurityContext: storeSecurityContext,
        requestInitToken: requestInitToken,
        renewToken: renewToken,
        getToken: getToken,
        isPasswordRequired: isPasswordRequired,
        login: login,
        logout: logout,
        isLoginAvailable: isLoginAvailable
    };
}
