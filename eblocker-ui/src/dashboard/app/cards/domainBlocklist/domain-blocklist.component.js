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
    templateUrl: 'app/cards/domainBlocklist/domain-blocklist.component.html',
    controllerAs: 'vm',
    controller: DomainBlocklistController,
    bindings: {
        cardId: '@'
    }
};

function DomainBlocklistController($rootScope, $scope, logger, $q, $interval, DataService, CustomDomainFilterService,
                                   DeviceSelectorService, EVENTS) {
    'ngInject';
    'use strict';

    const vm = this;
    const CARD_NAME = 'DOMAIN_BLOCKLIST';
    let updating = false;

    vm.customDomainFilter = CustomDomainFilterService.emptyFilter();
    vm.user = undefined;

    vm.$onInit = function() {
        loadCustomDomainFilter();
    };

    $scope.$on(EVENTS.DEVICE_SELECTED, loadCustomDomainFilter);

    $scope.$on(EVENTS.CUSTOM_DOMAIN_FILTER_UPDATED, function(event) {
        if (!updating) { // don't reload if update was triggered by this card, so the user has a chance to undo
            loadCustomDomainFilter();
        }
    });

    function loadCustomDomainFilter() {
        CustomDomainFilterService.getCustomDomainFilter(true).then(function(response) {
            vm.customDomainFilter = response.data;
        });
        vm.user = CustomDomainFilterService.getUserName();
    }

    vm.updateBlocklist = updateBlocklist;

    function updateBlocklist(domains) {
        updating = true;
        return CustomDomainFilterService.updateBlocklist(domains).then(function(response) {
            $rootScope.$broadcast(EVENTS.CUSTOM_DOMAIN_FILTER_UPDATED);
            return response;
        }, function(reason) {
            logger.error('Failed to update custom domain filter blocklist', reason);
            return $q.reject(reason);
        }).finally(function() {
            updating = false;
        });
    }
}
