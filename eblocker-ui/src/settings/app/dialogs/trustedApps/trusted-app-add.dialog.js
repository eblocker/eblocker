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
export default function TrustedAppAddController($mdDialog, module, TrustedAppsService) {
    'ngInject';

    const vm = this;

    vm.title = module.name;

    vm.module = module;

    vm.nameMaxLength = 50;
    vm.descriptionMaxLength = 150;
    vm.domainIpMaxLength = 2048;

    vm.cancel = function() {
        $mdDialog.cancel();
    };

    vm.nameChange = function() {
        vm.appModuleForm.name.$setValidity('uniqueName', true);
    };

    vm.submit = function() {
        if (!vm.appModuleForm.$valid) {
            return;
        }

        TrustedAppsService.parseAndSave(vm.module).then(function(module){
            $mdDialog.hide(module);
        }, function error(response) {
            if (response.status === 409) {
                vm.appModuleForm.name.$setValidity('uniqueName', false);
            }
        });
    };

}
