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
export default function ConsoleService(logger, $http, $window, $location, security) {
    'ngInject';

    let PATH = '/api/advice/console/ip';
    let consoleUrl, dashboardUrl, dashboardPauseUrl;

    function init() {
        return $http.get(PATH, {
            headers: {
                'Scheme': $window.location.protocol
            }
        }).then(function(response) {
                if (angular.isDefined(response) && angular.isString(response.data)) {
                    consoleUrl = response.data;
                    dashboardUrl = consoleUrl + '/dashboard/';
                    // use dashboard action state
                    dashboardPauseUrl = consoleUrl + '/dashboard/#!/action/pause/' + security.getToken();
                    return consoleUrl;
                }
            }, function(response) {
                logger.error('Error while getting server url ', response);
            }
        );
    }

    function goToConsole(openInCurrentTab) {
        if (openInCurrentTab) {
            $window.location.href = consoleUrl;
            $location.replace();
        } else {
            $window.open(consoleUrl, 'eblocker.console');
        }
    }

    function goToConsoleAnon(openInCurrentTab) {
        if (openInCurrentTab) {
            $window.location.href = consoleUrl;
            $location.replace();
        } else {
            $window.open(consoleUrl + '/#!/anonymization', 'eblocker.console.anon');
        }
    }

    function goToDashboard(openInCurrentTab) {
        if (openInCurrentTab) {
            $window.location.href = dashboardUrl;
            $location.replace();
        } else {
            $window.open(dashboardUrl, 'eblocker.dashboard');
        }
    }

    function goToPausedDashboard(openInCurrentTab) {
        if (openInCurrentTab) {
            $window.location.href = dashboardPauseUrl;
            $location.replace();
        } else {
            $window.open(dashboardPauseUrl, 'eblocker.dashboard.paused');
        }
    }

    function close(icon) {
        let pos = 'close-eblocker-overlay';
        if (icon === 'right') {
            pos = 'close-eblocker-overlay-right';
        } else if (icon === 'left') {
            pos = 'close-eblocker-overlay-left';
        }
        let data = {
            'type': pos
        };
        window.parent.postMessage(data, '*');
    }

    function getDashboardUrl() {
        return dashboardUrl;
    }

    function getDashboardPausedUrl() {
        return dashboardPauseUrl;
    }

    return {
        init: init,
        goToConsole: goToConsole,
        goToDashboard: goToDashboard,
        getDashboardUrl: getDashboardUrl,
        getDashboardPausedUrl: getDashboardPausedUrl,
        goToConsoleAnon: goToConsoleAnon,
        goToPausedDashboard: goToPausedDashboard,
        close: close
    };
}
