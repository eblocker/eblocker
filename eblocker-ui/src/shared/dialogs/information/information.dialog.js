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
export default function InformationDialogController(logger, $mdDialog, msgKeys, subject, checkbox, okAction) {
    'ngInject';

    const vm = this;

    vm.msgKeys = msgKeys;
    vm.inProgress = false;
    vm.checkboxValue = checkbox;

    vm.ok = function() {
        vm.inProgress = true;
        if (angular.isFunction(okAction)) {
            okAction(subject, vm.checkboxValue).then(function success() {
                $mdDialog.hide(subject, vm.checkboxValue);
            }, function(data) {
                logger.error('Error executing action ', data);
            }).finally(function done() {
                vm.inProgress = false;
            });
        } else {
            vm.inProgress = false;
            $mdDialog.hide(subject, vm.checkboxValue);
        }

    };
}
