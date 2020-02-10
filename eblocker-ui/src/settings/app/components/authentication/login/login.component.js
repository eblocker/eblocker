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
    templateUrl: 'app/components/authentication/login/login.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(security, logger, StateService, STATES, BE_ERRORS, $timeout, ConsoleService) {
    'ngInject';
    'use strict';

    const vm = this;

    let loginTimeout;
    vm.secondsTillLogin = 0;

    vm.passwordForm = {

    };

    vm.maxLength = 50;

    vm.adminPassword = '';

    vm.submit = submit;
    vm.goToResetPassword = goToResetPassword;

    vm.$onInit = function() {
        if (!ConsoleService.isInitialized()) {
            ConsoleService.init();
        }
    };

    function submit() {

        const password = vm.adminPassword;

        security.login(password).then(function success(response) {
            const securityContext = response.data;
            securityContext.isLoggedIn = true;
            security.storeSecurityContext(securityContext);
            logger.info('Successfully logged in. Going back to auth state.');
            // We have saved the security context
            // now go back to auth; we should be be able to continue
            StateService.goToState(STATES.AUTH);
        }, function error(response) {

            const errorCode = response.data;

            if (errorCode === BE_ERRORS.CREDENTIALS_INVALID) {
                // ** password invalid:
                // Get timeout from server and start countdown, display error msg.
                vm.backendErrorKey = 'PASSWORD_INVALID';
                setAndStartCountdown();
            } else if (errorCode === 'error.credentials.too.soon') {
                vm.backendErrorKey = 'PASSWORD_TOO_FREQ';
            } else {
                vm.backendErrorKey = 'UNKNOWN';
            }

            vm.passwordForm.adminPassword.$setValidity('backend', false);
        });
    }

    function setAndStartCountdown() {
        security.isLoginAvailable().then(function success(response) {
            if (response.data > 0) {
                vm.secondsTillLogin = response.data;
                loginTimeout = $timeout(onTimeout, 1000);
            }
        });
    }

    function onTimeout() {
        vm.secondsTillLogin--;
        if (vm.secondsTillLogin > 0) {
            loginTimeout = $timeout(onTimeout, 1000);
        } else {
            if (angular.isDefined(loginTimeout)) {
                $timeout.cancel(loginTimeout);
            }
        }
    }

    function goToResetPassword() {
        StateService.goToState(STATES.RESET_PASSWORD);
    }

}
