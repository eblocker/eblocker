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
export default function EditUserRoleController(logger, $mdDialog, user, UserService) {
    'ngInject';

    const vm = this;
    vm.user = angular.copy(user);

    vm.submit = submit;
    vm.cancel = cancel;
    vm.userRoleChange = userRoleChange;

    if (angular.isUndefined(vm.userRoles)) {
        vm.userRoles = UserService.getUserRoles();
    }

    if (angular.isUndefined(vm.user.userRole)) {
        vm.user.userRole = 'PARENT';
    }

    // Init birthday
    if (angular.isUndefined(vm.user.birthday) && vm.user.userRole === 'CHILD') {
        vm.user.birthday = Date.now();
    }

    function cancel() {
        $mdDialog.cancel();
    }

    function save() {
        UserService.updateUser(vm.user).then(function() {
            $mdDialog.hide(vm.user);
        }, function(error) {
            // Do nothing
        });
    }

    function submit() {
        save();
    }

    function userRoleChange() {
        if (vm.user.userRole !== 'CHILD') {
            vm.user.birthday = undefined;
        } else {
            vm.user.birthday = vm.user.birthday || Date.now();
        }
    }
}
