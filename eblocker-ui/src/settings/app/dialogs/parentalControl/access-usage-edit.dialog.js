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
export default function Controller($mdDialog, usageByDay) {
    'ngInject';

    // module.fromQuarterHour = module.fromMinutes % 60;
    // module.fromHours = module.fromMinutes - module.fromQuarterHour;
    // module.tillQuarterHour = module.tillMinutes % 60;
    // module.tillHours = module.tillMinutes - module.tillQuarterHour;

    const vm = this;

    vm.hours = [ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24 ];
    vm.minutes = [ 0, 15, 30, 45 ];

    usageByDay.forEach(function(usage) {
        usage.displayHours = Math.floor(usage.minutes / 60);
        usage.displayMinutes = usage.minutes % 60;

    });
    vm.usageByDay = usageByDay;

    vm.cancel = function() {
        $mdDialog.cancel();
    };

    vm.onChange = function (usage) {
        if (usage.displayHours === 24) {
            usage.displayMinutes = 0;
        }
        usage.minutes = usage.displayHours * 60 + usage.displayMinutes;
    };

    vm.save = function() {
        $mdDialog.hide();
    };
}
