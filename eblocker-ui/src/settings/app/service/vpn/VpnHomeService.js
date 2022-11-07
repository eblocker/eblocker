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
export default function VpnHomeService(logger, $http, $q, NotificationService, $interval) {
    'ngInject';

    const PATH = '/api/adminconsole/openvpn';
    const PATH_CONNECTION_TEST = PATH + '/test';
    const PATH_HOSTNAME_TEST = PATH + '/dns';
    const STATUS_UPDATE_TIMEOUT = 60000; // one minute in ms

    function startStopServer(status) {
        return $http.post(PATH + '/status', status, {timeout: STATUS_UPDATE_TIMEOUT}).
        then(standardSuccess, function(response) {
            NotificationService.error('ADMINCONSOLE.SERVICE.VPN_HOME.NOTIFICATION.SERVER_START', response);
            return $q.reject(response);
        });
    }

    function setStatus(status) {
        return $http.post(PATH + '/status', status, {timeout: STATUS_UPDATE_TIMEOUT}).
        then(standardSuccess, function(response) {
            NotificationService.error('ADMINCONSOLE.SERVICE.VPN_HOME.NOTIFICATION.SERVER_POST', response);
            return $q.reject(response);
        });
    }

    function resetServer() {
        return $http.delete(PATH + '/status').
        then(standardSuccess, function(response) {
            NotificationService.error('ADMINCONSOLE.SERVICE.VPN_HOME.NOTIFICATION.SERVER_RESET', response);
            return $q.reject(response);
        });
    }

    function loadStatus() {
        return $http.get(PATH + '/status').
        then(standardSuccess, function(response) {
            NotificationService.error('ADMINCONSOLE.SERVICE.VPN_HOME.NOTIFICATION.SERVER_GET', response);
            return $q.reject(response);
        });
    }

    function loadCertificates() {
        return $http.get(PATH + '/certificates').
        then(standardSuccess, function(response) {
            NotificationService.error('ADMINCONSOLE.SERVICE.VPN_HOME.NOTIFICATION.CERTIFICATES_GET', response);
            return $q.reject(response);
        });
    }

    function generateDownloadUrl(deviceId, operatingSystemType) {
        return $http.get(PATH + '/certificates/generateDownloadUrl/' + deviceId + '/' + operatingSystemType).
        then(standardSuccess, function(response) {
            NotificationService.error('ADMINCONSOLE.SERVICE.VPN_HOME.NOTIFICATION.CONFIG_DOWNLOAD', response);
            return $q.reject(response);
        });
    }

    function enableDevice(deviceId) {
        return $http.post(PATH + '/enable/' + deviceId).
        then(standardSuccess, function(response) {
            NotificationService.error('ADMINCONSOLE.SERVICE.VPN_HOME.NOTIFICATION.ENABLE_DEVICE', response);
            return $q.reject(response);
        });
    }

    function disableDevice(deviceId) {
        return $http.post(PATH + '/disable/' + deviceId).
        then(standardSuccess, function(response) {
            NotificationService.error('ADMINCONSOLE.SERVICE.VPN_HOME.NOTIFICATION.DISABLE_DEVICE', response);
            return $q.reject(response);
        });
    }

    function setPrivateNetworkAccess(deviceId, privateNetworkAccess) {
        return $http.put(PATH + '/privateNetworkAccess/' + deviceId, privateNetworkAccess)
            .then(standardSuccess, function(response) {
                NotificationService
                    .error('ADMINCONSOLE.SERVICE.VPN_HOME.NOTIFICATION.PRIVATE_NETWORK_ACCESS', response);
                return $q.reject(response);
            });
    }

    let connectionTestInterval,
        connectionTestIntervalRepeat = 20;
    const connectionTestIntervalSize = 1000;

    /**
     * connectionTestPromise is resolved after the connection test returns distinct result.
     */
    let connectionTestPromise, testCancelled;
    function doConnectionTest() {
        testCancelled = false;
        connectionTestIntervalRepeat = 20;
        connectionTestPromise = $q.defer();
        startConnectionTest(connectionTestPromise).then(function success() {
            // don't even start to poll if user has cancelled before this promise has been resolved
            if (testCancelled !== true) {
                startPollingForConnectionStatus();
            }
        }, function error(response) {
            connectionTestPromise.reject(response);
        });
        return connectionTestPromise.promise;
    }

    function cancelConnectionTest() {
        if (angular.isFunction(connectionTestPromise.reject)) {
            connectionTestPromise.reject({customReason: 'CANCELED'});
        }
        testCancelled = true;
        stopConnectionTest();
        stopPollingForConnectionStatus();
    }

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

    function startConnectionTest() {
        return $http.post(PATH_CONNECTION_TEST, {timeout: connectionTestPromise.promise});
    }

    function stopConnectionTest() {
        return $http.delete(PATH_CONNECTION_TEST);
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

    let hostTestPromise;
    function cancelHostNameTest() {
        if (angular.isFunction(hostTestPromise.reject)) {
            hostTestPromise.reject({customReason: 'CANCELED'});
        }
    }

    function doHostnameTest() {
        hostTestPromise = $q.defer();
        $http.post(PATH_HOSTNAME_TEST, {timeout: hostTestPromise.promise}).then(function(response) {
            logger.info('hostname result', response.data);
            if (response.data) {
                return hostTestPromise.resolve();
            } else {
                return hostTestPromise.reject(false);
            }
        }, function() {
            return hostTestPromise.reject(true);
        });
        return hostTestPromise.promise;
    }

    return {
        startStopServer: startStopServer,
        setStatus: setStatus,
        resetServer: resetServer,
        loadStatus: loadStatus,
        loadCertificates: loadCertificates,
        generateDownloadUrl: generateDownloadUrl,
        doConnectionTest: doConnectionTest,
        cancelConnectionTest: cancelConnectionTest,
        doHostnameTest: doHostnameTest,
        cancelHostNameTest: cancelHostNameTest,
        getConnectionTestResult: getConnectionTestResult,
        enableDevice: enableDevice,
        disableDevice: disableDevice,
        setPrivateNetworkAccess: setPrivateNetworkAccess
    };

    function standardSuccess(response) {
        return response;
    }

    function standardError(response) {
        return $q.reject(response);
    }
}
