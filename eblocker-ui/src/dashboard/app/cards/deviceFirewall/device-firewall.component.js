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

function DeviceFirewallController(logger, $transitions, $interval, DataService, DomainRecorderService,
                                  DeviceSelectorService, DeviceService) {
    'ngInject';
    'use strict';

    const vm = this;

    const CARD_NAME = 'DEVICE_FIREWALL';

    vm.recordedDomains = [];
    vm.device = {};

    vm.resetRecording = resetRecording;

    function onDeviceSelected() {
        logger.warn('***** The selected device has changed! *****');
        loadDevice();
    }

    vm.$onInit = function() {
        loadDevice();
        DeviceSelectorService.registerDeviceSelected(loadDevice);
    };

    vm.$onDestroy = function() {
        DeviceSelectorService.unregisterDeviceSelected(loadDevice);
    };

    function loadDevice() {
        vm.device = DeviceSelectorService.getSelectedDevice();
        logger.warn('***** Got selected device ' + vm.device.id + ' *****', vm.device);
        DomainRecorderService.getRecordedDomains(vm.device.id).then(function(response) {
            vm.recordedDomains = angular.copy(response.data).sort(compareRecordedDomains);
        }, function(reason) {
            logger.error('No luck');
        });
    }

    vm.onChangeRecordingEnabled = function() {
        DeviceService.update(vm.device).then(function(response) {
            logger.warn('***** Updated device *****', response);
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

    /*
      Sort by:
      1. descending count
      2. ascending name
    */
    function compareRecordedDomains(a, b) {
        return (b.count - a.count) || (a.domain > b.domain);
    }
}
