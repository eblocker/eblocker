/*
 * Copyright 2026 eBlocker Open Source GmbH
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
export default function RestoreFromBackupService(logger, $http, $q) {
    'ngInject';

    const BACKUP_TIMEOUT = 60000; // importing/exporting backups could take longer than 10s.

    const PATH = '/api/adminconsole/restore/';
    const PATH_AVAILABLE = PATH + 'isBackupAvailable';
    const PATH_CANCEL = PATH + 'cancel';
    const PATH_IMPORT = PATH + 'import';
    const PATH_VERIFY = PATH + 'verify';

    function backupAvailable() {
        return $http.get(PATH_AVAILABLE).then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        });
    }

    function cancelImport() {
        return $http.post(PATH_CANCEL, {}).then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        });
    }

    function verifyConfig(password) {
        const data = {password: password};
        const config = {timeout: BACKUP_TIMEOUT};
        return $http.post(PATH_VERIFY, data, config).then(
            function success(response){
                return response.data;
            }, function error(response) {
                logger.error('Error verifying configuration backup', response);
                return $q.reject(response.data);
            });
    }

    function importConfig(password) {
        const data = {password: password};
        const config = {timeout: BACKUP_TIMEOUT};
        return $http.post(PATH_IMPORT, data, config).then(
            function success(response){
                return response.data;
            }, function error(response) {
                logger.error('Error importing configuration backup', response);
                return $q.reject(response.data);
            });
    }

    return {
        backupAvailable: backupAvailable,
        cancelImport: cancelImport,
        importConfig: importConfig,
        verifyConfig: verifyConfig
    };
}
