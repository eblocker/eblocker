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

    function getEndpoint() {
        return $http.get(PATH + '/endpoint')
            .then(standardSuccess, standardError);
    }

    function setEndpoint(config) {
        return $http.put(PATH + '/endpoint', config)
            .then(standardSuccess, standardError);
    }

    function standardSuccess(response) {
        return response;
    }

    function standardError(response) {
        return $q.reject(response);
    }

    return {
        getStatus: getStatus,
        enable: enable,
        disable: disable,
        getPeers: getPeers,
        getEndpoint: getEndpoint,
        setEndpoint: setEndpoint
    };
}
