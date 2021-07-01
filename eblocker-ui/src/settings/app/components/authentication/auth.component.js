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
    controller: AuthController,
    controllerAs: 'vm'
};

function AuthController(security, logger, StateService, STATES, Idle) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.loginRequired = false;
    vm.loginDone = false;

    // ** when resetting the admin password we need to reboot the eBlocker. After the reboot we continue with the
    // workflow state w/o authenticating (since user does not know password).
    if (security.isAuthenticated()) {
        // we have a security context from local storage or are otherwise
        // authenticated: continue the workflow; go to next state ...
        logger.info('Successfully authenticated.');
        continueWorkflow();
    } else {
        logger.info('Not authenticated. Loading token from server...');
        // we need to get a token from the server
        security.requestInitToken().then(function success(response) {
            const initSecurityContext = response.data;
            security.storeSecurityContext(initSecurityContext);
            // if password required, we need additional token..
            if (initSecurityContext.passwordRequired && StateService.getWorkflowState() !== STATES.RESET_PASSWORD) {
                logger.info('Password required. Going to login state...');
                // make login possible
                vm.loginRequired = true;
                StateService.goToState(STATES.LOGIN);
            } else {
                logger.info('No Password required.');
                // we're done: no password required ..
                initSecurityContext.isLoggedIn = true;
                // continue the workflow: go to next state ...
                continueWorkflow();
            }
        }, function error(response) {
            logger.error('AuthService failed to request initToken ',response);
            // if (response.status === 401) {
            //     security.logout();
            //     ..reload or go to AUTH (self)
            // }
        });
    }

    function continueWorkflow() {
        const nextState = StateService.getWorkflowState();
        let param = StateService.getWorkflowParam();

        if (angular.isDefined(nextState) && !StateService.isWorkflowStatePersistent()) {
            // Network wizard may loose param (current step in wizard) on reboot. If we clear the workflow state for the
            // network wizard when we return to auth, we then get redirected to login state, because the token is
            // lost on reboot. Then, after login, we end up here and do not have the workflow state anymore.
            StateService.clearWorkflow(); // prevent loop or unexpected return to this state.
        } else {
            // Next time, once login is complete, we can clear the workflow state.
            // also, this makes sure that we reset persistence-value and do not get stuck in loop
            StateService.isWorkflowStatePersistent(false);
        }

        let transTo = StateService.isStateValid(nextState) ? nextState : STATES.DEFAULT;
        // ** to open navbar on small devices
        param = StateService.isStateValid(nextState) ? param : {openNavbar: true};
        logger.info('Continuing workflow with state ' + transTo + '...');
        startIdle();
        StateService.goToState(transTo, param);
    }

    function startIdle() {
        // Start watching for idleness, if not yet doing so.
        // We do this, even when we do not need a password, to start the keepalive process.
        // Because the JWT must be renewed even when there is no password!
        if (!Idle.running()) {
            Idle.watch();
        }
    }
}
