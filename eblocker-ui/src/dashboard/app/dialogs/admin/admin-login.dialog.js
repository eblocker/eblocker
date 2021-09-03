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
export default function AdminLoginDialogController(logger, $scope, security, DeviceSelectorService,
                                                   RedirectService, $mdDialog, APP_CONTEXT, onCancel, onOk) {
    'ngInject';
    let vm = this;
    vm.devices = [];
    vm.loggedIn = false;
    vm.adminPasswordRequired = true;

    vm.$onInit = function() {
        security.isPasswordRequired(APP_CONTEXT.adminAppContextName).then(function (response) {
            vm.adminPasswordRequired = response;
        }, function (reason) {
            logger.error('Error getting admin password requirement: ' + reason);
        });
    };

    vm.cancel = function() {
        onCancel(vm.loggedIn);
        $mdDialog.cancel();
    };

    function loadDevices() {
        return DeviceSelectorService.getDevicesByName().then(function(response) {
            vm.devices = response;
        }, function(reason) {
            logger.error('Failed: ' + JSON.stringify(reason));
        });
    }

    vm.selectDevice = function(deviceId) {
        //alert('You selected: ' + deviceId);
        onOk(deviceId);
        $mdDialog.hide();
    };

    vm.login = function() {
        security.requestAdminToken(APP_CONTEXT.adminAppContextName, vm.adminPassword).then(function success(response) {
            //alert('Got response: ' + JSON.stringify(response));
            vm.loggedIn = true;
            return loadDevices();
        }, function (reason) {
            logger.error('Admin login failed: ' + reason);
            if (reason === 'error.credentials.invalid') {
                vm.backendErrorKey = 'PASSWORD_INVALID';
            } else if (reason === 'error.credentials.too.soon') {
                vm.backendErrorKey = 'PASSWORD_TOO_FREQ';
            } else {
                vm.backendErrorKey = 'UNKNOWN';
            }
            vm.passwordForm.adminPassword.$setValidity('backend', false);
        });
    };

    vm.setAdminPassword = function() {
        RedirectService.toConsole(false, '/#!/system/adminpassword');
        vm.cancel();
    };
}
