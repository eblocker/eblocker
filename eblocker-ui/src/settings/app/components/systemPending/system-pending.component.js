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
    templateUrl: 'app/components/systemPending/system-pending.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(SystemService, DialogService, $window, $interval, $stateParams, StateService, STATES) {
    'ngInject';
    'use strict';

    const vm = this;
    vm.isCheckingStatus = isCheckingStatus;
    vm.isRunningWithoutWarnings = isRunningWithoutWarnings;
    vm.isRunningWithWarning = isRunningWithWarning;
    vm.isError = isError;

    vm.actionsDisabled = false;

    vm.$onInit = function() {
        vm.isNetworkWizard = false;
        if (angular.isObject($stateParams.param)) {
            vm.isNetworkWizard = $stateParams.param.origin === STATES.NETWORK_WIZARD;
        }

        startPollingForStatus();
        SystemService.start(2000);
    };

    vm.$onDestroy = function() {
        stopPollingForStatus();
        SystemService.stop();
    };

    let inter;

    function startPollingForStatus() {
        inter = $interval(function() {
            SystemService.getStatusPromise().then(function success(response) {
                vm.systemStatus = response;
                vm.exeState = response.executionState;
            });
        }, 1000);
    }

    function stopPollingForStatus() {
        if (angular.isDefined(inter)){
            $interval.cancel(inter);
            inter = undefined;
        }
    }

    function isCheckingStatus() {
        return SystemService.isScanningForStatus();
    }

    function isRunningWithoutWarnings() {
        return StateService.getCurrentState().name !== STATES.FACTORY_RESET_SCREEN &&
            vm.exeState === 'RUNNING' &&
            vm.systemStatus.warnings.length === 0;
    }

    function isRunningWithWarning() {
        return vm.exeState === 'RUNNING' && vm.systemStatus.warnings.length > 0;
    }

    function isError() {
        return vm.exeState === 'ERROR';
    }

    vm.reload = reload;
    vm.reboot = reboot;
    vm.shutdown = shutdown;

    function reload() {
        $window.location.reload();
    }
    function reboot(event) {
        confirmShutdownOrReboot(event, true);
    }

    function shutdown(event) {
        confirmShutdownOrReboot(event, false);
    }

    function confirmShutdownOrReboot(event, rebooting) {
        vm.actionsDisabled = false;
        const okAction = rebooting ? SystemService.rebootOnError :
            SystemService.shutdownOnError;
        const cancel = function() {};
        DialogService.shutdownOrReboot(event, rebooting, okAction, cancel).finally(function() {
            vm.actionsDisabled = false;
            SystemService.setCurrentProcess(rebooting ? 'RESTART' : 'SHUTDOWN');
            StateService.goToState(STATES.STAND_BY);
            SystemService.start(2000);
        });
    }
}
