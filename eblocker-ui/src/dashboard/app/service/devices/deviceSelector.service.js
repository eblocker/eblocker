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
export default function DeviceSelectorService($rootScope, $state, $stateParams, logger, DeviceService,
                                              security, ArrayUtilsService, EVENTS) {
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
            notifyListenersSelected();
        });
    }

    function goToDevice(deviceId) {
        if (deviceId === localDevice.id) {
            return goToLocalDevice();
        }
        getDevicesByName().then(function(response) {
            let device = ArrayUtilsService.getItemBy(response, 'id', deviceId);
            if (angular.isObject(device)) {
                selectedDevice = device;
                $state.go('remote', {deviceId: deviceId}).then(function(newState, params) {
                    notifyListenersSelected();
                });
            } else {
                logger.error('No access to device ' + deviceId);
            }
        }, function(reason) {
            logger.error('Could not get devices', reason);
        });
    }

    function getSelectedDevice() {
        return selectedDevice;
    }

    function getDevicesByName() {
        let isLoggedInAsAdmin = security.isLoggedInAsAdmin();
        let func = isLoggedInAsAdmin ? DeviceService.getAllDevices : DeviceService.getOperatingUserDevices;
        return func().then(function(response) {
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

    // Clients of the service can signal that they have updated the device,
    // so the service can reload the selected device and broadcast a DEVICE_UPDATED event.
    function onDeviceUpdate() {
        logger.info('Device was updated. Reloading selected device...');
        let deviceId = getSelectedDevice().id;
        getDevicesByName().then(function(response) {
            let device = ArrayUtilsService.getItemBy(response, 'id', deviceId);
            if (angular.isObject(device)) {
                selectedDevice = device;
                notifyListenersUpdated();
            } else {
                logger.error('No access to device ' + deviceId);
            }
        }, function(reason) {
            logger.error('Could not get devices', reason);
        });
    }

    function notifyListenersSelected() {
        $rootScope.$broadcast(EVENTS.DEVICE_SELECTED);
    }

    function notifyListenersUpdated() {
        $rootScope.$broadcast(EVENTS.DEVICE_UPDATED);
    }

    return {
        getDevicesByName: getDevicesByName,
        isRemoteDevice: isRemoteDevice,
        isLocalDevice: isLocalDevice,
        goToLocalDevice: goToLocalDevice,
        goToDevice: goToDevice,
        getSelectedDevice: getSelectedDevice,
        initSelectedDevice: initSelectedDevice,
        onDeviceUpdate: onDeviceUpdate
    };
}
