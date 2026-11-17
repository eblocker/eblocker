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
        REMOVED: 'REMOVED',
        FAILED: 'FAILED'
    };
    const DRIVE_SEARCHING_INTERVAL = 3000;

    const vm = this;
    vm.driveState = DriveState.SEARCHING;
    vm.exporting = false;
    vm.currentStep = 0;
    vm.maxLength = 50;
    vm.driveSearch = null;
    vm.waitRemoval = null;

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
        stopWaitingForRemoval();
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
            startWaitingForRemoval(result);
        }, function(response) {
            NotificationService.error(response.toUpperCase());
            vm.driveState = DriveState.FAILED;
        });
    };

    function getMountedPartition() {
        ConfigBackupService.mountedPartition().then(function(result) {
            if (result != null) {
                vm.partitionName = result.friendlyName;
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

    function getPartitionStatus(partition) {
        ConfigBackupService.partitionStatus(partition).then(function(result) {
            if (!result.attached) {
                vm.driveState = DriveState.REMOVED;
                stopWaitingForRemoval();
            }
        }, function(response) {
            logger.error(response);
        });
    }

    function startWaitingForRemoval(partition) {
        if (vm.waitRemoval == null) {
            getPartitionStatus(partition);
            vm.waitRemoval = $interval(getPartitionStatus, DRIVE_SEARCHING_INTERVAL, 0, true, partition);
        }
    }

    function stopWaitingForRemoval() {
        if (vm.waitRemoval != null) {
            $interval.cancel(vm.waitRemoval);
        }
        vm.waitRemoval = null;
    }

    vm.showDrive = function() {
        return vm.driveState !== DriveState.REMOVED && vm.driveState !== DriveState.FAILED;
    };

    vm.showProgress = function() {
        return vm.driveState === DriveState.SEARCHING || vm.driveState === DriveState.WRITING || vm.driveState === DriveState.WROTE;
    };

    vm.isWritten = function() {
        return vm.driveState === DriveState.REMOVED || vm.driveState === DriveState.WROTE;
    };

    vm.isComplete = function() {
        return vm.driveState === DriveState.REMOVED;
    };

    vm.mayStartWriting = function() {
        return vm.driveState === DriveState.FOUND;
    };

    vm.closeDialog = function() {
        stopSearchingForDrive();
        stopWaitingForRemoval();
        $mdDialog.hide();
    };

    vm.cancel = function() {
        stopSearchingForDrive();
        stopWaitingForRemoval();
        $mdDialog.cancel();
    };
}
