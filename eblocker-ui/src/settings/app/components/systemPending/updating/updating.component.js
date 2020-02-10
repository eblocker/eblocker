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
    templateUrl: 'app/components/systemPending/updating/updating.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(logger, $interval, SystemService, StateService, UpdateService, STATES) {
    'ngInject';
    'use strict';

    const vm = this;
    vm.isCheckingStatus = isCheckingStatus;

    vm.$onInit = function() {
        startPollingForStatus();
    };

    vm.$onDestroy = function() {
        stopPollingForStatus();
    };

    let inter;

    let counter = 0;
    const TIMEOUT_STABLE_STATE = 2;

    function startPollingForStatus() {
        inter = $interval(function() {
            SystemService.getStatusPromise().then(function s(r) {
                vm.noUpdate = false;
                vm.systemStatus = r;
                vm.updatingStatus = UpdateService.getUpdatingStatus(vm.systemStatus);
                if (!angular.isObject(vm.updatingStatus) || !angular.isNumber(vm.updatingStatus.progress)) {
                    vm.noUpdate = true;
                }
                vm.exeState = r.executionState;
                logger.debug('Currently in UPDATE component. System execution state is ' + vm.exeState + '...');
                if (vm.exeState !== 'UPDATING') {
                    // ** wait a couple of seconds if new state is "stable"
                    counter++;
                    if (counter > TIMEOUT_STABLE_STATE) {
                        logger.debug('... leaving UPDATING state.');
                        // Done here, go back
                        SystemService.setCurrentProcess('RESTART');
                        SystemService.reloadAfterBoot(true);
                        StateService.goToState(STATES.STAND_BY);
                    }
                } else {
                    // ** reset counter
                    counter = 0;
                }
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
}
