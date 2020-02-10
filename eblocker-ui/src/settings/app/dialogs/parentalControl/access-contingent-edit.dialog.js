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
export default function Controller($mdDialog, $translate, module) {
    'ngInject';

    const vm = this;

    vm.isMeridiem = isMeridiem;

    vm.endOfDay = !module.isNew && module.tillMinutes === 1440;

    const beginDate = new Date();
    const numHoursStart = module.fromMinutes % 60;
    beginDate.setHours(numHoursStart);
    beginDate.setMinutes(module.fromMinutes - (numHoursStart * 60));

    const endDate = new Date();
    const numHoursEnd = module.tillMinutes % 60;
    endDate.setHours(numHoursEnd);
    endDate.setMinutes(module.tillMinutes - (numHoursEnd * 60));

    vm.timeSlot = {
        beginTime: beginDate,
        endTime: endDate
    };

    vm.timePickerMessage = {
        hour: $translate.instant('ADMINCONSOLE.DIALOG.EDIT_ACCESS_CONTINGENT.ERROR.REQUIRED'),
        minute: $translate.instant('ADMINCONSOLE.DIALOG.EDIT_ACCESS_CONTINGENT.ERROR.REQUIRED'),
        meridiem: $translate.instant('ADMINCONSOLE.DIALOG.EDIT_ACCESS_CONTINGENT.ERROR.REQUIRED')
    };

    function isMeridiem(){
        return angular.isDefined($translate.use()) && $translate.use() === 'en';
    }

    vm.module = module;

    vm.displayDays = [
        { 'day': 1, 'label': 'PARENTAL_CONTROL_DAY_1' },
        { 'day': 2, 'label': 'PARENTAL_CONTROL_DAY_2' },
        { 'day': 3, 'label': 'PARENTAL_CONTROL_DAY_3' },
        { 'day': 4, 'label': 'PARENTAL_CONTROL_DAY_4' },
        { 'day': 5, 'label': 'PARENTAL_CONTROL_DAY_5' },
        { 'day': 6, 'label': 'PARENTAL_CONTROL_DAY_6' },
        { 'day': 7, 'label': 'PARENTAL_CONTROL_DAY_7' },
        { 'day': 8, 'label': 'PARENTAL_CONTROL_DAY_8' },
        { 'day': 9, 'label': 'PARENTAL_CONTROL_DAY_9' }
    ];

    vm.cancel = function() {
        $mdDialog.cancel();
    };

    function onChange() {
        module.fromMinutes = vm.timeSlot.beginTime.getMinutes() + (vm.timeSlot.beginTime.getHours() * 60);
        module.tillMinutes = vm.endOfDay ?
            module.tillMinutes = 1440 :
            vm.timeSlot.endTime.getMinutes() + (vm.timeSlot.endTime.getHours() * 60);

        const validRange = module.fromMinutes < module.tillMinutes;
        vm.accessContingentForm.$setValidity('validRange', validRange);
    }

    vm.save = function() {
        onChange();
        if (!vm.accessContingentForm.$valid) {
            return;
        }
        $mdDialog.hide(module);
    };
}
