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
    templateUrl: 'app/components/devices/discovery/devices-discovery.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        users: '<',
        profiles: '<',
        dnsEnabled: '<',
        sslEnabled: '<'
    }
};

function Controller(DeviceService, $interval) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.$onInit = function() {
        vm.deviceScanningInterval = 10; // default
        getScanningInterval();
        isScanningAvailable();
        isAutoEnableNewDevices();
    };

    vm.setScanningInterval = setScanningInterval;
    vm.setAutoEnableNewDevices = setAutoEnableNewDevices;
    vm.scanDevices = scanDevices;


    function getScanningInterval() {
        DeviceService.getScanningInterval().then(function success(response) {
            vm.deviceScanningInterval = response.data;
        });
    }

    function setScanningInterval() {
        DeviceService.setScanningInterval(vm.deviceScanningInterval);
    }

    function isScanningAvailable() {
        DeviceService.isScanningAvailable().then(function(response) {
            vm.scanningAvailable = response.data;
        });
    }

    function isAutoEnableNewDevices() {
        DeviceService.isAutoEnableNewDevices().then(function(response) {
            vm.isAutoEnableNewDevices = response.data;
        });
    }

    function setAutoEnableNewDevices() {
        DeviceService.setAutoEnableNewDevices(vm.isAutoEnableNewDevices);
    }

    function scanDevices() {
        vm.scanInProgress = true;
        vm.scanningProgress = 0;
        $interval(function() {
            vm.scanningProgress += 10;
            if (vm.scanningProgress >= 100) {
                vm.scanInProgress = false;
            }
        }, 200, 10);
        DeviceService.scan();
    }
}
