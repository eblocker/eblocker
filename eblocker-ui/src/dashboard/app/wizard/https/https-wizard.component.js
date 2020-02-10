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
    templateUrl: 'app/wizard/https/https-wizard.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(logger, $state, $interval, deviceDetector, DeviceService, NotificationService, DialogService, // jshint ignore: line
                    SslService) {
    'ngInject';

    const vm = this;

    vm.finish = finish;
    vm.close = close;
    vm.backToDashboard = backToDashboard;
    vm.nextStep = nextStep;
    vm.prevStep = prevStep;

    vm.isFirefox = isFirefox;
    vm.isWindows = isWindows;
    vm.isIos = isIos;
    vm.isMac = isMac;
    vm.isAndroid = isAndroid;
    vm.isOther = isOther;

    const STEP_TEST = 7;
    const STEP_FINISHED = 8;
    const STEP_ALREADY_INSTALLED = 9;
    vm.STEP_NO_WIZARD = 10;

    vm.$onInit = function() {
        vm.maxSteps = STEP_ALREADY_INSTALLED;
        vm.osTypes = [
            {type: 'WINDOWS', name: 'windows', value: 'Windows'},
            {type: 'MAC', name: 'mac', value: 'MacOS'},
            {type: 'IOS', name: 'ios', value: 'iOS'},
            {type: 'ANDROID', name: 'android', value: 'Android'}
            // ,
            // {type: 'OTHER', name: 'other', value: 'SHARED.MOBILE.DEVICE_TYPE.OTHER'}
        ];

        vm.deviceOs = getDeviceTypeObject(vm.osTypes, deviceDetector.os);

        vm.deviceBrowser = deviceDetector.browser;

        vm.currentStep = isOther() && !isFirefox() ? vm.STEP_NO_WIZARD : 1;

        vm.isCertInstalledFirefox = false;
        vm.isCertInstalledOs = false;

        loadDevice();
        loadSslStatus(true).then(function success(response) {

            vm.isRenwal = vm.ssl.renewalCertificateAvailable && !vm.ssl.renewalCertificateInstalled;

            if (vm.isRenwal) {
                vm.certificateDownloadPath = '/api/ssl/renewalCertificate.crt';
                // to download the cert for the OS; w/o Firefox trying to install the cert
                vm.firefoxCertificateInstallationPath = '/api/ssl/firefox/renewalCertificate.crt';
            } else {
                vm.certificateDownloadPath = '/api/ssl/caCertificate.crt';
                // to download the cert for the OS; w/o Firefox trying to install the cert
                vm.firefoxCertificateInstallationPath = '/api/ssl/firefox/caCertificate.crt';
            }

            vm.isCertInstalledOs = (vm.ssl.currentCertificateInstalled &&
                !vm.ssl.renewalCertificateAvailable) ||
                (vm.ssl.renewalCertificateAvailable && vm.ssl.renewalCertificateInstalled);

            vm.isCertInstalledFirefox = vm.isCertInstalledOs;

        });
    };

    function loadDevice() {
        return DeviceService.getDevice().then(function success(response) {
            if (angular.isObject(response.data)) {
                vm.device = response.data;
            }
        });
    }

    function loadSslStatus(reload) {
        return SslService.getSslStatus(reload).then(function success(response) {
            vm.ssl = response.data;
            return response;
        });
    }

    function getDeviceTypeObject(types, type) {
        let ret = {name: 'other', type: 'OTHER', value: 'SHARED.MOBILE.DEVICE_TYPE.OTHER'};
        types.forEach((item) => {
            if (item.name === type) {
                ret = item;
            }
        });
        return ret;
    }

    function finish() {
        close(true);
    }

    function isFirefox() {
        return vm.deviceBrowser === 'firefox';
        // return false;
    }

    function isWindows() {
        return vm.deviceOs.type === 'WINDOWS';
        // return true;
    }

    function isIos() {
        return vm.deviceOs.type === 'IOS';
        // return true;
    }

    function isMac() {
        return vm.deviceOs.type === 'MAC';
        // return false;
    }

    function isAndroid() {
        return vm.deviceOs.type === 'ANDROID';
        // return true;
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

    function nextStep() { // jshint ignore: line
        // make sure that checkboxes have to be re-checked and that a failed test is not displayed again
        resetCheckboxVariables();
        resetSslTestVariables();

        if (vm.currentStep === 1 && isFirefox() && vm.isCertInstalledFirefox) {
            vm.currentStep = STEP_ALREADY_INSTALLED;
        } else if (vm.currentStep === 1 && !isFirefox() && !vm.isCertInstalledOs) {
            // NOT firefox --> offer to install in OS
            vm.currentStep = 4;
        } else if (vm.currentStep === 1 && !isFirefox() && vm.isCertInstalledOs) {
            // NOT firefox --> already installed, go to INFO page ("for firefox, do wizard again .. ")
            vm.currentStep = STEP_ALREADY_INSTALLED; // ALREADY INSTALLED
        } else if (vm.currentStep === 2 && vm.isCertInstalledOs) {
            // Firefox, just installed, OS already installed --> go to test page
            vm.currentStep = STEP_FINISHED;
        } else if (isFirefox() && (vm.currentStep === 3 || vm.currentStep === STEP_ALREADY_INSTALLED)) {
            // Firefox just installed, OS not installed
            // Firefox already installed, OS not installed
            // --> offer to install for OS
            vm.currentStep = 4;
        } else if (vm.currentStep === 4 && !isMac() && !isWindows()) {
            // skipping two extra add-states that are only required for mac and win
            vm.currentStep = STEP_TEST;
        } else if (isNextStepAllowed(vm.currentStep, vm.maxSteps)) {
            vm.currentStep++;
        }
    }

    function prevStep() { // jshint ignore: line
        if (vm.currentStep <= 1) {
            return;
        }

        if (vm.currentStep === STEP_TEST  && !isMac() && !isWindows()) {
            // skipping two extra add-states that are only required for mac and win
            vm.currentStep = 4;
        } else if (vm.currentStep === 3 && isFirefox() && vm.isCertInstalledFirefox) {
            vm.currentStep = 1;
        } else if (vm.currentStep === 4 && isFirefox() && vm.isCertInstalledFirefox) {
            vm.currentStep = STEP_ALREADY_INSTALLED;
        } else if (vm.currentStep === 4 && !isFirefox()) {
            vm.currentStep = 1;
        } else if (vm.currentStep === STEP_ALREADY_INSTALLED) {
            vm.currentStep = 1;
        } else {
            vm.currentStep--;
        }
    }

    function isNextStepAllowed(current, max) {
        return (current + 1) <= max;
    }

    function resetCheckboxVariables() {
        vm.confirmSelectStore = false;
        vm.confirmCertListedMac = false;
        vm.confirmInstallCertFirefox = false;
        vm.confirmDownload = false;
    }

    // STEP 2 Import cert into firefox
    vm.getFirefoxCertificateDownloadUrl = getFirefoxCertificateDownloadUrl;
    vm.loadCertificateFirefox = loadCertificateFirefox;

    function getFirefoxCertificateDownloadUrl(){
        return vm.certificateDownloadPath;
    }

    function loadCertificateFirefox() {
        logger.debug('Loading certificate for Firefox ...');
    }


    // STEP 4 download cert not firefox
    vm.loadCertificate = loadCertificate;
    vm.getCertificateDownloadUrl = getCertificateDownloadUrl;

    function getCertificateDownloadUrl() {
        return isFirefox() ? vm.firefoxCertificateInstallationPath : vm.certificateDownloadPath;
    }

    function loadCertificate() {
        logger.debug('Loading certificate ...');
    }

    // STEP 5 Test certificate, not firefox
    vm.testCertificate = testCertificate;
    let sslUpdateTimer, sslPollCounter;

    function resetSslTestVariables() {
        vm.currentCertificateInstalled = false;
        vm.currentCertificateFailed = false;
    }

    function testCertificate() {
        const certSerial = vm.isRenwal ? vm.ssl.renewalCertSerialNumber : vm.ssl.rootCertSerialNumber;

        resetSslTestVariables();

        logger.debug('Testing cert ' + certSerial);

        if (angular.isDefined(certSerial) && angular.isObject(vm.device)) {
            vm.isTestingCert = true;
            sslPollCounter = 0;
            /**
             * Test call will try to do SSL handshake. On success it updates the SSL status on the server.
             * So after the test, we keep polling the SSL status and check for the installed-flag.
             */
            SslService.testSsl(vm.device.id, certSerial, vm.isRenwal ? 3002 : 3001);
            sslUpdateTimer = $interval(pollForSsl, 1000);
        } else {
            const deviceId = angular.isObject(vm.device) ? vm.device.id : 'undefined';
            logger.warn('Cannot check certificate status for cert ' + certSerial + ' of device: ' + deviceId);
            if (!angular.isObject(vm.device)) {
                NotificationService.error('WIZARD.HTTPS.NOTIFICATION.NOTIFY_NO_DEVICE');
            } else {
                NotificationService.error('WIZARD.HTTPS.NOTIFICATION.NOTIFY_NO_CERT');
            }
        }
    }

    function pollForSsl() {
        loadSslStatus(true).then(function success(response) {

            // TODO not sure if we really need two vars: might be; for navigation and one for ssl-testing
            vm.currentCertificateInstalled = (vm.ssl.currentCertificateInstalled &&
                !vm.ssl.renewalCertificateAvailable) ||
                (vm.ssl.renewalCertificateAvailable && vm.ssl.renewalCertificateInstalled);

            vm.isCertInstalledFirefox = vm.currentCertificateInstalled;

            if (vm.currentCertificateInstalled) {
                // automatically check the checkbox, when the certificate test is successful
                vm.confirmInstallCertFirefox = true;
            }

            if (vm.currentCertificateInstalled) {
                logger.debug('Certificate is installed. Stop polling SSL status.');
                // it is installed, we are done here
                stopUpdateTimer();
            }
        }).finally(function done() {
            sslPollCounter++;
            if (sslPollCounter >= 10) {
                logger.debug('Counter threshold reached. Stop polling SSL status.');
                // we've tried 10 times to check for installed cert. So it's probably not installed. stop polling.
                stopUpdateTimer();
                vm.currentCertificateFailed = true;
            }
        });
    }

    function stopUpdateTimer() {
        vm.isTestingCert = false;
        if (angular.isDefined(sslUpdateTimer)) {
            $interval.cancel(sslUpdateTimer);
            sslUpdateTimer = undefined;
        }
    }
}
