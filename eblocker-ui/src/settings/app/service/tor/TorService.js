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
export default function TorService(logger, $http, $q) {
    'ngInject';

    const PATH = '/api/adminconsole/tor';
    const PATH_COUNTRY = PATH + '/countries';
    const PATH_SELECTED = PATH_COUNTRY + '/selected';

    function getAllTorCountries() {
        return $http.get(PATH_COUNTRY).then(standardSuccess, standardError);
    }

    function getSelectedTorExitNodes() {
        return $http.get(PATH_SELECTED).then(standardSuccess, standardError);
    }

    function updateSelectedTorExitNodes(nodes) {
        return $http.put(PATH_SELECTED, nodes).then(standardSuccess, standardError);
    }

    function getDeviceConfig(deviceId) {
        return $http.get(PATH + '/config/' + deviceId);
    }

    function setDeviceConfig(config, deviceId) {
        return $http.put(PATH + '/config/' + deviceId, config);
    }

    function getShowWarnings() {
        return $http.get(PATH + '/device/showwarnings').then(function success(response) {
            return response;
        }, function success(response) {
            logger.error('Unable to get show warnings for Tor activation dialog', response);
            return $q.reject(response);
        });
    }

    function setShowWarnings(bool) {
        return $http.post(PATH + '/device/showwarnings', {showWarnings: bool}).then(function success(response) {
            return response;
        }, function success(response) {
            logger.error('Unable to set show warnings for Tor activation dialog', response);
            return $q.reject(response);
        });
    }

    function getNewTorIdentity() {
        return $http.put(PATH + '/newidentity').then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Unable to get new Tor identity', response);
            return $q.reject(response);
        });
    }

    return {
        getAllTorCountries: getAllTorCountries,
        getSelectedTorExitNodes: getSelectedTorExitNodes,
        updateSelectedTorExitNodes: updateSelectedTorExitNodes,
        getDeviceConfig: getDeviceConfig,
        setDeviceConfig: setDeviceConfig,
        getShowWarnings: getShowWarnings,
        setShowWarnings: setShowWarnings,
        getNewTorIdentity: getNewTorIdentity
    };

    function standardSuccess(response) {
        return response;
    }

    function standardError(response) {
        return $q.reject(response);
    }
}
