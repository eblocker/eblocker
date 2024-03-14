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

    vm.reboot = reboot;
    vm.shutdown = shutdown;

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
