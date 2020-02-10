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
export default  {
    templateUrl: 'app/cards/ssl/ssl.component.html',
    controller: SslController,
    controllerAs: 'vm',
    bindings: {
        cardId: '@'
    }
};

function SslController($interval, $q, $window, $translate, DeviceService, SslService, UserProfileService, CardService, // jshint ignore: line
                       $timeout, logger, DataService) {
    'ngInject';
    'use strict';

    const vm = this;

    const CARD_NAME = 'SSL'; // 'card-3';

    // Information about the status of the device
    vm.status = {};
    // Information about the certificate
    vm.certificate = {};
    vm.renewal = {};
    vm.isAddCertificateLinkClicked = false;
    vm.deviceId = '';

    const UPDATE_INTERVAL = 10000;

    let updateTimer;


    vm.$onInit = function() {
        // load data once manually to init the card
        loadData().then(function success(response) {
            const ssl = angular.isObject(response.data) ? response.data : null;
            if (angular.isObject(ssl) && ssl.executeSslBackgroundCheck) {
                return testSslCertificate(response.data);
            }
            const deferred = $q.defer();
            deferred.resolve();
            return deferred.promise;
        }, angular.noop).then(angular.noop, angular.noop).finally(function(response) {
            // In any case we want to activate the HTTPS wizard.
            vm.sslTestDone = true;
        });
        startUpdateTimer(); // update the status via interval

        DataService.registerComponentAsServiceListener(CARD_NAME, 'DeviceService');
        DataService.registerComponentAsServiceListener(CARD_NAME, 'SslService');
        DataService.registerComponentAsServiceListener(CARD_NAME, 'UserProfileService');
    };

    function testSslCertificate(ssl) {
        const isRenewal = ssl.renewalCertificateAvailable;
        const certSerial = ssl.rootCertSerialNumber;
        if (isRenewal) {
            const renewalCertSerial = ssl.renewalCertSerialNumber;
            return $q.all([
                SslService.testSsl(vm.deviceId, certSerial, 3001, true),
                SslService.testSsl(vm.deviceId, renewalCertSerial, 3002, true)
            ]);
        }

        return SslService.testSsl(vm.deviceId, certSerial, 3001, true);
    }

    vm.$onDestroy = function() {
        stopUpdateTimer();
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'DeviceService');
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'SslService');
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'UserProfileService');
    };

    vm.$postLink = function() {
        $timeout(function() {
            CardService.scrollToCard(CARD_NAME);
        }, 300);
    };

    function loadData() {
        return DeviceService.getDevice().then(function(response) {
            vm.deviceId = response.data.id;
            return update();
        }).finally(function(response) {
            return response;
        });
    }

    /*
     * Timer and update functions
     */
    function startUpdateTimer() {
        // already running?
        if (angular.isDefined(updateTimer)) {
            return;
        }
        updateTimer = $interval(loadData, UPDATE_INTERVAL);
    }

    function stopUpdateTimer() {
        if (angular.isDefined(updateTimer)) {
            $interval.cancel(updateTimer);
            updateTimer = undefined;
        }
    }

    function update() {
        // TODO this seems unnecessarily complicated: maybe just set 'vm.status = response.data' and work with that..
        return SslService.getSslStatus().then(function success(response) {
            const status = response.data;

            // Global status
            vm.status.global = status.globalSslStatus;
            vm.executeSslBackgroundCheck = angular.isDefined(status.executeSslBackgroundCheck) &&
                status.executeSslBackgroundCheck;

            // Additional info only if needed
            if (vm.status.global) {
                // SSL active for current device?
                vm.status.active = status.deviceSslStatus;

                // Is current device restricted under Parental Control?
                updateParentalControlData();

                // Is current certificate available?
                vm.certificate.available = status.currentCertificateAvailable;
                if (status.currentCertificateAvailable) {
                    vm.certificate.installed = status.currentCertificateInstalled;
                    vm.certificate.display = true;
                    // Copy name of CA/certificate to display ("eBlocker - Name - YYYY/MM/DD")
                    vm.certificate.name = status.currentCertificate.distinguishedName.commonName;
                    vm.certificate.serialNumber = status.rootCertSerialNumber;
                    vm.certificate.expires = {
                        date: {
                            year: status.currentCertificateEndDate.year,
                            month: status.currentCertificateEndDate.month,
                            day: status.currentCertificateEndDate.day
                        }
                    };
                    vm.certificate.expires.days = status.currentCertificateEndDate.daysTill;
                    vm.certificate.expires.displayWarning = status.currentCertificateEndDate.daysTill < 30;
                }
                // Is renewal certificate available?
                vm.renewal.available = status.renewalCertificateAvailable;
                if (status.renewalCertificateAvailable) {// TODO: or use ....daysTill<30 in the condition?
                    vm.renewal.installed = status.renewalCertificateInstalled;
                    vm.renewal.display = !vm.renewal.installed;// Only display when not installed
                    // Copy name of CA/certificate to display ("eBlocker - Name - YYYY/MM/DD")
                    vm.renewal.name = status.renewalCertificate.distinguishedName.commonName;
                    vm.renewal.serialNumber = status.renewalCertSerialNumber;
                    vm.renewal.expires = {
                        date: {
                            year: status.renewalCertificateEndDate.year,
                            month: status.currentCertificateEndDate.month,
                            day: status.currentCertificateEndDate.day
                        }
                    };
                } else {
                    vm.renewal.display = false;
                }
            }
            vm.status.display = true;
            determineCardStatus();
            return response;
        }, function error(response) {
            // fail
            return $q.reject(response);
        });
    }

    function updateParentalControlData() {
        return UserProfileService.getCurrentUsersProfile()
            .then(function(response) {
                const userProfile = response.data;
                if (userProfile.controlmodeMaxUsage || userProfile.controlmodeTime ||
                    userProfile.controlmodeUrls) {
                    vm.status.restricted = true;
                }
                return userProfile;
            }, function(response) {
                // fail
                return $q.reject(response);
            });
    }

    /*
     * Functions related to the card status
     */
    vm.showRenewalCertificateWarning = function() {
        return vm.status.global && vm.renewal.display;
    };

    vm.showRenewalCertificate = function() {
        return vm.status.global && vm.renewal.available;
    };

    function determineCardStatus() {
        if (!vm.executeSslBackgroundCheck) {
            // We are not interested in the installation status of the certs
            // The overall card status depends only on the SSL activation status.
            vm.cardStatus = (vm.status.global && vm.status.active ? '' : 'WARN');

        } else if (vm.status.global && vm.getCaStatus() === 'ERROR') {
            // Error case on top, since errors supersede warnings;
            // this is relevant for when the root cert is not imported and SSL is not active for a device.
            // Here we want to show an error (untrusted root cert) and not a warning (SSL disabled for device).
            // Error case only relevant if SSL enabled at all.
            vm.cardStatus = 'ERROR';
        } else if (!vm.status.global ||
            !vm.status.active ||
            vm.getCaStatus() === 'WARN' ||
            vm.showRenewalCertificateWarning()) {
            vm.cardStatus = 'WARN';
        } else {
            vm.cardStatus = '';
        }
    }

    vm.getCaStatus = function() {
        if (vm.certificate.installed === false && !vm.isAddCertificateLinkClicked && vm.status.active) {
            return 'ERROR';
        } else if (angular.isUndefined(vm.certificate.installed) || vm.certificate.installed === false) {
            return 'WARN';
        }
        return 'OK';
    };

    /*
     * Buttons
     */
    vm.activateSsl = function() {
        SslService.setDeviceStatus(true)
            .then(function(response){
                vm.status.active = response;
                determineCardStatus();
            }, function(response){
                // fail
            });
    };

    vm.deactivateSsl = function() {
        SslService.setDeviceStatus(false)
            .then(function(response){
                vm.status.active = response;
                determineCardStatus();
            }, function(response){
                // fail
            });
    };

    vm.openSettings = function() {
        $window.open('/', 'eblocker.console');
    };

    vm.installCertificate = function () {
        logger.info('Link clicked to download CA root certificate.');
        vm.isAddCertificateLinkClicked = true;
        determineCardStatus(); // update card status
    };

    vm.openHelp = function(langId) {
        $translate(langId).then(function success(translation) {
            $window.open('/help/#/ssl?lang=' + translation, '_blank');
        });

    };
}
