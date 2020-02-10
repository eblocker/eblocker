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
export default function ProvidePinDialogController($scope, $mdDialog, $translate, user, users,
                                                  mode, lockEnabled, onCancel, onOk, onLock) {
    'ngInject';
    let vm = this;
    vm.user = user;
    vm.user.name = $translate.instant(vm.user.name);
    vm.users = users;
    vm.mode = mode;
    vm.lockEnabled = lockEnabled;

    vm.cancel = function() {
        onCancel();
        $mdDialog.cancel();
    };

    vm.commit = function() {
        if (vm.mode === 'askForPin') {
            doChangeUser();
        } else if (vm.mode === 'selectUser') {
            doSelectUser();
        } else if (vm.mode === 'confirmLock') {
            lock();
        }
    };

    vm.confirmLock = function() {
        vm.mode = 'confirmLock';
        $scope.pinForm.$setPristine();
        $scope.pinForm.$setUntouched();
    };

    function doChangeUser() {
        $scope.pinForm.userPin.$setValidity('pinInvalid', true);

        // Anything wrong?
        if (!$scope.pinForm.$valid) {
            return;
        }

        let promise = onOk(vm.user, vm.pin);

        promise.then(function(data) {
            $mdDialog.hide(data);
        }, function(error) {
            $scope.pinForm.userPin.$setValidity(error, false);
        });
    }

    function lock() {
        onLock();
        $mdDialog.hide();
    }

    function doSelectUser() {
        if (!vm.user.containsPin) {
            onOk(vm.user).then(function(data) {
                $mdDialog.hide(data);

            });
            return;
        }
        vm.mode = 'askForPin';
        $scope.pinForm.$setPristine();
        $scope.pinForm.$setUntouched();
    }
}
