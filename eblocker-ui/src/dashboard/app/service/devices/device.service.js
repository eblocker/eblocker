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

    const PATH = '/api/device';

    const config = {timeout: 3000};
    let syncTimer, deviceCache;

    function startSyncTimer(interval) {
        if (!angular.isDefined(syncTimer) && angular.isNumber(interval)) {
            syncTimer = $interval(syncData, interval);
        } else if (!angular.isNumber(interval)) {
            logger.warn('Cannot start synch timer with interval ', interval);
        }
    }

    function stopSyncTimer() {
        if (angular.isDefined(syncTimer)) {
            $interval.cancel(syncTimer);
            syncTimer = undefined;
        }
    }

    function syncData() {
        getDevice(true);
    }

    function getDevice(reload) {
        deviceCache = DataCachingService.loadCache(deviceCache, PATH, reload, config).then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Getting device from the eBlocker failed with status ' +
                response.status + ' - ' + response.data);
            return $q.reject(response.data);
        });
        return deviceCache;
    }

    function getAllDevices() {
        return $http.get('/api/admindashboard/devices', config).then(function(response) {
            return response.data;
        }, function(reason) {
            logger.error('Getting all devices failed with status ' + reason.status + ' - ' + reason.data);
            return $q.reject(reason.data);
        });
    }

    function getOperatingUserDevices() {
        return $http.get('/api/dashboard/operatinguser/devices', config).then(function(response) {
            return response.data;
        }, function(reason) {
            logger.error('Getting operating user\'s devices failed with status ' + reason.status + ' - ' + reason.data);
            return $q.reject(reason.data);
        });
    }

    function update(device) {
        let path = PATH + '/' + device.id;
        return $http.put(path, device).then(function success(response) {
            return response;
        }, function error(response) {
            return response;
        }).finally(function() {
            deviceCache = undefined;
        });
    }

    function updateShowWelcomeFlags(flags) {
        return $http.put(PATH + '/showWelcomeFlags', flags).then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        });
    }

    return {
        start: startSyncTimer,
        stop: stopSyncTimer,
        getDevice: getDevice,
        getAllDevices: getAllDevices,
        getOperatingUserDevices: getOperatingUserDevices,
        update: update,
        updateShowWelcomeFlags: updateShowWelcomeFlags
    };
}
