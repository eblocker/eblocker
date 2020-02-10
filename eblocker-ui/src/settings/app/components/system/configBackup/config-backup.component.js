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
export default {
    templateUrl: 'app/components/system/configBackup/config-backup.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(logger, $window, ConfigBackupService, NotificationService, security) {
    'ngInject';
    'use strict';

    const vm = this;

    function configBackupDownloadUrl() {
        return ConfigBackupService.downloadUrl() + '?Authorization=Bearer+' + security.getToken();
    }

    vm.createConfigBackup = function() {
        NotificationService.info('ADMINCONSOLE.CONFIG_BACKUP.INFO.DOWNLOADED');
        $window.location = configBackupDownloadUrl();
    };

    vm.uploadConfigBackup = function(file, invalidFiles) {
        logger.debug('File: ' + file);
        logger.debug('Invalid files: ' + invalidFiles);
        if (file) {
            ConfigBackupService.importConfig(file).then(
                function success(response) {
                    NotificationService.info('ADMINCONSOLE.CONFIG_BACKUP.INFO.UPLOADED');
                },
                function error(response) {
                    NotificationService.error(response.toUpperCase());
                });
        } else {
            NotificationService.error('ADMINCONSOLE.CONFIG_BACKUP.ERROR.INVALID_FILE');
        }
    };
}
