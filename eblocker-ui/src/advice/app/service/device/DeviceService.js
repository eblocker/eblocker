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
export default function Device(logger, $http, $q, $interval, DataCachingService) {
    'ngInject';
    'use strict';

    const PATH = '/api/advice/device';
    const config = {timeout: 3000};
    let deviceCache;

    function get(reload) {
        deviceCache = DataCachingService.loadCache(deviceCache, PATH, reload, config).then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Getting device from the eBlocker failed with status ' +
                response.status + ' - ' + response.data);
            return $q.reject(response.data);
        });
        return deviceCache;
    }

    function updateShowWelcomeFlags(flags) {
        return $http.put(PATH + '/showWelcomeFlags', flags).then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        });
    }

    return {
        get: get,
        updateShowWelcomeFlags: updateShowWelcomeFlags
    };
}
