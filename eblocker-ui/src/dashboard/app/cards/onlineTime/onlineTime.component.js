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
    templateUrl: 'app/cards/onlineTime/onlineTime.component.html',
    controllerAs: 'vm',
    controller: OnlineTimeController,
    bindings: {
        cardId: '@'
    }
};

function OnlineTimeController($interval, $q, $timeout, LocalTimestampService, onlineTime,
                              UserProfileService, CardService, DataService, LanguageService, settings) {
    'ngInject';
    'use strict';

    /*
     * Contingent Object from Profile:
     * {
     *     fromMinutes: 900 (Minutes of day, 900 = 15 = 15 Uhr / 3 o'clock)
     *     onDay: 1         (1 = Monday, .., 7 = Sunday)
     *     tillMinutes: 1440
     * }
     *
     * Daily time usage object from online time service:
     * {
     *     accountedTime: 2352.581
     *     active: true
     *     allowed: true (only considers remaining time)
     *     maxUsageTime: 79200 (in seconds)
     *     usedTime: 2055.744 (in seconds)
     * }
     */

    const vm = this;
    const CARD_NAME = 'ONLINE_TIME';

    vm.startOnlineTime = startOnlineTime;
    vm.stopOnlineTime = stopOnlineTime;
    vm.startStopDisabled = startStopDisabled;
    vm.hasMinutes = hasMinutes;
    vm.getLocalizedTime = getLocalizedTime;

    vm.isOnline = false;

    vm.$onInit = function() {
        vm.isLoading = true;
        DataService.registerComponentAsServiceListener(CARD_NAME, 'onlineTime');
        DataService.registerComponentAsServiceListener(CARD_NAME, 'UserProfileService');
        DataService.registerComponentAsServiceListener(CARD_NAME, 'LocalTimestampService');
        onlineTimeIntervalExpired().finally(loadingDone);
        startOnlineTimeInterval();
    };

    vm.$onDestroy = function() {
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'onlineTime');
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'UserProfileService');
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'LocalTimestampService');
        stopOnlineTimeInterval();
    };

    vm.$postLink = function() {
        $timeout(function() {
            CardService.scrollToCard(CARD_NAME);
        }, 300);
    };

    let onlineTimeInterval;

    function startOnlineTimeInterval() {
        if (angular.isUndefined(onlineTimeInterval)) {
            onlineTimeInterval = $interval(onlineTimeIntervalExpired, 3000, 0, true, false);
        }
    }

    function stopOnlineTimeInterval() {
        if (angular.isDefined(onlineTimeInterval)) {
            $interval.cancel(onlineTimeInterval);
            onlineTimeInterval = undefined;
        }
    }

    function onlineTimeIntervalExpired(reload) {
        //
        return $q.all([
            LocalTimestampService.getLocalTimestamp(reload),
            UserProfileService.getCurrentUsersProfile(reload),
            onlineTime.getRemainingTime(reload)
        ]).then(function success(responses) {
            const timestamp = responses[0].data;
            const userProfile = responses[1].data;
            const ot = responses[2].data;

            vm.internetBlocked = userProfile.internetBlocked;
            vm.hasContingent = userProfile.controlmodeTime;
            vm.hasDailyTime = userProfile.controlmodeMaxUsage;

            const contingents = userProfile.internetAccessContingents;
            vm.dailyTime = convertToDailyTime(ot);
            vm.contingents = onlineTime.computeRemainingContingents(contingents, timestamp, vm.dailyTime);

            // vm.contingents.contingents only comprises contingents of today
            vm.contingents.contingents = setOrderPropertyWhen(vm.contingents.contingents);

            vm.hasContingentsToday = angular.isObject(vm.contingents) &&
                angular.isArray(vm.contingents.contingents) &&
                vm.contingents.contingents.length > 0;

            vm.isOnline = !vm.internetBlocked &&
                (!vm.hasContingent || (vm.hasContingent && vm.contingents.inContingent)) &&
                (!vm.hasDailyTime || (vm.hasDailyTime && ot.active));


            if (ot.active && vm.hasContingent && !vm.contingents.inContingent) {
                // just a fallback: online time is active, but we are not in contingent. stop online time.
                stopOnlineTime();
            }
            return responses;
        });
    }

    function setOrderPropertyWhen(contingents) {
        contingents.forEach(c => {
            if (c.when === 'past') {
                c.whenSortProperty = 0;
            } else if (c.when === 'present') {
                c.whenSortProperty = 1;
            } else if (c.when === 'future') {
                c.whenSortProperty = 2;
            }
        });
        return contingents;
    }

    function convertToDailyTime(ot) {
        let remaining = 0;
        if (angular.isNumber(ot.maxUsageTime) &&
            angular.isNumber(ot.usedTime) &&
            ot.maxUsageTime > ot.usedTime) {
            remaining = ot.maxUsageTime - ot.usedTime;
        }
        const tuple = {
            total: angular.isNumber(ot.maxUsageTime) ? ot.maxUsageTime : 0,
            remaining: remaining
        };
        return onlineTime.computeRemainingOnlineTime(tuple);
    }

    function loadingDone() {
        vm.isLoading = false;
    }

    function hasMinutes(obj) {
        return angular.isObject(obj) && angular.isDefined(obj.minutes) && obj.minutes !== '00' && obj.minutes !== '0';
    }

    function getLocalizedTime(time) {
        const loc = settings.locale();
        return LanguageService.convertTimeToStringByLanguage(time.hours, time.minutes, loc.language);
    }

    function startStopDisabled() {
        return vm.isLoading || vm.hasContingent && !vm.contingents.inContingent;
    }

    function startOnlineTime() {
        vm.isLoading = true;
        onlineTime.startUsingRemainingTime().then(function success() {
            onlineTimeIntervalExpired(true);
        }).finally(loadingDone);
    }

    function stopOnlineTime() {
        vm.isLoading = true;
        onlineTime.stopUsingRemainingTime().then(function success() {
            onlineTimeIntervalExpired(true);
        }).finally(loadingDone);
    }

}
