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
    const PATH = '/api/ssl/status';

    const PATH_SSL = '/api/dashboard/ssl/whitelist';
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

    /**
     * FIXME: not used anymore?
     *
     * Here we try to connect via HTTPS on <port> to the eBlocker.
     * On success the browser knows eBlocker's cert and the ICAP server will
     * set the certificate to INSTALLED.
     * @param deviceId current device's ID
     * @param certSerial cert to be checked and which status will be updated
     * @param port 3001 for current certificate and 3002 for renewal cert
     * @param setFailState explicitly set the certificate to UNINSTALLED, if the browser cannot connect to eBlocker
     * This allows to reset the installed-status, if e.g. the user removed the cert from the store.
     * @returns {*}
     */
    function testSsl(deviceId, certSerial, port, setFailState) {
        const sslTestXhr = new XMLHttpRequest();

        const deferred = $q.defer();
        sslTestXhr.onreadystatechange = (function(sslTestXhr, def) {
            return function() { // returns the result from this IIFE call.
                if (sslTestXhr.readyState === 4) {
                    if (sslTestXhr.status === 200) {
                        def.resolve(sslTestXhr);
                    } else {
                        // sslTestXhr failed: means, cert is not installed.
                        if (setFailState) {
                            // we want to explicitly set the cert to NOT_INSTALLED and wait for the call to finish.
                            setSSLFailState(certSerial).then(angular.noop, angular.noop).finally(function() {
                                def.reject(sslTestXhr);
                            });
                        } else {
                            def.reject(sslTestXhr);
                        }
                    }
                }

            };
        })(sslTestXhr, deferred); // IIFE with params sslTestXhr and defer

        const host = $location.host();
        const sslTestUrl = 'https://' + host + ':' + port + '/' + encodeURIComponent(deviceId);

        sslTestXhr.open('GET', sslTestUrl, true);
        sslTestXhr.send();
        return deferred.promise;
    }

    function setSSLFailState(certSerial) {
        const errorUrl = '/ssl/test/' + certSerial;
        return $http.post(errorUrl).then(function success(response) {
            logger.info('Set certificate (serial: ' + certSerial + ') to state: not installed.');
            return response;
        }, function fail(response) {
            logger.warning('Unable to set certificate (serial: ' + certSerial + ') to uninstalled: ', response);
            return response;
        });
    }

    function whitelistDomain(domain) {
        return $http.post(PATH_SSL, domain).then(function(response) {
            return response.data;
        }, function(response) {
            logger.error('Whitelist domain failed with status ' + response.status + ' - ' + response.data);
            return $q.reject(response.data);
        });
    }


    return {
        start: startSyncTimer,
        stop: stopSyncTimer,
        getSslStatus: getSslStatus,
        setDeviceStatus: setDeviceStatus,
        testSsl: testSsl,
        whitelistDomain: whitelistDomain
    };
}
