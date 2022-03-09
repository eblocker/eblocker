/*
 * Copyright 2022 eBlocker Open Source UG (haftungsbeschraenkt)
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
export default function ConfigBackupImportController(logger, $scope, $mdDialog, fileName, passwordRequired,
                                                     passwordRetry) {
    'ngInject';

    const vm = this;
    vm.fileName = fileName;
    vm.passwordRequired = passwordRequired;
    vm.includeKeys = passwordRequired; // default: ask for password if keys are contained in the backup
    vm.passwordRetry = passwordRetry;
    vm.maxLength = 50;

    vm.close = function() {
        if (!vm.configBackupImportForm.$valid) {
            return;
        }
        $mdDialog.hide(vm.password);
    };

    vm.cancel = function() {
        $mdDialog.cancel();
    };
}
