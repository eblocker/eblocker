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
export default function ChangePinDialogController($scope, $mdDialog, module, UserService) {
    'ngInject';

    let vm = this;

    vm.title = module.name;

    vm.module = module;

    vm.isNew = !angular.isDefined(module.id);

    vm.cancel = function() {
        resetPin();
        $mdDialog.cancel();
    };

    vm.save = function() {
        // Do new PINs match?
        if (module.containsPin) {
            $scope.changePinForm.oldPin.$setValidity('pinInvalid', true);
        }
        if (vm.module.newPin !== vm.module.newPinAgain){
            $scope.changePinForm.newPin2.$setValidity('mustMatch', false);
        } else {
            $scope.changePinForm.newPin2.$setValidity('mustMatch', true);
        }
        // Anything wrong?
        if (!$scope.changePinForm.$valid) {
            return;
        }
        UserService.savePin(vm.module).then(function(){
            resetPin();
            vm.module.containsPin = true;
            // notificationService.info('INFO_SERVICE_PARENTAL_CONTROL_CHANGE_PIN');
            $mdDialog.hide(module);
        }, function(module){
            $scope.changePinForm.oldPin.$setValidity('pinInvalid', false);
        });
    };

    function resetPin() {
        vm.module.oldPin = undefined;
        vm.module.newPin = undefined;
        vm.module.newPinAgain = undefined;
    }
}
