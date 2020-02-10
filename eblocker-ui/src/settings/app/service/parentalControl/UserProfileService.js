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
export default function UserProfileService(logger, $http, $q, $translate, $filter,
                                           RegistrationService, DataCachingService) {
    'ngInject';

    const  PATH = '/api/adminconsole/userprofiles';
    const PATH_SET_INTERNET_BONUS_TIME = '/api/adminconsole/userprofile/bonustime/';

    const registrationInfo = RegistrationService.getRegistrationInfo();

    function saveNewProfile(profile){
        return $http.post(PATH, stripDownProfile(profile)).then(function(response) {
            return normalizeProfile(response.data);
        }, function(response){
            return response;
        });
    }

    function saveProfile(profile){
        const tmp = angular.copy(profile);
        delete tmp.tmpUsageToday;
        delete tmp.tmpActivatedFilterList;
        return $http.put(PATH, stripDownProfile(tmp)).then(function success(response){
            return normalizeProfile(response.data);
        }, function error(response){
            return response;
        });
    }

    let profileCache, allProfiles;
    function getAll(reload) {
        profileCache = DataCachingService.loadCache(profileCache, PATH, reload).then(function (response) {
            allProfiles = response.data.map(p => normalizeProfile(p));
            return $q.resolve({data: allProfiles});
        }, function (response) {
            return $q.reject(response);
        });
        return profileCache;
    }

    function invalidateCache() {
        profileCache = undefined;
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

    function getBuiltinProfiles(profiles) {
        return $filter('filter')(profiles, function(profile) {
            return profile.builtin;
        });
    }

    function getProfileWithParentalControlPresets(templateProfile, age) {
        const builtinProfiles = getBuiltinProfiles(allProfiles);
        const preset = getPresetProfile(age, builtinProfiles);
        return copyParentalControlSettings(templateProfile, preset);
    }

    function getPresetProfile(age, profileList) {
        let nameKey = 'PARENTAL_CONTROL_DEFAULT_PROFILE_NAME';
        if (age < 6) {
            nameKey = 'PARENTAL_CONTROL_FRAG_FINN_PROFILE_NAME';
        } else if (age < 12) {
            nameKey = 'PARENTAL_CONTROL_FULL_2_PROFILE_NAME';
        } else if (age < 15) {
            nameKey = 'PARENTAL_CONTROL_MED_2_PROFILE_NAME';
        } else if (age < 18) {
            nameKey = 'PARENTAL_CONTROL_MED_PROFILE_NAME';
        }
        let ret = null;
        profileList.forEach((p) => {
            if (p.nameKey === nameKey) {
                ret = p;
            }
        });
        return ret;
    }

    function copyParentalControlSettings(original, parentalControlTemplate) {
        const ret = angular.copy(parentalControlTemplate);
        // now override non parental control related from original profile
        ret.id = original.id;
        ret.name = original.name;
        ret.description = original.description;
        ret.nameKey = original.nameKey;
        ret.descriptionKey = original.descriptionKey;
        ret.hidden = original.hidden;
        ret.standard = original.standard;
        ret.builtin = original.builtin;
        ret.parentalControlSettingValidated = false; // makes sure that user validates PC settings

        return ret;
    }

    function stripDownProfile(profile) {
        if (!profile) {
            return {};
        }
        return {
            id: profile.id,
            name: profile.name,
            description: profile.description,
            controlmodeUrls: profile.controlmodeUrls,
            controlmodeTime: profile.controlmodeTime,
            controlmodeMaxUsage: profile.controlmodeMaxUsage,
            internetAccessRestrictionMode: profile.internetAccessRestrictionMode,
            accessibleSitesPackages: profile.accessibleSitesPackages,
            inaccessibleSitesPackages: profile.inaccessibleSitesPackages,
            internetAccessContingents: profile.internetAccessContingents,
            maxUsageTimeByDay: stripMaxUsageTimeByDay(profile),
            parentalControlSettingValidated: profile.parentalControlSettingValidated
        };
    }

    function getEmptyProfile() {
        return {
            id: undefined,
            builtin: false,
            name: '',
            description: '',
            controlmodeUrls: false,
            controlmodeTime: false,
            internetAccessRestrictionMode: 1,
            accessibleSitesPackages: [],
            inaccessibleSitesPackages: [],
            internetAccessContingents: [],
            appliedToUsers: [],
            normalizedMaxUsageTimeByDay: normalizeMaxUsageTimeByDay({})
        };
    }

    // maps usage restrictions array based on usage restriction map
    function normalizeMaxUsageTimeByDay(profile) {
        let get = function(day, maxUsageTimeByDay) {
            return angular.isDefined(maxUsageTimeByDay) &&
            angular.isDefined(maxUsageTimeByDay[day]) ? maxUsageTimeByDay[day] : 60;
        };
        return [
            { day: 'MONDAY', label: 'PARENTAL_CONTROL_DAY_1', index: 0, minutes: get('MONDAY', profile.maxUsageTimeByDay)}, // jshint ignore: line
            { day: 'TUESDAY', label: 'PARENTAL_CONTROL_DAY_2', index: 1, minutes: get('TUESDAY', profile.maxUsageTimeByDay)}, // jshint ignore: line
            { day: 'WEDNESDAY', label: 'PARENTAL_CONTROL_DAY_3', index: 2, minutes: get('WEDNESDAY', profile.maxUsageTimeByDay)}, // jshint ignore: line
            { day: 'THURSDAY', label: 'PARENTAL_CONTROL_DAY_4', index: 3, minutes: get('THURSDAY', profile.maxUsageTimeByDay)}, // jshint ignore: line
            { day: 'FRIDAY', label: 'PARENTAL_CONTROL_DAY_5', index: 4, minutes: get('FRIDAY', profile.maxUsageTimeByDay)}, // jshint ignore: line
            { day: 'SATURDAY', label: 'PARENTAL_CONTROL_DAY_6', index: 5, minutes: get('SATURDAY', profile.maxUsageTimeByDay)}, // jshint ignore: line
            { day: 'SUNDAY', label: 'PARENTAL_CONTROL_DAY_7', index: 6, minutes: get('SUNDAY', profile.maxUsageTimeByDay)} // jshint ignore: line
        ];
    }

    // maps normalized usage restrictions back to restrictions map
    function stripMaxUsageTimeByDay(profile) {
        return {
            'MONDAY': profile.normalizedMaxUsageTimeByDay[0].minutes,
            'TUESDAY': profile.normalizedMaxUsageTimeByDay[1].minutes,
            'WEDNESDAY': profile.normalizedMaxUsageTimeByDay[2].minutes,
            'THURSDAY': profile.normalizedMaxUsageTimeByDay[3].minutes,
            'FRIDAY': profile.normalizedMaxUsageTimeByDay[4].minutes,
            'SATURDAY': profile.normalizedMaxUsageTimeByDay[5].minutes,
            'SUNDAY': profile.normalizedMaxUsageTimeByDay[6].minutes
        };
    }

    function normalizeProfile(profile) {
        if (angular.isUndefined(profile)) {
            return undefined;
        }
        if (angular.isDefined(profile.nameKey)) {
            profile.name = $translate.instant('ADMINCONSOLE.PARENTAL_CONTROL.DEFAULT_PROFILE.' +
                profile.nameKey);
        }
        if (angular.isDefined(profile.descriptionKey)) {
            profile.description = $translate.instant('ADMINCONSOLE.PARENTAL_CONTROL.DEFAULT_PROFILE.' +
                profile.descriptionKey);
        }
        if (angular.isDefined(profile.internetAccessContingents)) {
            for (let c = 0; c < profile.internetAccessContingents.length; c++) {
                profile.internetAccessContingents[c].id = c;
            }
        }
        profile.assignedToUsers = [];
        profile.normalizedMaxUsageTimeByDay = normalizeMaxUsageTimeByDay(profile);
        return profile;
    }

    function deleteProfile(id) {
        return $http.delete(PATH + '/' + id).then(function(response){
            return response;
        }, function (response) {
            return $q.reject(response);
        }).finally(function () {
            invalidateCache();
        });
    }

    function deleteAllProfiles(ids) {
        return $http.post(PATH + '/all', ids).then(function(response){
            return response;
        }, function (response) {
            return $q.reject(response);
        }).finally(function () {
            invalidateCache();
        });
    }

    function uniqueName(name, id) {
        let def = $q.defer();

        $http.get(PATH + '/unique?name='+encodeURIComponent(name)+(angular.isDefined(id) ? '&id=' + id : ''))
            .then(function(){
                def.resolve();
            }, function(response) {
                if (response.status === 409) {
                    return def.reject(response);
                } else {
                    return def.resolve(response);
                }
            });
        return def.promise;

    }

    /**
     * Calls BE method: getProfilesBeingUpdated
     */
    function updates() {
        return $http.get(PATH + '/updates').then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        }).finally(function () {
            invalidateCache();
        });
    }

    function isLicensed() {
        if (!angular.isDefined(registrationInfo.productInfo)) {
            return false;
        }
        if (!angular.isDefined(registrationInfo.productInfo.productFeatures)) {
            return false;
        }
        return registrationInfo.productInfo.productFeatures.indexOf('FAM') >= 0;
    }

    return {
        saveNewProfile: saveNewProfile,
        saveProfile: saveProfile,
        deleteProfile: deleteProfile,
        deleteAllProfiles: deleteAllProfiles,
        isLicensed: isLicensed,
        normalizeMaxUsageTimeByDay: normalizeMaxUsageTimeByDay,
        updates: updates,
        getAll: getAll,
        invalidateCache: invalidateCache,
        uniqueName: uniqueName,
        getEmptyProfile: getEmptyProfile,
        getProfileWithParentalControlPresets: getProfileWithParentalControlPresets,
        getBonusTimeForToday: getBonusTimeForToday,
        resetBonusTimeForToday: resetBonusTimeForToday,
        addBonusTimeForToday: addBonusTimeForToday
    };
}
