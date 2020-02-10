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
export default function VpnService($http, $q) {
    'ngInject';

    const PATH = '/api/adminconsole/vpn/';
    const PROFILE = PATH + 'profile';
    const PATH_BY_ID = PROFILE + '/status/';

    function createProfile(profile) {
        return $http.post(PROFILE, profile).then(standardSuccess, standardError);
    }

    function getProfile(profile) {
        return $http.get(PROFILE + '/' + profile.id).then(standardSuccess, standardError);
    }

    function getProfiles() {
        return $http.get(PROFILE + 's').then(standardSuccess, standardError);
    }

    function updateProfile(profile) {
        return $http.put(PROFILE + '/' + profile.id, profile).then(standardSuccess, standardError);
    }

    function deleteProfile(profile) {
        return $http.delete(PROFILE + '/' + profile.id).then(standardSuccess, standardError);
    }

    function getProfileConfig(profile) {
        return $http.get(PROFILE + '/' + profile.id + '/config').then(standardSuccess, standardError);
    }

    function uploadProfileConfig(profile, config) {
        return $http.put(PROFILE + '/' + profile.id + '/config', config).then(standardSuccess, standardError);
    }

    function uploadProfileConfigOption(profile, optionParam, optionContent) {
        return $http.put(PROFILE + '/' + profile.id + '/config/' + optionParam, optionContent).
        then(standardSuccess, standardError);
    }

    function setVpnStatus(profile, status) {
        return $http.put(PROFILE + '/' + profile.id + '/status', status).then(standardSuccess, standardError);
    }

    function getVpnStatus(profile) {
        return $http.get(PROFILE + '/' + profile.id + '/status').then(standardSuccess, standardError);
    }

    function getVpnDeviceStatus(profile, deviceId) {
        return $http.get(PROFILE + '/' + profile.id + '/status/' + deviceId).then(standardSuccess, standardError);
    }

    function getVpnStatusByDeviceId(deviceId) {
        return $http.get(PATH_BY_ID + deviceId).then(standardSuccess, standardError);
    }

    function setVpnDeviceStatus(profile, deviceId, status) {
        return $http.put(PROFILE + '/' + profile.id + '/status/' + deviceId, status).
        then(standardSuccess, standardError);
    }

    function updateCompletionStatus(dialog) {
        const tmpDialog = angular.copy(dialog);
        if (!tmpDialog.parsedOptions) {
            tmpDialog.configurationComplete = false;
        } else {
            const requiredFilesUploaded = angular.isUndefined(tmpDialog.parsedOptions.requiredFiles) ? true :
                !tmpDialog.parsedOptions.requiredFiles.find(function(e){
                return !e.uploaded || tmpDialog.requiredFileError[e.option];
            });
            const noValidationErrors = typeof tmpDialog.parsedOptions.validationErrors === 'undefined' ||
                tmpDialog.parsedOptions.validationErrors.length === 0;
            tmpDialog.configurationComplete = requiredFilesUploaded && noValidationErrors;
        }
        return tmpDialog;
    }

    return {
        getProfile: getProfile,
        getProfiles: getProfiles,
        createProfile: createProfile,
        updateProfile: updateProfile,
        deleteProfile: deleteProfile,
        getProfileConfig: getProfileConfig,
        uploadProfileConfig: uploadProfileConfig,
        uploadProfileConfigOption: uploadProfileConfigOption,
        setVpnStatus: setVpnStatus,
        getVpnStatus: getVpnStatus,
        getVpnDeviceStatus: getVpnDeviceStatus,
        setVpnDeviceStatus: setVpnDeviceStatus,
        updateCompletionStatus: updateCompletionStatus,
        getVpnStatusByDeviceId: getVpnStatusByDeviceId
    };

    function standardSuccess(response) {
        return response;
    }

    function standardError(response) {
        return $q.reject(response);
    }
}
