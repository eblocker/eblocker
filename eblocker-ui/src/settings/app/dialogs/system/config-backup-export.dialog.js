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
export default function ConfigBackupExportController(logger, $scope, $window, $mdDialog, // jshint ignore: line
                                                     ConfigBackupService, NotificationService) {
    'ngInject';

    const vm = this;
    vm.blobUrl = undefined;
    vm.downloadFilename = undefined;
    vm.currentStep = 0;
    vm.exporting = false;
    vm.includeKeys = true;
    vm.maxLength = 50;
    vm.downloadSaved = false;

    vm.isStepAllowed = function(step) {
        // only earlier steps are allowed
        return step <= vm.currentStep;
    };

    function removeBlobUrl() {
        if (angular.isDefined(vm.blobUrl)) {
            $window.URL.revokeObjectURL(vm.blobUrl);
            vm.blobUrl = undefined;
        }
    }

    function downloadBackup(fileReference) {
        vm.currentStep = 1;
        removeBlobUrl();
        vm.downloadSaved = false;
        ConfigBackupService.downloadConfig(fileReference).then(function(result) {
            let blob = new Blob([result.data], { type: 'application/octet-stream' });
            vm.blobUrl = $window.URL.createObjectURL(blob);
            vm.downloadFilename = angular.isDefined(result.filename) ? result.filename : 'eblocker-config.eblcfg';
        }, function (response) {
            NotificationService.error(response.toUpperCase());
        });
    }

    vm.saveDownload = function(src) {
        const elem = $window.document.getElementById('downloadBlobAnchor');
        if (angular.isObject(elem)) {
            elem.click();
        } else {
            NotificationService.error('ADMINCONSOLE.CONFIG_BACKUP.ERROR.DOWNLOAD_FAILURE');
        }
    };

    vm.setDownloadSaved = function(src) {
        vm.downloadSaved = true;
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
            downloadBackup(data.fileReference);
        }, function(response) {
            NotificationService.error(response.toUpperCase());
        }).finally(function() {
            vm.exporting = false;
        });
    };

    vm.closeDialog = function() {
        removeBlobUrl();
        $mdDialog.hide();
    };

    vm.cancel = function() {
        removeBlobUrl();
        $mdDialog.cancel();
    };

}
