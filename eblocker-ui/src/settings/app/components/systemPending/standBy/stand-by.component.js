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
    templateUrl: 'app/components/systemPending/standBy/stand-by.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(logger, $interval, $stateParams, SystemService, StateService, STATES, ConsoleService) {
    'ngInject';
    'use strict';

    const vm = this;

    let countdownInterval, inter;
    let counter = 0;
    const maxTimeToWaitForShutdown = 120;
    const maxTimeToWaitForReboot = 600;
    const countdownTime = 30;
    let stateIsRunningCounter = 0;

    vm.$onInit = function() {
        if (angular.isObject($stateParams.param)) {
            vm.mode = $stateParams.param.mode;
            vm.isPasswordReset = $stateParams.param.mode === 'PW_RESET';
            vm.origin = $stateParams.param.origin;
        }
        // refresh the promise
        SystemService.loadSystemStatus().then(function success() {
            // success
        }).finally(function done() {
            // start poller that uses the promise
            startPollingForStatus();
        });

    };

    vm.$onDestroy = function() {
        stopPollingForStatus();
        countdownInterval = undefined;
    };

    function getSystemStatus() {
        SystemService.getStatusPromise().then(function success(status) { // jshint ignore: line
            vm.systemStatus = status;
            vm.exeState = status.executionState;

            logger.debug('Currently in STAND-BY component. System execution state is ' + vm.exeState + '.');

            if (vm.exeState === 'RUNNING' || vm.exeState === 'OK') {
                // ** Letting system return 'RUNNING' four times is not necessarily required. 'Just to be save'
                // in case the server takes some time to update its internal state.
                stateIsRunningCounter++;
                if (stateIsRunningCounter > 3) {
                    logger.debug('State Running since ' + stateIsRunningCounter + ' checks. Going into settings ...');
                    // Done, go back to origin
                    if (vm.origin === 'DASHBOARD') {
                        ConsoleService.init().then(function success() {
                            ConsoleService.goToDashboard(true);
                        });
                    } else if (vm.origin === 'SETUP') {
                        ConsoleService.init().then(function success() {
                            ConsoleService.goToSetup(true);
                        });
                    } else {
                        goToState(STATES.AUTH);
                    }
                }
            } else if (vm.exeState === 'SHUTTING_DOWN' || vm.exeState === 'SHUTTING_DOWN_FOR_REBOOT') {
                SystemService.setCurrentProcess(vm.exeState === 'SHUTTING_DOWN_FOR_REBOOT' ?
                    'RESTART' : 'SHUTDOWN');
                goToState(STATES.SHUTDOWN);
            } else if (vm.exeState === 'BOOTING' || vm.exeState === 'RESTARTING' || vm.exeState === 'ERROR') {
                goToState(STATES.BOOTING);
            } else if (vm.exeState === 'UPDATING') {
                goToState(STATES.UPDATING);
            } else if (vm.exeState === 'DOWN' || vm.exeState === 'OFF') { // jshint ignore: line
                // Lets the app display 'wait for reboot' or 'save to turn off'
                // Relevant for SHUTTING_DOWN_FOR_REBOOT and UPDATING
                vm.isReboot = SystemService.isRestarting();
                counter++;
                logger.debug('Waiting for ' + (vm.isReboot ? 'reboot' : 'shutdown') +
                    '. System down for '+ counter + ' seconds.');

                if (vm.isReboot && counter >= maxTimeToWaitForReboot) {
                    vm.showRebootHint = true;
                    if (angular.isUndefined(countdownInterval)) {
                        startCountdown();
                    }
                } else if (!vm.isReboot && counter >= maxTimeToWaitForShutdown) {
                    SystemService.stop();
                    stopPollingForStatus();
                    vm.isReadyToTurnOff = true;
                }
            }
        }, function error(response) {
            logger.error('Unable to get system status : ', response);
        });
    }

    function startCountdown() {
        vm.countdown = countdownTime;
        countdownInterval = $interval(function() {
            vm.countdown = vm.countdown - 1;
            if (vm.countdown === 0) {
                stopCountdown();
                stopPollingForStatus();
                SystemService.stop();
            }
        }, 1000);
    }

    function stopCountdown() {
        if (angular.isDefined(countdownInterval)) {
            $interval.cancel(countdownInterval);
        }
    }

    function goToState(name) {
        stopPollingForStatus();
        // Stop countdown so it does not continue while in other state (e.g. booting)
        // --> else we might get stuck in booting state.
        stopCountdown();
        StateService.goToState(name);
    }

    function startPollingForStatus() {
       inter = $interval(getSystemStatus, 1000);
    }

    function stopPollingForStatus() {
        if (angular.isDefined(inter)){
            $interval.cancel(inter);
            inter = undefined;
        }
    }
}
