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
export default function AnalysisToolService(logger, $http, $interval, NotificationService) {
    'ngInject';

    const PATH = '/api/adminconsole/recorder';
    const PATH_RESULTS = PATH + '/results';
    const PATH_WATH_IF = PATH + '/whatifmode';

    function start(transactionRecorderInfo) {
        logger.debug('Start recording');
        return $http.post(PATH, transactionRecorderInfo).then(function success() {
            return true; // hopefully started
        }, function error(response) {
            NotificationService.error('ADMINCONSOLE.SERVICE.ANALYSIS_TOOL.NOTIFICATION.ERROR_SERVICE_RECORDER_START',
                response);
            return false; // probably not started
        });
    }

    function stop(){
        logger.debug('Stop recording');
        return $http.delete(PATH).then(function success() {
            return true; // hopefully stopped
        }, function error(response) {
            NotificationService.error('ADMINCONSOLE.SERVICE.ANALYSIS_TOOL.NOTIFICATION.ERROR_SERVICE_RECORDER_STOP',
                response);
            return false; // probably not stopped
        });
    }

    let statusChecker;
    let statusCheckActive = false;

    function checkStatus(callback) {
        if (statusCheckActive) {
            // no need to do this in parallel
            return;
        }
        statusCheckActive = true;
        $http.get(PATH).then(function success(response) {
            callback(response.data);
        }).finally(function() {
            statusCheckActive = false;
        });
    }

    function activateStatusCheck(callback) {
        if (angular.isDefined(statusChecker)) {
            return;
        }
        statusChecker = $interval(function() {
            checkStatus(callback);
        }, 2000);
    }

    function deactivateStatusCheck() {
        if (angular.isDefined(statusChecker)){
            $interval.cancel(statusChecker);
            statusChecker = undefined;
        }
    }

    function getAll() {
        logger.debug('Get recorded transactions');
        return $http.get(PATH_RESULTS).then(function success(response) {
            return response.data;
        }, function error(response) {
            NotificationService.error('ADMINCONSOLE.SERVICE.ANALYSIS_TOOL.NOTIFICATION.ERROR_SERVICE_RECORDER_GETALL',
                response);
            return {};
        });
    }

    function getWhatIfMode() {
        logger.debug('Get whatIfMode');
        return $http.get(PATH_WATH_IF).then(function success(response) {
            return response.data.mode;
        }, function error(response) {
            NotificationService.error('ADMINCONSOLE.SERVICE.ANALYSIS_TOOL.NOTIFICATION.' +
                'ERROR_SERVICE_RECORDER_GETWHATIFMODE', response);
            return false;
        });
    }

    function setWhatIfMode(whatIfMode) {
        logger.debug('Set whatIfMode to ' + whatIfMode);
        return $http.put(PATH_WATH_IF, { 'mode': whatIfMode }).then(function success() {
        }, function error(response) {
            NotificationService.error('ADMINCONSOLE.SERVICE.ANALYSIS_TOOL.NOTIFICATION.' +
                'ERROR_SERVICE_RECORDER_PUTWHATIFMODE', response);
            return false;
        });
    }

    return {
        start: start,
        stop: stop,
        activateStatusCheck: activateStatusCheck,
        deactivateStatusCheck: deactivateStatusCheck,
        getAll: getAll,
        getWhatIfMode: getWhatIfMode,
        setWhatIfMode: setWhatIfMode
    };
}
