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
                                  DeviceSelectorService, DeviceService, CustomDomainFilterService, SslService,
                                  FilterModeService, FILTER_TYPE, EVENTS) {
    'ngInject';
    'use strict';

    const vm = this;

    const CARD_NAME = 'DEVICE_FIREWALL';

    vm.recordedDomains = [];
    vm.device = {};
    vm.globalSslEnabled = false;
    vm.patternFiltered = {};
    vm.customDomainFilter = CustomDomainFilterService.emptyFilter();

    vm.resetRecording = resetRecording;
    vm.selectedAction = 'block';
    vm.applyChanges = applyChanges;
    vm.anyDomainSelected = anyDomainSelected;

    function onDeviceSelected() {
        logger.warn('***** The selected device has changed! *****');
        loadData();
    }

    vm.$onInit = function() {
        loadData();
    };

    $scope.$on(EVENTS.DEVICE_SELECTED, loadData);

    $scope.$on(EVENTS.CUSTOM_DOMAIN_FILTER_UPDATED, loadData);

    function loadData() {
        $q.all([loadDevice(), loadCustomDomainFilter(), loadSslStatus()]).then(function(data) {
            findPatternFilteredDomains();
        }, function(reason) {
            logger.error('Could not load data', reason);
            return $q.reject(reason);
        });
    }

    function loadDevice() {
        vm.device = DeviceSelectorService.getSelectedDevice();
        return DomainRecorderService.getRecordedDomains(vm.device.id).then(function(response) {
            vm.recordedDomains = angular.copy(response.data).sort(compareRecordedDomains);
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

    function loadSslStatus() {
        return SslService.getSslStatus().then(function(response) {
            vm.globalSslEnabled = response.data.globalSslStatus;
            return vm.globalSslEnabled;
        }, function(reason) {
            logger.error('Could not get SSL status', reason);
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
            loadDevice();
        }, function(reason) {
            logger.error('Could not reset recording', reason);
        });
    }

    function applyChanges() {
        let selectedDomains = vm.recordedDomains.filter(entry => entry.selected);
        let domains = selectedDomains.map(obj => obj.domain);
        let updater, listToUpdate;
        if (vm.selectedAction === 'block') {
            updater = CustomDomainFilterService.updateBlocklist;
            listToUpdate = vm.customDomainFilter.blacklistedDomains;
        } else if (vm.selectedAction === 'allow') {
            updater = CustomDomainFilterService.updatePasslist;
            listToUpdate = vm.customDomainFilter.whitelistedDomains;
        } else {
            logger.error('Unexpected action to apply to domains: ' + vm.selectedAction);
            return;
        }
        appendDomains(listToUpdate, domains);
        updater(listToUpdate).then(function(result) {
            $rootScope.$broadcast(EVENTS.CUSTOM_DOMAIN_FILTER_UPDATED);
        }, function(reason) {
            logger.error('Failed to apply changes:' + vm.selectedAction + ': ' + domains, reason);
            return $q.reject(reason);
        });
    }

    function anyDomainSelected() {
        return angular.isDefined(vm.recordedDomains.find(entry => entry.selected));
    }

    function deviceUsesPatternFilter() {
        return FilterModeService.getEffectiveFilterMode(vm.globalSslEnabled, vm.device) === FILTER_TYPE.PATTERN;
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
      If the pattern filter is active for the device and a domain was not blocked, the reason could be one of:
      - the domain (or one of its parent domains) was allowed explicitly, so the pattern filter was not applied
      - the domain was not on any domain block list (but requests might have been blocked by the pattern filter)
      We want to distinguish these cases in the UI.
     */
    function findPatternFilteredDomains() {
        if (!deviceUsesPatternFilter()) {
            return;
        }
        let allowed = {}; // build a tree of allowed domains
        vm.customDomainFilter.whitelistedDomains.forEach(domain => {
            let parts = domain.split('.').reverse();
            var node = allowed;
            for (var i = 0; i < parts.length; i++) {
                let part = parts[i];
                if (node[part] === true) {
                    break; // already allowed
                }
                if (angular.isUndefined(node[part])) {
                    node[part] = {}; // new subdomain
                }
                if (i === parts.length - 1) { // last element?
                    node[part] = true;
                } else {
                    node = node[part]; // go to subdomain
                }
            }
        });
        function isAllowed(domain) {
            let parts = domain.split('.').reverse();
            var node = allowed;
            for (var i = 0; i < parts.length; i++) {
                let part = parts[i];
                if (node[part] === true) {
                    return true;
                }
                if (angular.isUndefined(node[part])) {
                    node[part] = {}; // try subdomain
                }
                node = node[part];
            }
            return false;
        }
        vm.recordedDomains.forEach(recordedDomain => {
            let domain = recordedDomain.domain;
            if (!recordedDomain.blocked) {
                if (!isAllowed(domain)) {
                    recordedDomain.patternFiltered = true;
                }
            }
        });
    }
}
