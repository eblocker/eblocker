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
    templateUrl: 'app/cards/deviceStatus/device-status.component.html',
    controllerAs: 'vm',
    controller: DeviceStatusController,
    bindings: {
        cardId: '@'
    }
};

function DeviceStatusController(logger, DeviceSelectorService, DeviceService) {
    'ngInject';
    'use strict';

    const vm = this;

    const CARD_NAME = 'DEVICE_STATUS';

    vm.device = {};
    vm.onChangeDevice = onChangeDevice;

    vm.$onInit = function() {
        getSelectedDevice();
        DeviceSelectorService.registerDeviceSelected(getSelectedDevice);
    };

    vm.$onDestroy = function() {
        DeviceSelectorService.unregisterDeviceSelected(getSelectedDevice);
    };

    function getSelectedDevice() {
        vm.device = DeviceSelectorService.getSelectedDevice();
    }

    function onChangeDevice() {
        DeviceService.update(vm.device).then(function success(response){
            vm.device = response.data;
        }, function error(response) {
            logger.error('Could not en-/disabled state of device', response);
        });
    }
}
