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
export default function DeviceSelectorService($rootScope, $state, $stateParams, logger, DeviceService, ArrayUtilsService,
                                              EVENTS) {
    'ngInject';
    'use strict';

    let selectedDevice;
    let localDevice;

    function isRemoteDevice() {
        return $state.includes('remote');
    }

    function isLocalDevice() {
        return !isRemoteDevice();
    }

    function goToLocalDevice() {
        selectedDevice = localDevice;
        $state.go('main').then(function(newState, params) {
            notifyListeners();
        });
    }

    function goToDevice(deviceId) {
        if (deviceId === localDevice.id) {
            return goToLocalDevice();
        }
        getDevicesByName().then(function(response) {
            selectedDevice = ArrayUtilsService.getItemBy(response, 'id', deviceId);
            $state.go('remote', {deviceId: deviceId}).then(function(newState, params) {
                notifyListeners();
            });
        }, function(reason) {
            logger.error('Could not get devices', reason);
        });
    }

    function getSelectedDevice() {
        return selectedDevice;
    }

    function getDevicesByName() {
        return DeviceService.getDevices().then(function(response) {
            let devices = response.filter(device => !device.isEblocker);
            return ArrayUtilsService.sortByProperty(devices, 'name');
        }, function(reason) {
            logger.error('Failed: ' + JSON.stringify(reason));
        });
    }

    function initSelectedDevice(device) { // injected in routeConfig.js
        localDevice = device;
        selectedDevice = device;
    }

    function notifyListeners() {
        $rootScope.$broadcast(EVENTS.DEVICE_SELECTED);
    }

    return {
        getDevicesByName: getDevicesByName,
        isRemoteDevice: isRemoteDevice,
        isLocalDevice: isLocalDevice,
        goToLocalDevice: goToLocalDevice,
        goToDevice: goToDevice,
        getSelectedDevice: getSelectedDevice,
        initSelectedDevice: initSelectedDevice
    };
}
