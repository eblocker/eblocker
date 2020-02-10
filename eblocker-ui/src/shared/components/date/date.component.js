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
    templateUrl: 'components/date/date.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        date: '=',
        placeholderLabel: '@'
    }
};

function Controller($scope, LanguageService) {
    'ngInject';

    const vm = this;

    vm.$onInit = function() {
        /**
         * We want to record the date regardless of the current local time. If the user sets the date in time zone
         * GMT +2 at 1:00 o'clock in the morning, we still want to capture the same day in UTC, even though technically
         * the same time-in-millis in UTC would match the day before. So, to normalize the date in UTC and to record the
         * correct date, we need to convert the date into UTC and then add the offset.
         */

        // make sure that internalDate is set with valid value.
        vm.otw = $scope.$watch(function() {
            return vm.date;
        }, function() {
            if (angular.isDefined(vm.date)) {
                vm.internalDate = new Date(vm.date);
                removeOtw();
            }
        });
        vm.maxDate = new Date(Date.now());
        vm.dateLocale = {
            formatDate: function(date) {
                vm.hasError = !angular.isDate(date);
                return date ? LanguageService.getDate(date, 'SHARED.DATE.DATE_FORMAT') : '';
            },
            parseDate: function(dateString) {
                const m = LanguageService.getDateFromString(dateString, 'SHARED.DATE.DATE_FORMAT');
                vm.hasError = !m.isValid();
                return m.isValid() ? m.toDate() : new Date(NaN);
            }
        };
    };

    vm.$onDestroy = function() {
        removeOtw();
    };

    function removeOtw() {
        if (angular.isFunction(vm.otw)) {
            vm.otw();
            vm.otw = undefined;
        }
    }

    vm.onDateChange = function() {
        if (vm.internalDate) {
            const offsetHours = Math.abs(vm.internalDate.getTimezoneOffset() / 60);
            // vm.internalDate is model and is set in UTC, now we add the offset to get the actual date in case we are
            // around midnight with respect to the offset.
            const internalDateTweaked = new Date();
            internalDateTweaked.setTime(vm.internalDate.getTime() + (offsetHours * 60 * 60 * 1000));

            // here we create an UTC date based on year / month / day-of-month of the real date of birth.
            // We reset the time since we do not need it. Note: If the date was today, this could mean
            // that we actually create a date in the future, because the time has not elapsed yet in UTC.
            vm.date = new Date(Date.UTC(internalDateTweaked.getFullYear(), internalDateTweaked.getMonth(),
                internalDateTweaked.getDate(), 0, 0, 0)).getTime();
        } else {
            vm.hasError = true;
        }
    };
}
