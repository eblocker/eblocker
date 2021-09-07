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
    templateUrl: 'app/cards/deviceFirewall/device-firewall.component.html',
    controllerAs: 'vm',
    controller: DeviceFirewallController,
    bindings: {
        cardId: '@'
    }
};

function DeviceFirewallController($rootScope, $scope, $q, logger, $transitions, $interval, DataService, DomainRecorderService, // jshint ignore: line
                                  DeviceSelectorService, DeviceService, CustomDomainFilterService,
                                  NotificationService, FilterModeService, FILTER_TYPE, EVENTS) {
    'ngInject';
    'use strict';

    const vm = this;

    const CARD_NAME = 'DEVICE_FIREWALL';

    vm.recordedDomains = [];
    vm.recordedDomainsFiltered = [];
    vm.device = {};
    vm.patternFiltered = {};
    vm.showLegend = false;
    vm.customDomainFilter = CustomDomainFilterService.emptyFilter();

    vm.resetRecording = resetRecording;
    vm.selectedAction = 'block';
    vm.applyChanges = applyChanges;
    vm.anyDomainSelected = anyDomainSelected;
    vm.loadData = loadData;
    vm.setShowLegend = setShowLegend;

    vm.searchProps = ['domain'];
    vm.searchTerm = '';

    vm.$onInit = function() {
        loadData();
    };

    $scope.$on(EVENTS.DEVICE_SELECTED, loadData);
    $scope.$on(EVENTS.DEVICE_UPDATED, loadData);

    $scope.$on(EVENTS.CUSTOM_DOMAIN_FILTER_UPDATED, loadCustomDomainFilter);

    function loadData() {
        $q.all([loadDevice(), loadCustomDomainFilter()]).then(function(data) {
            processRecordedDomains();
            vm.searchTerm = '';
        }, function(reason) {
            logger.error('Could not load data', reason);
            return $q.reject(reason);
        });
    }

    function loadDevice() {
        vm.device = DeviceSelectorService.getSelectedDevice();
        return DomainRecorderService.getRecordedDomains(vm.device.id).then(function(response) {
            let recordedDomains = [];
            Object.keys(response.data).forEach((domain) => {
                let elem = response.data[domain];
                elem.domain = domain;
                recordedDomains.push(elem);
            });
            vm.recordedDomains = recordedDomains;
            return vm.recordedDomains;
        }, function(reason) {
            logger.error('Could not get recorded domains', reason);
            return $q.reject(reason);
        });
    }

    function loadCustomDomainFilter() {
        return CustomDomainFilterService.getCustomDomainFilter(true).then(function(response) {
            vm.customDomainFilter = response.data;
            return vm.customDomainFilter;
        }, function(reason) {
            logger.error('Could not get custom domain filter', reason);
            return $q.reject(reason);
        });
    }

    vm.onChangeRecordingEnabled = function() {
        DeviceService.update(vm.device).then(function(response) {
            logger.info('Updated device', response);
        }, function(reason) {
            logger.error('Could not update device', reason);
        });
    };

    function resetRecording() {
        DomainRecorderService.resetRecording(vm.device.id).then(function(response) {
            loadData();
        }, function(reason) {
            logger.error('Could not reset recording', reason);
        });
    }

    function applyChanges() {
        let selectedDomains = vm.recordedDomainsFiltered.filter(entry => entry.selected);
        let domains = selectedDomains.map(obj => obj.domain);
        let updater, listToUpdate;
        let toastKey = 'DEVICE_FIREWALL.CARD.MESSAGE.';
        if (vm.selectedAction === 'block') {
            updater = CustomDomainFilterService.updateBlocklist;
            listToUpdate = vm.customDomainFilter.blacklistedDomains;
            toastKey += 'BLOCK';
        } else if (vm.selectedAction === 'allow') {
            updater = CustomDomainFilterService.updatePasslist;
            listToUpdate = vm.customDomainFilter.whitelistedDomains;
            toastKey += 'ALLOW';
        } else {
            logger.error('Unexpected action to apply to domains: ' + vm.selectedAction);
            return;
        }
        appendDomains(listToUpdate, domains);
        updater(listToUpdate).then(function(result) {
            $rootScope.$broadcast(EVENTS.CUSTOM_DOMAIN_FILTER_UPDATED);
            deselectDomains();
            NotificationService.info(toastKey, {numberOfDomains: domains.length});
        }, function(reason) {
            logger.error('Failed to apply changes:' + vm.selectedAction + ': ' + domains, reason);
            return $q.reject(reason);
        });
    }

    function setShowLegend(show) {
        vm.showLegend = show;
    }

    function anyDomainSelected() {
        return angular.isDefined(vm.recordedDomainsFiltered.find(entry => entry.selected));
    }

    function deselectDomains() {
        vm.recordedDomainsFiltered.forEach(domain => {
            if (domain.selected) {
                delete domain.selected;
                domain.deselected = true; // show as gray / italic
            }
        });
    }

    /*
      Append domains that are not already on the first list
    */
    function appendDomains(list, domains) {
        let exists = {};
        list.forEach(d => {
            exists[d] = true;
        });
        domains.forEach(d => {
            if (!exists[d]) {
                list.push(d);
            }
        });
        return list;
    }

    /*
      Sort by:
      1. descending count
      2. ascending name
    */
    function compareRecordedDomains(a, b) {
        return (b.count - a.count) || (a.domain > b.domain);
    }

    /*
      Process the recorded Domains, so the correct icon is shown for each:
      - if patternBlocked > 0 and patternPassed == 0: show as blocked
      - if patternBlocked == 0 and patternPassed > 0: show as passed
      - if patternBlocked > 0 and patternPassed > 0: show as filtered
     */
    function processRecordedDomains() {
        vm.recordedDomains.forEach(domain => {
            if (!domain.blocked) {
                if (domain.patternBlocked > 0 && domain.patternPassed === 0) {
                    domain.blocked = true;
                } else if (domain.patternBlocked > 0 && domain.patternPassed > 0) {
                    domain.patternFiltered = true;
                }
            }
            domain.count = Math.max(domain.count, domain.patternBlocked + domain.patternPassed);
        });
        vm.recordedDomains.sort(compareRecordedDomains);
        vm.recordedDomainsFiltered = angular.copy(vm.recordedDomains);
    }
}
