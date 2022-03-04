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

    const PATH = '/api/configbackup/';
    const PATH_EXPORT = PATH + 'export';
    const PATH_DOWNLOAD = PATH + 'download';
    const PATH_UPLOAD = PATH + 'upload';
    const PATH_IMPORT = PATH + 'import';

    function exportConfig(passwordRequired, password) {
        return $http.post(PATH_EXPORT, {passwordRequired: passwordRequired, password: password}).then(
            function(response) {
                return response.data;
            }, function(reason) {
                logger.error('Could not export config: ' + reason);
                return $q.reject(reason);
            });
    }

    function downloadConfigUrl(fileReference) {
        return PATH_DOWNLOAD + '/' + fileReference;
    }

    function uploadConfig(file) {
        return $http.put(PATH_UPLOAD, file, {'headers': {'Content-type': 'application/octet-stream'}}).then(
            function success(response){
                return response.data;
            }, function error(response) {
                logger.error('Error uploading configuration backup ', response);
                return $q.reject(response.data);
            });
    }

    function importConfig(filename, password) {
        return $http.post(PATH_IMPORT, {fileReference: filename, password: password}).then(
            function success(response){
                return response;
            }, function error(response) {
                logger.error('Error importing configuration backup ', response);
                return $q.reject(response.data);
            });
    }

    return {
        exportConfig: exportConfig,
        downloadConfigUrl: downloadConfigUrl,
        uploadConfig: uploadConfig,
        importConfig: importConfig,
    };
}
