/*
 * Copyright 2023 eBlocker Open Source UG (haftungsbeschraenkt)
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
export default {
    templateUrl: 'app/components/network/settingsIp6/network-settings-ip6.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        configurationIp6: '<'
    }
};

function Controller(logger, DeveloperService, NetworkService) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.updateIp6Config = updateIp6Config;
    vm.developer = false;

    vm.$onInit = function() {
        updateDisplayData(vm.configurationIp6);
        vm.developer = DeveloperService.isDeveloper();
    };

    function updateDisplayData(config) {
        vm.localAddresses = {
            value: config.localAddresses.join('\n')
        };
        vm.globalAddresses = {
            value: config.globalAddresses.join('\n')
        };

        vm.showWarningGlobalAddressMissing = config.routerAdvertisementsEnabled &&
            config.globalAddresses.length === 0;

        vm.showWarningIp6Leak = !config.routerAdvertisementsEnabled &&
            config.globalAddresses.length > 0;
    }

    function updateIp6Config() {
        vm.isUpdatingIp6Config = true;
        NetworkService.setNetworkIp6Config(vm.configurationIp6).then(function(response) {
            saved(response.data);
        }, function() {
            cancelled();
        }).finally(function() {
            vm.isUpdatingIp6Config = false;
        });
    }

    function saved(config) {
        updateDisplayData(config);
    }

    function cancelled() {
    }
}
