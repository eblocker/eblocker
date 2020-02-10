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
export default function UserProfileService(logger, $http, $interval, $q, DataCachingService) {
    'ngInject';
    'use strict';

    const PATH = '/api/userprofile';

    const PATH_PC_PROFILES = '/api/dashboard/userprofiles';
    const PATH_PC_SEARCH_ENGINE = '/api/dashboard/searchEngineConfig';

    const PATH_SET_CONTORL_MODE_MAX_USAGE = '/api/dashboard/userprofile/maxusage/';
    const PATH_SET_CONTORL_MODE_CONTENT_FILTER = '/api/dashboard/userprofile/contentfilter/';
    const PATH_SET_INTERNET_ACCESS_STATUS = '/api/dashboard/userprofile/inetnetaccess/';
    const PATH_SET_INTERNET_BONUS_TIME = '/api/dashboard/userprofile/bonustime/';

    const config = {timeout: 3000};
    const SYNC_INTERVAL = 10000;// every 10 seconds
    let userProfilePromise, allProfilesPromise, syncTimer;

    /*
     * Timer related to periodically synchronizing the profile with
     * the eBlocker
     */
    function startSyncTimer(interval) {
        if (!angular.isDefined(syncTimer)) {
            syncTimer = $interval(syncProfile, angular.isDefined(interval) ? interval : SYNC_INTERVAL);
        }
    }
    function stopSyncTimer() {
        if (angular.isDefined(syncTimer)) {
            $interval.cancel(syncTimer);
            syncTimer = undefined;
        }
    }

    function syncProfile() {
        // TODO: remove loadProfile and update to use getUserProfiles with getUserProfileById
        loadProfile(true);
        getUserProfiles(true);
    }

    function loadProfile(reload) {
        userProfilePromise = DataCachingService.loadCache(userProfilePromise, PATH, reload, config).
        then(function success(response) {
            return response;
        }, function (response) {
            logger.error('Getting profile from the eBlocker failed with status ' +
                response.status + ' - ' + response.data);
            return $q.reject(response.data);
        });
        return userProfilePromise;
    }

    let profilesCache;
    function getUserProfiles(reload) {
        allProfilesPromise = DataCachingService.loadCache(allProfilesPromise, PATH_PC_PROFILES, reload, config).
        then(function success(response) {
            profilesCache = response.data;
            return response;
        }, function (response) {
            logger.error('Getting user profiles failed with status ' +
                response.status + ' - ' + response.data);
            return $q.reject(response.data);
        });
        return allProfilesPromise;
    }

    function invalidateCache() {
        allProfilesPromise = undefined;
    }

    function getUserProfileById(id) {
        let ret = null;
        if (angular.isArray(profilesCache)) {
            profilesCache.forEach(profile => {
                if (profile.id === id) {
                    ret = profile;
                }
            });
        }
        return ret;
    }

    function getSearchEngineConfig() {
        return $http.get(PATH_PC_SEARCH_ENGINE).then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Unable to get filter lists', response);
            return $q.reject(response);
        });
    }

    /*
     * Returns profile if known or loads it from the eBlocker (and
     * keeps a local copy which is then regularly updated)
     */
    function getCurrentUsersProfile(reload) {
        return loadProfile(reload);
    }

    function setControlModeMaxUsage(id, value) {
        return $http.post(PATH_SET_CONTORL_MODE_MAX_USAGE + id, value).then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Unable to set ControlModeMaxUsage for profile ' + id, response);
            return $q.reject(response);
        });
    }

    function setControlModeContentFilter(profileId, value) {
        return $http.post(PATH_SET_CONTORL_MODE_CONTENT_FILTER + profileId, value).then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Unable to set ControlModeContentFilter for profile ' + profileId, response);
            return $q.reject(response);
        });
    }

    function setInternetAccessStatus(profileId, value) {
        return $http.post(PATH_SET_INTERNET_ACCESS_STATUS + profileId, value).then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Unable to set internet access status for profile ' + profileId);
            return $q.reject(response);
        });
    }

    function getInternetAccessStatus(profileId) {
        return $http.get(PATH_SET_INTERNET_ACCESS_STATUS + profileId).then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Unable to get internet access status for profile ' + profileId);
            return $q.reject(response);
        });
    }

    function addBonusTimeForToday(profileId, minutes) {
        return $http.post(PATH_SET_INTERNET_BONUS_TIME + profileId, minutes).then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Unable to add bonus time for profile ' + profileId);
            return $q.reject(response);
        });
    }

    function getBonusTimeForToday(profile) {
        if (angular.isObject(profile.bonusTimeUsage) && angular.isArray(profile.bonusTimeUsage.dateTime) &&
            profile.bonusTimeUsage.dateTime.length >= 3) {
            const dt = profile.bonusTimeUsage.dateTime;
            const today = new Date(Date.now());
            if (today.getFullYear() === dt[0] && (today.getMonth() + 1) === dt[1] && today.getDate() === dt[2]) {
                return profile.bonusTimeUsage;
            }
        }
        return null;
    }

    function resetBonusTimeForToday(profileId) {
        return $http.delete(PATH_SET_INTERNET_BONUS_TIME + profileId).then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Unable to remove bonus time for profile ' + profileId);
            return $q.reject(response);
        });
    }

    return {
        start: startSyncTimer,
        stop: stopSyncTimer,
        addBonusTimeForToday: addBonusTimeForToday,
        getBonusTimeForToday: getBonusTimeForToday,
        resetBonusTimeForToday: resetBonusTimeForToday,
        getCurrentUsersProfile: getCurrentUsersProfile,
        getUserProfiles: getUserProfiles,
        getUserProfileById: getUserProfileById,
        getSearchEngineConfig: getSearchEngineConfig,
        getInternetAccessStatus: getInternetAccessStatus,
        setControlModeMaxUsage: setControlModeMaxUsage,
        setControlModeContentFilter: setControlModeContentFilter,
        setInternetAccessStatus: setInternetAccessStatus,
        invalidateCache: invalidateCache
    };
}
