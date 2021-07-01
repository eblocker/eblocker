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
export default function HttpInterceptor($httpProvider) {
    'ngInject';

    $httpProvider.interceptors.push(CustomHttpInterceptor);

}

function CustomHttpInterceptor(logger, $q, StateService, STATES, BE_ERRORS) {
    'ngInject';

    const HTTP_REQUEST_DEFAULT_TIMEOUT = 10000;

    function responseError(rejection) {
        if (rejection.status > -1) {
            logger.debug('(HTTP-Interceptor) Request has been rejected: ' +
                rejection.status + ' [' + rejection.data + ']');
            // ** If we get a 401 'token-invalid' the session has expired.
            if (rejection.status === 401 && rejection.data === BE_ERRORS.TOKEN_INVALID) {
                logger.warning('(HTTP-Interceptor) Invalid token. Redirecting to logout state.');
                StateService.goToState(STATES.LOGOUT);
            } else if (rejection.status === 503) {
                logger.warning('(HTTP-Interceptor) Status 503. Redirecting to standby state.');
                // fix issue: "Controller method generateConsoleToken not yet available"
                StateService.goToState(STATES.STAND_BY);
            }

        }
        return $q.reject(rejection);
    }

    function request(config) {
        if (angular.isUndefined(config.timeout)) {
            config.timeout = HTTP_REQUEST_DEFAULT_TIMEOUT;
        }
        return config;
    }

    return {
        request: request,
        responseError: responseError
    };

}
