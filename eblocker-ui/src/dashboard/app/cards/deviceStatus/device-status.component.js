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

function DeviceStatusController($scope, logger, DeviceSelectorService, DeviceService, FilterModeService, SslService,
                                FILTER_TYPE, EVENTS) {
    'ngInject';
    'use strict';

    const vm = this;

    const CARD_NAME = 'DEVICE_STATUS';

    vm.device = {};
    vm.globalSslEnabled = false;
    vm.domainFilterActive = false;
    vm.patternFilterActive = false;

    vm.onChangeDevice = onChangeDevice;

    vm.$onInit = function() {
        loadData();
    };

    $scope.$on(EVENTS.DEVICE_SELECTED, loadData);
    $scope.$on(EVENTS.DEVICE_UPDATED, loadData);

    function loadData() {
        loadSslStatus().then(function(response) {
            getSelectedDevice();
            getFilterMode();
        });
    }

    function getSelectedDevice() {
        vm.device = DeviceSelectorService.getSelectedDevice();
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

    function onChangeDevice() {
        DeviceService.update(vm.device).then(function success(response){
            vm.device = response.data;
        }, function error(response) {
            logger.error('Could not en-/disable state of device', response);
        });
    }

    function getFilterMode() {
        const filterMode = FilterModeService.getEffectiveFilterMode(vm.globalSslEnabled, vm.device);
        vm.domainFilterActive = false;
        vm.patternFilterActive = false;
        if (filterMode === FILTER_TYPE.PATTERN) {
            vm.patternFilterActive = true;
        } else if (filterMode === FILTER_TYPE.DNS) {
            vm.domainFilterActive = true;
        }
    }
}
