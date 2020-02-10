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
export default function CloakingService(logger, $http, $q, $interval, DataCachingService) {
    'ngInject';

    const PATH = '/api/dashboard/useragent/';
    const PATH_CLOAKED = PATH + 'cloaked';
    const PATH_LIST = PATH + 'list';

    let cloakingCache, syncTimer, cachedDeviceId, cachedAssignedUserId;

    function getCloakedUserAgentByDeviceId(deviceId, assignedUserId, reload) {
        if (angular.isDefined(deviceId) && angular.isDefined(assignedUserId)) {
            cachedDeviceId = deviceId;
            cachedAssignedUserId = assignedUserId;
            const path = PATH_CLOAKED + '?deviceId=' + deviceId + '&userId=' + assignedUserId;
            cloakingCache = DataCachingService.loadCache(cloakingCache, path, reload).then(function success(response) {
                return response;
            }, function (response) {
                logger.error('Getting the cloaked user agend failed with status ' + response.status + ' - ' +
                    response.data);
                return $q.reject(response);
            });
        }
        return cloakingCache;
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
        getCloakedUserAgentByDeviceId(cachedDeviceId, cachedAssignedUserId, true);
    }

    function setCloakedUserAgentByDeviceId(cloaked) {
        return $http.put(PATH_CLOAKED, cloaked).then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        });
    }

    function getUserAgents() {
        return $http.get(PATH_LIST).then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        });
    }

    return {
        start: startSyncTimer,
        stop: stopSyncTimer,
        getCloakedUserAgentByDeviceId: getCloakedUserAgentByDeviceId,
        setCloakedUserAgentByDeviceId: setCloakedUserAgentByDeviceId,
        getUserAgents: getUserAgents
    };
}
