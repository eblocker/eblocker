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
export default function VpnService($http, logger, $q) {
    'ngInject';

    let PATH = '/api/vpn/profiles';

    function getVpnProfiles() {
        return $http.get(PATH);
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

    function getVpnStatusByDevice(device) {
        // /api/vpn/profiles/status/{device}
        return $http.get(PATH + '/status/' + device).then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Error loading VPN device status ', response);
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

    return {
        'getVpnProfiles': getVpnProfiles,
        'getVpnStatus': getVpnStatus,
        'getVpnStatusByDevice': getVpnStatusByDevice,
        'setVpnDeviceStatus': setVpnDeviceStatus
    };
}
