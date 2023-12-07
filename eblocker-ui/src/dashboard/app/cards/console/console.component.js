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
    templateUrl: 'app/cards/console/console.component.html',
    controller: ConsoleController,
    controllerAs: 'vm',
    bindings: {
        cardId: '@'
    }
};

function ConsoleController($scope, $timeout, LanguageService, RedirectService, NetworkService,
                           IpUtilsService, CardService, registration, DeviceSelectorService, EVENTS) {
    'ngInject';
    'use strict';

    const vm = this;

    const CARD_NAME = 'CONSOLE'; // 'card-6'

    vm.openConsole = openConsole;

    vm.showOtherAddresses = false;
    vm.otherIps = [];

    vm.$onInit = function() {
        NetworkService.getNetworkStatus().then(function success(response) {
            if (angular.isObject(response.data)) {
                vm.gateway = response.data.gateway;
                vm.eblocker = response.data.ipAddress;
                vm.localDevice = response.data.userIpAddress;
                setDeviceIps();
            }
        });
    };

    $scope.$on(EVENTS.DEVICE_SELECTED, setDeviceIps);

    vm.$postLink = function() {
        $timeout(function() {
            CardService.scrollToCard(CARD_NAME);
        }, 300);
    };

    vm.toggleOtherAddresses = function() {
        vm.showOtherAddresses = !vm.showOtherAddresses;
    };

    function setDeviceIps() {
        let device = DeviceSelectorService.getSelectedDevice();
        let sortedIps = IpUtilsService.sortByVersion(device.ipAddresses);
        vm.device = sortedIps.shift();
        vm.otherIps = sortedIps;
    }

    function openConsole() {
        RedirectService.toConsole(false);
    }
}
