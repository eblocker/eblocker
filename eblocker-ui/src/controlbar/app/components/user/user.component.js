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
    templateUrl: 'app/components/user/user.component.html',
    controller: UserController,
    controllerAs: 'ctrl'
};

function UserController(logger, $mdDialog, $window, DeviceService, UserService) {
    'ngInject';

    let vm = this;
    let returnedUser, lockedUser;

    vm.closeDropdown = {};

    vm.options = {
        returnDevice: {
            label: 'CONTROLBAR.MENU.USER.ACTIONS.RETURN_DEVICE',
            imageUrl: '/img/icons/baseline-keyboard_return.svg',
            onlyWhenNotOwner: true, // do not show if operating user is assigned user
            onlyWhenNotLocked: true, // do not show if device is locked
            actionCallback: returnDevice
        },
        lockInternet: {
            name: 'lock_internet',
            label: 'CONTROLBAR.MENU.USER.ACTIONS.LOCK_INTERNET',
            imageUrl: '/img/icons/ic_lock_black.svg',
            actionCallback: lockInternet
        },
        changePin: {
            label: 'CONTROLBAR.MENU.USER.ACTIONS.CHANGE_PIN',
            imageUrl: '/img/icons/ic_dialpad_black.svg',
            onlyWhenNotLocked: true, // do not show if device is locked
            actionCallback: changePin
        },
        changeUser: {
            label: 'CONTROLBAR.MENU.USER.ACTIONS.CHANGE_USER',
            actionCallback: changeUser
        }
    };

    function returnDevice() {
        setOperatingUser(returnedUser, 'askForPin');
    }

    function lockInternet() {
        setOperatingUser(lockedUser, 'confirmLock');
    }

    function changePin() {
        openChangePinDialog();
    }

    function changeUser(user) {
        setOperatingUser(user, 'askForPin');
    }

    function getDevice() {
        return DeviceService.getDevice().then(function success(response) {
            if (angular.isDefined(response) && angular.isObject(response.data)) {
                vm.device = response.data;
            }
        }, function error(response) {
            logger.error('Error loading device for user component: ', response);
        });
    }

    function getUsers() {
        vm.users = UserService.getAllUsers();
        vm.operatingUser = UserService.getOperatingUser();
        vm.assignedUser = UserService.getAssignedUser();
        returnedUser = {
            'id': 0,
            'containsPin': vm.assignedUser.containsPin,
            'name': vm.assignedUser.name,
            'nameKey': vm.assignedUser.nameKey
        };
        lockedUser = {
            'id': 2,
            'containsPin': true
        };
    }

    function openChangePinDialog(){
        $mdDialog.show({
            controller: 'ChangePinDialogController',
            controllerAs: 'Dialog',
            templateUrl: 'app/dialogs/pin/change-pin.dialog.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false,
            locals: {
                module: vm.operatingUser
            }
        });
        vm.closeDropdown.now();
    }

    function setOperatingUser(user, mode) {
        if (!user.containsPin) {
            UserService.setOperatingUser(user).then(function() {
                $window.location.reload();
            });
            return;
        }

        $mdDialog.show({
            controller: 'ProvidePinDialogController',
            controllerAs: 'Dialog',
            templateUrl: 'app/dialogs/pin/provide-pin.dialog.html',
            parent: angular.element(document.body),
            clickOutsideToClose: false,
            locals: {
                user: user,
                mode: mode,
                lockEnabled: vm.operatingUser.containsPin,
                users: [],
                onOk: function(user, pin) {
                    return UserService.setOperatingUser(user, pin);
                },
                onLock: function() {
                    return UserService.setOperatingUser(lockedUser);
                },
                onCancel: function() {
                    //
                }
            }
        }).then(function() {
            $window.location.reload();
        }, function() {
            // handle rejection: dialog closed.
        });
        vm.closeDropdown.now();
    }

    getDevice();
    getUsers();
}
