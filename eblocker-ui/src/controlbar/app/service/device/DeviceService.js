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
export default function DeviceService($http, $q, logger, FILTER_TYPE) {
    'ngInject';

    let PATH = '/api/controlbar/device';
    let PATH_PAUSE = '/api/device/';
    let currentDeviceCache;

    function getDevice() {
        if (angular.isUndefined(currentDeviceCache)) {
            currentDeviceCache = $http.get(PATH).then(function success(response) {
                return response;
            });
        }
        return currentDeviceCache;
    }

    function getCurrentDevice() {
        return getDevice();
    }

    function getShowPauseDialogFlag() {
        // '/api/device/dialogStatus'
        return $http.get(PATH_PAUSE + 'dialogStatus').then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Unable to get pause dialog status flag ', response);
            return $q.reject(response);
        });
    }

    function getPauseDialogDoNotShowAgainFlag() {
        // '/api/device/dialogStatusDoNotShowAgain'
        return $http.get(PATH_PAUSE + 'dialogStatusDoNotShowAgain').then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Unable to get pause dialog do not show again flag ', response);
            return $q.reject(response);
        });
    }

    function getPauseStatus() {
        // '/api/device/pauseStatus'
        return $http.get(PATH_PAUSE + 'pauseStatus').then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Unable to get pause status ', response);
            return $q.reject(response);
        });
    }

    function updateShowPauseDialogFlag(param) {
        // '/api/device/dialogStatus'
        return $http.post(PATH_PAUSE + 'dialogStatus', {showPauseDialog: param}).then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Unable to set show pause dialog flag  ', response);
            return $q.reject(response);
        });
    }

    function updatePauseDialogDoNotShowAgainFlag(param) {
        // '/api/device/dialogStatusDoNotShowAgain'
        return $http.post(PATH_PAUSE + 'dialogStatusDoNotShowAgain', {doNotShowPauseDialogAgain: param})
            .then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Unable to set pause dialog do not show again flag  ', response);
            return $q.reject(response);
        });
    }

    function updatePlugAndPlayAdsEnabledStatus(id, bool) {
        return $http.put(PATH + '/ads/' + id, bool).then(function success(response) {
            return response;
        }, function error(response) {
            return response;
        });
    }

    function updatePlugAndPlayTrackersEnabledStatus(id, bool) {
        return $http.put(PATH + '/trackers/' + id, bool).then(function success(response) {
            return response;
        }, function error(response) {
            return response;
        });
    }

    function getFilterMode(mode, globalSslState, device) {
        // Not automatic mode and SSL disabled either globally or on device (plug and play has different view)
        if (mode === FILTER_TYPE.DNS && (!globalSslState || !device.sslEnabled)) {
            return mode + '_NO_SSL';
        } else if (mode !== FILTER_TYPE.AUTOMATIC && globalSslState && device.sslEnabled) {
            return mode;
        }
        return globalSslState && device.sslEnabled ? FILTER_TYPE.PATTERN : FILTER_TYPE.DNS;
    }

    return {
        getDevice: getDevice,
        getCurrentDevice: getCurrentDevice,
        getShowPauseDialogFlag: getShowPauseDialogFlag,
        getPauseDialogDoNotShowAgainFlag: getPauseDialogDoNotShowAgainFlag,
        getPauseStatus: getPauseStatus,
        updateShowPauseDialogFlag: updateShowPauseDialogFlag,
        updatePauseDialogDoNotShowAgainFlag: updatePauseDialogDoNotShowAgainFlag,
        updatePlugAndPlayAdsEnabledStatus: updatePlugAndPlayAdsEnabledStatus,
        updatePlugAndPlayTrackersEnabledStatus: updatePlugAndPlayTrackersEnabledStatus,
        getFilterMode: getFilterMode
    };
}
