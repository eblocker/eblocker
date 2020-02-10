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
    templateUrl: 'app/components/parentalControl/users/users.component.html',
    controller: UsersController,
    controllerAs: 'vm',
    // bindings are resolve by parent state
    bindings: {
        users: '<',
        dnsEnabled: '<',
        sslEnabled: '<'
    }
};

// jshint ignore: line
function UsersController(logger, STATES, $q, $filter, $stateParams, StateService, UserService, UserProfileService, DialogService, // jshint ignore: line
                         TableService, LanguageService, DeviceService, ArrayUtilsService) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.profiles = [];
    vm.$onInit = function() {
        $q.all([
            UserProfileService.getAll(),
            DeviceService.getAll()
        ]).then(function success(responses){
            vm.profiles = responses[0].data;
            vm.devices = responses[1].data;
            return getAll();
        }).finally(function done() {
            const id = StateService.getIdFromParam($stateParams);
            const entry = ArrayUtilsService.getItemBy(vm.filteredTableData, 'id', id);
            if (angular.isObject(entry)) {
                goToUser(entry);
            }
        });

        vm.detailsParams = {
            getTableData: function() {
                return vm.filteredTableData;
            },
            getAssignedProfile: function(user) {
                const ret = $filter('filter')(vm.profiles, function(profile) {
                    return user.associatedProfileId === profile.id;
                });
                return ret.length === 1 ? ret[0] : {};
            }
        };
    };

    function goToUser(user) {
        vm.detailsParams.entry = user;
        StateService.goToState(vm.detailsState, vm.detailsParams);
    }

    vm.profileById = {};
    vm.showSslDnsWarningMessage = false;

    vm.addNewUser = addNewUser;

    // ** START: TABLE
    vm.templateCallback = {
        getUserAge: getUserAge
    };
    vm.tableId = TableService.getUniqueTableId('parentalcontrol-users-table');
    vm.detailsState = STATES.USER_DETAILS;
    vm.tableHeaderConfig = [
        {
            label: '',
            icon: '/img/icons/ic_person_black.svg',
            isSortable: true,
            isXsColumn: true,
            defaultSorting: true,
            sortingKey: 'type'
        },
        {
            label: 'ADMINCONSOLE.USERS.TABLE.COLUMN.NAME',
            isSortable: true,
            secondSorting: true,
            sortingKey: 'name'
        },
        {
            label: 'ADMINCONSOLE.USERS.TABLE.COLUMN.ONLINE_STATUS',
            flex: 10,
            sortingKey: 'isOnline',
            showOnSmallTable: false,
            isSortable: true
        },
        {
            label: 'ADMINCONSOLE.USERS.TABLE.COLUMN.DEVICES',
            flex: 10,
            sortingKey: 'numDevices',
            showOnSmallTable: false,
            isSortable: true
        },
        {
            label: 'ADMINCONSOLE.USERS.TABLE.COLUMN.CONTENT_RESTRICTIONS',
            showOnSmallTable: true,
            sortingKey: 'hasContentRestrictions',
            isSortable: true
        },
        {
            label: 'ADMINCONSOLE.USERS.TABLE.COLUMN.TIME_RESTRICTIONS',
            showOnSmallTable: true,
            sortingKey: 'hasTimeRestrictions',
            isSortable: true
        }
    ];

    vm.isSelectable = isSelectable;
    vm.isDeletable = isDeletable;
    vm.deleteEntries = deleteEntries;

    // Filtering following props of table entry
    vm.searchProps = ['name'];

    function isSelectable(value) {
        return isDeletable(value);
    }

    function isDeletable(value) {
        return !value.system && value.nameKey !== 'SHARED.USER.NAME.STANDARD_USER';
    }

    function deleteEntries(values) {
        const devicesInUse = DeviceService.getDevicesAssignedOrOperatedByUser(values, vm.devices);

        if (devicesInUse.length > 0) {
            return DialogService.deleteUserThatIsAssignedOrOperatingDevice(null, function yesClicked() {
                // first reassign devices, so that we can delete the users
                return DeviceService.reassignDefaultUserToDevices(values, devicesInUse);
            }, angular.noop).then(function yesClickedPath() {
                // devices are reassigned, so actually delete the users
                return deleteUsersAndProfiles(values);
            }, function noClickedPath(response) {
                const deferred = $q.defer();
                deferred.resolve({data: 0});
                return deferred.promise;
            });
        }
        return deleteUsersAndProfiles(values);
    }

    function deleteUsersAndProfiles(values) {
        const userIds = values.map(user => user.id);
        return UserService.deleteAllUsers(userIds).then(function success(response) {
            // if user could not be deleted, we need to keep the profile as well
            const errorMap = response.data || {};
            const failedIds = Object.keys(errorMap);
            const profilesToBeDeleted = [];
            values.forEach(user => {
                if (failedIds.indexOf(user.id) === -1) {
                    // user.id not in failed-list, so user delete was successful, also delete the profile
                    profilesToBeDeleted.push(user.associatedProfileId);
                }
            });
            return UserProfileService.deleteAllProfiles(profilesToBeDeleted).then(function success() {
                // number of deleted profiles should / must match number of deleted users
                // this allows a notification how many users have been deleted with respect to number of selected users
                return {data: profilesToBeDeleted.length};
            });
        }).finally(function () {
            getAll();
        });
    }

    function getUserAge(birthday) {
        return LanguageService.calculateAge(birthday);
    }
    // *** END table stuff

    function getDevicesWithNoSsl(devices) {
        const devsWithNoSsl = [];
        for(let i = 0; i < devices.length; ++i) {
            if (!devices[i].sslEnabled) {
                devsWithNoSsl.push(devices[i]);
            }
        }
        return devsWithNoSsl;
    }

    function updateSslWarning(user) {
        const profileHasUrlRestrictions = angular.isDefined(vm.profileById[user.associatedProfileId]) &&
            vm.profileById[user.associatedProfileId].controlmodeUrls;
        user.operatingDevicesWithNoSsl = getDevicesWithNoSsl(user.operatingDevices);
        user.assingedDevicesWithNoSsl = getDevicesWithNoSsl(user.assignedToDevices);
        const hasSslDisabledDevs = (user.operatingDevicesWithNoSsl.length + user.assingedDevicesWithNoSsl.length) > 0;
        user.showSslDnsWarningMessage = !vm.dnsEnabled && profileHasUrlRestrictions && hasSslDisabledDevs;
    }

    function setup() {

        vm.showSslDnsWarningMessage = !vm.dnsEnabled && !vm.sslEnabled;

        UserService.processUserDeviceAssociations(vm.tableData, vm.devices);

        // build profile by id map
        vm.profiles.forEach((profile) => {
            vm.profileById[profile.id] = profile;
        });

        // update per user warnings, above params have to be set, so iterate again.
        vm.tableData.forEach((user) => {
            updateSslWarning(user);
            const profile = vm.profileById[user.associatedProfileId];
            if (angular.isObject(profile)) {
                processUserProfile(user, profile);
            } else {
                logger.warn('Unable to find associated profile ' + user.associatedProfileId + ' for user ' +
                    user.name);
            }
            user.numDevices = user.assignedToDevices.length;
        });
    }

    /*
     * Edit details for a new user
     */
    function addNewUser() {
        let user = UserService.getEmptyUser();
        addNewUserDialog(user);
    }

    function addNewUserDialog(user, event) {
        DialogService.userAdd(event, user, vm.profiles, false).then(savedNewUser, cancelledNewUser);
    }

    function savedNewUser(newUser) {
        UserProfileService.getAll(true).then(function success(response){
            vm.profiles = response.data;
            return getAll();
        }).finally(function done() {
            const user = ArrayUtilsService.getItemBy(vm.filteredTableData, 'id', newUser.id);
            goToUser(user);
        });
    }

    function cancelledNewUser() {
        // Do nothing, just close the dialog
    }

    function processUserProfile(user, profile) {
        if (profile.controlmodeMaxUsage || profile.controlmodeMaxUsage) {
            user.hasTimeRestrictions = 1;
        } else {
            user.hasTimeRestrictions = 0;
        }
        if (profile.controlmodeUrls) {
            user.hasContentRestrictions = 1;
        } else {
            user.hasContentRestrictions = 0;
        }
    }

    function getAll() {
        return UserService.getAll().then(function success(response) {
            vm.tableData = response.data.filter(function(user) {
                return !user.system || user.nameKey === 'SHARED.USER.NAME.STANDARD_USER';
            });
            setup();
            vm.filteredTableData = angular.copy(vm.tableData);
            return response;
        });
    }
}
