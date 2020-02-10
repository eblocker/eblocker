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
export default {
    templateUrl: 'app/cards/user/user.component.html',
    controller: ConsoleController,
    controllerAs: 'vm',
    bindings: {
        cardId: '@'
    }
};

function ConsoleController($q, $timeout, $interval, DeviceService, UserService, DataService, // jshint ignore: line
                           DialogService, CardService, ArrayUtilsService, $filter) {
    'ngInject';
    'use strict';

    const vm = this;
    const CARD_NAME = 'USER'; // 'card-12';
    const UPDATE_INTERVAL = 5000;
    let returnedUser, lockedUser, updateTimer;

    vm.returnDevice = returnDevice;
    vm.lockInternet = lockInternet;
    vm.changePin = changePin;
    vm.changeUser = changeUser;
    vm.getIconPath = getIconPath;

    vm.$onInit = function() {
        vm.users = [];
        DataService.registerComponentAsServiceListener(CARD_NAME, 'UserService');
        DataService.registerComponentAsServiceListener(CARD_NAME, 'DeviceService');
        loadData(false);
        startUpdateTimer();
    };

    vm.$onDestroy = function() {
        stopUpdateTimer();
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'UserService');
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'DeviceService');
    };

    vm.$postLink = function() {
        $timeout(function() {
            CardService.scrollToCard(CARD_NAME);
        }, 300);
    };

    function startUpdateTimer() {
        if (angular.isDefined(updateTimer)) {
            return;
        }
        updateTimer = $interval(intervalExpired, UPDATE_INTERVAL);
    }

    function intervalExpired() {
        loadData(false);
    }

    function stopUpdateTimer() {
        if (angular.isDefined(updateTimer)) {
            $interval.cancel(updateTimer);
            updateTimer = undefined;
        }
    }

    function loadData(reload) {
        return $q.all([
            DeviceService.getDevice(reload),
            UserService.getUsers(reload)
        ]).then(function success(responses) {
            if (angular.isObject(responses[0].data) && angular.isObject(responses[1].data)) {
                setUsers(responses[1].data, responses[0].data);
                if (reload) {
                    CardService.scheduleDashboardReload(true);
                }
            }
            return responses;
        });
    }

    function getIconPath() {
        if (angular.isObject(vm.operatingUser) && angular.isString(vm.operatingUser.userRole) &&
            vm.operatingUser.userRole !== 'OTHER') {
            if (vm.operatingUser.userRole === 'CHILD') {
                return '/img/icons/icons8-teddy-bear.svg';
            } else if (vm.operatingUser.userRole === 'PARENT' &&
                vm.operatingUser.nameKey !== 'SHARED.USER.NAME.STANDARD_USER') {
                return '/img/icons/baseline-supervisor_account.svg';
            } else {
                return '/img/icons/ic_devices_black.svg';
            }
        }
        return '/img/icons/ic_person_black.svg';
    }

    /**
     * Prevent flickering of list in mdSelect of users. Allows to only update the list, when the user list has
     * actually changed.
     * @param list1
     * @param list2
     * @returns {boolean}
     */
    function updateUserList(list1, list2) {
        return list1.length !== list2.length ||
            $filter('filter')(list2, function(newUser) {
            return !ArrayUtilsService.containsByProperty(list1, 'id', newUser.id);
        }).length > 0;
    }

    function setUsers(users, device) {
        if (angular.isObject(device)) {
            // create new user list: may differ from list loaded from server: system users and users w/o PIN
            // that are not assigned to the current device are filtered.
            const newList = UserService.getUsersWithPinAsList(users,
                device.assignedUser);
            if (updateUserList(vm.users, newList)) {
                // actually update the user list, since the list has changed.
                vm.users = newList;
            }
            vm.operatingUser = UserService.getUserById(device.operatingUser);

            // Use only the ID to avoid clash due to ngChange in mdSelect and the update of vm.operatingUser
            // in this controller. Otherwise a change in the mdSelect model here (change of vm.operatingUser) will
            // in turn trigger ngChange again. When the ID (type int) stays the same, ngChange is not triggered.
            vm.opModel = vm.operatingUser.id;

            vm.assignedUser = UserService.getUserById(device.assignedUser);
            vm.selectedUser = undefined;

            returnedUser = {
                id: 0,
                containsPin: vm.assignedUser.containsPin,
                name: vm.assignedUser.name,
                nameKey: vm.assignedUser.nameKey
            };
            lockedUser = {
                id: 2,
                name: 'USER.CARD.LABEL_LOCKED_USER',
                containsPin: true
            };
        }
    }

    function returnDevice() {
        setOperatingUser(returnedUser, 'askForPin');
    }

    function lockInternet() {
        setOperatingUser(lockedUser, 'confirmLock');
    }

    function changePin() {
        DialogService.userChangePin(vm.operatingUser);
    }

    function changeUser() {
        setOperatingUser(UserService.getUserById(vm.opModel), 'askForPin');
    }

    function setOperatingUser(user, mode) {
        if (mode === 'askForPin' && !user.containsPin) {
            onOk(user, null).then(function() {
                loadData(true);
            });
        } else {
            DialogService.userProvidePin(user, vm.operatingUser.containsPin, mode, onOk, onLock).then(function () {
                loadData(true);
            });
        }
    }

    function onOk(user, pin) {
        return UserService.setOperatingUser(user, pin);
    }

    function onLock() {
        return UserService.setOperatingUser(lockedUser);
    }

}
