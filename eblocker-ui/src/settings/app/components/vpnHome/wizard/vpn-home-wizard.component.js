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
    templateUrl: 'app/components/vpnHome/wizard/vpn-home-wizard.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        vpnHomeStatus: '<'
    }
};

function Controller(logger, StateService, STATES, VpnHomeService, NotificationService, UpnpService, DialogService) { // jshint ignore: line
    'ngInject';

    const vm = this;
    vm.openSaveWizardDialog = openSaveWizardDialog;
    vm.openCloseWizardDialog = openCloseWizardDialog;
    vm.goBack = goBack;
    vm.nextStep = nextStep;
    vm.prevStep = prevStep;
    vm.currentStep = 1; // XXX save for later if user cancels / continues
    vm.maxSteps = 7;

    vm.minPort = 1;
    vm.maxPort = 65535;

    vm.ACCESS_OPTIONS = {
        fixedIp: 'FIXED_IP',
        dynDns: 'DYN_DNS',
        ebDynDns: 'EBLOCKER_DYN_DNS'
    };

    vm.PORT_MAPPING_OPTIONS = {
        auto: 'AUTO',
        manual: 'MANUAL'
    };

    vm.$onInit = function() {
        // initialize VPN status if not already defined in server
        vm.vpnHomeStatus.portForwardingMode = vm.vpnHomeStatus.portForwardingMode || vm.PORT_MAPPING_OPTIONS.auto;
        vm.vpnHomeStatus.externalAddressType = vm.vpnHomeStatus.externalAddressType || vm.ACCESS_OPTIONS.dynDns;
        vm.vpnHomeStatus.portForwardingMode = vm.vpnHomeStatus.portForwardingMode || vm.PORT_MAPPING_OPTIONS.auto;
        const port = vm.vpnHomeStatus.mappedPort;
        vm.vpnHomeStatus.mappedPort = angular.isNumber(port) ? port : 1194;

        vm.vpnHomeStatus.isRunning = false; // stop server, so that connection test will work.
        vm.portMapping = vm.vpnHomeStatus.mappedPort;
        vm.accessType = vm.vpnHomeStatus.externalAddressType;
        vm.portMappingType = vm.vpnHomeStatus.portForwardingMode;

        // save init to server (if required) and shutdown eBlocker mobile server
        setVpnStatus(vm.vpnHomeStatus);
    };

    function goBack() {
        return StateService.goToState(STATES.VPN_HOME);
    }

    function nextStep() {
        if (isNextStepAllowed()) {
            vm.currentStep++;
        }
    }

    function prevStep() {
        if (vm.currentStep > 1) {
            vm.currentStep--;
        }
    }

    function isNextStepAllowed() {
        const num = vm.currentStep + 1;
        return num <= vm.maxSteps;
    }

    function validateStatus(status) {
       const errors = [];

        // HOST ERROR
        if (!angular.isString(status.host) || status.host === '') {
            errors.push('ADMINCONSOLE.VPN_HOME_WIZARD.STEP_SET_IP.ERROR.HOST_REQUIRED');
        }

        // PORT ERROR
        if (!angular.isNumber(status.mappedPort)) {
            errors.push('ADMINCONSOLE.VPN_HOME_WIZARD.STEP_DO_PORT_MAPPING.ERROR.PORT_REQUIRED');
            errors.push('ADMINCONSOLE.VPN_HOME_WIZARD.STEP_DO_PORT_MAPPING.ERROR.PORT_NUMBER');
        } else if (status.mappedPort > vm.maxPort) {
            errors.push('ADMINCONSOLE.VPN_HOME_WIZARD.STEP_DO_PORT_MAPPING.ERROR.PORT_TOO_LARGE');
        } else if (status.mappedPort < vm.minPort) {
            errors.push('ADMINCONSOLE.VPN_HOME_WIZARD.STEP_DO_PORT_MAPPING.ERROR.PORT_TOO_SMALL');
        }
        return errors;
    }

    function openSaveWizardDialog(event) {
        DialogService.mobileWizardSaveConfirm(event, goBack, setVpnStatus, vm.vpnHomeStatus, validateStatus);
    }

    function openCloseWizardDialog(event) {
        DialogService.mobileWizardCloseConfirm(event, goBack, angular.noop);
    }

    function setVpnStatus(status) {
        return VpnHomeService.setStatus(status);
    }

    // ************** STEP ACCESS: step 2 **************
    vm.accessShowMore = false;
    vm.accessToggleShowMore = accessToggleShowMore;
    vm.accessTypeChange = accessTypeChange;

    function accessTypeChange() {
        vm.vpnHomeStatus.externalAddressType = vm.accessType;
        vm.vpnHomeStatus.host = '';
    }

    function accessToggleShowMore() {
        vm.accessShowMore = !vm.accessShowMore;
    }
    // ############## END STEP ACCESS ##############



    // ************** STEP SET IP / HOSTNAME **************
    vm.ebDynConnectionConfirmed = false;

    vm.changeHost = function() {
        if (!vm.isHostNameOrIpValid()) {
            return;
        }
        nextStep();
    };

    vm.isHostNameOrIpValid = function() {
        return vm.vpnHomeForm.$valid;
    };
    // ############## END STEP SET IP / HOSTNAME ##############



    // ************** STEP CHOOSE PORT MAPPING **************
    vm.choosePortMappingShowMore = false;
    vm.choosePortMappingToggleShowMore = choosePortMappingToggleShowMore;
    vm.portMappingTypeChange = portMappingTypeChange;

    function choosePortMappingToggleShowMore() {
        vm.choosePortMappingShowMore = !vm.choosePortMappingShowMore;
    }

    function portMappingTypeChange() {
        vm.vpnHomeStatus.portForwardingMode = vm.portMappingType;
    }
    // ############## END STEP CHOOSE PORT MAPPING ##############



    // ************** STEP DO PORT MAPPING: step 5 **************
    vm.saveVpnStatusAndContinue = saveVpnStatusAndContinue;
    vm.onChangePort = onChangePort;
    vm.manuallyMappedPortsConfirm = false;
    vm.portsAreMapped = false;
    vm.portsMappingError = false;
    vm.eblockerMobilePortConfig = {
        value: '1194'
    };

    vm.mapPortsNow = mapPortsNow;
    function mapPortsNow() {
        vm.isMappingPorts = true;
        vm.portsAreMapped = false;
        vm.portsMappingError = false;

        UpnpService.mapVpnPorts(vm.portMapping).then(function success(response) {
            NotificationService.
            info('ADMINCONSOLE.VPN_HOME_WIZARD.STEP_DO_PORT_MAPPING.NOTIFICATION.PORT_MAPPING_SUCCESS');
            vm.portsAreMapped = true;
            vm.portsMappingError = !vm.portsAreMapped;
            vm.isMappingPorts = false;
        }, function error(response) {
            logger.error('unable to set port forwarding', response);
            vm.portsMappingError = true;
            NotificationService.
            error('ADMINCONSOLE.VPN_HOME_WIZARD.STEP_DO_PORT_MAPPING.NOTIFICATION.PORT_MAPPING_ERROR', response);
            vm.isMappingPorts = false;// To stop the spinner eventually
        });
    }

    function saveVpnStatusAndContinue() {
        vm.isSavingConfig = true;
        setVpnStatus(vm.vpnHomeStatus).then(function() {
            nextStep();
        }).finally(function done() {
            vm.isSavingConfig = false;
        });
    }

    function onChangePort() {
        vm.manuallyMappedPortsConfirm = false;
        vm.portsAreMapped = false;
        vm.portsMappingError = false;
        vm.connectionOk = false;
        vm.connectionError = false;
        vm.vpnHomeStatus.mappedPort = vm.portMapping;
    }

    vm.isPortValid = function() {
        return vm.portMappingForm.$valid;
    };
    // ############## END STEP DO PORT MAPPING ##############


    // ************** STEP TEST CONNECTION **************
    vm.testConnectionShowMore = false;
    vm.isTestingConnection = false;
    vm.connectionOk = false;
    vm.connectionError = false;
    vm.isTestingHostname = false;
    vm.hostnameOk = false;
    vm.hostnameError = false;
    vm.testConnectionToggleShowMore = testConnectionToggleShowMore;
    vm.testConnection = testConnection;
    vm.cancelTestConnection = cancelTestConnection;
    vm.testHostname = testHostname;
    vm.resetConnectionTestVars = resetConnectionTestVars;

    function testConnectionToggleShowMore() {
        vm.testConnectionShowMore = !vm.testConnectionShowMore;
    }

    function resetConnectionTestVars() {
        vm.connectionOk = false;
        vm.connectionError = false;
    }

    function cancelTestConnection() {
        resetConnectionTestVars();
        vm.isTestingConnection = false;
        VpnHomeService.cancelConnectionTest();
    }

    function testConnection() {
        vm.isTestingConnection = true;
        VpnHomeService.doConnectionTest().then(function success(response) {
            NotificationService.info('ADMINCONSOLE.VPN_HOME_WIZARD.STEP_CONNECTION_TEST.NOTIFICATION.TEST_SUCCESS');
            vm.connectionOk = true;
            vm.connectionError = false;
        }, function error(response) {
            if (response.customReason !== 'CANCELED') {
                const errorDetails = VpnHomeService.getConnectionTestResult(response);
                NotificationService.error('ADMINCONSOLE.VPN_HOME_WIZARD.STEP_CONNECTION_TEST.NOTIFICATION.TEST_ERROR',
                    errorDetails);
                vm.connectionOk = false;
                vm.connectionError = true;
            }
        }).finally(function done() {
            vm.isTestingConnection = false;
        });
    }

    function cancelTestHostname() {
        vm.isTestingHostname = false;
        vm.hostnameOk = false;
        vm.hostnameError = false;
        VpnHomeService.cancelHostNameTest();
    }

    function testHostname() {
        vm.isTestingHostname = true;
        VpnHomeService.doHostnameTest().then(function success() {
            NotificationService.info(
                'ADMINCONSOLE.VPN_HOME_WIZARD.STEP_CONNECTION_TEST.NOTIFICATION.TEST_HOSTNAME_SUCCESS'
            );
            vm.hostnameOk = true;
            vm.hostnameError = false;
        }, function error(technicalReason) {
            if (technicalReason.customReason !== 'CANCELED') {
                if (technicalReason) {
                    NotificationService.error(
                        'ADMINCONSOLE.VPN_HOME_WIZARD.STEP_CONNECTION_TEST.NOTIFICATION.TEST_HOSTNAME_ERROR'
                    );
                } else {
                    NotificationService.error(
                        'ADMINCONSOLE.VPN_HOME_WIZARD.STEP_CONNECTION_TEST.NOTIFICATION.TEST_HOSTNAME_FAILURE'
                    );
                }
                vm.hostnameOk = false;
                vm.hostnameError = true;
            }
        }).finally(function done() {
            vm.isTestingHostname = false;
        });
    }
    // ############## END STEP TEST CONNECTION ##############




    // ************** STEP FINISH **************
    vm.isLaunchError = false; // XXX save for later if user cancels / continues
    vm.isLaunchSuccess = false; // XXX save for later if user cancels / continues
    vm.isLaunching = false;
    vm.launchVpnServer = launchVpnServer;

    function launchVpnServer() {
        vm.nextStep();
        vm.isLaunching = true;
        // Start server.
        vm.vpnHomeStatus.isRunning = true;

        setVpnStatus(vm.vpnHomeStatus).then(function success() {
            vm.isLaunchSuccess = true;
        }, function error() {
            vm.isLaunchError = !vm.isLaunchSuccess;
        }).finally(function done() {
            vm.isLaunching = false;
        });
    }
    // ############## END STEP FINISH ##############

}
