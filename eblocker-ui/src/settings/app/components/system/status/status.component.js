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
    templateUrl: 'app/components/system/status/status.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(DialogService, SystemService, StateService, NotificationService, logger, STATES) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.$onInit = $onInit;
    vm.reboot = reboot;
    vm.shutdown = shutdown;
    vm.formatTemperature = formatTemperature;
    vm.formatLoad = formatLoad;
    vm.formatMemory = formatMemory;

    function $onInit() {
        loadSystemParameters();
    }

    function loadSystemParameters() {
        return SystemService.loadSystemParameters().then(function success(response) {
            vm.systemParameters = response.data;
            return vm.systemParameters;
        }, function error(reason) {
            logger.warn('Could not load system parameters', reason);
            vm.systemParameters = null;
            return null;
        });
    }

    function formatTemperature(value) {
        return angular.isNumber(value) ? value.toFixed(1) + ' °C' : '-';
    }

    function formatLoad(parameters) {
        if (!angular.isObject(parameters) ||
            !angular.isNumber(parameters.loadAverage1Minute) ||
            !angular.isNumber(parameters.loadAverage5Minutes) ||
            !angular.isNumber(parameters.loadAverage15Minutes)) {
            return '-';
        }
        return parameters.loadAverage1Minute.toFixed(2) + ' / ' +
            parameters.loadAverage5Minutes.toFixed(2) + ' / ' +
            parameters.loadAverage15Minutes.toFixed(2);
    }

    function formatMemory(parameters) {
        if (!angular.isObject(parameters) ||
            !angular.isNumber(parameters.memoryAvailableBytes) ||
            !angular.isNumber(parameters.memoryTotalBytes)) {
            return '-';
        }
        return formatMegabytes(parameters.memoryAvailableBytes) + ' / ' +
            formatMegabytes(parameters.memoryTotalBytes);
    }

    function formatMegabytes(bytes) {
        return Math.round(bytes / 1024 / 1024) + ' MB';
    }

    function reboot(event) {
        confirmShutdownOrReboot(event, true);
    }

    function shutdown(event) {
        confirmShutdownOrReboot(event, false);
    }

    function confirmShutdownOrReboot(event, rebooting) {
        const okAction = function() { return performShutdownOrReboot(rebooting); };
        const cancel = function() {};
        SystemService.setCurrentProcess(rebooting ? 'RESTART' : 'SHUTDOWN');
        DialogService.shutdownOrReboot(event, rebooting, okAction, cancel).then(goToSystemPending);
    }

    function goToSystemPending() {
        StateService.goToState(STATES.STAND_BY);
    }

    function performShutdownOrReboot(rebooting) {
        const action = rebooting ? SystemService.reboot : SystemService.shutdown;
        return action().then(function(response) {
            return response;
        }, function error(reason) {
            logger.error('Could not shut down or reboot eBlocker', reason);
            NotificationService.error('ADMINCONSOLE.STATUS.ERROR.' +
                                      (rebooting ? 'REBOOT' : 'SHUTDOWN'), reason);
            return true;
            // the shutdown/reboot was probably denied because a system update
            // is in progress. We continue and go to the standby state.
        });
    }
}
