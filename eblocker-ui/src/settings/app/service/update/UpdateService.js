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
export default function UpdateService($http, LanguageService) {
    'ngInject';

    const PATH = '/api/adminconsole/updates/';

    function getStatus() {
        return $http.get(PATH + 'status');
    }

    function setStatus(config) {
        return $http.post(PATH + 'status', config);
    }

    function getAutoUpdateInfo() {
        return $http.get(PATH + 'autoupdate');
    }

    function setAutoUpdateStatus(status) {
        return $http.post(PATH + 'automaticUpdatesStatus', {automaticUpdatesActivated: status});
    }

    function setAutoUpdateConfig(config) {
        return $http.post(PATH + 'automaticUpdatesConfig', config);
    }

    function checkForUpdates() {
        return $http.get(PATH + 'check');
    }

    function getUpdatingStatus(systemStatusDetails) {
        const updatingStatus = systemStatusDetails.updatingStatus;
        // Details for updating...
        const dateTimeFormat = 'ADMINCONSOLE.SERVICE.SYSTEM.DATE_TIME_FORMAT';
        if (angular.isDefined(updatingStatus)) {
            // eBlocker OS Version
            if (angular.isDefined(updatingStatus.projectVersion)) {
                updatingStatus.displayDeviceVersion = updatingStatus.projectVersion;
            }
            // eBlocker Filter Version
            if (angular.isDefined(updatingStatus.listsPacketVersion) &&
                updatingStatus.listsPacketVersion.length >= 14) {
                const v = updatingStatus.listsPacketVersion;
                updatingStatus.displayFilterVersion = v.substr(0, 4) + '-' + v.substr(4, 2) + '-' + v.substr(6, 2) +
                    '-' + v.substr(8, 2) + '-' + v.substr(10, 2) + '-' + v.substr(12, 2);
            }
            // Last Update
            if (angular.isDefined(updatingStatus.lastAutomaticUpdate) && updatingStatus.lastAutomaticUpdate !== '') {
                updatingStatus.displayLastUpdate = LanguageService.getDate(updatingStatus.lastAutomaticUpdate,
                    dateTimeFormat);
            } else {
                updatingStatus.displayLastUpdate = '-';
            }
            // Next Update
            if (angular.isDefined(updatingStatus.nextAutomaticUpdate) && updatingStatus.nextAutomaticUpdate !== '') {
                updatingStatus.displayNextUpdate = LanguageService.getDate(updatingStatus.nextAutomaticUpdate,
                    dateTimeFormat);
            } else {
                updatingStatus.displayNextUpdate = '-';
            }
            // If updating, calculate a progress
            updatingStatus.progress = 0;
            if (updatingStatus.updateablePackages.length > 0) {
                updatingStatus.progress = Math.floor(100 /
                    (updatingStatus.updateablePackages.length * 3 + 1) *
                    // 3 steps for each package: Preparing to unpack, unpack, setting up
                    // Additional step to prevent showing 100% while the last step actually just started
                    updatingStatus.updateProgress.length); // All steps made so far
            }
            if (updatingStatus.progress > 100) {
                // Should there have been more than 3 expected steps per packet, avoid progress exceeding 100%
                updatingStatus.progress = 100;
            }
        }
        return updatingStatus;
    }

    return {
        getStatus: getStatus,
        setStatus: setStatus,
        getAutoUpdateInfo: getAutoUpdateInfo,
        checkForUpdates: checkForUpdates,
        setAutoUpdateStatus: setAutoUpdateStatus,
        setAutoUpdateConfig: setAutoUpdateConfig,
        getUpdatingStatus: getUpdatingStatus
    };

}
