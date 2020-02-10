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
    templateUrl: 'app/cards/parentalControl/parental-control.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        cardId: '@',
        userId: '<'
    }
};

function Controller(logger, $timeout, $q, $interval, $filter,//jshint ignore: line
                    CardService, DataService, UserService, UserProfileService, FilterService, onlineTime,
                    LocalTimestampService, settings, ArrayUtilsService, RedirectService, LanguageService) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.editUser = editUser;
    vm.getUserName = getUserName;
    vm.showUsedTime = showUsedTime;
    vm.showRemainingTime = showRemainingTime;
    vm.getConvertedTime = getConvertedTime;
    vm.getListAsCommaSeparatedString = getListAsCommaSeparatedString;
    vm.hasTimeLimitsChange = hasTimeLimitsChange;
    vm.hasContentFilterChange = hasContentFilterChange;
    vm.checkboxHasChanged = checkboxHasChanged;
    vm.addBonusTimeForToday = addBonusTimeForToday;
    vm.resetBonusTimeForToday = resetBonusTimeForToday;
    vm.isMaxTimeReached = isMaxTimeReached;
    vm.blockInternetAccess = blockInternetAccess;
    vm.allowInternetAccess = allowInternetAccess;
    vm.isUserOnline = isUserOnline;
    vm.onlineTimeStopped = onlineTimeStopped;

    const UPDATE_INTERVAL = 3000;
    const SLEEP_WHILE_UPDATE = 100;

    let cardDataInterval;

    vm.$onInit = function() {
        vm.CARD_NAME = 'PARENTAL_CONTROL_' + vm.userId;
        setIsLoading();
        vm.initialLoading = true; // show spinner for instead of online-status-label while loading for the first time.
        vm.contingents = {};
        vm.hasContingentToday = true;
        updateData().finally(loadingDone);
        startCardDataInterval();

        DataService.registerComponentAsServiceListener(vm.CARD_NAME, 'UserService');
        DataService.registerComponentAsServiceListener(vm.CARD_NAME, 'UserProfileService');
    };

    vm.$postLink = function() {
        $timeout(function() {
            CardService.scrollToCard(vm.CARD_NAME);
        }, 300);
    };

    vm.$onDestroy = function() {
        stopCardDataInterval();
        DataService.unregisterComponentAsServiceListener(vm.CARD_NAME, 'UserService');
        DataService.unregisterComponentAsServiceListener(vm.CARD_NAME, 'UserProfileService');
    };

    function startCardDataInterval() {
        if (angular.isUndefined(cardDataInterval)) {
            cardDataInterval = $interval(cardUpdateIntervalExpired, UPDATE_INTERVAL);
        }
    }

    function cardUpdateIntervalExpired() {
        // do not update while loading, so that cached value (update here w/o reload) does not override
        // potential new update (toggle online time switch)
        if (!vm.isLoading) {
            // ** use vm.isUpdating instead of vm.isLoading to avoid flickering of UI, because vm.isLoading disables
            // the buttons.
            vm.isUpdating = true;
            updateData().finally(function() {
                loadingDone();
            });
        }
    }

    function stopCardDataInterval() {
        if (angular.isDefined(cardDataInterval)) {
            $interval.cancel(cardDataInterval);
            cardDataInterval = undefined;
        }
    }

    function setIsLoading() {
        vm.isLoading = true;
    }

    function updateData() {
        return $q.all([
            UserService.getUsers(false),
            UserProfileService.getUserProfiles(false)
        ]).then(function success() {
            vm.referencingUser = UserService.getUserById(vm.userId);
            vm.associatedProfile = UserProfileService.getUserProfileById(vm.referencingUser.associatedProfileId);

            vm.hasBonusTime = hasBonusTime(vm.associatedProfile);
            vm.internetBlocked = vm.associatedProfile.internetBlocked;

            // has online time per day?
            vm.hasTimeLimits = vm.associatedProfile.controlmodeMaxUsage;

            // Blacklist: has blocked content? (URL blocking, 'Video', 'Social networks' ..)
            vm.hasBlackLists = vm.associatedProfile.controlmodeUrls &&
                vm.associatedProfile.internetAccessRestrictionMode === 1 &&
                angular.isArray(vm.associatedProfile.inaccessibleSitesPackages) &&
                    vm.associatedProfile.inaccessibleSitesPackages.length > 0;

            // Whitelist: has allowed content? (fragFinn, custom whitelists)
            vm.hasWhitelists = vm.associatedProfile.controlmodeUrls &&
                vm.associatedProfile.internetAccessRestrictionMode === 2 &&
                angular.isArray(vm.associatedProfile.accessibleSitesPackages) &&
                vm.associatedProfile.accessibleSitesPackages.length > 0;

            vm.hasContentFilter =  vm.hasBlackLists || vm.hasWhitelists;

            if (vm.hasContentFilter) {
                getFilterContent(vm.associatedProfile.inaccessibleSitesPackages,
                    vm.associatedProfile.accessibleSitesPackages).then(function success(lists) {
                        vm.blockedContent = ArrayUtilsService.
                        sortByProperty(getBlockedContent(lists), 'localizedName');
                        vm.exceptionContent = ArrayUtilsService.
                        sortByProperty(getExceptionsContent(lists), 'localizedName');
                });
            }

            vm.hasControlModeTime = vm.associatedProfile.controlmodeTime;

            // has contingents? relevant for bedtime, which is the end time of the last slot
            if (vm.hasControlModeTime) {
                getTodaysContingents(vm.associatedProfile.internetAccessContingents);
            }

            return updateOnlineTimeIntervalStatus(vm.associatedProfile.controlmodeMaxUsage);
        }, angular.noop).finally(function success() {
            vm.isOnline = onlineTime.isOnline(vm.dailyTime);
            vm.inContingent = onlineTime.isInContingent(vm.contingents);
        });
    }

    function isUserOnline() {
        // has contingent and is in contingent && has daily time and is online
        // no contingent && has daily time and is online
        // has contingent and is in contingent && has no daily time
        // no contingent && no daily time
        return !vm.internetBlocked && ((vm.isOnline && vm.inContingent) ||
            (!vm.hasControlModeTime && vm.isOnline) ||
            (vm.inContingent && !vm.hasTimeLimits) ||
            (!vm.hasControlModeTime && !vm.hasTimeLimits));
    }

    function onlineTimeStopped() {
        // has daily time and is offline && either has no contingent or is in contingent
        return vm.hasTimeLimits && !vm.isOnline && !vm.internetBlocked &&
            (vm.inContingent || !vm.hasControlModeTime);
    }

    function hasBonusTime(profile) {
        const bonus = UserProfileService.getBonusTimeForToday(profile);
        return angular.isObject(bonus) && angular.isNumber(bonus.bonusMinutes);
    }

    // ** filter list for blocked pageviews
    function getFilterListMap() {
        return FilterService.getFilterLists().then(function(response) {
            return response;
        }, angular.noop);
    }

    // ** User's Online Time Updates
    function updateOnlineTimeIntervalStatus(controlmodeMaxUsage) {
        // time per day: update online time related values if controlmodeMaxUsage is active
        if (controlmodeMaxUsage) {
            // return, so that we can wait in update() for dailyTime to be loaded and set.
            return updateUserOnlineTime(vm.userId);
        }
        // return mock promise, so that finally block can be called when no maxUsage is set for that user
        const deferred = $q.defer();
        deferred.resolve();
        return deferred.promise;
    }

    function updateUserOnlineTime(userId) {
        return onlineTime.getRemainingTimeById(userId).then(function success(response) {
            vm.dailyTime = onlineTime.computeDailyTime(response.data);
            vm.dailyTime.timesDisplay = onlineTime.computeRemainingOnlineTime(vm.dailyTime.times);
            vm.dailyTime.hasDailyTime = vm.hasTimeLimits;
            return response;
        }, function error(response) {
            return $q.reject(response);
        });
    }

    // Helper functions
    function getTodaysContingents(internetAccessContingents) {
        return LocalTimestampService.getLocalTimestamp().then(function(response) {
            const timestamp = response.data;
            vm.contingents = internetAccessContingents;
            vm.contingents.statusDisplay = onlineTime.computeRemainingContingents(internetAccessContingents,
                timestamp, vm.dailyTime);

            vm.hasContingentToday = onlineTime.hasContingentsToday(internetAccessContingents, timestamp);
            vm.contingents.hasContingent = vm.hasControlModeTime;

            const nextStart = onlineTime.getNextStartTimeForToday(internetAccessContingents, timestamp);
            vm.startTime = angular.isObject(nextStart) && angular.isDefined(nextStart.start) ?
                nextStart.start : undefined;

            const lastContingent = onlineTime.getLatestEndTimeForToday(internetAccessContingents, timestamp);
            vm.bedtime = angular.isObject(lastContingent) && angular.isDefined(lastContingent.end) ?
                lastContingent.end : undefined;

            return response;
        }, angular.noop);
    }

    function getFilterContent(inaccessibleSitesPackages, accessibleSitesPackages) {
        return getFilterListMap().then(function success(response) {
            const filterList = response.data;
            const activatedFilterlists = [];
            const lang = settings.locale().language;
            filterList.forEach((list) => {
                if (inaccessibleSitesPackages.indexOf(list.id) > -1 ||
                    accessibleSitesPackages.indexOf(list.id) > -1) {
                    // ** account for custom white/black lists not having translated names / descriptions
                    list.localizedName = list.builtin ? list.name[lang] : list.customerCreatedName;
                    list.localizedDescription = list.builtin ? list.description[lang] : '';
                    list.isActive = true;
                    activatedFilterlists.push(list);
                }
            });
            return activatedFilterlists;
        }, angular.noop);
    }

    function getBlockedContent(lists) {
        return $filter('filter')(lists, function(list) {
            return list.filterType === 'blacklist';
        });
    }

    function getExceptionsContent(lists) {
        return $filter('filter')(lists, function(list) {
            return list.filterType === 'whitelist';
        });
    }

    // ** UI Functions
    function getUserName() {
        if (angular.isObject(vm.referencingUser)) {
            return vm.referencingUser.name;
        }
    }
    function editUser() {
        RedirectService.toConsole(false, '/#!/parentalcontrol/users/' + vm.userId);
    }

    /**
     * Avoid NaN display in UI
     */
    function showUsedTime(timesDisplay) {
        return angular.isObject(timesDisplay) &&
            angular.isObject(timesDisplay.usedTime) &&
            angular.isNumber(timesDisplay.usedTime.hours) &&
            !Number.isNaN(timesDisplay.usedTime.hours);
    }

    /**
     * Avoid NaN display in UI
     */
    function showRemainingTime(timesDisplay) {
        return angular.isObject(timesDisplay) &&
            angular.isObject(timesDisplay.remainingTime) &&
            angular.isNumber(timesDisplay.remainingTime.hours) &&
            !Number.isNaN(timesDisplay.remainingTime.hours);
    }

    function getConvertedTime(time) {
        if (angular.isObject(time) && angular.isDefined(time.hours) && angular.isDefined(time.minutes)) {
            const loc = settings.locale();
            return LanguageService.convertTimeToStringByLanguage(time.hours, time.minutes, loc.language);
        }
    }

    function getListAsCommaSeparatedString(list, property) {
        return angular.isArray(list) ? list.map(e => e[property]).join(', ') : '';
    }

    // update loading status
    function hasTimeLimitsChange() {
        if (vm.isUpdating) {
            $timeout(hasTimeLimitsChange, SLEEP_WHILE_UPDATE);
        } else {
            setIsLoading();
            UserProfileService.setControlModeMaxUsage(vm.associatedProfile.id, vm.hasTimeLimits).then(function () {
                UserProfileService.invalidateCache();
                return updateData();
            }, angular.noop).finally(loadingDone);
        }
    }

    function hasContentFilterChange() {
        UserProfileService.setControlModeContentFilter(vm.associatedProfile.id, vm.hasContentFilter);
    }

    function checkboxHasChanged(list) {
        // TODO update profile
    }

    function callUpdateUserOnlineTime() {
        return updateUserOnlineTime(vm.userId);
    }

    // update loading status
    function allowInternetAccess() {
        if (vm.isUpdating) {
            $timeout(allowInternetAccess, SLEEP_WHILE_UPDATE);
        } else {
            setIsLoading();
            UserProfileService.setInternetAccessStatus(vm.associatedProfile.id, false).then(function () {
                UserProfileService.invalidateCache();
                return updateData();
            }, angular.noop).then(callUpdateUserOnlineTime, angular.noop).finally(loadingDone);
        }
    }

    // update loading status
    function blockInternetAccess() {
        if (vm.isUpdating) {
            $timeout(blockInternetAccess, SLEEP_WHILE_UPDATE);
        } else {
            setIsLoading();
            UserProfileService.setInternetAccessStatus(vm.associatedProfile.id, true).then(function () {
                UserProfileService.invalidateCache();
                return updateData();
            }, angular.noop).then(callUpdateUserOnlineTime, angular.noop).finally(loadingDone);
        }
    }

    // update loading status
    function addBonusTimeForToday() {
        if (vm.isUpdating) {
            $timeout(addBonusTimeForToday, SLEEP_WHILE_UPDATE);
        } else {
            setIsLoading();
            const tenMinutes = 10;
            UserProfileService.addBonusTimeForToday(vm.associatedProfile.id, tenMinutes).then(function success() {
                UserProfileService.invalidateCache();
                return updateData();
            }).finally(loadingDone);
        }
    }

    // update loading status
    function resetBonusTimeForToday() {
        if (vm.isUpdating) {
            $timeout(resetBonusTimeForToday, SLEEP_WHILE_UPDATE);
        } else {
            setIsLoading();
            UserProfileService.resetBonusTimeForToday(vm.associatedProfile.id).then(function success() {
                UserProfileService.invalidateCache();
                return updateData();
            }).finally(loadingDone);
        }
    }

    /**
     * Must not be greater than 1440 min -> 24 hours. Next day has new online time.
     * @returns {*|boolean}
     */
    function isMaxTimeReached() {
        // 86400 s / 60 = 1440 m = 24 h
        return angular.isObject(vm.dailyTime) &&
            angular.isObject(vm.dailyTime.times) &&
            angular.isNumber(vm.dailyTime.times.remaining) &&
            vm.dailyTime.times.remaining >= 86400;
    }

    function loadingDone() {
        vm.isLoading = false;
        vm.initialLoading = false;
        vm.isUpdating = false;
    }

}
