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
export default function PasswordDialogController($http, $mdDialog, PasswordService, enabled, passwordRequired) {
    'ngInject';

    const vm = this;
    vm.enable = enabled;
    vm.passwordRequired = passwordRequired;
    vm.credentials = {
        currentPassword: '',
        newPassword: ''
    };

    vm.maxLength = 50;

    vm.cancel = function() {
        $mdDialog.cancel();
    };

    vm.ok = function() {
        if (angular.isDefined(vm.passwordForm.currentPassword)) {
            vm.passwordForm.currentPassword.$setValidity('passwordInvalid', true);
        }
        if (angular.isDefined(vm.passwordForm.repeatPassword)) {
            vm.passwordForm.repeatPassword.$setValidity('mustMatch', true);
        }

        //
        // Do new passwords match?
        //
        if (vm.enable && vm.credentials.newPassword !== vm.credentials.repeatPassword) {
            vm.passwordForm.repeatPassword.$setValidity('mustMatch', false);
        }
        //
        // Any other form error?
        //
        if (!vm.passwordForm.$valid) {
            return;
        }

        if (enabled) {
            PasswordService.enable(vm.credentials).then(function(response){
                $mdDialog.hide(response);
            }, function(error) {
                vm.passwordForm.currentPassword.$setValidity(error, false);
            });
        } else {
            PasswordService.disable(vm.credentials).then(function(response){
                $mdDialog.hide(response);
            }, function(error) {
                vm.passwordForm.currentPassword.$setValidity(error, false);
            });
        }
    };
}
