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

/**
 * Dashboard API boundary for a WireGuard peer associated with the
 * authorized device.
 *
 * The dashboard never supplies a peer id. All peer operations are
 * resolved server-side from the authorized device id.
 */
export default function WireGuardDashboardService($http, $q) {
    'ngInject';
    'use strict';

    const PATH = '/api/dashboard/wireguard';

    function devicePeerPath(deviceId) {
        return PATH + '/' + encodeURIComponent(deviceId) + '/peer';
    }

    function deviceStatusPath(deviceId) {
        return PATH + '/' + encodeURIComponent(deviceId) + '/status';
    }

    function getStatus(deviceId) {
        return $http.get(
            deviceStatusPath(deviceId)
        ).then(standardSuccess, standardError);
    }

    function getPeer(deviceId) {
        return $http.get(
            devicePeerPath(deviceId)
        ).then(standardSuccess, standardError);
    }

    function createPeer(deviceId) {
        return $http.post(
            devicePeerPath(deviceId),
            null
        ).then(standardSuccess, standardError);
    }

    function deletePeer(deviceId) {
        return $http.delete(
            devicePeerPath(deviceId)
        ).then(standardSuccess, standardError);
    }

    function setLanAccess(deviceId, allowLanAccess) {
        return $http.put(
            devicePeerPath(deviceId) + '/lanAccess',
            allowLanAccess
        ).then(standardSuccess, standardError);
    }

    function getClientConfig(deviceId) {
        return $http.get(
            devicePeerPath(deviceId) + '/clientConfig'
        ).then(standardSuccess, standardError);
    }

    function getQrCode(deviceId) {
        return $http.get(
            devicePeerPath(deviceId) + '/qrcode',
            {
                responseType: 'arraybuffer'
            }
        ).then(standardSuccess, standardError);
    }

    function standardSuccess(response) {
        return response;
    }

    function standardError(response) {
        return $q.reject(response);
    }

    return {
        getStatus: getStatus,
        getPeer: getPeer,
        createPeer: createPeer,
        deletePeer: deletePeer,
        setLanAccess: setLanAccess,
        getClientConfig: getClientConfig,
        getQrCode: getQrCode
    };
}
