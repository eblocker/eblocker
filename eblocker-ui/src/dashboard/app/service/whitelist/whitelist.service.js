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
export default function WhitelistService(logger, $http, $interval, DataCachingService) {
    'ngInject';

    let whitelistCache, whitelistConfigCache, syncTimer;

    /*
     * Timer related to periodically synchronizing the profile with
     * the eBlocker
     */
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
        getWhitelist(true);
        getWhitelistConfig(true);
    }

    function getWhitelist(reload) {
        whitelistCache = DataCachingService.loadCache(whitelistCache, '/summary/whitelist/all', reload).
        then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Failed to call getWhitelist: ', response);
            return $q.reject(response);
        });
        return whitelistCache;
    }

    function setWhitelist(whitelist) {
        return $http.put('/summary/whitelist/all', whitelist)
            .then(function success(response) {
                return response;
            }, function error(response) {
                logger.error('Error while updating whitelist: ', response);
                return $q.reject(response);
            });
    }

    function updateWhitelistEntry(entry) {
        return $http.put('/summary/whitelist/update', entry)
            .then(function success(response) {
                return response;
            }, function error(response) {
                logger.error('Error while updating entry: ', response);
                return $q.reject(response);
            });
    }

    function updateWhitelistConfig(config) {
        return $http.put('/summary/whitelist/config', config)
            .then(function(response) {
                return response;
            }, function error(response) {
                logger.error('Error while updating config: ', response);
                return $q.reject(response);
            });
    }

    function getWhitelistConfig(reload) {
        whitelistConfigCache = DataCachingService.loadCache(whitelistConfigCache, '/summary/whitelist/config', reload).
        then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Error while loading config: ', response);
            return $q.reject(response);
        });
        return whitelistConfigCache;
    }

    return {
        start: startSyncTimer,
        stop: stopSyncTimer,
        getWhitelist: getWhitelist,
        setWhitelist: setWhitelist,
        updateWhitelistEntry: updateWhitelistEntry,
        updateWhitelistConfig: updateWhitelistConfig,
        getWhitelistConfig: getWhitelistConfig
    };
}
