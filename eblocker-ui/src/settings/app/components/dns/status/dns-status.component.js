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
    templateUrl: 'app/components/dns/status/dns-status.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(logger, DnsService, StateService, STATES, $q, NotificationService, NetworkService) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.dnsSwitch = false;
    vm.dnsStatusFlagChanged = dnsStatusFlagChanged;
    vm.saveDnsConfiguration = saveDnsConfiguration;
    vm.goToCustomList = goToCustomList;
    vm.flushCache = flushCache;
    vm.isDefaultGatewayDefined = isDefaultGatewayDefined;
    vm.hasBeenFlushed = false;

    vm.$onInit = function() {

        vm.configuration = {
            dnsModeListStrategy: 'default'
        };
        vm.loading = true;
        $q.all([
            loadStatus(),
            loadConfiguration(),
            loadAndSetNetworkConfiguration()
        ]).finally(function notLoading() {
            vm.loading = false;
        });
    };

    function loadAndSetNetworkConfiguration() {
        NetworkService.getNetworkConfig().then(function success(response) {
            vm.networkConfig = response.data;
            vm.isAutoMode = vm.networkConfig.automatic;
        });
    }

    function loadStatus(reload) {
        return DnsService.loadDnsStatus(reload).then(function(response) {
            vm.dnsSwitch = response.data;
        });
    }

    function dnsStatusFlagChanged() {
        // vm.dnsSwitch = !vm.dnsSwitch; // set to let UI react quickly
        if (!vm.dnsSwitch) {
            // quickly turn off DNS manually, so that UI disables DNS tabs
            DnsService.setDnsStatusLocally(false);
        }
        return DnsService.saveDnsStatus(vm.dnsSwitch).then(function(status) {
            // vm.dnsSwitch = status; // now set to actual value (should be the same as above..)
            loadStatus(true);
            if (angular.isUndefined(vm.configuration.selectedDnsMode)){
                loadConfiguration();
            }
        });
    }

    function saveDnsConfiguration() {
        DnsService.saveDnsConfiguration(vm.configuration).then(function success(configuration) {
            vm.configuration = configuration;
            setGatewayIp(configuration);
            NotificationService.info('ADMINCONSOLE.DNS_STATUS.NOTIFICATION.INFO_SAVED_DNS_CONFIGURATION');
        });
    }

    function setGatewayIp(configuration) {
        if (configuration.selectedDnsMode === 'dhcp' &&
            angular.isArray(configuration.nameServers) &&
            configuration.nameServers.length === 1) {
            vm.gatewayIp = configuration.nameServers[0];
        }
    }

    function isDefaultGatewayDefined() {
        return angular.isString(vm.gatewayIp);
    }

    function loadConfiguration() {
        return DnsService.loadDnsConfiguration().then(function(configuration) {
            vm.configuration = configuration;
            setGatewayIp(configuration);
        });
    }

    function flushCache() {
        vm.flushingCache = true;
        DnsService.flushDnsCache().then(function(response){
            NotificationService.info('ADMINCONSOLE.DNS_STATUS.NOTIFICATION.INFO_CACHE_FLUSHED');
        }, function(response){
            logger.error('error flushing DNS cache ', response);
            NotificationService.info('ADMINCONSOLE.DNS_STATUS.NOTIFICATION.ERROR_CACHE_FLUSHED');
        }).finally(function setFlag() {
            vm.flushingCache = false;
            vm.hasBeenFlushed = true;
        });
    }

    function goToCustomList() {
        StateService.goToState(STATES.DNS_SERVER_LIST);
    }
}
