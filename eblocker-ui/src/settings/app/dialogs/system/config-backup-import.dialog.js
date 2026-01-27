/*
 * Copyright 2022 eBlocker Open Source UG (haftungsbeschraenkt)
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
export default function ConfigBackupImportController(logger, $scope, $mdDialog, // jshint ignore: line
                                                     ConfigBackupService, NotificationService,
                                                     SystemService, StateService, STATES) {
    'ngInject';

    const vm = this;
    vm.fileName = ''; //fileName;
    vm.fileReference = '';
    vm.passwordRequired = false;
    vm.includeKeys = false;
    vm.passwordRetry = false;
    vm.maxLength = 50;
    vm.currentStep = 0;
    vm.uploading = false;
    vm.verifying = false;
    vm.importing = false;

    vm.isStepAllowed = function(step) {
        // reboot? No turning back...
        if (vm.currentStep === 3) {
            return step === 3;
        }
        // only earlier steps are allowed
        return step <= vm.currentStep;
    };

    vm.updateIncludeKeys = function() {
        if (vm.includeKeys === false) {
            delete vm.password;
        }
    };

    vm.uploadConfigBackup = function(file, invalidFiles) {
        if (file) {
            vm.uploading = true;
            ConfigBackupService.uploadConfig(file).then(
                function success(data) {
                    vm.fileName = file.name;
                    vm.fileReference = data.fileReference;
                    vm.passwordRequired = data.passwordRequired;
                    // default: ask for password if keys are contained in the backup
                    vm.includeKeys = data.passwordRequired;
                    vm.currentStep = 1;
                },
                function error(response) {
                    NotificationService.error(response.toUpperCase());
                }).finally(function() {
                    vm.uploading = false;
                });
        } else {
            NotificationService.error('ADMINCONSOLE.CONFIG_BACKUP.ERROR.INVALID_FILE');
        }
    };

    vm.verifyConfigBackup = function() {
        if (!vm.configBackupImportForm.$valid) {
            return;
        }
        vm.passwordRetry = false;
        vm.verifying = true;
        ConfigBackupService.verifyConfig(vm.fileReference, vm.password).then(function(result) {
            vm.currentStep = 2;
        }, function(response) {
            const errCode = response.toUpperCase();
            if (errCode === 'ADMINCONSOLE.CONFIG_BACKUP.ERROR.INVALID_PASSWORD') {
                vm.passwordRetry = true;
            } else {
                NotificationService.error(errCode);
            }
        }).finally(function() {
            vm.verifying = false;
        });
    };

    vm.importConfigBackup = function() {
        vm.importing = true;
        ConfigBackupService.importConfig(vm.fileReference, vm.password).then(function(result) {
            vm.currentStep = 3;
        }, function(response) {
            NotificationService.error(response.toUpperCase());
        }).finally(function() {
            vm.importing = false;
        });
    };

    vm.reboot = function() {
        SystemService.setCurrentProcess('RESTART');
        SystemService.reboot().then(function(response) {
            StateService.goToState(STATES.STAND_BY);
        }, function error(reason) {
            logger.error('Could not reboot eBlocker', reason);
            NotificationService.error('ADMINCONSOLE.STATUS.ERROR.REBOOT', reason);
        });
        $mdDialog.hide();
    };

    vm.cancel = function() {
        $mdDialog.cancel();
    };
}
