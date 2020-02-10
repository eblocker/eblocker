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
    templateUrl: 'app/cards/icon/icon.component.html',
    controller: IconController,
    controllerAs: 'vm',
    bindings: {
        cardId: '@'
    }
};

function IconController($timeout, $interval, iconService, CardService, DeviceService, SslService, DataService) {
    'ngInject';
    'use strict';

    const vm = this;

    const CARD_NAME = 'ICON'; // 'card-7';

    /*
     * Variables
     */
    vm.show = false;
    vm.fiveSeconds = false;
    vm.browserOnly = false;
    vm.iconPosition = 'RIGHT';
    vm.getStatus = getStatus;

    vm.device = {};

    function getStatus() {
        if ((!vm.sslGloballyEnabled || !vm.device.sslEnabled) && vm.device.displayIconOn) {
            return 'WARN';
        }
        return '';
    }

    function parseSettings(settings){
        vm.show = settings.enabled;
        vm.fiveSeconds = settings.fiveSeconds;
        vm.browserOnly = settings.browserOnly;
        vm.iconPosition = settings.iconPosition;
    }
    function getSettings() {
        iconService.getSettings().then(parseSettings);
    }

    function getDevice(reload) {
        return DeviceService.getDevice(reload).then(function success(response) {
            const device = response.data; // use in UI
            if (device.iconMode === 'OFF'){
                device.displayIconOn = false;
                device.displayIconFiveSeconds = false;
                device.displayIconBrowserOnly = false;
            } else if (device.iconMode === 'ON_ALL_DEVICES') {
                device.displayIconOn = true;
                device.displayIconFiveSeconds = false;
                device.displayIconBrowserOnly = false;
            } else if (device.iconMode === 'ON') {// =ON_BROWSER_ONLY
                device.displayIconOn = true;
                device.displayIconFiveSeconds = false;
                device.displayIconBrowserOnly = true;
            } else if (device.iconMode === 'FIVE_SECONDS') {
                device.displayIconOn = true;
                device.displayIconFiveSeconds = true;
                device.displayIconBrowserOnly = false;
            } else if (device.iconMode === 'FIVE_SECONDS_BROWSER_ONLY') {
                device.displayIconOn = true;
                device.displayIconFiveSeconds = true;
                device.displayIconBrowserOnly = true;
            }
            vm.device = device;
            return response.data;
        });
    }

    function getSslState() {
        SslService.getSslStatus().then(function(response) {
            vm.sslGloballyEnabled = response.data.globalSslStatus;
        });
    }

    /*
     * Initialization, destructor
     */
    vm.$onInit = function() {
        getSettings();
        updateData();
        startUpdateInterval();
        DataService.registerComponentAsServiceListener(CARD_NAME, 'SslService');
        DataService.registerComponentAsServiceListener(CARD_NAME, 'DeviceService');
    };

    vm.$postLink = function() {
        $timeout(function() {
            CardService.scrollToCard(CARD_NAME);
        }, 300);
    };

    vm.$onDestroy = function() {
        stopUpdateInterval();
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'SslService');
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'DeviceService');
    };

    let dataUpdateInterval;

    function startUpdateInterval() {
        dataUpdateInterval = $interval(updateData, 2000);
    }

    function stopUpdateInterval() {
        if (angular.isDefined(dataUpdateInterval)) {
            $interval.cancel(dataUpdateInterval);
            dataUpdateInterval = undefined;
        }
    }

    function updateData() {
        getDevice();
        getSslState();
    }

    vm.reset = function() {
        iconService.resetSettings().then(parseSettings);
    };

    vm.onChange = function() {
        const settings={
            enabled: vm.show,
            fiveSeconds: vm.fiveSeconds,
            browserOnly: vm.browserOnly,
            iconPosition: vm.iconPosition
        };
        iconService.setSettings(settings).then(parseSettings).finally(function done() {
            // vm.device.displayIconOn = vm.show;
            getDevice(true);
        });
    };
}
