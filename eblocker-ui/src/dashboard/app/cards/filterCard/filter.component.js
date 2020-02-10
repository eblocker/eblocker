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
    templateUrl: 'app/cards/filterCard/filter.component.html',
    controllerAs: 'vm',
    controller: MessageController,
    bindings: {
        cardId: '@'
    }
};

function MessageController(logger, $interval, $timeout, DeviceService, SslService, DnsService, CardService,
                           RedirectService, DataService) {
    'ngInject';
    'use strict';

    const vm = this;

    const CARD_NAME = 'FILTER'; // 'card-9';

    vm.onChange = onChange;
    vm.showWarning = showWarning;
    vm.goToHttps = goToHttps;
    vm.goToDns = goToDns;
    vm.activateSsl = activateSsl;
    vm.getStatus = function() {
        return vm.showWarning() ? 'WARN' : '';
    };

    vm.device = {
        filterMode: ''
    };

    let dataUpdateInterval;
    const INTERVAL = 2000;

    vm.$onInit = function() {
        updateData();
        startUpdateInterval();
        DataService.registerComponentAsServiceListener(CARD_NAME, 'DnsService');
        DataService.registerComponentAsServiceListener(CARD_NAME, 'SslService');
        DataService.registerComponentAsServiceListener(CARD_NAME, 'DeviceService');
    };

    vm.$onDestroy = function() {
        stopUpdateInterval();
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'DnsService');
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'SslService');
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'DeviceService');
    };

    vm.$postLink = function() {
        $timeout(function() {
            CardService.scrollToCard(CARD_NAME);
        }, 300);
    };

    function startUpdateInterval() {
        if (angular.isUndefined(dataUpdateInterval)) {
            dataUpdateInterval = $interval(updateData, INTERVAL);
        }
    }

    function stopUpdateInterval() {
        if (angular.isDefined(dataUpdateInterval)) {
            $interval.cancel(dataUpdateInterval);
            dataUpdateInterval = undefined;
        }
    }

    function updateData(reload) {
        DeviceService.getDevice(reload).then(function success(response) {
            if (angular.isObject(response.data)) {
                vm.device = response.data;
            }
        });

        SslService.getSslStatus().then(function success(response) {
            vm.sslGloballyEnabled = response.data.globalSslStatus;
        });

        DnsService.getStatus().then(function success(response) {
            vm.dnsEnabled = response.data;
        });
    }

    function onChange(dev) {
        return DeviceService.update(dev);
    }

    function showWarning() {
        return ((!vm.sslGloballyEnabled || !vm.device.sslEnabled) && vm.device.filterMode === 'ADVANCED') ||
            (!vm.dnsEnabled && vm.device.filterMode === 'PLUG_AND_PLAY') ||
            !vm.device.malwareFilterEnabled ||
            vm.device.filterMode === 'NONE';
    }

    function goToHttps(){
        RedirectService.toConsole(false, '/#!/ssl/status');
    }

    function goToDns(){
        RedirectService.toConsole(false, '/#!/dns/status');
    }

    function activateSsl() {
        SslService.setDeviceStatus(true).then(function(response){
            updateData(true);
        }, function(response){
            // fail
        });
    }
}

