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
export default function TorService(logger, $http, $q, $interval, DataCachingService) {
    'ngInject';

    const PATH = '/api/dashboard/tor/';

    let torConfigCache, syncTimer;

    function getDeviceConfig(reload) {
        torConfigCache = DataCachingService.loadCache(torConfigCache, PATH + 'config', reload).
        then(function success(response) {
            return response;
        }, function(response) {
            logger.error('Getting the Tor config failed with status ' + response.status + ' - ' + response.data);
            return $q.reject(response);
        });
        return torConfigCache;
    }

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
        getDeviceConfig(true);
    }

    function setDeviceConfig(config) {
        return $http.put(PATH + 'config', config);
    }

    function getShowWarnings() {
        return $http.get(PATH + 'device/showwarnings').then(function success(response) {
            return response;
        }, function success(response) {
            logger.error('Unable to get show warnings for Tor activation dialog', response);
            return $q.reject(response);
        });
    }

    function setShowWarnings(bool) {
        return $http.post(PATH + 'device/showwarnings', {showWarnings: bool}).then(function success(response) {
            return response;
        }, function success(response) {
            logger.error('Unable to set show warnings for Tor activation dialog', response);
            return $q.reject(response);
        });
    }

    function getTorCheckServices() {
        return $http.get(PATH + 'checkservices').then(function success(response) {
            return response;
        }, function success(response) {
            logger.error('Unable to get Tor check service URLs', response);
            return $q.reject(response);
        });
    }

    function getNewTorIdentity() {
        return $http.put(PATH + 'newidentity').then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Unable to get new Tor identity', response);
            return $q.reject(response);
        });
    }

    return {
        start: startSyncTimer,
        stop: stopSyncTimer,
        getDeviceConfig: getDeviceConfig,
        setDeviceConfig: setDeviceConfig,
        getShowWarnings: getShowWarnings,
        setShowWarnings: setShowWarnings,
        getTorCheckServices: getTorCheckServices,
        getNewTorIdentity: getNewTorIdentity
    };
}
