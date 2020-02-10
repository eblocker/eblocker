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
export default function NetworkEditContentController(logger, $mdDialog, configuration, NotificationService,
                                                           NetworkService, SystemService, StateService, STATES,
                                                     dnsEnabled) {
    'ngInject';

    const vm = this;
    vm.onFormUpate = onFormUpate;
    vm.nextStep = nextStep;
    vm.prevStep = prevStep;

    vm.configuration = angular.copy(configuration);
    vm.dnsEnabled = dnsEnabled;
    vm.dhcpLeaseTimes = NetworkService.getDhcpLeaseTimes();

    vm.currentStep = 0;

    vm.dhcpServices = [
        {
            name: 'ADMINCONSOLE.NETWORK_SETTINGS.DHCP.EBLOCKER',
            value: true
        },
        {
            name: 'ADMINCONSOLE.NETWORK_SETTINGS.DHCP.EXTERNAL',
            value: false
        }
    ];

    function prevStep() {
        vm.currentStep = 0;
    }

    function nextStep() {
        vm.currentStep++;
    }

    vm.cancel = function() {
        $mdDialog.cancel();
    };

    vm.save = function() {
        if (!vm.networkSettingsForm.$valid) {
            logger.debug('Form is not valid ... ' + vm.networkSettingsForm.$valid);
            return;
        }
        vm.formSubmitting = true;
        NetworkService.setNetworkConfig(vm.configuration).then(function(response){
            vm.configuration  = response.data; //.rebootNecessary;
            if (vm.configuration.rebootNecessary) {
                vm.currentStep = 1;
            } else {
                $mdDialog.hide(vm.configuration);
            }
        },function(response){
            vm.errors = NetworkService.processBackendErrors(response.data.split(', '));
            setFormInvalid(vm.errors);
            vm.currentStep = 0;
            NotificationService.error('ADMINCONSOLE.SERVICE.NETWORK.NOTIFICATION.ERROR.UPLOAD_CONFIG', response);
        }).finally(function done() {
            vm.formSubmitting = false;
        });
    };

    vm.rebootAndClose = function() {
        SystemService.reboot().then(function success(response) {
            StateService.setWorkflowState(STATES.NETWORK);
            StateService.goToState(STATES.STAND_BY).then(function success() {
                $mdDialog.hide(vm.configuration);
            });
        }, function error(response) {
            NotificationService.error('ADMINCONSOLE.SERVICE.SYSTEM.NOTIFICATION.ERROR_REBOOT', response);
        });
    };

    function onFormUpate() {
        setFormValid();
    }

    function setFormValid() {
        if (angular.isObject(vm.networkSettingsForm.ip)) {
            vm.networkSettingsForm.ip.$setValidity('backendError', true);
        }
        if (angular.isObject(vm.networkSettingsForm.gateway)) {
            vm.networkSettingsForm.gateway.$setValidity('backendError', true);
        }
        if (angular.isObject(vm.networkSettingsForm.netmask)) {
            vm.networkSettingsForm.netmask.$setValidity('backendError', true);
        }
        if (angular.isObject(vm.networkSettingsForm.firstIp)) {
            vm.networkSettingsForm.firstIp.$setValidity('backendError', true);
        }
        if (angular.isObject(vm.networkSettingsForm.lastIp)) {
            vm.networkSettingsForm.lastIp.$setValidity('backendError', true);
        }
    }

    function setFormInvalid(errors) {
        if (angular.isDefined(errors.ipAddress) && angular.isObject(vm.networkSettingsForm.ip)) {
            vm.networkSettingsForm.ip.$setValidity('backendError', false);
        }
        if (angular.isDefined(errors.gateway) && angular.isObject(vm.networkSettingsForm.gateway)) {
            vm.networkSettingsForm.gateway.$setValidity('backendError', false);
        }
        if (angular.isDefined(errors.firstDHCP) && angular.isObject(vm.networkSettingsForm.firstIp)) {
            vm.networkSettingsForm.firstIp.$setValidity('backendError', false);
        }
        if (angular.isDefined(errors.lastDHCP) && angular.isObject(vm.networkSettingsForm.lastIp)) {
            vm.networkSettingsForm.lastIp.$setValidity('backendError', false);
        }
        if (angular.isDefined(errors.networkMask) && angular.isObject(vm.networkSettingsForm.netmask)) {
            vm.networkSettingsForm.netmask.$setValidity('backendError', false);
        }
    }
}
