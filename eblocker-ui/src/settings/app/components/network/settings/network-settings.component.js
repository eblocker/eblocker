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
export default {
    templateUrl: 'app/components/network/settings/network-settings.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        configuration: '<',
        dnsEnabled: '<'
    }
};

function Controller(logger, DialogService, NetworkService, StateService, STATES) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.editMode = editMode;
    vm.editContent = editContent;
    vm.networkSettingsForm = {};
    vm.$onInit = function() {
        updateDisplayData(vm.configuration);
    };

    function updateDisplayData(config) {
        vm.mode = {
            value: getModeTranslationKey(config)
        };
        vm.ipAddress = {
            value: config.ipAddress
        };
        vm.networkMask = {
            value: config.networkMask
        };
        vm.gateway = {
            value: config.gateway
        };
        vm.dhcpService = {
            value: config.dhcp ? 'ADMINCONSOLE.NETWORK_SETTINGS.DHCP.EBLOCKER' :
                'ADMINCONSOLE.NETWORK_SETTINGS.DHCP.EXTERNAL'
        };
        vm.firstIp = {
            value: config.dhcpRangeFirst
        };
        vm.lastIp = {
            value: config.dhcpRangeLast
        };
        vm.dhcpLeaseTime = {
                value: NetworkService.getDhcpLeaseTimeTranslation(config.dhcpLeaseTime)
        };
    }

    function editContent(event, config) {
        DialogService.networkEditContent(event, config, vm.dnsEnabled).then(function ok(newConfig) {
            // update may not be necessary since there's going to be a reboot anyway ..
            updateDisplayData(newConfig);
            vm.configuration = newConfig;
        }, function error() {
            // user has clicked cancel
        });
    }

    function editMode(event) {
        DialogService.networkEditMode(event, vm.configuration).then(function ok(newConfig) {
            if (noChangeInConfig(newConfig, vm.configuration)) {
                return;
            }
            if (newConfig.automatic || !newConfig.expertMode) {
                // changed to automatic: open wizard
                const param = {configuration: newConfig};
                StateService.goToState(STATES.NETWORK_WIZARD, param);
            } else if (newConfig.expertMode) {
                // changed to expert mode: just show view
                editContent(event, newConfig);
                // vm.configuration = newConfig;
                // updateDisplayData(vm.configuration);
            }
        }, function cancel() {

        });
    }

    function noChangeInConfig(newConfig, oldConfig) {
        if (newConfig.automatic && oldConfig.automatic) {
            // is and was automatic
            return true;
        } else if (!newConfig.automatic && !oldConfig.automatic &&
            newConfig.expertMode && oldConfig.expertMode) {
            // is and was expert
            return true;
        } else if (!newConfig.automatic && !newConfig.expertMode &&
            !oldConfig.automatic && !oldConfig.expertMode) {
            // is and was individual
            return true;
        }
        return false;
    }

    function getModeTranslationKey(config) {
        if (config.automatic) {
            return 'ADMINCONSOLE.NETWORK_SETTINGS.MODE.AUTO';
        } else if (config.expertMode) {
            return 'ADMINCONSOLE.NETWORK_SETTINGS.MODE.EXPERT';
        }
        return 'ADMINCONSOLE.NETWORK_SETTINGS.MODE.INDIVIDUAL';
    }
}
