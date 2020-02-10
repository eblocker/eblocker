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
export default function SystemService(logger, $rootScope, $http, $q, $interval) {
    'ngInject';

    const PATH = '/api/adminconsole/systemstatus';

    let syncTimer, systemStatusPromise, process, currentServerState, serverIsRunning, hasBeenRebooted,
        hasBeenRebootedInterval, renewTokenWatcher;
    let isReloadAfterBoot = false;


    function reboot() {
        return $http.post(PATH + '/reboot', {timeout: 2000}).then(function success(response) {
            return response;
        },function(response) {
            return $q.reject(response);
        });
    }

    function shutdown() {
        return $http.post(PATH + '/shutdown', {timeout: 2000}).then(function success(response) {
            return response;
        },function(response) {
            return $q.reject(response);
        });
    }

    function rebootOnError() {
        return $http.post(PATH + '/reboot/onerror', {timeout: 2000}).then(function success(response) {
            return response;
        },function(response) {
            return $q.reject(response);
        });
    }

    function shutdownOnError() {
        return $http.post(PATH + '/shutdown/onerror', {timeout: 2000}).then(function success(response) {
            return response;
        },function(response) {
            return $q.reject(response);
        });
    }

    function startSyncTimer(interval) {
        if (!angular.isDefined(syncTimer) && angular.isNumber(interval)) {
            syncTimer = $interval(loadSystemStatus, interval);
        } else if (!angular.isNumber(interval)) {
            logger.warn('Cannot start synch timer with interval ', interval);
        }
    }

    function stopSyncTimer() {
        if (angular.isDefined(syncTimer)) {
            $interval.cancel(syncTimer);
            syncTimer = undefined;
        }
    }

    function isScanningForStatus() {
        return angular.isDefined(syncTimer);
    }

    function getStatusPromise() {
        return systemStatusPromise || $q.reject({});
    }

    let serverNotRunningCounter = 0;
    const SERVER_NOT_RUNNING_THRESHOLD = 2;

    function loadSystemStatus() {
        systemStatusPromise = $http.get(PATH, {timeout: 2000}).then(function success(response) {
             // response.data.executionState = 'DOWN';
            currentServerState = response.data.executionState;

             if (currentServerState === 'UPDATING' ||
                 currentServerState === 'BOOTING' ||
                 currentServerState === 'RUNNING') {
                 // Reset the current state, to make sure that it changes when system
                 // is actually getting into an stable state.
                 setCurrentProcess(currentServerState);
             }

             if (currentServerState === 'BOOTING') {
                 // ** After a reboot the token is invalid, so we need to reload the browser/token page. This is needed,
                 // because the booting screen in dashboard / setup implies that handling a server reboot is automated.
                 // So showing a booting screen and then the app does not work because the token is invalid is a
                 // bad user experience. So here, once we get the status 'booting' we can be certain (?) that there
                 // was a reboot and that we need to refresh the browser/token some point afterwards.
                 hasBeenRebooted = true;
             }


             // ** provide threshold so that dashboard does not go into overlay-mode right away. Wait until we are
             // sure that the server is down.
             if (currentServerState !== 'RUNNING') {
                 serverNotRunningCounter++;
             } else {
                 serverNotRunningCounter = 0;
             }

             serverIsRunning = currentServerState === 'RUNNING' ||
                 serverNotRunningCounter <= SERVER_NOT_RUNNING_THRESHOLD;

             return response.data;
        }, function error(response) {
            const status = response.status;

            serverNotRunningCounter++;
            serverIsRunning = serverNotRunningCounter <= SERVER_NOT_RUNNING_THRESHOLD;

            currentServerState = 'DOWN';

            if (status >= 400 && status <= 499) {
                // Other client error is unexpected error
                return $q.reject(response.data);
            } else {
                // Server error indicates eBlocker is shutting down or rebooting.
                return {executionState: 'DOWN'};
            }
        });
        return systemStatusPromise;
    }

    function isServerRunning() {
        return serverIsRunning;
    }

    function getCurrentStatus() {
        return currentServerState || 'DOWN';
    }

    /**
     * Right now only used to determine whether the system is expected to restart
     * once it's down. So we set the process to restart, when we are updating or clicking
     * on a restart button. Sometimes, when there are errors, the spa may only
     * receive ERROR status. So by merely checking the system status we cannot
     * know if we are expecting a reboot.
     */
    function setCurrentProcess(state) {
        process = state;
    }

    function isRestarting() {
        return process === 'RESTART';
    }

    /**
     * Used to reload the browser after update and reboot. Called in settings.controller.js.
     * We need to save the isReloadAfterBoot state so that it can be queried after the reboot is done (we cannot
     * know why we rebooted otherwise).
     * Templates may have changed, so after update we need to reload the browser, to reload the app.
     * @param bool
     */
    function reloadAfterBoot(bool) {
        if (angular.isDefined(bool)) {
            isReloadAfterBoot = bool;
        }
        return isReloadAfterBoot;
    }

    /**
     * Renew token after reboot.
     * @param getToken
     * @param contextName
     */
    function startTokenWatcher(getToken, contextName) {
        hasBeenRebootedInterval = $interval(function() {
            if (hasBeenRebooted && angular.isUndefined(renewTokenWatcher)) {
                renewTokenWatcher = $rootScope.$watch(function() {
                    return serverIsRunning;
                }, function(newValue, oldValue) {
                    if (newValue === true) {
                        // renew token
                        getToken(contextName);
                        renewTokenWatcher();
                        renewTokenWatcher = undefined;
                        hasBeenRebooted = false;
                    }
                });
            }
        }, 5000);
    }

    function stopTokenWatcher() {
        if (angular.isDefined(hasBeenRebootedInterval)) {
            $interval.cancel(hasBeenRebootedInterval);
        }
        if (angular.isDefined(renewTokenWatcher)) {
            renewTokenWatcher();
        }
    }

    return {
        reboot: reboot,
        shutdown: shutdown,
        rebootOnError: rebootOnError,
        shutdownOnError: shutdownOnError,
        start: startSyncTimer,
        stop: stopSyncTimer,
        loadSystemStatus: loadSystemStatus,
        isScanningForStatus: isScanningForStatus,
        getStatusPromise: getStatusPromise,
        setCurrentProcess: setCurrentProcess,
        getCurrentStatus: getCurrentStatus,
        isRestarting: isRestarting,
        reloadAfterBoot: reloadAfterBoot,
        isServerRunning: isServerRunning,
        startTokenWatcher: startTokenWatcher,
        stopTokenWatcher: stopTokenWatcher
    };
}
