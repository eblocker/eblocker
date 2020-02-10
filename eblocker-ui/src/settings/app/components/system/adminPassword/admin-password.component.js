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
    templateUrl: 'app/components/system/adminPassword/admin-password.component.html',
    controller: AdminPasswordController,
    controllerAs: 'vm'
};

function AdminPasswordController(logger, $mdDialog, $translate,
                                 security, NotificationService, UserService, DeviceService) {
    'ngInject';

    const vm = this;

    vm.openChangePasswordDialog = openChangePasswordDialog;
    vm.togglePasswordRequired = togglePasswordRequired;
    vm.changePassword = changePassword;

    vm.enabled = security.isPasswordRequired();

    function togglePasswordRequired() {
        if (security.isPasswordRequired()) {
            openChangePasswordDialog(false).then(function() {
                vm.enabled = false;
                security.isPasswordRequired(false);
                NotificationService.info('ADMINCONSOLE.ADMIN_PASSWORD.NOTIFICATION.INFO_PASSWORD_DISABLED');
            }, function() {
                vm.enabled = true;
            });

        } else {
            openChangePasswordDialog(true).then(function() {
                vm.enabled = true;
                security.isPasswordRequired(true);
                NotificationService.info('ADMINCONSOLE.ADMIN_PASSWORD.NOTIFICATION.INFO_PASSWORD_ENABLED');
            }, function() {
                vm.enabled = false;
            });
        }
    }

    function changePassword() {
        openChangePasswordDialog(true).then(function success() {
            NotificationService.info('ADMINCONSOLE.ADMIN_PASSWORD.NOTIFICATION.INFO_PASSWORD_CHANGED');
        });
    }


    function openChangePasswordDialog(enabled) {
        return $mdDialog.show({
            controller: 'PasswordDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/adminPassword/password.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose: false,
            locals: {
                enabled: enabled,
                passwordRequired: security.isPasswordRequired()
            }
        }).then(function success(response) {
            if (enabled) {
                // reload to avoid having two default users
                UserService.getAll(true).then(function success(response) {
                    let defaultParent;
                    response.data.forEach((user) => {
                        if (user.userRole === 'PARENT' && user.nameKey === 'SHARED.USER.NAME.DEFAULT_PARENT') {
                            defaultParent = user;
                        }
                    });
                    if (angular.isUndefined(defaultParent)) {
                        defaultParent = UserService.getEmptyUser();
                        defaultParent.name = $translate.instant('SHARED.USER.NAME.DEFAULT_PARENT');
                        defaultParent.nameKey = 'SHARED.USER.NAME.DEFAULT_PARENT';
                        defaultParent.userRole = 'PARENT';
                        UserService.createUserWithProfile(defaultParent).then(function success(newUser) {
                            assignUserToCurrentDevice(newUser);
                        }, function error(response) {
                            logger.error('Unable to create default parent ', response);
                        });
                    } else {
                        assignUserToCurrentDevice(defaultParent);
                    }
                });
            }
        });
    }

    function assignUserToCurrentDevice(user) {
        DeviceService.getAll().then(function success(response) {
            response.data.forEach((device) => {
                if (device.isCurrentDevice) {
                    device.assignedUser = user.id;
                    device.operatingUser = user.id;
                    DeviceService.update(device.id, device);
                }
            });
        });
    }
}
