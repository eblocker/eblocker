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
export default function PauseService(logger, $http, $q) {
    'ngInject';
    'use strict';

    const PATH = '/api/adminconsole/device/pause';

    const config = {timeout: 3000};

    let pauseCache;

    function getPause(reload, deviceId) {
        if (angular.isUndefined(pauseCache) || reload) {
            pauseCache = $http.get(PATH + '?deviceId=' + deviceId, config).then(function(response) {
                return response.data;
            }, function(response) {
                logger.error('Getting pause status failed with status ' + response.status +
                    ' - ' + response.data);
                return $q.reject(response.data);
            });
        }
        return pauseCache;
    }

    function setPause(seconds, deviceId) {
        const data = {pausing: seconds};
        return $http.put(PATH + '?deviceId=' + deviceId, data, config).then(function(response) {
            return response.data;

        }, function(response) {
            logger.error('Setting pause status failed with status ' + response.status +
                ' - ' + response.data);
            return $q.reject(response.data);
        });
    }

    return {
        getPause: getPause,
        setPause: setPause
    };
}
