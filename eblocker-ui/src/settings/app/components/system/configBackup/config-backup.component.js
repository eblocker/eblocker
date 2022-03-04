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

function Controller(logger, $window, ConfigBackupService, DialogService, NotificationService, security) {
    'ngInject';
    'use strict';

    const vm = this;
    vm.exporting = false;
    vm.includeKeys = true;

    function configBackupDownloadUrl(fileReference) {
        return ConfigBackupService.downloadConfigUrl(fileReference) + '?Authorization=Bearer+' + security.getToken();
    }

    vm.getAuthorization = function() {
        return 'Bearer ' + security.getToken();
    };

    vm.getDownloadURL = function() {
        return ConfigBackupService.downloadUrl();
    };

    vm.createConfigBackup = function() {
        var password;
        if (vm.includeKeys) {
            // Do passwords match?
            vm.passwordForm.repeatPassword.$setValidity('mustMatch', vm.newPassword === vm.repeatPassword);

            // Any other form error?
            if (!vm.passwordForm.$valid) {
                return;
            }
            password = vm.newPassword;
        }

        vm.exporting = true;
        ConfigBackupService.exportConfig(vm.includeKeys, password).then(function(data) {
            NotificationService.info('ADMINCONSOLE.CONFIG_BACKUP.INFO.DOWNLOADED');
            $window.location = configBackupDownloadUrl(data.fileReference);
        }, function(reason) {
            logger.error('Could not export backup configuration');
        }).finally(function() {
            vm.exporting = false;
        });
    };

    vm.uploadConfigBackup = function(file, invalidFiles) {
        if (file) {
            ConfigBackupService.uploadConfig(file).then(
                function success(data) {
                    DialogService.configBackupImport(file.name, data.passwordRequired).then(function(password) {
                        logger.warn('Got import result: ' + JSON.stringify(password));
                        ConfigBackupService.importConfig(data.fileReference, password).then(function(result) {
                            NotificationService.info('ADMINCONSOLE.CONFIG_BACKUP.INFO.UPLOADED');
                        }, function(response) {
                            logger.error('Could not import backup configuration');
                            NotificationService.error(response.toUpperCase());
                        });
                    });
                },
                function error(response) {
                    NotificationService.error(response.toUpperCase());
                });
        } else {
            NotificationService.error('ADMINCONSOLE.CONFIG_BACKUP.ERROR.INVALID_FILE');
        }
    };
}
