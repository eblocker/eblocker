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
    templateUrl: 'app/main.component.html',
    controllerAs: 'vm',
    controller: MainController,
    bindings: {
        postRegistrationInformation: '<',
        registrationInfo: '<',
        configSections: '<'
    }
};

// jshint ignore: line
function MainController($window, $mdSidenav, logger, RegistrationService, SetupService, StateService, STATES, // jshint ignore: line
                        SystemService, SplashService, ConsoleService, security) {
    'ngInject';
    const vm = this;

    vm.showSpinner = function() {
        return ConsoleService.isPageSpinner();
    };

    // we need to update setup info after setup wizard or license reset, so binding is not sufficient, because
    // it is not reloaded when controller is reloaded due to state changes.
    function updateSetupInfo() {
        SetupService.getInfo(true).then(function success(response) {
            vm.setupRequired = response.data.setupRequired;
            if (vm.setupRequired && !SetupService.hasSetupBeenExecuted()) {
                vm.openSetupWizard();
                SetupService.hasSetupBeenExecuted(true);
            }
        });
    }

    vm.$onInit = function() {
        vm.navBarName = 'left';
        if(SystemService.reloadAfterBoot()) {
            SystemService.reloadAfterBoot(false);
            $window.location.reload();
        }

        updateSetupInfo();

        const registrationInfo = vm.registrationInfo;

        const isRegistered = angular.isObject(registrationInfo) &&
            angular.isDefined(registrationInfo.registrationState) &&
            registrationInfo.registrationState === 'OK' || registrationInfo.registrationState === 'OK_UNREGISTERED';

        // const isLicenseAboutToExpire = angular.isObject(registrationInfo) &&
        //     angular.isDefined(registrationInfo.licenseAboutToExpire) &&
        //     registrationInfo.licenseAboutToExpire;

        if (angular.isObject(vm.postRegistrationInformation)) {
            vm.postRegistrationInformationContent = vm.postRegistrationInformation.content;
            vm.showPostRegInfoReminder = true;
        } else {
            vm.showPostRegInfoReminder = false;
        }

        vm.showSplashScreen = SplashService.getValue();

        if (vm.showSplashScreen && !SplashService.hasBeenShown() && isRegistered) {
            StateService.goToState(STATES.SPLASH);
        }

        if (!ConsoleService.isInitialized()) {
            ConsoleService.init();
        }
    };

    vm.$postLink = function() {
        // automatically open side nav for small devices
        if ($window.innerWidth < 1280 && ConsoleService.initiallyShowNavBar()) {
            toggleSidenav();
        }
    };

    vm.goToState = goToState;
    vm.goToVpnReminder = goToVpnReminder;
    vm.goToSplashScreen = goToSplashScreen;
    vm.openSetupWizard = openSetupWizard;
    vm.isSideNavOpen = isSideNavOpen;
    vm.isLicensed = isLicensed;
    vm.hideNavEntry = hideNavEntry;
    vm.logout = logout;
    vm.showLogout = showLogout;
    vm.showStaticHelp = showStaticHelp;
    vm.showDashboard = showDashboard;
    vm.showDashboardButton = showDashboardButton;

    function showLogout() {
        return security.isPasswordRequired();
    }

    function logout() {
        StateService.goToState(STATES.LOGOUT);
    }

    function showDashboard() {
        ConsoleService.goToDashboard(false);
    }

    function showDashboardButton() {
        return ConsoleService.showDashboardButton();
    }

    function showStaticHelp() {
        ConsoleService.goToStaticHelp();
    }

    function isLicensed(feature) {
        return RegistrationService.hasProductKey(feature);
    }

    function hideNavEntry(state) {
        return !RegistrationService.isRegistered() && state.requiredLicense() !== 'WOL';
    }

    function toggleSidenav() {
        $mdSidenav(vm.navBarName).toggle();
    }

    function closeSidenav() {
        $mdSidenav(vm.navBarName).close();
    }

    function openSetupWizard() {
        StateService.goToState(STATES.ACTIVATION);
    }

    function goToSplashScreen() {
        StateService.goToState(STATES.SPLASH);
    }

    function goToVpnReminder() {
        // StateService.setWorkflowState(StateService.getCurrentState().name);
        const param = {
            isReminder: true,
            postRegistrationInformation: vm.postRegistrationInformationContent
        };
        StateService.goToState(STATES.ACTIVATION_FINISH, param);
    }

    /**
     * Make sure that the sidenav toolbar is only visisble
     * when it is not locked-open on medium or smaller screens.
     * @param name of the sidenav, here 'left'
     */
    function isSideNavOpen() {
        return $mdSidenav(vm.navBarName).isOpen() && !$mdSidenav(vm.navBarName).isLockedOpen();
    }

    function goToState(state) {
        closeSidenav();
        StateService.goToState(state.name);
    }
    // angular.element($window).bind('resize', closeSidenav);

}
