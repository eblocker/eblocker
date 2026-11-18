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
export default function ConfigBackupService(logger, $http, $q) {
    'ngInject';

    const BACKUP_TIMEOUT = 300000; // importing/exporting backups could take longer than 10s.

    const PATH = '/api/configbackup/';
    const PATH_EXPORT = PATH + 'export';
    const PATH_DOWNLOAD = PATH + 'download';
    const PATH_UPLOAD = PATH + 'upload';
    const PATH_IMPORT = PATH + 'import';
    const PATH_VERIFY = PATH + 'verify';
    const PATH_MOUNTED_PARTITION = PATH + 'mountedPartition';
    const PATH_PARTITION_STATUS  = PATH + 'partitionStatus';
    const PATH_WRITE_TO_DISK     = PATH + 'writeToDisk';
    const RE_CONTENT_DISPOSITION = /attachment; filename="(.*?)"/;

    function exportConfig(passwordRequired, password) {
        const data = {passwordRequired: passwordRequired, password: password};
        const config = {timeout: BACKUP_TIMEOUT};
        return $http.post(PATH_EXPORT, data, config).then(
            function(response) {
                return response.data;
            }, function(response) {
                return handleError('Error exporting configuration backup', response);
            });
    }

    function downloadConfigUrl(fileReference) {
        return PATH_DOWNLOAD + '/' + fileReference;
    }

    function extractFilename(contentDisposition) {
        if (angular.isUndefined(contentDisposition)) {
            return undefined;
        }
        let match = RE_CONTENT_DISPOSITION.exec(contentDisposition);
        if (match == null) {
            return undefined;
        }
        return match[1];
    }

    function downloadConfig(fileReference) {
        const config = {responseType: 'blob'};
        return $http.get(downloadConfigUrl(fileReference), config).then(
            function success(response) {
                if (response.headers('Content-Type') === 'application/octet-stream') {
                    return {
                        data: response.data,
                        filename: extractFilename(response.headers('Content-Disposition'))
                    };
                } else {
                    logger.error('Error downloading config backup. ' +
                                 'Expected content-type application/octet-stream, but got ' +
                                 response.headers('Content-Type'));
                    return $q.reject('bad content-type');
                }
            }, function error(response) {
                return handleError('Error downloading configuration backup', response);
            });
    }

    function uploadConfig(file) {
        const config = {'headers': {'Content-type': 'application/octet-stream'}};
        return $http.put(PATH_UPLOAD, file, config).then(
            function success(response){
                return response.data;
            }, function error(response) {
                if (response.status === 413) { // request entity too large?
                    response.data = 'ADMINCONSOLE.CONFIG_BACKUP.ERROR.UPLOAD_TOO_LARGE';
                }
                return handleError('Error uploading configuration backup', response);
            });
    }

    function verifyConfig(fileReference, password) {
        const data = {fileReference: fileReference, password: password};
        const config = {timeout: BACKUP_TIMEOUT};
        return $http.post(PATH_VERIFY, data, config).then(
            function success(response){
                return response.data;
            }, function error(response) {
                return handleError('Error verifying configuration backup', response);
            });
    }

    function importConfig(fileReference, password, clientFileName) {
        const data = {fileReference: fileReference, password: password, clientFileName: clientFileName};
        const config = {timeout: BACKUP_TIMEOUT};
        return $http.post(PATH_IMPORT, data, config).then(
            function success(response){
                return response.data;
            }, function error(response) {
                return handleError('Error importing configuration backup', response);
            });
    }

    function mountedPartition() {
        return $http.get(PATH_MOUNTED_PARTITION).then(
            function success(response){
                if (angular.isDefined(response) && angular.isObject(response.data)) {
                    return response.data;
                } else {
                    return null;
                }
            }, function error(response) {
                return handleError('Error getting mounted partition name', response);
            });
    }

    function partitionStatus(partition) {
        return $http.post(PATH_PARTITION_STATUS, partition).then(
            function success(response){
                return response.data;
            }, function error(response) {
                return handleError('Error getting partition status', response);
            });
    }

    function writeConfigToDisk(fileReference) {
        const data = {fileReference: fileReference};
        const config = {timeout: BACKUP_TIMEOUT};
        return $http.post(PATH_WRITE_TO_DISK, data, config).then(
            function success(response){
                return response.data;
            }, function error(response) {
                return handleError('Error writing configuration backup to disk', response);
            });
    }

    function handleError(msg, response) {
        logger.error(msg, response);
        if (angular.isString(response.data)) {
            return $q.reject(response.data.toUpperCase());
        } else {
            if (response.xhrStatus === 'timeout') {
                return $q.reject('ADMINCONSOLE.CONFIG_BACKUP.ERROR.TIMEOUT');
            } else {
                return $q.reject(msg);
            }
        }
    }

    return {
        exportConfig: exportConfig,
        downloadConfig: downloadConfig,
        uploadConfig: uploadConfig,
        importConfig: importConfig,
        verifyConfig: verifyConfig,
        mountedPartition: mountedPartition,
        partitionStatus: partitionStatus,
        writeConfigToDisk: writeConfigToDisk
    };
}
