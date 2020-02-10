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
export default function ConsoleService(logger, $http, $window, $location, $translate, $q, security) {
    'ngInject';

    let PATH = '/api/adminconsole/console/ip';
    let consoleUrl, dashboardUrl, dashboardPauseUrl, setupUrl;

    function init() {
        return $http.get(PATH, {headers: {Scheme: $window.location.protocol}}).then(function(response) {
            if (angular.isDefined(response) && angular.isString(response.data)) {
                consoleUrl = response.data;
                dashboardUrl = consoleUrl + '/dashboard/';
                setupUrl = consoleUrl + '/setup/';
                // use dashboard action state
                dashboardPauseUrl = consoleUrl + '/dashboard/#!/action/pause/' + security.getToken();
                return consoleUrl;
            }
        }, function(response) {
            logger.error('Error while getting server url ', response);
            // Quickfix: do not reject, or app will not load on init / booting
            // return $q.reject(response);
        });
    }

    function isInitialized() {
        return angular.isString(consoleUrl) &&
            angular.isString(dashboardUrl) &&
            angular.isString(setupUrl);
    }

    function goToConsoleAnon(openInCurrentTab) {
        if (openInCurrentTab) {
            $window.location.href = consoleUrl;
            $location.replace();
        } else {
            $window.open(consoleUrl + '/#/anonymization', 'eblocker.console.anon');
        }
    }

    function goToDashboard(openInCurrentTab, scrollToCard) {
        // use dashboard action state
        let goToUrl = angular.isString(scrollToCard) ? dashboardUrl + '#!/action/scroll/' + scrollToCard : dashboardUrl;
        if (openInCurrentTab) {
            $window.location.href = goToUrl;
            $location.replace();
        } else {
            $window.open(goToUrl, 'eblocker.dashboard');
        }
    }

    function goToSetup(openInCurrentTab) {
        if (openInCurrentTab) {
            $window.location.href = setupUrl;
            $location.replace();
        } else {
            $window.open(setupUrl, 'eblocker.setup');
        }
    }

    function goToDashboardMobileWizard(openInCurrentTab) {
        let goToUrl = dashboardUrl + '#!/wizard/eblockerMobile';
        if (openInCurrentTab) {
            $window.location.href = goToUrl;
            $location.replace();
        } else {
            $window.open(goToUrl, 'eblocker.dashboard');
        }
    }

    function goToStaticHelp(openInCurrentTab, scrollToCard) {
        let goToUrl = $translate.instant('ADMINCONSOLE.TOOLBAR.STATIC_HELP_LINK');
        $window.open(goToUrl, 'eblocker.staticHelp');
    }

    function showDashboardButton() {
        return angular.isDefined(dashboardUrl);
    }

    function goToPausedDashboard(openInCurrentTab) {
        if (openInCurrentTab) {
            $window.location.href = dashboardPauseUrl;
            $location.replace();
        } else {
            $window.open(dashboardPauseUrl, 'eblocker.dashboard.paused');
        }
    }

    function getDashboardUrl() {
        return dashboardUrl;
    }

    function getDashboardPausedUrl() {
        return dashboardPauseUrl;
    }

    let showNavbar;

    /**
     * When we go to the default state (e.g. devices) we want to open the navbar so that the user
     * has an orientation. We manage the information of whether to open the navbar or not with this service.
     * At least until the devices page (small table) is less confusing.
     * @param bool
     * @returns {*}
     */
    function initiallyShowNavBar(bool) {
        if (angular.isDefined(bool)) {
            showNavbar = bool;
        }
        return showNavbar;
    }

    let pageSpinnerStatus, globalSpinnerStatus;

    function isPageSpinner(bool) {
        if (angular.isDefined(bool)) {
            pageSpinnerStatus = bool;
        }
        return pageSpinnerStatus;
    }

    function isGlobalSpinner(bool) {
        if (angular.isDefined(bool)) {
            globalSpinnerStatus = bool;
        }
        return globalSpinnerStatus;
    }

    return {
        init: init,
        goToSetup: goToSetup,
        goToDashboard: goToDashboard,
        goToStaticHelp: goToStaticHelp,
        showDashboardButton: showDashboardButton,
        getDashboardUrl: getDashboardUrl,
        getDashboardPausedUrl: getDashboardPausedUrl,
        goToConsoleAnon: goToConsoleAnon,
        goToPausedDashboard: goToPausedDashboard,
        initiallyShowNavBar: initiallyShowNavBar,
        goToDashboardMobileWizard: goToDashboardMobileWizard,
        isInitialized: isInitialized,
        isPageSpinner: isPageSpinner,
        isGlobalSpinner: isGlobalSpinner
    };
}
