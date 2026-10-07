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
export default function ConfigBackupExportToDriveController(logger, $scope, $mdDialog, $interval, // jshint ignore: line
                                                            ConfigBackupService, NotificationService) {
    'ngInject';

    const DriveState = {
        CANCELLED: 'CANCELLED',
        SEARCHING: 'SEARCHING',
        FOUND: 'FOUND',
        WRITING: 'WRITING',
        WROTE: 'WROTE',
        FAILED: 'FAILED'
    };
    const DRIVE_SEARCHING_INTERVAL = 3000;

    const vm = this;
    vm.driveState = DriveState.SEARCHING;
    vm.exporting = false;
    vm.currentStep = 0;
    vm.maxLength = 50;
    vm.driveSearch = null;

    vm.isStepAllowed = function(step) {
        // only earlier steps are allowed
        return step <= vm.currentStep;
    };

    vm.createBackupTabSelected = function() {
        vm.fileReference = null;
        vm.warnings = [];
        vm.partitionName = null;

        // User might have gone back to first tab, probably to change the password
        stopSearchingForDrive();
    };

    vm.createConfigBackup = function() {
        var password;

        // Do passwords match?
        vm.passwordForm.repeatPassword.$setValidity('mustMatch', vm.newPassword === vm.repeatPassword);

        // Any other form error?
        if (!vm.passwordForm.$valid) {
            return;
        }
        password = vm.newPassword;

        vm.exporting = true;
        ConfigBackupService.exportConfig(true, password).then(function(result) {
            vm.warnings = result.warnings;
            vm.fileReference = result.configBackupReference.fileReference;
            logger.warn('Exported config: ' + JSON.stringify(vm.fileReference));
            vm.exportBackupToDrive(vm.fileReference);
        }, function(response) {
            NotificationService.error(response.toUpperCase());
        }).finally(function() {
            vm.exporting = false;
        });
    };

    vm.exportBackupToDrive = function(fileReference) {
        vm.currentStep = 1;
        startSearchingForDrive();
    };

    vm.writeBackup = function() {
        stopSearchingForDrive();
        vm.driveState = DriveState.WRITING;
        ConfigBackupService.writeConfigToDisk(vm.fileReference).then(function(result) {
            logger.warn('Wrote to drive: ' + JSON.stringify(result));
            vm.driveState = DriveState.WROTE;
        }, function(response) {
            NotificationService.error(response.toUpperCase());
            vm.driveState = DriveState.FAILED;
        });
    };

    function getMountedPartition() {
        ConfigBackupService.mountedPartition().then(function(result) {
            logger.warn('mountedPartition: ' + JSON.stringify(result));
            if (result != null) {
                vm.partitionName = result;
                vm.driveState = DriveState.FOUND;
            } else {
                vm.partitionName = null;
                vm.driveState = DriveState.SEARCHING;
            }
        }, function(response) {
            logger.error(response);
        });
    }

    function startSearchingForDrive() {
        if (vm.driveSearch == null) {
            getMountedPartition();
            vm.driveSearch = $interval(getMountedPartition, DRIVE_SEARCHING_INTERVAL);
        }
    }

    function stopSearchingForDrive() {
        if (vm.driveSearch != null) {
            $interval.cancel(vm.driveSearch);
        }
        vm.driveSearch = null;
    }

    vm.showProgress = function() {
        return vm.driveState === DriveState.SEARCHING || vm.driveState === DriveState.WRITING;
    };

    vm.showAttachMessage = function() {
        return vm.driveState === DriveState.SEARCHING;
    };

    vm.showSuccessMessage = function() {
        return vm.driveState === DriveState.WROTE;
    };

    vm.isComplete = function() {
        return vm.driveState === DriveState.WROTE || vm.driveState === DriveState.FAILED;
    };

    vm.mayStartWriting = function() {
        return vm.driveState === DriveState.FOUND;
    };

    vm.closeDialog = function() {
        stopSearchingForDrive();
        $mdDialog.hide();
    };

    vm.cancel = function() {
        stopSearchingForDrive();
        $mdDialog.cancel();
    };
}
