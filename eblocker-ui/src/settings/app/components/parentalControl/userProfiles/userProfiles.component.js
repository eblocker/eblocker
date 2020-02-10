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
    templateUrl: 'app/components/parentalControl/userProfiles/userProfiles.component.html',
    controller: UserProfilesController,
    controllerAs: 'vm',
    // bindings are resolve by parent state
    bindings: {
        profiles: '<',
        devices: '<',
        dnsEnabled: '<',
        sslEnabled: '<'
    }
};

function UserProfilesController(logger, UserProfileService, UserService, $interval, $translate, FilterService, AccessContingentService, // jshint ignore: line
                                StateService, STATES, ArrayUtilsService, DialogService, TableService) {
    'ngInject';
    'use strict';

    const vm = this;

    // ***** Table stuff

    vm.templateCallback = {
        formatTimeRangeList: formatTimeRangeList,
        formatProfileUsersList: formatProfileUsersList,
        getContingentDay: getContingentDay,
        getContingentDisplayTime: getContingentDisplayTime,
        goToUsers: goToUsers
    };
    vm.detailsState = 'userprofiledetails';
    vm.tableId = TableService.getUniqueTableId('user-profiles-table');

    vm.tableHeaderConfig = [
        {
            label: '',
            icon: '/img/icons/ic_group_black.svg',
            isSortable: true,
            isXsColumn: true,
            sortingKey: 'assignedToUsers',
            showHeader: true,
        },
        {
            label: 'ADMINCONSOLE.USER_PROFILES.TABLE.COLUMN.NAME',
            isSortable: true,
            sortingKey: 'name',
            flexGtXs: 20,
            showHeader: true,
        },
        {
            label: 'ADMINCONSOLE.USER_PROFILES.TABLE.COLUMN.RESTRICTED_WEBSITES',
            isSortable: false,
            showHeader: true,
        },
        {
            label: 'ADMINCONSOLE.USER_PROFILES.TABLE.COLUMN.RESTRICTED_TIME_RANGE',
            isSortable: false,
            showHeader: true,
        },
        {
            label: 'ADMINCONSOLE.USER_PROFILES.TABLE.COLUMN.RESTRICTED_TIMES',
            isSortable: false,
            showHeader: true,
        },
        {
            label: 'ADMINCONSOLE.USER_PROFILES.TABLE.COLUMN.ASSIGNED_USERS',
            isSortable: false,
            showHeader: true,
        }
    ];

    vm.isSelectable = isSelectable;
    vm.changeOrder = changeOrder;
    vm.getUserProfile = getUserProfile;
    vm.goToUsers = goToUsers;
    vm.isDeletable = isDeletable;
    vm.deleteEntry = deleteEntry;
    vm.onSingleDeleteDone = onSingleDeleteDone;


    // Filtering following props of table entry
    vm.searchProps = ['name'];

    function isSelectable(value) {
        return isDeletable(value);
    }

    function isDeletable(value) {
        return !value.builtin && value.assignedToUsers.length === 0;
    }

    function deleteEntry(value) {
        return UserProfileService.deleteProfile(value.id);
    }

    function onSingleDeleteDone() {
        getAll();
    }

    function getUserProfile(profileId) {
        if (!angular.isArray(vm.tableData)) {
            return '';
        }
        let ret;
        vm.tableData.forEach((profile) => {
            if (profileId === profile.id) {
                ret = profile;
            }
        });
        return ret.name;
    }

    function goToUsers() {
        StateService.goToState(STATES.USERS);
    }

    function changeOrder(key) {
        if (key === vm.orderKey) {
            vm.reverseOrder = !vm.reverseOrder;
        } else {
            vm.orderKey = key;
        }
    }
    // *** END table stuff

    let openDetailsForNewProfile = true;

    vm.loading = false;

    vm.updates = [];

    vm.showSslDnsWarningMessage = false;

    vm.addNewProfile = addNewProfile;
    vm.getContingentDay = getContingentDay;
    vm.getContingentDisplayTime = getContingentDisplayTime;


    let checkUpdateStatus;

    let blacklists = [];
    let whitelists = [];
    let filterlistMap = {};
    vm.users = [];

    vm.$onInit = function() {
        // setup must be called from here, so that
        // the bindings are actually resolved!
        UserService.getAll().then(function success(response){
            vm.users = response.data;
            getAll();
        });
    };

    function getAll() {
        UserProfileService.getAll().then(function success(response) {
            vm.tableData = response.data.filter(function(profile) {
                return !profile.hidden;
            });
            setup();
            return loadAllFilterLists();
        }).then(function success() {
            vm.tableData.forEach((profile) => {
                profile.tmpActivatedFilterList = ArrayUtilsService.
                sortByProperty(getActivatedFilterlists(profile), 'localizedName');
            });

            startCheckUpdateStatus();
            // ** sort access, so that 'Monday' is before 'Sunday' etc.
            vm.tableData.forEach((profile) => {
                if (angular.isArray(profile.internetAccessContingents)) {
                    profile.internetAccessContingents = ArrayUtilsService.
                    sortByProperty(profile.internetAccessContingents, 'onDay');
                }
                if (profile.controlmodeMaxUsage) {
                    profile.tmpUsageToday = getTodaysUsage(profile.normalizedMaxUsageTimeByDay);
                }
            });
        }).finally(function () {
            vm.filteredTableData = angular.copy(vm.tableData);
        });
    }

    vm.getList = getList;

    function getList(profile) {
        return profile.tmpActivatedFilterList;
    }

    function getTodaysUsage(usages) {
        const now = new Date();
        let today;
        // Sunday - Saturday : 0 - 6
        const day = now.getDay();
        if (day === 0) {
            today = getMinutesForUsageDay(usages, 7);
            // sunday:
            // "PARENTAL_CONTROL_DAY_7": "Sunday",
            // "PARENTAL_CONTROL_DAY_9": "Saturday and Sunday"
        } else if (day >= 1 && day <= 5) {
            today = getMinutesForUsageDay(usages, day);
            // "PARENTAL_CONTROL_DAY_XXX": "THE DAY",
        //     "PARENTAL_CONTROL_DAY_8": "Monday - Friday",
        } else if (day === 6) {
            today = getMinutesForUsageDay(usages, day);
        //     "PARENTAL_CONTROL_DAY_6": "Saturday",
        //     "PARENTAL_CONTROL_DAY_9": "Saturday and Sunday"
        }
        return today;
    }

    function getMinutesForUsageDay(usages, day) {
        let ret = -1;
        usages.forEach((usage) => {
            if (usage.label === 'PARENTAL_CONTROL_DAY_' + day) {
                ret = usage.minutes;
            }
        });
        return ret;
    }

    function startCheckUpdateStatus() {
        if (angular.isUndefined(checkUpdateStatus)) {
            checkUpdateStatus = $interval(function () {
                UserProfileService.updates().then(function success(response) {
                    vm.updates = response.data;
                });
            }, 2000);
        }
    }

    function stopCheckUpdateStatus() {
        if (angular.isDefined(checkUpdateStatus)) {
            $interval.cancel(checkUpdateStatus);
            checkUpdateStatus = undefined;
        }
    }

    function updateSslWarning(profile) {
        profile.showSslWarningMessage = false;
        if (profile.controlmodeUrls && !vm.dnsEnabled) {
            for (let i = 0; i < profile.assignedToUsers.length; ++i) {
                if (!profile.assignedToUsers[i].devicesSslEnabled) {
                    profile.showSslWarningMessage = true;
                    return;
                }
            }
        }
    }

    function setup() { // jshint ignore: line

        vm.showSslDnsWarningMessage = !vm.dnsEnabled && !vm.sslEnabled;

        // temporary map to simplify association of users
        let profileMap = {};
        for (let i = 0; i < vm.tableData.length; i++) {
            let profile = vm.tableData[i];
            profile.assignedToUsers = [];
            profileMap[profile.id] = profile;
        }

        for (let i = 0; i < vm.users.length; i++) {
            let user = vm.users[i];
            if (angular.isDefined(user.associatedProfileId)) {
                if (angular.isDefined(profileMap[user.associatedProfileId])) {
                    profileMap[user.associatedProfileId].assignedToUsers.push(user);
                }
            }
        }

        // create temporary user by id map and initialize some extra fields
        let userMap = {};
        for (let i = 0; i < vm.users.length; i++) {
            let user = vm.users[i];
            user.assignedToDevices = [];
            user.operatingDevices = [];
            user.devicesSslEnabled = true;
            userMap[user.id] = user;
        }

        // set devices-ssl-enabled flag for each user
        for (let i = 0; i < vm.devices.length; i++) {
            let device = vm.devices[i];
            if (device.isEblocker || device.isGateway || device.sslEnabled) {
                continue;
            }
            if (angular.isDefined(device.assignedUser)) {
                if (angular.isDefined(userMap[device.assignedUser]) && !device.sslEnabled) {
                    userMap[device.assignedUser].devicesSslEnabled = false;
                }
            }
            if (angular.isDefined(device.operatingUser)) {
                if (angular.isDefined(userMap[device.operatingUser])) {
                    userMap[device.operatingUser].devicesSslEnabled = false;
                }
            }
        }

        // update per profile warnings
        for(let i = 0; i < vm.tableData.length; ++i) {
            updateSslWarning(vm.tableData[i]);
        }
    }

    // Get all white-/blacklists
    function loadAllFilterLists(){
        return FilterService.getAllFilterLists().then(function(response) {
            const filterlists = response.data;
            blacklists = [];
            whitelists = [];
            for (let i = 0; i < filterlists.length; i++) {
                let list = filterlists[i];
                // this filterlist map is used to display restricted websites.
                filterlistMap[list.id] = list;
                if (list.filterType === 'whitelist'){
                    whitelists.push(list);
                } else if (list.filterType === 'blacklist') {
                    blacklists.push(list);
                }
                // else: unknown list type
            }
        });
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
            normalizedMaxUsageTimeByDay: UserProfileService.normalizeMaxUsageTimeByDay({})
        };
    }

    function formatProfileUsersList(list, isEmpty) {
        let ret = '';
        if (list.length > 0) {
            ret = ret.concat(list[0].name);
            if (list.length > 1) {
                ret = ret.concat(' (+' + (list.length - 1) + ')');
            }
        } else {
            ret = ret.concat(isEmpty);
        }

        return ret;
    }

    function formatTimeRangeList(list) {
        let ret = '';
        if (list.length > 0) {
            const first = getContingentDay(list[0]);
            ret = $translate.instant(first);
            if (list.length > 1) {
                ret = ret.concat(' (+' + (list.length - 1) + ')');
            }
        }

        return ret;
    }

    function getContingentDay(contingent) {
        return AccessContingentService.getContingentDay(contingent);
    }

    function getContingentDisplayTime(minutesFromMidnight) {
        return AccessContingentService.getContingentDisplayTime(minutesFromMidnight);
    }

    function addNewProfile(event) {
        let profile = UserProfileService.getEmptyProfile();
        profile.accessRestrictionType = 'blacklisting'; // hardwired for now
        DialogService.profileAddEdit(profile, openDetailsForNewProfile, event).then(savedNewProfile);
    }

    function savedNewProfile(profile, goToDetails) {
        // ** add new profile to make is visible in list w/o reload
        vm.tableData.push(profile);
        vm.filteredTableData.push(profile);
        // openDetailsForNewProfile = goToDetails;
        // if (openDetailsForNewProfile) {
        //     openDetails(profile);
        // }
        UserService.invalidateCache();
    }

    function getActivatedFilterlists(profile) {
        let activatedFilterlists = [];
        let activatedFilterlistIds = profile.internetAccessRestrictionMode === 1 ?
            profile.inaccessibleSitesPackages : profile.accessibleSitesPackages;
        for (let i = 0; i < activatedFilterlistIds.length; i++) {
            let filterlist = filterlistMap[activatedFilterlistIds[i]];
            if (angular.isDefined(filterlist)) {
                activatedFilterlists.push(filterlist);
            }
        }
        return activatedFilterlists;
    }

    vm.$onDestroy = function() {
        stopCheckUpdateStatus();
    };
}
