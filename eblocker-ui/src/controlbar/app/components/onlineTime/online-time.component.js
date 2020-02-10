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
    templateUrl: 'app/components/onlineTime/online-time.component.html',
    controller: OnlineTimeController,
    controllerAs: 'ctrl'
};

function OnlineTimeController(logger, TimeService, AccessUsageService,
                              AccessContingentService, DeviceService, settings) {
    'ngInject';

    const vm = this;
    let userProfile;

    vm.onlineTimeProfile = {
        remainingTime: { hours: 0, minutes: 0 }
    };

    vm.closeDropdown = {};
    vm.getTimeStamp = getTimeStamp;
    vm.getRemainingTime = getRemainingTime;
    vm.withinContingent = withinContingent;
    vm.startUsage = startUsage;
    vm.stopUsage = stopUsage;
    vm.getContingentDay = getContingentDay;
    vm.getContingentDisplayTime = getContingentDisplayTime;
    vm.loading = true;

    function setUserProfile() {
        DeviceService.getCurrentDevice().then(function success(response) {
            userProfile = response.data.effectiveUserProfile;
            vm.onlineTimeProfile.profileName = userProfile.name;
            vm.onlineTimeProfile.showContingents = userProfile.controlmodeTime;
            vm.onlineTimeProfile.showUsageActions = userProfile.controlmodeMaxUsage;
            vm.onlineTimeProfile.contingents = userProfile.internetAccessContingents;
        }).finally(function done() {
            vm.loading = false;
        });
    }

    function getTimeStamp() {
        vm.loading = true;
        let minuteOfDay;
        let dayOfWeek;
        return TimeService.getLocalTime().then(function success(response) {
            let timestamp = response.data;
            minuteOfDay = timestamp.hour * 60 + timestamp.minute;
            dayOfWeek = timestamp.dayOfWeek;
            return AccessUsageService.getUsage();
        }).then(function success(response) {
            let usage = response.data;
            vm.onlineTimeProfile.usage = usage;
            if (angular.isDefined(userProfile)) {
                vm.onlineTimeProfile.remainingTime = getRemainingTime(dayOfWeek, minuteOfDay, userProfile, usage);
            } else {
                logger.error('Unable to get remaining online time; userprofile is undefined.');
            }
        }).finally(function done() {
            vm.loading = false;
        });
    }

    function getRemainingTime(dayOfWeek, minuteOfDay, userProfile, usage) {
        // find the maximum remaining time
        let maxMinutes = 0;
        if (userProfile.controlmodeTime) {
            userProfile.internetAccessContingents.forEach(function (contingent) {
                if (withinContingent(contingent, dayOfWeek, minuteOfDay)) {
                    let minutesLeft = contingent.tillMinutes - minuteOfDay;
                    if (minutesLeft > maxMinutes) {
                        maxMinutes = minutesLeft;
                    }
                }
            });
        } else {
            maxMinutes = 24 * 60 - minuteOfDay;
        }

        if (userProfile.controlmodeMaxUsage) {
            let usageRemainingMinutes = Math.floor((usage.maxUsageTime - usage.usedTime) / 60);
            if (usageRemainingMinutes < maxMinutes) {
                maxMinutes = usageRemainingMinutes;
            }
        }

        let hours = Math.floor(maxMinutes / 60);
        let minutes = maxMinutes % 60;
        return {hours: hours, minutes: minutes};
    }

    function withinContingent(contingent, dayOfWeek, minuteOfDay) {
        if (contingent.onDay === dayOfWeek ||
            (contingent.onDay === 8 && dayOfWeek >= 1 && dayOfWeek <= 5) ||
            (contingent.onDay === 9 && dayOfWeek >= 6 && dayOfWeek <= 7)) {

            if (contingent.fromMinutes <= minuteOfDay && minuteOfDay <= contingent.tillMinutes) {
                return true;
            }
        }
        return false;
    }

    function getContingentDay(contingent) {
        return AccessContingentService.getContingentDay(contingent);
    }

    function getContingentDisplayTime(minutesFromMidnight) {
        return AccessContingentService.getContingentDisplayTime(minutesFromMidnight, settings.locale().language);
    }

    function startUsage() {
        AccessUsageService.startUsage().then(getTimeStamp);
    }

    function stopUsage() {
        AccessUsageService.stopUsage().then(getTimeStamp);
    }

    setUserProfile();
}
