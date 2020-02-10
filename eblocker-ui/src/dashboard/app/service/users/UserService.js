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
export default function UserService(logger, $http, $q, $interval, $translate, DataCachingService) {
    'ngInject';

    const PATH = '/api/dashboard/users';
    const SYNC_INTERVAL = 10000;// every 10 seconds

    let userCache, syncTimer, cachedRawUsers;

    function getUsers(reload) {
        userCache = DataCachingService.loadCache(userCache, PATH, reload).then(function success(response) {
            cachedRawUsers = response.data;
            return response;
        }, function error(response) {
            return $q.reject(response);
        });
        return userCache;
    }

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
        getUsers(true);
    }

    function savePin(user) {
        return $http.post(PATH + '/' + user.id + '/changepin', user)
            .then(function success(response) {
                return response;
            }, function error(response) {
                return $q.reject(response);
            });
    }

    function setOperatingUser(user, pin) {
        return $http.put(PATH + '/operatinguser', {id: user.id, pin: pin}).then(
            function success(response) {
                return $q.when(response.data);
            }, function error(response) {
                if (response.status === 401) {
                    // must be 'pinInvalid' or the same as in the Dialog ngMessage configuration
                    return $q.reject('pinInvalid');
                } else if (response.status === 403) {
                    return $q.reject('notPermitted');
                } else {
                    return $q.reject('otherError');
                }
            }
        );
    }

    function getUserById(id) {
        let op = null;
        if (angular.isArray(cachedRawUsers)) {
            cachedRawUsers.forEach((user) => {
                if (user.id === id) {
                    op = angular.copy(user);
                }
            });
        }
        return op;
    }

    function getUsersWithPinAsList(users, assignedUserId) {
        const allUsers = [];
        users.forEach((user) => {
            if (angular.isDefined(user.id) &&
                !user.system &&
                (user.containsPin || user.id === assignedUserId)) {
                allUsers.push(user);
            }
        });
        return allUsers;
    }

    return {
        start: startSyncTimer,
        stop: stopSyncTimer,
        getUsers: getUsers,
        savePin: savePin,
        setOperatingUser: setOperatingUser,
        getUserById: getUserById,
        getUsersWithPinAsList: getUsersWithPinAsList
    };
}
