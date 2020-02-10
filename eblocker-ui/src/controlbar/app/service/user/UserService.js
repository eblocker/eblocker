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
export default function UserService($http, $q, NotificationService) {
    'ngInject';

    let PATH = '/api/controlbar/users';

    let userCache, operatingUser, assignedUser, allUsers;

    function getUsers() {
        if (angular.isUndefined(userCache)) {
            userCache = $http.get(PATH).then(function success(response) {
                return response;
            }, function error(response) {
                NotificationService.error('CONTROLBAR.SERVICE.USER.NOTIFICATION.ERROR.USER_NOT_AVAILABLE', response);
                $q.reject(response);
            });
        }
        return userCache;
    }

    function savePin(user) {
        return $http.post(PATH + '/' + user.id + '/changepin', user)
            .then(function success(response) {
                NotificationService.info('CONTROLBAR.SERVICE.USER.NOTIFICATION.SUCCESS.CHANGE_PIN');
                return response;
            }, function error(response) {
                NotificationService
                    .error('CONTROLBAR.SERVICE.USER.NOTIFICATION.ERROR.CHANGE_PIN_FAIL', response, null, 3000);
                return $q.reject(response);
            });
    }

    function setOperatingUser(user, pin) {
        return $http.put(PATH + '/operatinguser', {id: user.id, pin: pin}).then(
            function success(response) {
                return $q.when(response.data);
            }, function error(response) {
                if (response.status === 401) {
                    NotificationService
                        .error('CONTROLBAR.SERVICE.USER.NOTIFICATION.ERROR.PIN_INVALID', response, null, 3000);
                    // must be 'pinInvalid' or the same as in the Dialog ngMessage configuration
                    return $q.reject('pinInvalid');
                } else if (response.status === 403) {
                    NotificationService.error('CONTROLBAR.SERVICE.USER.NOTIFICATION.ERROR.NOT_PERMITTED', response);
                    return $q.reject('notPermitted');
                } else {
                    NotificationService.error('CONTROLBAR.SERVICE.USER.NOTIFICATION.ERROR.OTHER_ERROR', response);
                    return $q.reject('otherError');
                }
            }
        );
    }

    function initializeUserService(device) {
        return getUsers().then(function success(response) {
            if (angular.isDefined(response) && angular.isObject(response.data)) {
                allUsers = [];
                let users = response.data;
                Object.keys(users).forEach((userId) => {
                    let user = users[userId];
                    if (angular.isDefined(user.id) &&
                        !user.system &&
                        user.containsPin &&
                        user.id !== device.operatingUser) {
                        allUsers.push(user);
                    }
                });
                operatingUser = users[device.operatingUser];
                assignedUser = users[device.assignedUser];
                return allUsers;
            }
        });
    }

    function getAllUsers() {
        return allUsers;
    }

    function getAssignedUser() {
        return assignedUser;
    }

    function getOperatingUser() {
        return operatingUser;
    }

    return {
        'getUsers': getUsers,
        'initializeUserService': initializeUserService,
        'savePin': savePin,
        'setOperatingUser': setOperatingUser,
        'getAssignedUser': getAssignedUser,
        'getOperatingUser': getOperatingUser,
        'getAllUsers': getAllUsers
    };
}
