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
    templateUrl: 'app/wizard/mobile/mobile-setup-wizard.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(logger, $state, $window, deviceDetector, DeviceService, VpnHomeService, NotificationService,
                    DialogService) { // jshint ignore: line
    'ngInject';

    const vm = this;

    vm.finish = finish;
    vm.close = close;
    vm.backToDashboard = backToDashboard;
    vm.downloadClientConf = downloadClientConf;
    vm.nextStep = nextStep;
    vm.prevStep = prevStep;
    vm.getConfigFileName = getConfigFileName;
    vm.getOpenVPNName = getOpenVPNName;

    vm.isWindows = isWindows;
    vm.isIos = isIos;
    vm.isMac = isMac;
    vm.isAndroid = isAndroid;
    vm.isOther = isOther;

    vm.$onInit = function() {
        vm.currentStep = 1;
        vm.maxSteps = 7;
        vm.osTypes = [
            {type: 'WINDOWS', name: 'windows', value: 'Windows'},
            {type: 'MAC', name: 'mac', value: 'MacOS'},
            {type: 'IOS', name: 'ios', value: 'iOS'},
            {type: 'ANDROID', name: 'android', value: 'Android'}
            // ,
            // {type: 'OTHER', name: 'other', value: 'SHARED.MOBILE.DEVICE_TYPE.OTHER'}
        ];
        vm.deviceOs = getDeviceTypeObject(vm.osTypes, deviceDetector.os);
        loadDevice().then(function success() {
            getOpenVPNName(vm.device);
        });
    };

    function finish() {
        close(true);
    }

    function isWindows() {
        return vm.deviceOs.type === 'WINDOWS';
    }

    function isIos() {
        return vm.deviceOs.type === 'IOS';
    }

    function isMac() {
        return vm.deviceOs.type === 'MAC';
    }

    function isAndroid() {
        return vm.deviceOs.type === 'ANDROID';
    }

    function isOther() {
        return vm.deviceOs.type === 'OTHER';
    }

    function backToDashboard(event) {
        DialogService.closeMobileWizard(event, close);
    }

    function close(reload) {
        return $state.transitionTo('main', undefined, {
            location: true,
            inherit: true,
            reload: reload === true,
            relative: $state.$current,
            notify: reload === true
        }).catch(function(e) {
            logger.error('Could not transition to main: ' + e);
        });
    }

    function nextStep() {
        if (isNextStepAllowed(vm.currentStep, vm.maxSteps)) {

            if (vm.currentStep === 2 && (!isWindows() && !isMac())) {
                // skip step 3, which is only for Windows or Mac (install app)
                vm.currentStep = 4;
            } else if (vm.currentStep === 4 && (!isWindows() && !isMac())) {
                // skip step 5, which is only for Windows and Mac (install config)
                vm.currentStep = 6;
            } else {
                vm.currentStep++;
            }
        }
    }

    function prevStep() {
        if (vm.currentStep > 1) {
            if (vm.currentStep === 6 && (!isWindows() && !isMac())) {
                vm.currentStep = 4;
            } else if (vm.currentStep === 4 && (!isWindows() && !isMac())) {
                vm.currentStep = 2;
            } else {
                vm.currentStep--;
            }
        }
    }

    function isNextStepAllowed(current, max) {
        const num = current + 1;
        return num <= max;
    }

    function loadDevice() {
        return DeviceService.getDevice().then(function success(response) {
            if (angular.isObject(response.data)) {
                vm.device = response.data;
            }
        });
    }

    function getConfigFileName() {
        return vm.openVpnFileName || '';
    }

    // STEP 2 -- Choose OS, download config

    function getDeviceTypeObject(types, type) {
        let ret = {name: 'other', value: 'SHARED.MOBILE.DEVICE_TYPE.OTHER'};
        types.forEach((item) => {
            if (item.name === type) {
                ret = item;
            }
        });
        return ret;
    }

    function getOpenVPNName(device) {
        if (!angular.isObject(device) || angular.isUndefined(device.id)) {
            NotificationService.error('WIZARD.MOBILE.CHOOSE_OS.NOTIFY_NO_DEVICE');
            return;
        }
        VpnHomeService.getOpenVpnFileName(device.id, vm.deviceOs.type).then(function success(response) {
            vm.openVpnFileName = response.data;
        }, function(response) {
            logger.error('Error getting VPN file name ', response);
        });
    }

    function downloadClientConf(device) {
        if (!angular.isObject(device)) {
            NotificationService.error('WIZARD.MOBILE.CHOOSE_OS.NOTIFY_NO_DEVICE');
            return;
        }
        vm.isDownloadingConf = true;
        VpnHomeService.generateDownloadUrl(device.id, vm.deviceOs.type).then(function success(response) {
            // Sort certificates into dic
            $window.location = response.data;
        }, function error(response) {
            // fail
        }).finally(function done() {
            vm.isDownloadingConf = false;
        });
    }

}
