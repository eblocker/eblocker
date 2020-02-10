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
export default function NetworkEditModeController(logger, $mdDialog, $translate, config) {
    'ngInject';

    const vm = this;

    vm.config = angular.copy(config);
    vm.maxLength = 2048;

    vm.networkMode = getNetworkMode(config);

    function getNetworkMode(config) {
        if (config.automatic) {
            return 'auto';
        } else if (config.expertMode) {
            return 'expert';
        }
        return 'individual';
    }

    vm.cancel = function() {
        $mdDialog.cancel();
    };

    vm.submit = function() {
        if (!vm.networkModeForm.$valid) {
            return;
        }

        if (vm.networkMode === 'auto') {
            vm.config.automatic = true;
            vm.config.expertMode = false;
        } else if (vm.networkMode === 'individual') {
            vm.config.automatic = false;
            vm.config.expertMode = false;
        } else if (vm.networkMode === 'expert') {
            vm.config.automatic = false;
            vm.config.expertMode = true;
        }

        $mdDialog.hide(vm.config);
    };
}
