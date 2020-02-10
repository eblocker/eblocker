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
export default function OnlineTime(logger, $http, $q, $interval, $filter, ArrayUtilsService, DataCachingService) {
    'ngInject';
    'use strict';

    const config = {timeout: 3000};
    const SECONDS_HOUR = 3600;
    const MINUTES_HOUR = 60;

    let timeCache, syncTimer;

    function startSyncTimer(interval) {
        if (!angular.isDefined(syncTimer) && angular.isNumber(interval)) {
            syncTimer = $interval(syncData, interval);
        } else if (!angular.isNumber(interval)) {
            logger.warn('Cannot start synch timer with interval ', interval);
        }
    }

    function stopSyncTimer() {
        if (angular.isDefined(syncTimer)) {
            $interval.cancel(syncTimer);
            syncTimer = undefined;
        }
    }

    function syncData() {
        getRemainingTime(true);
    }

    function getRemainingTime(reload) {
        timeCache = DataCachingService.loadCache(timeCache, '/api/parentalcontrol/usage', reload)
            .then(function success(response) {
            return response;
        }, function(response) {
                logger.error('Getting remaining time failed with status ' + response.status +
                    ' - ' + response.data);
            return $q.reject(response);
        });
        return timeCache;
    }

    function getRemainingTimeById(id) {
        return $http.get('/api/parentalcontrol/usage/' + id).then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Failed to get remaining time for user id ' + id);
            return $q.reject(response);
        });
    }

    function stopUsingRemainingTime() {
        const data = {};
        return $http.delete('/api/parentalcontrol/usage', data, config).then(function(response) {
            return response;
        }, function(response) {
            logger.error('Setting remaining time failed with status ' + response.status +
                ' - ' + response.data);
            return $q.reject(response);
        });
    }

    function startUsingRemainingTime() {
        const data = {};
        return $http.post('/api/parentalcontrol/usage', data, config).then(function(response) {
            return response;
        }, function(response) {
            logger.error('Setting remaining time failed with status ' + response.status +
                ' - ' + response.data);
            return $q.reject(response);
        });
    }

    /*
     * Functions related to remaining time
     */

    /*
     * Takes a duration in seconds and returns a duration as a tuple of *
     * (hour, minute) where minute is a string with heading "0"
     */
    function getHourMinuteDurationFromSeconds(seconds, roundUp) {
        const retHours = Math.floor(seconds / SECONDS_HOUR);
        const min = (roundUp ?
                Math.ceil((seconds % SECONDS_HOUR) / MINUTES_HOUR)
                : Math.floor((seconds % SECONDS_HOUR) / MINUTES_HOUR)
        );

        let retMinutes = angular.isNumber(min) ? min : 0;

        if (retMinutes > 9) {
            retMinutes = retMinutes.toString();
        } else if (retMinutes > 0) {
            retMinutes = '0' + retMinutes.toString();
        } else {
            retMinutes = '00';
        }
        return {hours: angular.isNumber(retHours) ? retHours : 0, minutes: retMinutes};
    }

    function computeDailyTime(onlineTime) {
        const totalTime = onlineTime.maxUsageTime;
        let remainingTime = totalTime - onlineTime.usedTime;
        // Frequency of measuring the online time in the backend may
        // allow the used time to be a few seconds over the allowed
        // time
        if (remainingTime < 0) {
            remainingTime = 0;
        }

        return {
            times: {
                total: totalTime,
                remaining: remainingTime
            },
            usage: {
                online: onlineTime.active,
                isDailyTimeLeft: onlineTime.allowed
            }
        };
    }


    /*
     * Computes how much time of the daily allowance is remaining (incl.
     * percentage bar)
     */
    function computeRemainingOnlineTime(timeTuple) {
        const totalTimeSeconds = timeTuple.total;
        const remainingTimeSeconds = timeTuple.remaining;
        const usedTimeSeconds = totalTimeSeconds - remainingTimeSeconds;
        // Compute total time in h:mm
        const totalTimeTuple = getHourMinuteDurationFromSeconds(totalTimeSeconds, false);
        // Compute remaining time in h:mm
        const remainingTimeTuple = getHourMinuteDurationFromSeconds(remainingTimeSeconds, false);
        const usedTimeTuple = getHourMinuteDurationFromSeconds(usedTimeSeconds, true);
        // Compute used percentage
        let percentageUsed;
        // Avoid division by zero
        if (totalTimeSeconds > 0) {
            percentageUsed = 100 - Math.floor(remainingTimeSeconds / totalTimeSeconds * 100);
        } else {
            // If total time to be used is 0, everything is already used, therefore 100%
            percentageUsed = 100;
        }
        return {
            totalTimeSeconds: totalTimeSeconds,
            remainingTimeSeconds: remainingTimeSeconds,
            totalTime: totalTimeTuple,
            remainingTime: remainingTimeTuple,
            usedTime: usedTimeTuple,
            percentageUsed: percentageUsed
        };
    }

    /*
     * Functions related to contingents
     */

    /*
     * Takes a duration in minutes and returns a duration as a tuple of
     * (hour, minute) where minute is a string with heading "0"
     */
    function getHourMinuteDurationFromMinutes(minutes) {
        const retHours = Math.floor(minutes / MINUTES_HOUR);
        const min = minutes % MINUTES_HOUR;
        let retMinutes = angular.isNumber(min) ? min : 0;
        if (retMinutes > 9) {
            retMinutes = retMinutes.toString();
        } else if (retMinutes > 0) {
            retMinutes = '0' + retMinutes.toString();
        } else {
            retMinutes = '00';
        }
        return {hours: angular.isNumber(retHours) ? retHours : 0, minutes: retMinutes};
    }
    /*
     * Determines if the given timestamp is on the same day as the given
     * contingent
     */
    function isContingentOnCurrentDay(timestamp, contingent) {
        return isSameDay(timestamp, contingent) ||
            isWeekday(timestamp, contingent) ||
            isWeekend(timestamp, contingent);
    }
    function isSameDay(timestamp, contingent) {
        return timestamp.dayOfWeek === contingent.onDay;
    }
    function isWeekday(timestamp, contingent) {
        return timestamp.dayOfWeek <= 5 && contingent.onDay === 8;
    }
    function isWeekend(timestamp, contingent) {
        return timestamp.dayOfWeek >= 6 && contingent.onDay === 9;
    }
    /*
     * Determines if the given timestamp is within the given contingent
     */
    function isCurrentContingent(timestamp, contingent) {
        const minutes = timestamp.hour * 60 + timestamp.minute;
        return (isContingentOnCurrentDay(timestamp, contingent) &&
        // right time?
        contingent.fromMinutes <= minutes && contingent.tillMinutes >= minutes);
    }
    /*
     * Find the longest-remaining contingent containing the given timestamp
     * and return its end in minutes
     */
    function getLongestRemainingContingent(contingents, timestamp) {
        let found = false;
        let endTime = 0;
        for (let i = 0; i < contingents.length; i++) {
            const contingent = contingents[i];
            if (isCurrentContingent(timestamp, contingent)) {
                found = true;
                endTime = (contingent.tillMinutes > endTime ? contingent.tillMinutes : endTime);
            }
        }
        return (found ? endTime : undefined);
    }
    /*
     * Find the longest-lasting chain of contingents containing the given
     * timestamp and return its end in minutes
     */
    function getLongestRemainingContingentChain(contingents, timestamp) {
        let endTime = 0;
        timestamp = angular.copy(timestamp);
        while (true) {
            const newEndTime = getLongestRemainingContingent(contingents, timestamp);
            // If no contingent at least containing the current timestamp is
            // found
            if (!angular.isDefined(newEndTime)) {
                return undefined;
            }
            if (newEndTime > endTime) {
                endTime = newEndTime;
                timestamp.hour = Math.floor(newEndTime / 60);
                timestamp.minute = newEndTime % 60;
                continue;
            }
            // No longer-lasting contingent found, endTime is at end of the
            // chain of contingents
            return endTime;
        }
    }

    /*
     * Returns array of all contingents of today in the form:
     *
     * [
     *   {
     *     fromMinutes: <0-1440>,
     *     start: {hours: <0-23>, minutes: "<00-59>"},
     *     tillMinutes: <0-1440>,
     *     end: {hours: <0-23>, minutes: "<00-59>"}
     *     when: <past, present, future>
     *   }, {
     *     ...
     *   }
     * ]
     */
    function getContingentsOfToday(contingents, timestamp) {
        const contingentsOfToday = [];
        for (let i = 0; i < contingents.length; i++) {
            const contingent = contingents[i];
            if (isContingentOnCurrentDay(timestamp, contingent)) {
                const c = {};
                c.fromMinutes = contingent.fromMinutes;
                c.tillMinutes = contingent.tillMinutes;
                c.start = getHourMinuteDurationFromMinutes(c.fromMinutes);
                c.end = getHourMinuteDurationFromMinutes(c.tillMinutes);
                c.when = getWhenIsContingent(c, timestamp);
                contingentsOfToday.push(c);
            }
        }
        return contingentsOfToday;
    }

    function hasContingentsToday(contingents, timestamp) {
        const today = getContingentsOfToday(contingents, timestamp);
        return angular.isArray(today) && today.length > 0;
    }

    function getLatestEndTimeForToday(contingents, timestamp) {
        const today = getContingentsOfToday(contingents, timestamp);
        if (angular.isArray(today) && today.length > 0) {
            return today.reduce(function(prevLarge, currentLarg) {
                return currentLarg.tillMinutes > prevLarge.tillMinutes ? currentLarg : prevLarge;
            });
        }
    }

    function getNextStartTimeForToday(contingents, timestamp) {
        const today = getContingentsOfToday(contingents, timestamp);
        const now = getPresentContingents(today);
        if (!angular.isArray(now) || now.length === 0) {
            return getNextFuture(today);
        } else {
            return now[0];
        }
    }

    function getPresentContingents(contingents) {
        return $filter('filter')(contingents, function(contingent) {
            return contingent.when === 'present';
        });
    }

    function getNextFuture(contingents) {
        const future = $filter('filter')(contingents, function(contingent) {
            return contingent.when === 'future';
        });
        const nextFuture = ArrayUtilsService.sortByProperty(future, 'fromMinutes');
        if (nextFuture.length > 0) {
            return nextFuture[0];
        }
        return null;
    }

    /*
     * Returns a string (past, present, future) indicating when the
     * contingent is, relative to the timestamp
     */
    function getWhenIsContingent(contingent, timestamp) {
        // Past contingent
        if (timestamp.hour * 60 + timestamp.minute > contingent.tillMinutes) {
            return 'past';
        } else if (timestamp.hour * 60 + timestamp.minute < contingent.fromMinutes) {
            return 'future';
        } else {
            return 'present';
        }
    }
    /*
     * Find the beginning of the next contingent
     */
    function getNextContingentStartingTime(contingents, timestamp) {
        let found = false;
        let startTime = 0;
        const minutes = timestamp.hour * 60 + timestamp.minute;
        for (let i = 0; i < contingents.length; i++) {
            const contingent = contingents[i];
            if (isContingentOnCurrentDay(timestamp, contingent) &&
                // It has not started yet
                contingent.fromMinutes > minutes &&
                // It is either the first encountered or starts sooner
                (!found || contingent.fromMinutes < startTime)) {
                found = true;
                startTime = contingent.fromMinutes;
            }
        }
        // startTime - minues to give the remaining time
        return (found ? startTime - minutes : undefined);
    }
    /*
     * Returns whether the user can access the Internet considering the available daily time
     */
    function isDailyTimeLeft(dailyTime) {
        // No restriction means time is available
        return (!dailyTime || !dailyTime.hasDailyTime ||
        // If restricted but with time left
        dailyTime.times.remaining > 0);
    }
    /*
     * Return shorter remaining time in minutes (rounded down), note
     * parameters are in minutes and seconds
     */
    function getShortestRemainingTimeMinutes(remainingTimeMinutes, remainingMinutesDailyUsage) {
        if (remainingTimeMinutes < Math.floor(remainingMinutesDailyUsage)) {
            return remainingTimeMinutes;
        }
        return Math.floor(remainingMinutesDailyUsage);
    }
    /*
     * The result is set to contingents.statusDisplay in parental-control.component.js. That value is then used
     * to check if we are inContingent property in this service. But, the inContingent is set regardless of here
     * calculated remaining time of contingent (which considers usage).
     * TODO refactor. too complicated.
     *
     * Computes remaining time of current contingent (and directly
     * following/overlapping ones) as well as time till the next contingent
     * is reached
     */
    function computeRemainingContingents(contingents, timestamp, dailyTime) {
        const result = {
            inContingent: false,
            nextContingentThisDay: false,
            contingentEndsInMinutes: undefined,
            contingentEndsInHours: undefined,
            nextContingentInMinutes: undefined,
            nextContingentInHours: undefined,
            contingents: []
        };
        result.contingents = getContingentsOfToday(contingents, timestamp);
        // If the end time of a remaining contingent / chain of remaining
        // contingents can be found and enough of the daily time is left,
        // the user can access the internet
        const contingentEndTime = getLongestRemainingContingentChain(contingents, timestamp);
        if (angular.isDefined(contingentEndTime) && isDailyTimeLeft(dailyTime)) {
            // User is within a contingent/chain of contingents, prepare
            // data for display
            result.inContingent = true;

            // Get remaining time between now and end of contingent
            let contingentRemainingTime = contingentEndTime - (timestamp.hour * 60 + timestamp.minute);

            let remainingMinutesDailyUsage = 0;
            if (angular.isObject(dailyTime) && angular.isObject(dailyTime.times) &&
                angular.isNumber(dailyTime.times.remaining)) {
                remainingMinutesDailyUsage = dailyTime.times.remaining / 60;
            }
            // Shorten if too little remaining daily time
            contingentRemainingTime = getShortestRemainingTimeMinutes(contingentRemainingTime,
                remainingMinutesDailyUsage);

            // Display remaining time as h:mm-tuple
            const contingentEndsIn = getHourMinuteDurationFromMinutes(contingentRemainingTime);
            result.contingentEndsInMinutes = contingentEndsIn.minutes;
            result.contingentEndsInHours = contingentEndsIn.hours;
            return result;
        }
        // If no remaining contingent can be found, look at the next
        // contingent starting this day if enough of the daily time is left
        const nextContingentRemainingTime = getNextContingentStartingTime(contingents, timestamp);
        if (angular.isDefined(nextContingentRemainingTime) && isDailyTimeLeft(dailyTime)) {
            // A contingent will come up this day, prepare data for display
            result.nextContingentThisDay = true;
            const nextContingentIn = getHourMinuteDurationFromMinutes(nextContingentRemainingTime);
            result.nextContingentInMinutes = nextContingentIn.minutes;
            result.nextContingentInHours = nextContingentIn.hours;
            return result;
        }
        // User is not within contingent and no further contingent on this
        // day can be found or there is no daily time left, therefore no
        // more internet access on this day
        return result;
    }

    function isOnline(dailyTime) {
        let dailyTimeActive = true;
        if (angular.isObject(dailyTime)) {
            const hasDailyTime = dailyTime.hasDailyTime;
            dailyTimeActive = !hasDailyTime || (hasDailyTime && dailyTime.usage.online);
        }
        return dailyTimeActive;
    }

    function isInContingent(contingents) {

        let inContingent = true;
        if (angular.isObject(contingents) && angular.isObject(contingents.statusDisplay)) {
            const hasContingent = contingents.hasContingent;
            inContingent = !hasContingent || (hasContingent && contingents.statusDisplay.inContingent);
        }
        return inContingent;
    }

    return {
        start: startSyncTimer,
        stop: stopSyncTimer,
        startUsingRemainingTime: startUsingRemainingTime,
        stopUsingRemainingTime: stopUsingRemainingTime,
        hasContingentsToday: hasContingentsToday,                    // PCC
        getLatestEndTimeForToday: getLatestEndTimeForToday,          // PCC
        getNextStartTimeForToday: getNextStartTimeForToday,          // PCC
        getRemainingTime: getRemainingTime,                          // accessDenied, CARDS: PCC, OT, FRAG_FINN
        getRemainingTimeById: getRemainingTimeById,                  // PCC
        computeDailyTime: computeDailyTime,                          // PCC, FRAG_FINN
        computeRemainingOnlineTime: computeRemainingOnlineTime,      // accessDenied, CARDS: PCC, OT
        computeRemainingContingents: computeRemainingContingents,    // CARDS: PCC, OT, FRAG_FINN
        isOnline: isOnline,                                          // CARDS: PCC, FRAG_FINN
        isInContingent: isInContingent                               // CARDS: PCC, FRAG_FINN
    };
}
