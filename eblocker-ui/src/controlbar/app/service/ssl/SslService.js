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
export default function SslService(logger, $http, $q, $interval, $location, DataCachingService) {
    'ngInject';
    'use strict';
    const PATH = '/api/controlbar/ssl/status';
    let sslStatusCache, syncTimer;

    function startSyncTimer(interval) {
        if (!angular.isDefined(syncTimer) && angular.isNumber(interval)) {
            syncTimer = $interval(syncData, interval);
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

    function syncData() {
        getSslStatus(true);
    }

    function getSslStatus(reload) {
        sslStatusCache = DataCachingService.loadCache(sslStatusCache, PATH, reload).then(function success(response) {
            return response;
        }, function(response) {
            logger.error('Getting the SSL status failed with status ' + response.status + ' - ' + response.data);
            return $q.reject(response);
        });
        return sslStatusCache;
    }

    function setDeviceStatus(newStatus) {
        return $http.post('/api/ssl/device/status', newStatus).then(function(response) {
            return response.data;
        }, function(response) {
            logger.error('Setting the device status failed with status ' + response.status + ' - ' + response.data);
            return $q.reject(response.data);
        });
    }

    function testSsl(deviceId, certSerial, port) {
        const sslTestXhr = new XMLHttpRequest();

        // *** Here we try to connect via HTTPS on <port> to the eBlocker.
        // On success (the browser knows eBlocker's cert): the ICAP server will
        // set the certificate to INSTALLED.
        // On fail: the fail-function will be called (see onreadystatechange).

        const host = $location.host();
        const sslTestUrl = 'https://' + host + ':' + port + '/' + encodeURIComponent(deviceId);

        // ** fail-function
        sslTestXhr.onreadystatechange = function () {
            // *** SSL handshake failed: now we explicitly set the eBlocker's root
            // certificate to uninstalled.
            if (sslTestXhr.readyState === 4 && sslTestXhr.status !== 200) {
                const errorUrl = '/ssl/test/' + certSerial;
                $http.post(errorUrl).then(function success() {
                        logger.info('Certificate (serial: ' + certSerial + ') is not installed.');
                    },
                    function fail(response) {
                        logger.warning('Unable to set certificate (serial: ' + certSerial + ') to uninstalled: ',
                            response);
                    });
            }
        };

        sslTestXhr.open('GET', sslTestUrl, true);
        sslTestXhr.send();
    }

    return {
        getSslStatus: getSslStatus
    };
}
