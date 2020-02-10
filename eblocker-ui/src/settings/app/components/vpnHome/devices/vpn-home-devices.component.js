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
    templateUrl: 'app/components/vpnHome/devices/vpn-home-devices.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(logger, VpnHomeService, DeviceService, NotificationService, $interval, $filter, $window,
                    PaginationService) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.status = {isRunning: false, isFirstStart: false, host: null};
    vm.devices = [];// Devices, not to be displayed
    vm.certificates = {};
    // vm.displayDevices; // Devices, only displaying certificates and selected information
    vm.isCreatingCertificate = false;
    vm.isRevokingCertificate = false;
    let updateTimer;
    const UPDATE_INTERVAL = 10000;

    vm.$onDestroy = function() {
        stopTimer();
    };

    vm.$onInit = function() {
        vm.query = PaginationService.getConfig(); // get default
        update(true);
        startTimer();
    };


    // ** START: TABLE
    vm.changeOrder = changeOrder;

    // Order table
    vm.orderKey = 'name';
    vm.reverseOrder = false;
    // Filter table
    vm.searchProps = ['name', 'displayIpAddresses'];

    function changeOrder(key) {
        if (key === vm.orderKey) {
            vm.reverseOrder = !vm.reverseOrder;
        } else {
            vm.orderKey = key;
        }
    }
    // END TABLE

    function startTimer() {
        if (!angular.isDefined(updateTimer)) {
            updateTimer = $interval(update, UPDATE_INTERVAL);
        }
    }

    function stopTimer() {
        if (angular.isDefined(updateTimer)) {
            $interval.cancel(updateTimer);
            updateTimer = undefined;
        }
    }

    function update(init) {
        VpnHomeService.loadStatus().then(function(response) {
            vm.status.isRunning = response.data.isRunning;
            vm.status.isFirstStart = response.data.isFirstStart;
            if (init === true) {
                // We do not want to override the host; user may be typing ...
                vm.status.host = response.data.host;
            }

        }, function(response) {
            // fail
            stopTimer();
        });

        if (!vm.status.isFirstStart) {
            DeviceService.getAll().then(function(response) {
                vm.devices = response.data;
                // Load certificates
                VpnHomeService.loadCertificates().then(function(certificates) {
                    // Sort certificates into dic
                    vm.certificates = certificates.data;
                    //sortCertificates(certificates);
                    processDevicesForDisplay();
                }, function(response) {
                    // fail
                });
            }, function(response) {
                // fail
            });
        }
    }

    function processDevicesForDisplay() {
        let tmpDisplay = [];
        for (let i = 0; i < vm.devices.length; i++) {
            let device = vm.devices[i];
            device.hasCertificate = angular.isDefined(vm.certificates) && vm.certificates.indexOf(device.id) > -1;
            tmpDisplay.push(device);
        }
        vm.tableData = $filter('filter')(tmpDisplay, function(device) {
            return !device.isEblocker && !device.isGateway;
        });
        vm.filteredTableData = angular.copy(vm.tableData);
    }

    vm.createCertificate = function(device) {
        if (device.hasCertificate || vm.status.isFirstStart || vm.isCreatingCertificate) {
            return;
        }

        stopTimer();
        vm.isCreatingCertificate = true;
        VpnHomeService.createCertificate(device.id).then(function(certificate) {
            device.hasCertificate = true;
            device.certificate = certificate;
            NotificationService.info('ADMINCONSOLE.VPN_HOME.NOTIFICATION.CERTIFICATE_CREATED');
            vm.isCreatingCertificate = false;
            device.hasCertificate = true;
            startTimer();
        }, function(response) {
            vm.isCreatingCertificate = false;
            update();
            startTimer();
        });
    };

    vm.downloadClientConf = function(device) {
        if (!device.hasCertificate || vm.status.isFirstStart) {
            return;
        }

        if (!angular.isString(vm.status.host) || vm.status.host === '') {
            NotificationService.error('ADMINCONSOLE.VPN_HOME.NOTIFICATION.HOST_MISSING');
        } else {
            VpnHomeService.generateDownloadUrl(device.id).then(function success(response) {
                // Sort certificates into dic
                $window.location = response.data;
            }, function error(response) {
                // fail
            });
        }
    };

    vm.revokeCertificate = function(device) {
        if (!device.hasCertificate || vm.status.isFirstStart) {
            return;
        }

        stopTimer();
        vm.isRevokingCertificate = true;
        VpnHomeService.revokeCertificate(device.id).then(function(response) {
            device.hasCertificate = false;
            device.certificate = undefined;
            NotificationService.info('ADMINCONSOLE.VPN_HOME.NOTIFICATION.CERTIFICATE_REVOKED');
            vm.isRevokingCertificate = false;
            device.hasCertificate = false;
            startTimer();
        }, function(response) {
            vm.isRevokingCertificate = false;
            update();
            startTimer();
        });
    };
}
