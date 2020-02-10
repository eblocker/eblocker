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
export default function VpnService(logger, $http, $q, $interval, DataCachingService) {
    'ngInject';

    let PATH = '/api/dashboard/vpn/profiles';

    let vpnProfileCache, vpnStatusByDeviceCache, syncTimer;

    function getVpnProfiles(reload) {
        vpnProfileCache = DataCachingService.loadCache(vpnProfileCache, PATH, reload).then(function success(response) {
            return response;
        }, function(response) {
            logger.error('Getting the VPN profile failed with status ' + response.status + ' - ' + response.data);
            return $q.reject(response);
        });
        return vpnProfileCache;
    }

    function getVpnStatusByDevice(device, reload) {
        vpnStatusByDeviceCache = DataCachingService.
        loadCache(vpnStatusByDeviceCache, PATH + '/status/' + device, reload).then(function success(response) {
            return response;
        }, function(response) {
            logger.error('Getting the VPN status by device failed with status ' +
                response.status + ' - ' + response.data);
            return $q.reject(response);
        });
        return vpnStatusByDeviceCache;
    }

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
        getVpnProfiles(true);
        getVpnStatusByDevice('me', true);
    }

    function getVpnStatus(profileId) {
        // /api/vpn/profiles/{id}/status
        return $http.get(PATH + '/' + profileId + '/status').then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Error loading VPN status ', response);
            return $q.reject(response);
        });
    }

    function setVpnDeviceStatus(profileId, device, activate) {
        // /api/vpn/profiles/{id}/status/{device}
        return $http.put(PATH + '/' + profileId + '/status/' + device, '' + activate).then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Error setting VPN device status ', response);
            return $q.reject(response);
        });
    }

    function setVpnThisDeviceStatus(profileId) {
        return $http.put(PATH + '/' + profileId + '/status-this').then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Error setting VPN status-this', response);
            return $q.reject(response);
        });
    }

    return {
        start: startSyncTimer,
        stop: stopSyncTimer,
        getVpnProfiles: getVpnProfiles,
        getVpnStatus: getVpnStatus,
        getVpnStatusByDevice: getVpnStatusByDevice,
        setVpnDeviceStatus: setVpnDeviceStatus,
        setVpnThisDeviceStatus: setVpnThisDeviceStatus
    };
}
