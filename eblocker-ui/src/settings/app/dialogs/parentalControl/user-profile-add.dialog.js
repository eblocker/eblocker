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
export default function Controller(logger, $mdDialog, module, openDetails,
                                   UserProfileService, NotificationService) {
    'ngInject';

    const vm = this;

    vm.title = module.name;

    vm.nameMaxLength = 50;
    vm.descMaxLength = 150;


    vm.module = angular.copy(module);

    vm.openDetails = openDetails;

    vm.submit = submit;
    vm.cancel = cancel;
    vm.isUniqueName = isUniqueName;

    vm.isNew = !angular.isDefined(module.id);

    function cancel() {
        $mdDialog.cancel();
    }

    function save() {
        UserProfileService.saveNewProfile(vm.module).then(function success(newProfile) {
            $mdDialog.hide(newProfile);
        }, function error(response) {
            NotificationService.error('ADMINCONSOLE.SERVICE.USER_PROFILE.ERROR_SAVE_NEW', response);
        });
    }

    function submit() {
        isUniqueName(vm.module.name, vm.module.id).then(function() {
            vm.newProfileForm.name.$setValidity('unique', true);
            if (!vm.newProfileForm.$valid) {
                return;
            }
            // ok will only close the dialog, if okAction() succeeded.
            save();

            }, function(data) {
                vm.newProfileForm.name.$setValidity('unique', false);
            });
    }

    function isUniqueName(name, id) {
        return UserProfileService.uniqueName(name, id);
    }
}
