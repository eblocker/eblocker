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
    templateUrl: 'app/components/systemPending/factoryReset/factory-reset.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(logger, $interval, SystemService, StateService, STATES, $stateParams) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.isCheckingStatus = isCheckingStatus;

    let countdownInterval, inter;

    // ** Allow to escape from factoryreset state. This should not be necessary. But it may be useful, depending
    // on response from server.
    let loopCount = 0;
    const LOOP_THRESHOLD = 120;

    vm.$onInit = function() {
        if (angular.isObject($stateParams.param)) {
            vm.mode = $stateParams.param.mode;
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

            logger.debug('Currently in FACTORY-RESET-SCREEN component. System execution state is ' + vm.exeState + '.');
            loopCount++;
            // ** We'd expect the next state to be down, since the system will shutdown.
            // any other state should not happen.
            if (vm.exeState === 'DOWN' || vm.exeState === 'OFF' || loopCount >= LOOP_THRESHOLD) {
                goToState(STATES.STAND_BY);
            }

        }, function error(response) {
            logger.error('Unable to get system status : ', response);
        });
    }

    function goToState(name) {
        stopPollingForStatus();
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

    function isCheckingStatus() {
        return SystemService.isScanningForStatus();
    }
}
