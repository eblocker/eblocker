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
    templateUrl: 'app/cards/whitelistDns/whitelist-dns.component.html',
    controllerAs: 'vm',
    controller: MessageController,
    bindings: {
        cardId: '@'
    }
};

function MessageController(logger, $q, $timeout, DeviceService, SslService, FilterService, ArrayUtilsService,
                           CustomDomainFilterService,
                           DialogService, CardService) {
    'ngInject';
    'use strict';

    const vm = this;

    const CARD_NAME = 'WHITELIST_DNS'; // 'card-10';

    vm.device = {
        filterMode: ''
    };

    vm.customDomainFilter = {
        blacklistedDomains: [],
        whitelistedDomains: []
    };

    vm.$onInit = function() {
        DeviceService.getDevice().then(function success(response) {
            if (angular.isObject(response.data)) {
                vm.device = response.data;
            }
        });

        CustomDomainFilterService.getCurrentUserCustomDomainFilter().then(function(response) {
            vm.customDomainFilter = response.data;
        }).catch(function error() {
            vm.customDomainFilter = {
                blacklistedDomains: [],
                whitelistedDomains: []
            };
        });

        SslService.getSslStatus(false).then(function success(response) {
            vm.sslGloballyEnabled = response.data;
        });
    };

    vm.$postLink = function() {
        $timeout(function() {
            CardService.scrollToCard(CARD_NAME);
        }, 300);
    };

    vm.onChangeDevice = function(dev) {
        return DeviceService.update(dev);
    };

    vm.updateBlacklist = function(domains) {
        vm.customDomainFilter.blacklistedDomains = domains;
        return save();
    };

    vm.updateWhitelist = function(domains) {
        vm.customDomainFilter.whitelistedDomains = domains;
        return save();
    };

    function save() {
        if (!vm.device.showDnsFilterInfoDialog) {
            return CustomDomainFilterService.setCurrentUserCustomDomainFilter(vm.customDomainFilter)
                .then(function successUpdateFilterList(response) {
                    return response;
                });
        } else {
            return DialogService.dnsFilterChangeInfo(undefined, saveDevice, vm.device).finally(function done() {
                return CustomDomainFilterService.setCurrentUserCustomDomainFilter(vm.customDomainFilter).
                then(function successUpdateFilterList(response) {
                    return response;
                });
            });
        }
    }

    function saveDevice(device, checkboxValue) {
        device.showDnsFilterInfoDialog = !checkboxValue;
        return DeviceService.update(device);
    }
}
