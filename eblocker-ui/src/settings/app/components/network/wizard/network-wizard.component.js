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
    templateUrl: 'app/components/network/wizard/network-wizard.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(logger, StateService, STATES, $stateParams, NetworkService, SystemService, NotificationService, // jshint ignore: line
                    $window, $q, UrlService) {
    'ngInject';

    const vm = this;
    vm.cancel = cancel;
    vm.goBack = goBack;
    vm.nextStep = nextStep;
    vm.currentStep = 0;

    vm.$onDestroy = function() {
        vm.stop = true;
    };

    vm.$onInit = function() {
        if (angular.isObject($stateParams) &&
            angular.isObject($stateParams.param) &&
            angular.isObject($stateParams.param.configuration)) {
            vm.configuration = angular.copy($stateParams.param.configuration);
            vm.dhcpShouldBeOnRouter = !vm.configuration.dhcp;
            vm.intoAutoMode = vm.configuration.automatic && !vm.configuration.expertMode;
            vm.intoIndividualMode = !vm.configuration.automatic && !vm.configuration.expertMode;
            vm.eblockerDhcpIp = vm.configuration.ipAddress;

            if (angular.isDefined($stateParams.param.individualExecutionStep)) {
                setIndividualExecutionStep($stateParams.param.individualExecutionStep);
                vm.currentStep = $stateParams.param.currentStep;
            } else {
                setIndividualExecutionStep(0);
            }
        }
        if (!angular.isObject(vm.configuration) || vm.configuration.expertMode) {
            logger.error('Cannot open network wizard without configuration object.');
            goBack();
        }
    };

    function goBack() {
        // Reset workflow state; so that when next time state AUTH is activated, we do not end up here again.
        // We just need to get back after the reboot when changing mode to individual settings.
        StateService.setWorkflowState(STATES.NETWORK);
        StateService.goToState(STATES.NETWORK);
    }

    function doExecute() {
        vm.isExecuting = true;
        return NetworkService.setNetworkConfig(vm.configuration).then(function(response) {
            vm.rebootNecessary = response.data.rebootNecessary;
            vm.settingsApplied = true;
        },function(response) {
            NotificationService.error('ADMINCONSOLE.SERVICE.NETWORK.NOTIFICATION.ERROR.UPLOAD_CONFIG', response);
            return $q.reject(response);
        }).finally(function waitingDone() {
            vm.isExecuting = false;
        });
    }

    vm.reboot = function(origin) {
        return SystemService.reboot().then(function success() {
            StateService.goToState(STATES.STAND_BY, {origin: origin});
        }, function error(response) {
            NotificationService.error('ADMINCONSOLE.SERVICE.SYSTEM.NOTIFICATION.ERROR_REBOOT', response);
            return $q.reject(response);
        });
    };

    function nextStep() {
        vm.currentStep++;

        if (vm.intoAutoMode && vm.currentStep === 2) {
            isDhcpEnabled();
        }
    }

    function cancel() {
        goBack();
    }

    function getDhcpServers() {
        return NetworkService.getDhcpServers();
    }

    function hasDhcpBesidesEblocker(list, eblockerIp) {
        const noEb = list.filter(function(e) {
            return e !== eblockerIp;
        });
        return noEb.length > 0;
    }

    // ************** AUTOMATIC WIZARD **************
    vm.executeAutomaticMode = executeAutomaticMode;
    vm.onSelect = onSelect;
    vm.gotToStepTwo = gotToStepTwo;
    vm.recheckDhcpAuto = recheckDhcpAuto;
    vm.isWaitingForDhcpLease = true;
    vm.useCheckmarkToConfirmDhcpUp = false;
    vm.isDhcpEnabled = false;
    vm.fallBackUrl = $window.location.href;

    function executeAutomaticMode() {
        StateService.isWorkflowStatePersistent(true); // let workflow state pass through reboot (see auth.component.js)
        StateService.setWorkflowState(STATES.NETWORK);
        doExecute();
    }

    function gotToStepTwo() {
        vm.stepTwo = true;
    }

    function onSelect() {
        vm.stepTwo = false;
    }

    let numCheckForDhcpEnabled = 0;

    function recheckDhcpAuto() {
        vm.isWaitingForDhcpLease = true;
        vm.useCheckmarkToConfirmDhcpUp = false;
        numCheckForDhcpEnabled = 0;
        isDhcpEnabled();
    }

    function isDhcpEnabled() {

        if (numCheckForDhcpEnabled === 10) {
            logger.warn('Unable to get DHCP server status, tried ' + numCheckForDhcpEnabled + ' times.');
            setManuallyConfirmDhcpEnabled();
            return;
        }

        numCheckForDhcpEnabled++;

        getDhcpServers().then(function success(response) {
            if (hasDhcpBesidesEblocker(response.data, vm.eblockerDhcpIp)) {
                logger.debug('Found a DHCP server ', response.data);
                vm.isWaitingForDhcpLease = false;
                vm.isDhcpEnabled = true;
            } else if (!vm.stop) {
                logger.warn('No DHCP servers found; scanning again.');
                isDhcpEnabled();
            }
        }, function error(response) {
            logger.error('Unable to get DHCP server status', response);
            setManuallyConfirmDhcpEnabled();
        });
    }

    function setManuallyConfirmDhcpEnabled() {
        vm.isWaitingForDhcpLease = false;
        vm.useCheckmarkToConfirmDhcpUp = true;
    }

    // ############## AUTOMATIC END ##############


    // ************** INDIVIDUAL WIZARD **************
    vm.finish = finish;
    vm.openPrintView = openPrintView;
    vm.recheckDhcpIndividual = recheckDhcpIndividual;
    vm.isStillGettingDhcpLeases = true;
    vm.isDhcpDisabled = false;
    vm.useCheckmarkToConfirmDhcpDown = false;

    function finish() {
        goBack();
    }

    let numCheckForDhcpDisabled = 0;

    function recheckDhcpIndividual() {
        numCheckForDhcpDisabled = 0;
        vm.isStillGettingDhcpLeases = true;
        vm.useCheckmarkToConfirmDhcpDown = false;
        isDhcpDisabled();
    }

    function isDhcpDisabled() {

        if (numCheckForDhcpDisabled === 1) {
            logger.warn('Unable to get DHCP server status, tried ' + numCheckForDhcpDisabled + ' times.');
            setManuallyConfirmDhcpDisabled();
            return;
        }

        numCheckForDhcpDisabled++;

        getDhcpServers().then(function success(response) {
            if (!hasDhcpBesidesEblocker(response.data, vm.eblockerDhcpIp)) {
                logger.debug('Could not find DHCP services.');
                vm.isStillGettingDhcpLeases = false;
                vm.isDhcpDisabled = true;
            } else if (!vm.stop) {
                logger.warn('Still found DHCP servers in network. Scanning again.', response.data);
                isDhcpDisabled();
            }
        }, function error(response) {
            logger.error('Unable to get DHCP server status', response);
            setManuallyConfirmDhcpDisabled();
        });
    }

    function setManuallyConfirmDhcpDisabled() {
        vm.isDhcpDisabled = false;
        vm.isStillGettingDhcpLeases = false;
        vm.useCheckmarkToConfirmDhcpDown = true;
    }

    vm.nextIndividualExecutionStep = function() {
        setIndividualExecutionStep(vm.individualExecutionStep + 1);
    };

    function setIndividualExecutionStep(step) {
        vm.individualExecutionStep = step;
        if (vm.individualExecutionStep === 1) {
            isDhcpDisabled();
        }
    }

    vm.executeForIndividualSettings = function() {
        vm.configuration.dhcp = true;
        doExecute().then(function success() {
            if (!vm.rebootNecessary) {
                vm.nextIndividualExecutionStep();
            }
        });
    };

    vm.rebootForIndividualSettings = function() {
        const workflowParam = {
            configuration: vm.configuration,
            individualExecutionStep: vm.individualExecutionStep + 1,
            currentStep: vm.currentStep
        };
        StateService.isWorkflowStatePersistent(true); // let workflow state pass through reboot (see auth.component.js)
        StateService.setWorkflowState(STATES.NETWORK_WIZARD, workflowParam);
        vm.reboot(STATES.NETWORK_WIZARD);
    };

    function openPrintView() {
        const param = {
            configuration: vm.configuration,
            fallBackUrl: vm.fallBackUrl,
            heading: 'ADMINCONSOLE.NETWORK_WIZARD.INDIVIDUAL.TAB.SETTINGS.PRINT.PRINTVIEW_TITLE',
            templateUrl: 'app/components/network/wizard/print-settings.template.html'
        };
        const printUrl = UrlService.getPrintViewUrl(param);
        $window.open(printUrl, '_blank');
    }
}
