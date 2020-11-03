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
export default function SetUpdateTimeDialogController(logger, $scope, $mdDialog, $translate,
						      config, updateIntervalHours,
						      LanguageService, UpdateService) {
    'ngInject';

    const vm = this;

    vm.update = angular.copy(config);
    vm.apply = apply;
    vm.updateEndTimeSet = updateEndTimeSet;
    vm.isMeridiem = isMeridiem;
    vm.setAutomaticUpdatesConfig = setAutomaticUpdatesConfig;
    vm.setEndTime = setEndTime;

    var timeFormat = $translate.instant('ADMINCONSOLE.UPDATE.TIME_FORMAT');

    function setDisplayTimes() {
        vm.update.endTimeDisplay = LanguageService.getDate(vm.update.endTime, timeFormat);
    }
    setDisplayTimes();

    vm.startWatch = $scope.$watch(function(){
        return vm.update.beginTime.getHours() + vm.update.beginTime.getMinutes();
    }, function (newValue, oldValue) {
        if (newValue !== oldValue) {
            setEndTime();
        }
    });

    vm.timePickerMessage = {
        hour: $translate.instant('ADMINCONSOLE.UPDATE.AUTO_TIME.ERROR.REQ'),
        minute: $translate.instant('ADMINCONSOLE.UPDATE.AUTO_TIME.ERROR.REQ'),
        meridiem: $translate.instant('ADMINCONSOLE.UPDATE.AUTO_TIME.ERROR.REQ')
    };

    function updateEndTimeSet() {
        return angular.isDefined(vm.update) &&
            angular.isDefined(vm.update.endTime) &&
            angular.isDefined(vm.update.endTime.getHours()) &&
            angular.isDefined(vm.update.endTime.getMinutes());
    }

    function isMeridiem(){
        return angular.isDefined($translate.use()) && $translate.use() === 'en';
        // if (angular.isDefined($translate.use()) && $translate.use() === 'en') {
        //     return true;
        // } else {
        //     return false;
        // }
    }

    function setAutomaticUpdatesConfig(){
        setEndTime();
        const config = {
            beginHour: vm.update.beginTime.getHours(),
            beginMin: vm.update.beginTime.getMinutes(),
            endHour: vm.update.endTime.getHours(),
            endMin: vm.update.endTime.getMinutes()
        };
        UpdateService.setAutoUpdateConfig(config).then(function success(response) {
            close(response.data);
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
            setDisplayTimes();
        }
    }

    function apply() {
        if (!vm.setUpdateTime.$valid) {
            return;
        }
        setAutomaticUpdatesConfig();
    }

    function close(param) {
        vm.startWatch();
        $mdDialog.hide(param);
    }

    vm.cancel = function() {
        vm.startWatch();
        $mdDialog.cancel();
    };

}
