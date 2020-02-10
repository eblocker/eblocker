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
    templateUrl: 'app/components/authentication/resetPassword/reset-password.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(logger, $interval, $stateParams, StateService, STATES, PasswordService, SystemService) {
    'ngInject';
    'use strict';

    const vm = this;

    let resetToken, shutdownGracePeriod, shutdownInterval, systemStatusInterval;

    vm.$onInit = function() {
        if (angular.isObject($stateParams) &&
            angular.isObject($stateParams.param) &&
            angular.isDefined($stateParams.param.resetToken)) {
            vm.step = 3;
            resetToken = $stateParams.param.resetToken;
            StateService.clearWorkflow(); // clear state, we only want to come back once after reboot
        } else {
            vm.step = 1;
        }
    };

    vm.$onDestroy = function() {
        stopCountdownInterval();
        stopPollingForStatus();
    };

    function stopCountdownInterval() {
        if (angular.isDefined(shutdownInterval)) {
            $interval.cancel(shutdownInterval);
        }
    }

    vm.cancel = function() {
        PasswordService.cancelReset(resetToken);
        close();
    };

    function close() {
        StateService.clearWorkflow(); // clear state, we only want to come back once after reboot
        StateService.goToState(STATES.AUTH);
    }

    vm.startReset = function() {
        vm.step = 2;
        getResetToken().then(function(response) {
            resetToken = response.resetToken;
            shutdownGracePeriod = response.shutdownGracePeriod;

            shutdownInterval = $interval(function() {
                shutdownGracePeriod -= 1; // always subtract 1 second...
                if (shutdownGracePeriod <= 0) {
                    // Cancel whole process
                    failure('ERROR_PASSWORD_RESET_SHUTDOWN_EXPIRED');
                }
                vm.shutdownTimer = formatTimer(shutdownGracePeriod);
            }, 1000);

            StateService.isWorkflowStatePersistent(true); // let workflow state pass through reboot
            StateService.setWorkflowState(STATES.RESET_PASSWORD, {resetToken: resetToken});

            startPollingForStatus();

        }, failure);
    };

    function getSystemStatus() {
        SystemService.getStatusPromise().then(function success(status) { // jshint ignore: line
            vm.exeState = status.executionState;
            if (vm.exeState !== 'RUNNING' && vm.exeState !== 'OK') {
                StateService.goToState(STATES.STAND_BY, {mode: 'PW_RESET'});
            }
        });
    }

    function startPollingForStatus() {
        SystemService.start(2000);
        systemStatusInterval = $interval(getSystemStatus, 1000);
    }

    function stopPollingForStatus() {
        SystemService.stop();
        if (angular.isDefined(systemStatusInterval)){
            $interval.cancel(systemStatusInterval);
            systemStatusInterval = undefined;
        }
    }

    vm.executeReset = function() {
        PasswordService.executeReset(resetToken).then(function() {
            close();
        }, failure);
    };

    function formatTimer(period) {
        const minutes = Math.floor(period / 60);
        const seconds = period % 60;
        return minutes + ':' + (seconds < 10 ? '0' : '' ) + seconds;
    }

    function getResetToken() {
        return PasswordService.initiateReset();
    }

    function failure(failureReason) {
        stopCountdownInterval();
        stopPollingForStatus();

        StateService.clearWorkflow(); // clear state, we only want to come back once after reboot

        // known errors, must be set in lang-files
        if (failureReason === 'ERROR_PASSWORD_RESET_SHUTDOWN_EXPIRED' ||
            failureReason === 'ERROR_PASSWORD_RESET_UNKNOWN_SERVER_STATE' ||
            failureReason === 'ERROR_PASSWORD_RESET_NOT_INITIATED' ||
            failureReason === 'ERROR_PASSWORD_RESET_INVALID_TOKEN' ||
            failureReason === 'ERROR_PASSWORD_RESET_TOKEN_EXPIRED' ||
            failureReason === 'ERROR_PASSWORD_RESET_UPDATE_RUNNING') {
            vm.failureReason = failureReason;
        } else {
            // something else, like:
            // 'Controller method cancelReset not yet available' or
            // 'Controller method generateConsoleToken not yet available'
            vm.unexpectedError = failureReason;
            vm.failureReason = 'UNEXPECTED';
        }
        PasswordService.cancelReset(resetToken); // ignore eventual errors from cancel request
    }
}
