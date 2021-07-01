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
export default function LocalTimestampService(logger, $http, $interval, $q, DataCachingService) {
    'ngInject';
    'use strict';
    const PATH = '/api/localtimestamp';
    const config = {timeout: 3000};

    const CLOCK_INTERVAL = 1000;// every seconds

    /**
     * time: loaded from server and then maintained by client interval (basically a clock)
     * clockTimer: interval object for clock (see time variable)
     * syncTimer: interval for server updates
     */
    let clockTimer, syncTimer, time;

    function clockUpdate() {
        if (angular.isObject(time)) {
            // Update seconds
            time.second += CLOCK_INTERVAL / 1000;
            // If necessary, adjust minutes as well
            if (time.second >= 60) {
                time.minute += Math.floor(time.second / 60);
                time.second = time.second % 60;
                // If necessary, adjust hours as well
                if (time.minute >= 60) {
                    time.hour += Math.floor(time.minute / 60);
                    time.minute = time.minute % 60;
                    // If necessary, adjust day of week as well
                    if (time.hour >= 24) {
                        time.dayOfWeek += (Math.floor(time.hour / 24)) % 7;
                        time.hour = time.hour % 24;
                        // No further adjustments - contingents do not consider
                        // day of month, weeks, month or even years!
                    }
                }
            }
        }
    }

    function startSyncTimer(interval) {
        if (!angular.isDefined(syncTimer) && angular.isNumber(interval)) {
            syncTimer = $interval(syncData, interval);
        } else if (!angular.isNumber(interval)) {
            logger.warn('Cannot start synch timer with interval ', interval);
        }

        if (!angular.isDefined(clockTimer)){
            clockTimer = $interval(clockUpdate, CLOCK_INTERVAL);
        }

    }

    function stopSyncTimer() {
        if (angular.isDefined(syncTimer)) {
            $interval.cancel(syncTimer);
            syncTimer = undefined;
        }

        if (angular.isDefined(clockTimer)) {
            $interval.cancel(clockTimer);
            clockTimer = undefined;
        }
    }

    function syncData() {
        getLocalTimestamp(true);
    }

    let serverTimeCache;
    /*
     * Returns local timestamp if known or loads one from the eBlocker (and
     * keeps a local copy which is then regularly updated)
     */
    function getLocalTimestamp(reload) {
        // FIXME: it does not make any sense to load a timestamp from a cache!
        serverTimeCache = DataCachingService.loadCache(serverTimeCache, PATH, reload, config).
        then(function success(response) {
            time = response.data;
            return response;
        }, function(response) {
            logger.error('Getting timestamp from the eBlocker failed with status ' +
                response.status + ' - ' + response.data);
            return $q.reject(response.data);
        });
        return serverTimeCache;
    }

    return {
        start: startSyncTimer,
        stop: stopSyncTimer,
        getLocalTimestamp: getLocalTimestamp
    };
}
