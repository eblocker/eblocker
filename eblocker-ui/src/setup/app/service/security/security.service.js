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
export default function SecurityService(logger, $http, $q, Idle) {
    'ngInject';
    'use strict';

    let securityContext = {};

    return {
        'requestToken': requestToken
    };

    function requestToken(appContext) {
        return $http.get('/api/token/' + appContext, {'timeout': 3000}).then(function(response) {
            // Start watching for idleness, if not yet doing so.
            // We do this, even when we do not need a password, to start the keepalive process.
            // Because the JWT must be renewed even when there is no password!
            if (!Idle.running()) {
                Idle.watch();
            }
            storeToken(response.data);

        }, function(response) {
            logger.error('Login failed with status ' + response.status + ' - ' + response.data);
            return $q.reject(response.data);
        });
    }

    function storeToken(data) {
        securityContext.token = data.token;
        securityContext.appContext = data.appContext;
        securityContext.expiresOn = data.expiresOn;
        securityContext.passwordRequired = data.passwordRequired;

        $http.defaults.headers.common.Authorization = 'Bearer ' + data.token;
    }
}
