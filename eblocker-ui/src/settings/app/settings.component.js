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
    templateUrl: 'app/settings.component.html',
    controllerAs: 'vm',
    controller: Controller,
    bindings: {
        systemStatus: '<',
        locale: '<'
    }
};

function Controller($scope, $mdSidenav, STATES, StateService, SplashService, ConsoleService, $mdDialog, // jshint ignore: line
                     logger, security, Idle, Title) {
    'ngInject';
    'use strict';

    const vm = this;
    let idleDialog;

    vm.showSpinner = function() {
        return ConsoleService.isGlobalSpinner();
    };

    vm.showMenuIcon = showMenuIcon;
    vm.toggleSidenav = toggleSidenav;

    function showMenuIcon() {
        return security.isAuthenticated();
    }

    function toggleSidenav() {
        $mdSidenav(vm.navBarName).toggle();
    }

    function visibilityChanged() {
        // Fixes EB1-254: Browsers will pause javascript when tab is inactive. This caused Safari to display a
        // stopped counter within the title. Once the user activated the tab the dialog was open. But it was possible
        // that when the user clicked on "Continue" the user was directed to the login screen, because the timeout
        // had already expired. Here we mimic user interaction (Idle.interrupt()) and close the dialog per se. That
        // will either allow to continue working (not timed out) or re-direct to the time-out screen with button
        // "Continue working" which redirects to the login screen.
        Idle.interrupt();
        if (angular.isDefined(idleDialog)) {
            $mdDialog.cancel();
        }
    }

    /**
     * Just for testing, allows to reenable the welcome screen, so we can test and click on "do-not-show-again"
     */
    // vm.setSplash = function() {
    //     SplashService.set(true);
    // };

    vm.$onDestroy = function() {
        document.removeEventListener('visibilitychange', visibilityChanged);
    };

    vm.$onInit = function() {
        vm.isLoading = true;
        document.addEventListener('visibilitychange', visibilityChanged);
        vm.navBarName = 'left';

        logger.info('Setting language to \'' + vm.locale.language + '\'');

        handleSystemStatus(vm.systemStatus);

        vm.getContent = [
            {
                label: 'ADMINCONSOLE.TOOLBAR.CONTENT.HELP.LABEL',
                image: '/img/icons/ic_help.svg',
                closeOnClick: true,
                url: 'ADMINCONSOLE.TOOLBAR.CONTENT.HELP.URL'
            },
            {
                label: 'SHARED.LOGOUT.LABEL',
                image: '/img/icons/ic_person_black.svg',
                hide: hideLogoutInMenu,
                closeOnClick: true,
                action: logout
            }
        ];
    };

    $scope.$on('IdleStart', function () {
        logger.info('No user interaction for a while. Idle started...');
        if (security.isPasswordRequired()) {
            idleDialog = $mdDialog.show({
                controller: 'IdleDialogController',
                controllerAs: 'idle',
                templateUrl: 'app/common/idle.dialog.html',
                parent: angular.element(document.body),
                clickOutsideToClose: false
            }).then(function () {
                Idle.interrupt();
                idleDialog = undefined;
            }, function () {
                idleDialog = undefined;
            });
        }
    });

    $scope.$on('IdleWarn', function () {
        if (!security.isPasswordRequired()) {
            Title.setEnabled(false);
            Idle.interrupt();
        }
    });

    $scope.$on('IdleTimeout', function () {
        if (angular.isDefined(idleDialog)) {
            $mdDialog.cancel();
        }
        if (security.isPasswordRequired()) {
            logger.info('... idle timed out. Logging out...');
            security.logout();
            StateService.goToState(STATES.EXPIRED);
            // StateService.goToState(STATES.AUTH);
        } else {
            logger.info('... idle timed out. Restarting idle...');
            // ** No password needed, just start again
            // to keep Keepalive running.
            Idle.watch();
        }
    });

    $scope.$on('IdleEnd', function () {
        logger.info('Idle circle finished.');
        // Keepalive.start();
    });

    $scope.$on('Keepalive', function() {
        logger.info('Keepalive');
        security.renewToken().then(
            function success(response) {
                logger.info('Successfully renewed security context.');
                const secCon = response.data;
                secCon.isLoggedIn = true;
                security.storeSecurityContext(secCon);
            },
            function error() {
                // catch rejection
            }
        );
    });

    vm.getActiveState = getActiveState;
    vm.logout = logout;
    vm.showLogout = showLogout;
    vm.showDashboard = showDashboard;
    vm.showStaticHelp = showStaticHelp;
    vm.showDashboardButton = showDashboardButton;
    vm.isLoggedIn = isLoggedIn;

    function isLoggedIn() {
        return security.isAuthenticated();
    }

    function showDashboard() {
        ConsoleService.goToDashboard(false);
    }

    function showStaticHelp() {
        ConsoleService.goToStaticHelp();
    }

    function getActiveState() {
        return StateService.getActiveState() || {};
    }

    function logout() {
        StateService.goToState(STATES.LOGOUT);
    }

    function showLogout() {
        return security.isPasswordRequired() && security.isAuthenticated();
    }

    function showDashboardButton() {
        return ConsoleService.showDashboardButton();
    }

    function hideLogoutInMenu() {
        return !showLogout();
    }

    function handleSystemStatus(systemStatus) {
        logger.info('Overall execution states of eBlocker OS: ' + systemStatus.executionState);
        if (systemStatus.executionState !== 'OK' && systemStatus.executionState !== 'RUNNING') {
            StateService.goToState(STATES.STAND_BY);
        } else {
            StateService.goToState(STATES.AUTH);
        }
    }
}
