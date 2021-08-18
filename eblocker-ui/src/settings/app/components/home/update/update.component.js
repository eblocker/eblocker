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
    templateUrl: 'app/components/home/update/update.component.html',
    controller: UpdateController,
    controllerAs: 'vm'
};

function UpdateController(logger, UpdateService, RegistrationService, NotificationService, // jshint ignore: line
                          $window, $translate, $filter, $interval, DialogService, StateService, STATES, LanguageService) { // jshint ignore: line
    'ngInject';

    const vm = this;

    const updateIntervalHours = 2;

    vm.osVersion = {
        value: '-'
    };

    vm.filterVersion = {
        value: '-'
    };

    vm.activationState = {
        value: '-'
    };

    vm.licenseUpdateType = {
        value: '-'
    };

    vm.lastUpdate = {
        value: '-'
    };

    vm.nextUpdate = {
        value: '-'
    };

    vm.isPreparingToCheck = false;
    let isDisabled, listsPacketVersion, getUpdateStatusInterval, startUpdateInterval;

    vm.autoUpdateTimeSelectVisible = false;

    vm.isRegistered = isRegistered;
    vm.hasLicenseExpired = hasLicenseExpired;
    vm.setAutomaticUpdateStatus = setAutomaticUpdateStatus;
    vm.openSetUpdateTimeDialog = openSetUpdateTimeDialog;
    vm.isDeviceReady = isDeviceReady;
    vm.checkForUpdates = checkForUpdates;
    vm.openUpdateDialog = openUpdateDialog;

    const registrationInfo = RegistrationService.getRegistrationInfo();

    vm.$onInit = function() {
        initUpdateTime();
        getUpdateStatus();
        // startStatusScan();
    };

    vm.$onDestroy = function() {
        stopStatusScan();
        stopPollForUpdateStart();
    };

    function isDeviceReady() {
        return !vm.isChecking && !vm.isUpdating && !vm.isPreparingToCheck;
    }

    function isRegistered() {
        return RegistrationService.isRegistered();
    }

    function hasLicenseExpired() {
        return RegistrationService.isRegistered() && registrationInfo.licenseExpired;
    }

    function evaluateStatus(status) {
        /*if($scope.isUpdating == true && status.updating == false){//update is finished
                    //reload page
                    reloadCurrentPage();
                }*/
        vm.isUpdating = status.updating;
        vm.isChecking = status.checking;
        vm.areUpdatesAvailable = status.updatesAvailable;
        vm.automaticUpdates = status.automaticUpdatesActivated && status.automaticUpdatesAllowed;
        vm.automaticUpdatesAllowed = status.automaticUpdatesAllowed;
        // $scope.lastAutomaticUpdate = status.lastAutomaticUpdate;
        // $scope.nextAutomaticUpdate = status.nextAutomaticUpdate;
        vm.projectVersion = status.projectVersion;
        listsPacketVersion = status.listsPacketVersion;
        vm.packageUpdates = status.updateablePackages;
        isDisabled = status.disabled;

        vm.dateFormat = $translate.instant('ADMINCONSOLE.UPDATE.DATE_FORMAT');
        vm.dateTimeFormat = $translate.instant('ADMINCONSOLE.UPDATE.DATE_TIME_FORMAT');

        if (angular.isDefined(vm.projectVersion)) {
            vm.osVersion.value = vm.projectVersion;
        }
        if (angular.isDefined(status.listsPacketVersion) && status.listsPacketVersion.length >= 14) {
            const v = status.listsPacketVersion;
            vm.filterVersion.value = v.substr(0, 4) + '-' + v.substr(4, 2) + '-' + v.substr(6, 2) + '-' +
                v.substr(8, 2) + '-' + v.substr(10, 2) + '-' + v.substr(12, 2);
        }
        let licenseUpdateType;
        if (vm.automaticUpdatesAllowed && registrationInfo.licenseLifetime) {
            licenseUpdateType = 'ADMINCONSOLE.UPDATE.LICENSE_UPDATE_TYPE.AUTOMATIC';
        } else if (vm.automaticUpdatesAllowed && angular.isDefined(registrationInfo.licenseNotValidAfter)) {
            licenseUpdateType = $translate.instant('ADMINCONSOLE.UPDATE.LICENSE_UPDATE_TYPE.AUTOMATIC') + ' ' +
                $translate.instant('ADMINCONSOLE.UPDATE.LICENSE_UPDATE_TYPE.UNTIL') + ' ' +
                LanguageService.getDate(registrationInfo.licenseNotValidAfter, vm.dateFormat);
        } else {
            licenseUpdateType = 'ADMINCONSOLE.UPDATE.LICENSE_UPDATE_TYPE.MANUAL';
        }
        vm.licenseUpdateType.value = licenseUpdateType;

        vm.activationState.value = getRegistrationState(registrationInfo);

        if (angular.isDefined(status.lastAutomaticUpdate) && status.lastAutomaticUpdate !== '') {
            vm.lastUpdate.value = LanguageService.getDate(status.lastAutomaticUpdate, vm.dateTimeFormat);
        } else {
            vm.lastUpdate.value = '-';
        }
        if (vm.automaticUpdatesAllowed && angular.isDefined(status.nextAutomaticUpdate) &&
            status.nextAutomaticUpdate !== '') {
            vm.nextUpdate.value = LanguageService.getDate(status.nextAutomaticUpdate, vm.dateTimeFormat);
        } else {
            vm.nextUpdate.value = '-';
        }
        evaluateAutomaticUpdates(status);
    }

    function getRegistrationState(registrationInfo) {
        if (registrationInfo.licenseExpired) {
            return 'ADMINCONSOLE.LICENSE.REGISTRATION_STATE.STATES.EXPIRED';
        } else if (registrationInfo.registrationState === 'REVOKED') {
            return 'ADMINCONSOLE.LICENSE.REGISTRATION_STATE.STATES.REVOKED';
        } else if (registrationInfo.licenseAboutToExpire) {
            return 'ADMINCONSOLE.LICENSE.REGISTRATION_STATE.STATES.ABOUT_TO_EXPIRE';
        } else {
            return 'ADMINCONSOLE.LICENSE.REGISTRATION_STATE.STATES.' +
                registrationInfo.registrationState;
        }
    }

    const NUM_CHECK_FOR_UPDATES = 4;

    function getUpdateStatus() {
        return UpdateService.getStatus().then(function success(response) {
            evaluateStatus(response.data);
            return response;
        }, function error(response) {
            logger.error('Unable to check for update status ', response);
            vm.isChecking = false;
            vm.isPreparingToCheck = false;
        });
    }

    function pollForUpdates() {
        const oldDisabledState = isDisabled;
        getUpdateStatus().then(function success(response) {
            if (vm.isUpdating) {
                // ** We got stuck in update component, although we are updating.
                // So go back to the stand-by state, which should show the updating view.
                StateService.goToState(STATES.STAND_BY);
            }

            if(vm.alreadyCheckedForUpdates && isDisabled !== oldDisabledState) {
                $window.location.reload();
            }

            vm.numPolledForUpdateStatus++;
            // vm.updatesStatus = response.data;
            logger.debug('Successfully received update-status. Updates available: ' +
                vm.areUpdatesAvailable + '. Still checking: ' + vm.isChecking);
            // vm.updatesAvailable = vm.updatesStatus.updatesAvailable;
            if ((vm.numPolledForUpdateStatus >= NUM_CHECK_FOR_UPDATES && !vm.isChecking) ||
                vm.areUpdatesAvailable) {
                vm.isChecking = false;
                vm.alreadyCheckedForUpdates = true;
                vm.isPreparingToCheck = false;
                stopStatusScan();
            }
        }, function error() {
            stopStatusScan();
        });
    }

    function checkForUpdates() {
        logger.info('Starting check for updates ...');
        // ** Make sure spinner is shown during entire checking process!
        // So we reset this flag in finally block.
        vm.isPreparingToCheck = true;
        UpdateService.checkForUpdates().then(function successCheckUpdates(response) {
            startStatusScan();
            // return getUpdateStatus();
        }, function errorCheckUpdates(response) {
            if (response.status === 400) {
                NotificationService.error('ADMINCONSOLE.UPDATE.NOTIFICATION.ERROR_UPDATES_CHECK_TOO_FREQUENT',
                    response);
            } else {
                NotificationService.error('ADMINCONSOLE.UPDATE.NOTIFICATION.ERROR_UPDATES_CHECK_FAILED',
                    response);
            }
            vm.isPreparingToCheck = false;
            stopStatusScan();
        });
    }

    function startStatusScan() {
        vm.numPolledForUpdateStatus = 0;
        getUpdateStatusInterval = $interval(pollForUpdates, 2000);
    }

    function stopStatusScan() {
        if (angular.isDefined(getUpdateStatusInterval)) {
            $interval.cancel(getUpdateStatusInterval);
        }
    }

    function setAutomaticUpdateStatus(){
        UpdateService.setAutoUpdateStatus(vm.automaticUpdates).then(function success(response) {
            evaluateStatus(response.data);
        });
    }

    function isUpdateStartTimeSet() {
        return angular.isDefined(vm.update) &&
            angular.isDefined(vm.update.beginTime) &&
            angular.isDefined(vm.update.beginTime.getHours()) &&
            angular.isDefined(vm.update.beginTime.getMinutes());
    }

    function setEndTime() {
        if (isUpdateStartTimeSet()) {
            vm.update.endTime = angular.copy(vm.update.beginTime);
            vm.update.endTime.setHours(vm.update.endTime.getHours() + updateIntervalHours);
        }
    }

    function evaluateAutomaticUpdates(data) {
        vm.update = vm.update || {};

        const beginDate = new Date();
        beginDate.setHours(data.beginHour);
        beginDate.setMinutes(data.beginMin);
        vm.update.beginTime = beginDate;

        const endDate = new Date();
        endDate.setHours(data.endHour);
        endDate.setMinutes(data.endMin);
        vm.update.endTime = endDate;
    }

    function initUpdateTime() {
        vm.update = {
            beginTime: new Date()
        };
        vm.update.beginTime.setHours(2);
        vm.update.beginTime.setMinutes(30);
        setEndTime();
    }

    function openSetUpdateTimeDialog(event) {
        return DialogService.updateSetTimeDialog(event, vm.update, updateIntervalHours).then(function success(update) {
            vm.update = update;
            evaluateStatus(update);
        });
    }


    const NUM_CHECK_FOR_START_UPDATE = 10;

    function waitForUpdateToStart() {
        getUpdateStatus().then(function success(response) {
            vm.numPolledForStart++;
            logger.debug('Successfully received update-status. Is updating: ' + vm.isUpdating + '.');
            if ((vm.numPolledForStart >= NUM_CHECK_FOR_START_UPDATE) || vm.isUpdating) {
                stopPollForUpdateStart();
                StateService.goToState(STATES.STAND_BY);
            }
        }, function error() {
            stopPollForUpdateStart();
        });
    }

    function pollForUpdateStart() {
        vm.numPolledForStart = 0;
        startUpdateInterval = $interval(waitForUpdateToStart, 2000);
    }

    function stopPollForUpdateStart() {
        vm.waitingForUpdateToStart = false;
        if (angular.isDefined(startUpdateInterval)) {
            $interval.cancel(startUpdateInterval);
        }
    }

    function updateNow() {
        vm.waitingForUpdateToStart = true;
        UpdateService.setStatus({updating: true}).then(function success(response) {
            pollForUpdateStart();
        }, function error(response) {
            NotificationService.error('ADMINCONSOLE.UPDATE.NOTIFICATION.ERROR_UPDATES_FAILED', response);
            logger.error('Failed to start update ', response);
            stopPollForUpdateStart();
        });
    }

    function openUpdateDialog(event) {
        return DialogService.updateStartConfirmDialog(event).then(function update() {
            logger.debug('User clicked on update now.');
            updateNow();
        }, function cancel() {
            logger.debug('User clicked on cancel.');
        });
    }
}
