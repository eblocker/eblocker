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
export default function PauseService(logger, $http, $q, $interval, DataCachingService, DeviceSelectorService) {
    'ngInject';
    'use strict';

    const PATH = '/api/device/pause/';

    const config = {timeout: 10000};
    const SYNC_INTERVAL = 10000; // every 10 seconds
    let pauseCache, syncTimer;

    function startSyncTimer(interval) {
        if (!angular.isDefined(syncTimer)) {
            syncTimer = $interval(syncProfile, angular.isDefined(interval) ? interval : SYNC_INTERVAL);
        }
    }

    function stopSyncTimer() {
        if (angular.isDefined(syncTimer)) {
            $interval.cancel(syncTimer);
            syncTimer = undefined;
        }
    }

    function syncProfile() {
        getPause(true);
    }

    function getPause(reload) {
        let path = PATH + DeviceSelectorService.getSelectedDevice().id;
        pauseCache = DataCachingService.loadCache(pauseCache, path, reload, config).then(function success(response) {
            return response;
        }, function(response) {
            logger.error('Getting pause status failed with status ' + response.status +
                ' - ' + response.data);
            return $q.reject(response.data);
        });
        return pauseCache;
    }

    function setPause(seconds) {
        const data = {pausing: seconds};
        let path = PATH + DeviceSelectorService.getSelectedDevice().id;
        return $http.put(path, data, config).then(function(response) {
            return response.data;

        }, function(response) {
            logger.error('Setting pause status failed with status ' + response.status +
                ' - ' + response.data);
            return $q.reject(response.data);
        });
    }

    return {
        start: startSyncTimer,
        stop: stopSyncTimer,
        getPause: getPause,
        setPause: setPause
    };
}
