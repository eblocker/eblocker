/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
    templateUrl: 'app/cards/domainBlocklist/domain-passlist.component.html',
    controllerAs: 'vm',
    controller: DomainPasslistController,
    bindings: {
        cardId: '@'
    }
};

function DomainPasslistController(logger, $q, $interval, DataService, CustomDomainFilterService,
                                  DeviceSelectorService) {
    'ngInject';
    'use strict';

    const vm = this;
    const CARD_NAME = 'DOMAIN_PASSLIST';

    vm.customDomainFilter = CustomDomainFilterService.emptyFilter();

    vm.$onInit = function() {
        loadCustomDomainFilter();
        DeviceSelectorService.registerDeviceSelected(loadCustomDomainFilter);
    };

    vm.$onDestroy = function() {
        DeviceSelectorService.unregisterDeviceSelected(loadCustomDomainFilter);
    };

    function loadCustomDomainFilter() {
        CustomDomainFilterService.getCustomDomainFilter(true).then(function(response) {
            vm.customDomainFilter = response.data;
        });
    }

    vm.updatePasslist = updatePasslist;

    function updatePasslist(domains) {
        logger.warn('Updating passlist', domains);
        return CustomDomainFilterService.updatePasslist(domains);
    }
}
