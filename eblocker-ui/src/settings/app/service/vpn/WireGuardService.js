/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License.
 */

export default function WireGuardService($http, $q) {
    'ngInject';
    'use strict';

    const PATH = '/api/adminconsole/wireguard';

    function getStatus() {
        return $http.get(PATH + '/status')
            .then(standardSuccess, standardError);
    }

    function enable() {
        return $http.post(PATH + '/enable')
            .then(standardSuccess, standardError);
    }

    function disable() {
        return $http.post(PATH + '/disable')
            .then(standardSuccess, standardError);
    }

    function getPeers() {
        return $http.get(PATH + '/peers')
            .then(standardSuccess, standardError);
    }

    function createPeerForDevice(deviceId) {
        return $http.post(
            PATH + '/devices/' + encodeURIComponent(deviceId) + '/peer'
        ).then(standardSuccess, standardError);
    }

    function deletePeer(peerId) {
        return $http.delete(
            PATH + '/peers/' + encodeURIComponent(peerId)
        ).then(standardSuccess, standardError);
    }

    function setLanAccess(peerId, allowLanAccess) {
        return $http.put(
            PATH + '/peers/' +
                encodeURIComponent(peerId) +
                '/lanAccess',
            allowLanAccess
        ).then(standardSuccess, standardError);
    }

    function getClientConfig(peerId) {
        return $http.get(
            PATH + '/peers/' + encodeURIComponent(peerId) + '/clientConfig'
        ).then(standardSuccess, standardError);
    }

    function getQrCode(peerId) {
        return $http.get(
            PATH + '/peers/' + encodeURIComponent(peerId) + '/qrcode',
            {responseType: 'arraybuffer'}
        ).then(standardSuccess, standardError);
    }

    function getEndpoint() {
        return $http.get(PATH + '/endpoint')
            .then(standardSuccess, standardError);
    }

    function setEndpoint(config) {
        return $http.put(PATH + '/endpoint', config)
            .then(standardSuccess, standardError);
    }

    function setRouting(peerId, config) {
        return $http.put(
            PATH + '/peers/' +
                encodeURIComponent(peerId) +
                '/routing',
            config
        ).then(standardSuccess, standardError);
    }

    function standardSuccess(response) {
        return response;
    }

    function standardError(response) {
        return $q.reject(response);
    }


    function getDeviceAuthorizations() {
        return $http.get(PATH + '/authorization/devices');
    }

    function getDeviceAuthorization(deviceId) {
        return $http.get(PATH + '/authorization/devices/' + deviceId);
    }

    function setDeviceAuthorization(deviceId, enabled) {
        return $http.put(PATH + '/authorization/devices/' + deviceId, enabled);
    }

    function setUserAuthorization(userId, enabled) {
        return $http.put(PATH + '/authorization/users/' + userId, enabled);
    }

    return {
        getDeviceAuthorizations: getDeviceAuthorizations,
        getDeviceAuthorization: getDeviceAuthorization,
        setDeviceAuthorization: setDeviceAuthorization,
        setUserAuthorization: setUserAuthorization,
        getStatus: getStatus,
        enable: enable,
        disable: disable,
        getPeers: getPeers,
        createPeerForDevice: createPeerForDevice,
        deletePeer: deletePeer,
        setLanAccess: setLanAccess,
        getClientConfig: getClientConfig,
        getQrCode: getQrCode,
        getEndpoint: getEndpoint,
        setEndpoint: setEndpoint,
        setRouting: setRouting
    };
}
