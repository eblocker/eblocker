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
export default function VpnHomeService(logger, $http, $q, NotificationService, $interval, DataCachingService) {
    'ngInject';

    const PATH = '/api/dashboard/openvpn';
    const PATH_CONNECTION_TEST = PATH + '/test';

    function getOpenVpnFileName(deviceId, osType) {
        return $http.get(PATH + '/filename/' + deviceId + '/' + osType).then(standardSuccess, standardError);
    }

    function startStopServer(status) {
        return $http.post(PATH + '/status', status).
        then(standardSuccess, function(response) {
            // NotificationService.error('ADMINCONSOLE.SERVICE.VPN_HOME.NOTIFICATION.SERVER_START', response);
            return $q.reject(response);
        });
    }

    function setStatus(status) {
        return $http.post(PATH + '/status', status).
        then(standardSuccess, function(response) {
            // NotificationService.error('ADMINCONSOLE.SERVICE.VPN_HOME.NOTIFICATION.SERVER_POST', response);
            return $q.reject(response);
        });
    }

    function resetServer() {
        return $http.delete(PATH + '/status').
        then(standardSuccess, function(response) {
            // NotificationService.error('ADMINCONSOLE.SERVICE.VPN_HOME.NOTIFICATION.SERVER_RESET', response);
            return $q.reject(response);
        });
    }

    let statusCache;

    function loadStatus() {
        statusCache = DataCachingService.loadCache(statusCache, PATH + '/status');
        return statusCache;
    }

    function loadCertificates() {
        return $http.get(PATH + '/certificates').
        then(standardSuccess, function(response) {
            // NotificationService.error('ADMINCONSOLE.SERVICE.VPN_HOME.NOTIFICATION.CERTIFICATES_GET', response);
            return $q.reject(response);
        });
    }

    function revokeCertificate(deviceId) {
        return $http.delete(PATH + '/certificates/' + deviceId).
        then(standardSuccess, function(response) {
            // NotificationService.error('ADMINCONSOLE.SERVICE.VPN_HOME.NOTIFICATION.CERTIFICATES_DELETE', response);
            return $q.reject(response);
        });
    }

    function generateDownloadUrl(deviceId, operatingSystemType) {
        return $http.get(PATH + '/certificates/generateDownloadUrl/' + deviceId + '/' + operatingSystemType).
        then(standardSuccess, function(response) {
            // NotificationService.error('ADMINCONSOLE.SERVICE.VPN_HOME.NOTIFICATION.CONFIG_DOWNLOAD', response);
            return $q.reject(response);
        });
    }


    /**
     * connectionTestPromise is resolved after the connection test returns distinct result.
     */
    let connectionTestPromise;
    function doConnectionTest() {
        connectionTestPromise = $q.defer();
        startConnectionTest().then(function success() {
            startPollingForConnectionStatus();
        }, function error(response) {
            connectionTestPromise.reject(response);
        });
        return connectionTestPromise.promise;
    }

    let connectionTestInterval, connectionTestIntervalSize = 1000,
        connectionTestIntervalRepeat = 20;
    function startPollingForConnectionStatus() {
        connectionTestInterval = $interval(pollForConnectionStatusUpdate, connectionTestIntervalSize);
    }

    function stopPollingForConnectionStatus() {
        if (angular.isDefined(connectionTestInterval)) {
            $interval.cancel(connectionTestInterval);
        }
        connectionTestInterval = undefined;
    }

    let lastStatus;
    function pollForConnectionStatusUpdate() {
        connectionTestIntervalRepeat--;
        logger.debug('Polling for connection test status (' + connectionTestIntervalRepeat + ' sec).');
        if (connectionTestIntervalRepeat <= 0) {
            logger.debug('Stop polling for connection test status');
            stopPollingForConnectionStatus();
            connectionTestPromise.reject({data: lastStatus});
        }

        getConnectionTestStatus().then(function success(response) {
            lastStatus = response.data;
            // const status = processConnectionStatus(response.data);
            const status = response.data.state;
            if (status === 'SUCCESS') {
                stopPollingForConnectionStatus();
                connectionTestPromise.resolve(response);
            } else if (status === 'ERROR' || status === 'FAILURE') {
                stopPollingForConnectionStatus();
                connectionTestPromise.reject(response);
            } else {
                logger.debug('Status not yet decided: ' + status);
            }
        });
    }

    // function processConnectionStatus(status) { // jshint ignore: line
    //     if (status.state === 'SUCCESS') {
    //         // done, success!
    //         console.log('Connection test result success!');
    //     } else if (status.state === 'TIMEOUT_REQUESTS' || status.state === 'TIMEOUT_RESULTS') {
    //         // timeout, nothing else going to happen
    //         console.log('Connection test result timeout: ' + status.state);
    //     } else if (status.state === 'PENDING_REQUESTS' || status.state === 'PENDING_RESULTS') {
    //         // pending, still waiting
    //         console.log('Connection test result pending: ' + status.state);
    //     } else if (status.state === 'ERROR' || status.state === 'FAILURE') {
    //         // error, nothing else going to happen
    //         console.log('Connection test result error: ' + status.state);
    //         return 'ERROR';
    //     } else if (status.state === 'NOT_STARTED' || status.state === 'CANCELED') {
    //         // inconsistent, this should not happen. User cannot cancel and we have started the test.
    //         console.log('Connection test result BROKE: ' + status.state);
    //     }
    //     return status.state;
    // }


    function startConnectionTest() {
        return $http.post(PATH_CONNECTION_TEST);
    }

    function getConnectionTestStatus() {
        return $http.get(PATH_CONNECTION_TEST);
    }

    function getConnectionTestResult(response) {
        const status = response.data;
        let data;
        if (angular.isDefined(status.udpMessagesSent) && angular.isDefined(status.tcpMessagesSent)) {
            data = 'UDP sent: ' + status.udpMessagesSent + ', received: ' + status.udpMessagesReceived + ' / ' +
                'TCP sent: ' + status.tcpMessagesSent + ', received: ' + status.tcpMessagesReceived;
        } else {
            data = status;
        }

        return {
            status: response.status ? response.status : '-',
            msg: response.msg ? response.msg : '-',
            data: data
        };
    }

    return {
        startStopServer: startStopServer,
        setStatus: setStatus,
        resetServer: resetServer,
        loadStatus: loadStatus,
        loadCertificates: loadCertificates,
        revokeCertificate: revokeCertificate,
        generateDownloadUrl: generateDownloadUrl,
        doConnectionTest: doConnectionTest,
        getConnectionTestResult: getConnectionTestResult,
        getOpenVpnFileName: getOpenVpnFileName
    };

    function standardSuccess(response) {
        return response;
    }

    function standardError(response) {
        return $q.reject(response);
    }
}
