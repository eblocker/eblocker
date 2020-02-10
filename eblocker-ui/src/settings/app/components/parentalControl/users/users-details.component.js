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
    templateUrl: 'app/components/parentalControl/users/users-details.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        dnsEnabled: '<',
        sslEnabled: '<',
        devices: '<'
    }
};

function Controller($filter, $q, $mdDialog, $interval, $stateParams, StateService, STATES, UserService, // jshint ignore: line
                    NotificationService, DialogService,
                    LanguageService, TableService, DeviceService, ArrayUtilsService, UserProfileService,
                    FilterService, AccessContingentService) {
    'ngInject';
    'use strict';

    const vm = this;
    vm.goToDevices = goToDevices;
    vm.getDeviceState = getDeviceState;
    vm.isUserNameEditable = isUserNameEditable;
    vm.editUserName = editUserName;
    vm.editBirthday = editBirthday;
    vm.editUserRole = editUserRole;
    vm.onChangeUser = onChangeUser;
    vm.togglePin = togglePin;
    vm.isStandardUser = isStandardUser;
    vm.isToday = isToday;

    vm.backState = STATES.USERS;
    vm.stateParams = $stateParams;

    vm.$onInit = function() {
        if (angular.isObject($stateParams.param) &&
            angular.isDefined($stateParams.param.entry)) {

            if (angular.isFunction($stateParams.param.getTableData)) {
                vm.tableUserData = $stateParams.param.getTableData();
            }
            if (angular.isFunction($stateParams.param.getAssignedProfile)) {
                vm.getAssignedProfile = $stateParams.param.getAssignedProfile;
            }

            vm.user = $stateParams.param.entry;

            vm.showSslDnsWarningMessage = !vm.dnsEnabled && !vm.sslEnabled;
            vm.onChangePaginator(vm.user);

            // PARENTAL CONTROL
            loadAllFilterLists();
            startCheckUpdateStatus();

        } else {
            StateService.goToState(vm.backState);
        }
    };

    vm.$onDestroy = function() {
        stopCheckUpdateStatus();
    };

    function isShowAddDeviceHint(user, profile) {
        // show 'add device to activate PC' hint when device list is empty AND if CHILD user or if
        // PC is active (and not a child).
        return (user.userRole === 'CHILD' || isParentalControlActivated(profile)) &&
            user.assignedToDevices.length === 0;
    }

    vm.onChangePaginator = function (entry) {
        vm.tableDataDevices = [];
        vm.filteredTableData = [];
        // devices / users may have changed (assigned user): cache is invalidated, when these values change. In that
        // case we reload the data here.
        $q.all([
            UserService.getAll(),
            DeviceService.getAll()
        ]).then(function success(responses) {
            const devices = responses[1].data;

            // sets various properties like 'assignedToDevices' array
            UserService.processUserDeviceAssociations(vm.tableUserData, devices);

            vm.user = ArrayUtilsService.getItemBy(vm.tableUserData, 'id', entry.id);
            refreshValues(vm.user, devices);
        });
    };

    function refreshValues(user, devices) {

        devices.forEach((d) => {
            // allow sorting of true/false value
            d.isOnlineSortable = d.isOnline ? 1 : 0;
        });

        vm.tableEditMode = false; // make sure table is not in edit mode when paginator is clicked

        vm.assignedProfile = vm.getAssignedProfile(user);

        getTodaysBonusTime(vm.assignedProfile);

        ArrayUtilsService.sortByProperty(vm.assignedProfile.internetAccessContingents, 'onDay');

        vm.showSetBirthdayHint = angular.isUndefined(user.birthday) && user.userRole === 'CHILD';
        vm.showAddDevicesHint = isShowAddDeviceHint(vm.user, vm.assignedProfile); // hint to add to device
        vm.showSSLWarning = user.showSslDnsWarningMessage; // warning: at least one device has SSL not activated
        // hint that user should check PC settings
        vm.showCheckParentalControlHint = user.userRole === 'CHILD' &&
            isParentalControlActivated(vm.assignedProfile) && !vm.assignedProfile.parentalControlSettingValidated;

        // only show the check-parental-control-hint once!
        if (vm.showCheckParentalControlHint) {
            vm.assignedProfile.parentalControlSettingValidated = true;
            doSaveProfile(vm.assignedProfile);
        }

        vm.tableDataDevices = angular.copy(user.assignedToDevices);
        vm.filteredTableData = angular.copy(user.assignedToDevices);

        if (vm.user.nameKey === 'SHARED.USER.NAME.STANDARD_USER') {
            vm.unassignedDevices = $filter('filter')(devices, function(dev) {
                return !dev.isEblocker && !dev.isGateway && dev.assignedUser !== dev.defaultSystemUser;
            });
        } else {
            vm.unassignedDevices = $filter('filter')(devices, function(dev) {
                return !dev.isEblocker && !dev.isGateway && dev.assignedUser !== user.id;
            });
        }

        setDisplayValues(user);
    }

    function isToday(usage) {
        const date = new Date(Date.now());
        return usage.index === date.getDay();
    }

    function getTodaysBonusTime(profile) {
        vm.bonusTime = UserProfileService.getBonusTimeForToday(profile);
        vm.hasBonusTime = angular.isObject(vm.bonusTime) && angular.isDefined(vm.bonusTime.bonusMinutes);
    }

    function isParentalControlActivated(profile) {
        return profile.controlmodeMaxUsage || profile.controlmodeTime || profile.controlmodeUrls ;
    }

    function setDisplayValues(user) {
        vm.userName = {
            value: user.name
        };
        vm.pin = {
            value: user.containsPin ? '****' : undefined
        };
        vm.birthday = {
            value: user.birthday ? LanguageService.getDate(user.birthday, 'ADMINCONSOLE.USERS.DATE_FORMAT') : ''
        };
        vm.age = {
            value: user.userRole === 'CHILD' ? LanguageService.calculateAge(user.birthday) : undefined
        };
        vm.userRole = {
            value: 'ADMINCONSOLE.USERS.DETAILS.USER_ROLE.' + user.userRole
        };
    }

    function goToDevices() {
        StateService.goToState(STATES.DEVICES);
    }

    function getDeviceState() {
        return STATES.DEVICES;
    }

    function togglePin(event, user) {
        DialogService.userUpdatePIN(event, user).then(function success(response) {
            if (response === 'PIN_RESET') {
                vm.user.containsPin = false;
                NotificationService.info('ADMINCONSOLE.USERS.DETAILS.NOTIFICATION.PIN_RESET');
            } else {
                vm.user.containsPin = true;
                NotificationService.info('ADMINCONSOLE.USERS.DETAILS.NOTIFICATION.PIN_SET');
            }
            // ** reload all users to update table in user-list and to show correct PIN setting
            UserService.invalidateCache();
            vm.onChangePaginator(vm.user);
        }, function error(response) {
            if (response !== 'PIN_RESET') {
                NotificationService.error('ADMINCONSOLE.USERS.DETAILS.NOTIFICATION.PIN_ERROR', response);
            }
        });
    }

    function editBirthday(event, entry) {
        if (!entry.system) {

            const msgKeys = {
                title: 'ADMINCONSOLE.DIALOG.EDIT_BIRTHDAY.TITLE',
                label: 'ADMINCONSOLE.DIALOG.EDIT_BIRTHDAY.LABEL'
            };
            const subject = {value: entry.birthday};

            // event, subject, msgKeys, okAction, isUnique, maxLength, minLength, isPassword, isDate
            DialogService.
            openEditDialog(event, subject, msgKeys, editBirthdayAction, undefined, undefined, undefined, false, true).
            then(function success(subject) {
                // ** we need to update the label's value as well
                setDisplayValues(vm.user);
            }, angular.noop);
        }
    }

    function editBirthdayAction(date) {
        vm.user.birthday = date;
        // @todo: refactor and use Update function in controller
        return onChangeUser(vm.user);
    }

    function editUserRole(event, entry) {
        if (!entry.system) {
            DialogService.
            userRoleEdit(event, vm.user).
            then(function success(user) {
                vm.user = user;

                // ** we need to update the label's value as well
                setDisplayValues(user);
            }, angular.noop);
        }
    }

    function isUserNameEditable(user) {
        return user.nameKey !== 'SHARED.USER.NAME.STANDARD_USER';
    }

    function editUserName(event, entry) {
        if (!entry.system) {

            const msgKeys = {
                title: 'ADMINCONSOLE.DIALOG.EDIT_USERNAME.TITLE',
                label: 'ADMINCONSOLE.DIALOG.EDIT_USERNAME.LABEL'
            };
            const subject = {value: entry.name};

            // event, subject, msgKeys, okAction, isUnique
            DialogService.
            openEditDialog(event, subject, msgKeys , editUserNameActionName, editUserNameIsUnique, 16, 1).
            then(function success(subject) {
                // ** we need to update the label's value as well
                vm.userName.value = subject.value;
            }, angular.noop);
        }
    }

    function editUserNameIsUnique(name, id) {
        return UserService.uniqueName(name, id);
    }
    function editUserNameActionName(name) {
        vm.user.name = name;
        return onChangeUser(vm.user);
    }

    function onChangeUser(user){
        // Here, the user is transmitted to the eBlocker to be saved

        // If the name of a system-generated user (e.g. "User for profile
        // 'Adult'") was changed, treat the name from now on like the name of a
        // user-created user
        user.nameKey = 'PARENTAL_CONTROL_USER_NAME';

        return doSaveUser(user);
    }

    function doSaveUser(user) {
        return UserService.updateUser(user).then(function(savedUser){
            // some values might have been changed by the backend, synchronize
            if (angular.isDefined(savedUser)) {
                user.id = savedUser.id;
                user.system = savedUser.system;
                user.name = savedUser.name;
                user.associatedProfileId = savedUser.associatedProfileId;
                user.birthday = savedUser.birthday;
                user.userRole = savedUser.userRole;
                updateSslWarning(user);

            } else {
                // Failure, try to synchronize with eBlocker
                NotificationService.info('ADMINCONSOLE.USERS.DETAILS.NOTIFICATION.ERROR_SAVE_USER');
                // loadAllUsers();
            }
        });
    }

    function updateSslWarning(user) {
        const allDevicesSslEnabled = function(devices) {
            for(let i = 0; i < devices.length; ++i) {
                if (!devices[i].sslEnabled) {
                    return false;
                }
            }
            return true;
        };

        const profileHasUrlRestrictions = vm.assignedProfile.controlmodeUrls;
        const sslEnabledDevices = allDevicesSslEnabled(user.assignedToDevices) &&
            allDevicesSslEnabled(user.operatingDevices);
        user.showSslDnsWarningMessage = !vm.dnsEnabled && profileHasUrlRestrictions && !sslEnabledDevices;
    }


    vm.tableId = TableService.getUniqueTableId('user-details-devices-table');

    vm.tableHeaderConfig = [
        {
            label: '',
            icon: '/img/icons/ic_computer_black.svg',
            isSortable: true,
            isXsColumn: true,
            sortingKey: 'enabled'
        },
        {
            label: 'ADMINCONSOLE.USERS.DETAILS.TABLE.COLUMN.NAME',
            isSortable: true,
            sortingKey: 'name'
        },
        {
            label: 'ADMINCONSOLE.USERS.DETAILS.TABLE.COLUMN.ONLINE_STATUS',
            isSortable: true,
            showOnSmallTable: false,
            sortingKey: 'isOnlineSortable'
        },
        {
            label: 'ADMINCONSOLE.USERS.DETAILS.TABLE.COLUMN.VENDOR',
            isSortable: true,
            secondSorting: true,
            showOnSmallTable: false,
            sortingKey: 'vendor'
        },
        {
            label: 'ADMINCONSOLE.USERS.DETAILS.TABLE.COLUMN.IP',
            isSortable: true,
            showHeader: true,
            sortingKey: 'sumIpAddress'
        }
    ];

    // ** Search filter by these properties
    vm.searchProps = ['name', 'vendor', 'displayIpAddresses'];
    vm.searchTerm = '';

    vm.addDeviceToUser = addDeviceToUser;
    vm.isDeletable = isDeletable;
    vm.bulkDelete = bulkDelete;
    vm.isSelectable = isSelectable;

    function isSelectable(value) {
        return isDeletable(value);
    }

    function isDeletable(value) {
        return true;
    }

    function bulkDelete(values) {
        const allPromises = [];
        values.forEach((device) => {
            // update user on device
            device.assignedUser = device.defaultSystemUser;
            device.operatingUser = device.defaultSystemUser;
            // update UI list (not relevant for server)
            vm.user.assignedToDevices = ArrayUtilsService.removeByProperty(vm.user.assignedToDevices, device, 'id');
            vm.user.operatingDevices = ArrayUtilsService.removeByProperty(vm.user.operatingDevices, device, 'id');
            vm.tableDataDevices = angular.copy(vm.user.assignedToDevices);
            vm.filteredTableData = angular.copy(vm.user.assignedToDevices);
            allPromises.push(DeviceService.update(device.id, device));
        });
        return $q.all(allPromises).then(function success() {

            DeviceService.invalidateCache(); // make sure to reload the devices, so that UI uses updated values
            UserService.invalidateCache();

            vm.showAddDevicesHint = isShowAddDeviceHint(vm.user, vm.assignedProfile);
            // to fix notification 'deleted x of y entries'
            return {data: values.length};
        }).finally(function done() {
            // update data
            vm.onChangePaginator(vm.user);
        });
    }

    function addDeviceToUser(event) {
        // ** This dialog just returns a list of all devices checked by the user
        DialogService.userAddDevice(event, vm.user, vm.unassignedDevices).then(function(checkedDevices) {
            // if a device is already assigned to another user, show a confirmation dialog before reassigning the user
            const alreadyAssigned = getAlreadyAssignedDevices(checkedDevices);
            if (vm.user.nameKey === 'SHARED.USER.NAME.STANDARD_USER') {
                returnDevices(checkedDevices);
            } else if (alreadyAssigned.length > 0) {
                // at least one device is already assigned
                let oldUser, oldUserName, devName;
                if (alreadyAssigned.length === 1) {
                    // exactly one device already assigned: show name of that user
                    oldUser = ArrayUtilsService.getItemBy(vm.tableUserData, 'id', alreadyAssigned[0].assignedUser);
                    oldUserName = angular.isObject(oldUser) ? oldUser.name : '';
                    devName = alreadyAssigned[0].name;
                } else {
                    // More than one device already assigned: show names of all devices (may be multiple user names)
                    devName = '';
                    alreadyAssigned.forEach((d, i) => {
                        devName = devName.concat(d.name);
                        if (i < alreadyAssigned.length - 1) {
                            devName = devName.concat(', ');
                        }
                    });
                }
                DialogService.confirmReassignUser(devName, vm.user.name, oldUserName).then(function yes() {
                    assignDevices(checkedDevices, vm.user.id);
                }, function no() {
                    const unassignedOnly = getUnassignedDevices(checkedDevices);
                    assignDevices(unassignedOnly, vm.user.id);
                });
            } else {
                assignDevices(checkedDevices, vm.user.id);
            }
        }, angular.noop);
    }

    function returnDevices(list) {
        const promises = [];
        list.forEach((d) => {
            promises.push(updateDevice(d, d.defaultSystemUser));
        });

        $q.all(promises).then(function success() {
            // update data
            vm.onChangePaginator(vm.user);
        });

        DeviceService.invalidateCache(); // make sure to reload the devices, so that UI uses updated values
        UserService.invalidateCache();
    }

    function assignDevices(list, userId) {
        const promises = [];
        list.forEach((d) => {
            promises.push(updateDevice(d, userId));
        });

        $q.all(promises).then(function success() {
            // update data
            vm.onChangePaginator(vm.user);
        });

        DeviceService.invalidateCache(); // make sure to reload the devices, so that UI uses updated values
        UserService.invalidateCache();
    }

    function updateDevice(d, userId) {
        d.assignedUser = userId;
        d.operatingUser = userId;
        DeviceService.update(d.id, d).then(function success() {
            vm.user.assignedToDevices.push(d);
            vm.user.operatingDevices.push(d);

            vm.tableDataDevices.push(d);
            vm.filteredTableData.push(d);
            vm.showAddDevicesHint = isShowAddDeviceHint(vm.user, vm.assignedProfile);
        });
    }

    function isStandardUser(user) {
        return user.nameKey === 'SHARED.USER.NAME.STANDARD_USER';
    }

    /**
     * Get all devices that are not yet assigned to a user
     */
    function getUnassignedDevices(list) {
        const ret = [];
        list.forEach((d) => {
            if (!angular.isNumber(d.assignedUser) || d.assignedUser === d.defaultSystemUser) {
                ret.push(d);
            }
        });
        return ret;
    }

    /**
     * Get all devices that are already assigned to a user
     */
    function getAlreadyAssignedDevices(list) {
        const ret = [];
        list.forEach((d) => {
            if (angular.isNumber(d.assignedUser) && (d.assignedUser !== d.defaultSystemUser)) {
                ret.push(d);
            }

        });
        return ret;
    }

    // *** PARENTAL CONTROL TODO: use classes, components, .. to prevent bloating of controller

    vm.onChangeUrlRestrictions = onChangeUrlRestrictions;
    vm.onChangeTimeRestrictions = onChangeTimeRestrictions;
    vm.onChangeUsageRestrictions = onChangeUsageRestrictions;

    vm.isProfileBeingUpdated = isProfileBeingUpdated;
    vm.getActivatedFilterlists = getActivatedFilterlists;
    vm.hasActivatedExceptionFilterLists = hasActivatedExceptionFilterLists;
    vm.getActivatedExceptionFilterlists = getActivatedExceptionFilterlists;
    vm.getContingentDisplayTime = getContingentDisplayTime;
    vm.getContingentDay = getContingentDay;
    vm.deleteAccessContingent = deleteAccessContingent;
    vm.editAccessContingent = editAccessContingent;
    vm.addAccessContingent = addAccessContingent;
    vm.editAccessUsage = editAccessUsage;
    vm.editAccessRestrictions = editAccessRestrictions;

    let checkUpdateStatus;
    let blacklists = [];
    let whitelists = [];
    let filterlistMap = {};
    vm.updates = [];

    function startCheckUpdateStatus() {
        checkUpdateStatus = $interval(function() {
            UserProfileService.updates().then(function success(response) {
                vm.updates = response.data;
            });
        }, 1000);
    }

    function stopCheckUpdateStatus() {
        if (angular.isDefined(checkUpdateStatus)) {
            $interval.cancel(checkUpdateStatus);
        }
    }

    function isProfileBeingUpdated(profile) {
        return vm.updates.indexOf(profile.id) !== -1;
    }

    function onChangeUrlRestrictions(profile) {

        // Show dialog if access restriction has just been activated and no white-/blacklisting
        // settings have ever been made to that profile
        if (profile.controlmodeUrls && (
            (profile.internetAccessRestrictionMode === 1 && profile.inaccessibleSitesPackages.length === 0) ||
            (profile.internetAccessRestrictionMode === 2 && profile.accessibleSitesPackages.length === 0)
        )) {
            editAccessRestrictions(profile);
        } else {
            // Save changes
            doSaveProfile(profile);
            // updateSslWarning(profile);
        }
    }

    function onChangeTimeRestrictions(profile) {
        // Show dialog if time restriction has just been activated
        if (profile.controlmodeTime && profile.internetAccessContingents.length === 0){
            addAccessContingent(profile);
        } else {
            // Save changes
            doSaveProfile(profile);
        }
    }

    function onChangeUsageRestrictions(profile) {
        // Save changes
        doSaveProfile(profile);
    }

    function editAccessRestrictions(profile) {
        let module = {
            blacklisted:[],
            whitelisted:[],
            accessRestrictionType: profile.internetAccessRestrictionMode // 1, 2
        };
        // Copy blacklist into module
        for (let i = 0; i < blacklists.length; i++){

            let blacklisted = blacklists[i];
            blacklisted.active = ArrayUtilsService.contains(profile.inaccessibleSitesPackages, blacklisted.id);
            module.blacklisted.push(blacklisted);
        }
        // Copy whitelist into module
        for (let i = 0; i < whitelists.length; i++){
            let whitelisted = whitelists[i];
            whitelisted.active = ArrayUtilsService.contains(profile.accessibleSitesPackages, whitelisted.id);
            module.whitelisted.push(whitelisted);
        }

        addEditAccessRestrictionsDialog(module, profile);
    }

    function addEditAccessRestrictionsDialog(module, profile) {
        vm.dialogOpen = true;
        $mdDialog.show({
            controller: 'EditRestrictionsDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/parentalControl/access-restrictions-edit.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false,
            locals: {
                module: module
            }
        }).then(function(){
            vm.dialogOpen = false;
            saved(module, profile);
        }, function() {
            vm.dialogOpen = false;
            cancelled(profile);
        });
    }

    function saved(module, profile) {
        let sitesPackage;
        profile.internetAccessRestrictionMode = module.accessRestrictionType;
        // Copy changed whitelist back into profile
        profile.accessibleSitesPackages = [];
        for (let i = 0; i < module.whitelisted.length; i++){
            sitesPackage = module.whitelisted[i];
            if (sitesPackage.active){
                profile.accessibleSitesPackages.push(sitesPackage.id);
            }
        }
        // Copy changed blacklist back into profile
        profile.inaccessibleSitesPackages = [];
        for (let i = 0; i < module.blacklisted.length; i++){
            sitesPackage = module.blacklisted[i];
            if (sitesPackage.active){
                profile.inaccessibleSitesPackages.push(sitesPackage.id);
            }
        }
        if (profile.accessibleSitesPackages.length === 0 && profile.inaccessibleSitesPackages.length === 0) {
            profile.controlmodeUrls = false;
        }

        // Save profile to eBlocker
        doSaveProfile(profile);
    }

    function cancelled(profile) {
        // If no white-/blacklisting settings have ever been made to that profile, deactivate access restrictions
        // Or if no filterlists have been selected
        if ((profile.internetAccessRestrictionMode === 1 && profile.inaccessibleSitesPackages.length === 0) ||
            (profile.internetAccessRestrictionMode === 2 && profile.accessibleSitesPackages.length === 0)) {
            profile.controlmodeUrls = false;
        }
        // Do nothing else, just close the dialog
    }

    function doSaveProfile(profile) {
        profile.internetAccessContingents = ArrayUtilsService.
        sortByProperty(profile.internetAccessContingents, 'onDay');
        vm.showAddDevicesHint = isShowAddDeviceHint(vm.user, profile); // update hint to add to device
        return UserProfileService.saveProfile(profile).finally(updateUserDashboard);
    }

    /**
     * When the user profile is updated, we may need to re-generate the dashboard cards columns of this user.
     * The dashboard cards columns are only auto-updated when the user is saved. So here we manually re-generate
     * the dashboard for this user. Otherwise e.g. FragFINN whitelist update would not result in the dashboard card
     * being shown, until the user is saved again and thus the dashboard is re-generated.
     */
    function updateUserDashboard() {
        if (vm.user.nameKey === 'SHARED.USER.NAME.STANDARD_USER') {
            // TODO this should be done on server by some User/UserProfile service
            // details of standard user which is only a proxy for defaultSystemUsers
            // so we need to update the dashboard of each defaultSystemUser of *every* device
            // even devices currently assigned to other user, because they may be released later on, after which
            // they need the same dashboard.
            return UserService.updateDashboardViewOfAllDefaultSystemUsers();
        }
        return UserService.updateUserDashboardView(vm.user.id);
    }

    // Get all white-/blacklists
    function loadAllFilterLists(){
        FilterService.getAllFilterLists().then(function(response) {
            const filterlists = response.data.filter(function(el) {
                return el.category === 'PARENTAL_CONTROL';
            });
            blacklists = [];
            whitelists = [];
            for (let i = 0; i < filterlists.length; i++) {
                let list = filterlists[i];
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

    function getActivatedFilterlists(profile) {
        let activatedFilterlists = [];
        if (angular.isObject(profile)) {
            let activatedFilterlistIds = profile.internetAccessRestrictionMode === 1 ?
                profile.inaccessibleSitesPackages : profile.accessibleSitesPackages;
            for (let i = 0; i < activatedFilterlistIds.length; i++) {
                let filterlist = filterlistMap[activatedFilterlistIds[i]];
                if (angular.isDefined(filterlist)) {
                    activatedFilterlists.push(filterlist);
                }
            }
        }
        return activatedFilterlists;
    }

    function hasActivatedExceptionFilterLists(profile) {
        let activatedFilterlistIds = [];
        if (angular.isObject(profile)) {
            activatedFilterlistIds = profile.internetAccessRestrictionMode === 1 ?
                profile.accessibleSitesPackages : profile.inaccessibleSitesPackages;
        }
        return activatedFilterlistIds.length > 0;
    }

    function getActivatedExceptionFilterlists(profile) {
        let activatedFilterlists = [];
        if (angular.isObject(profile)) {
            let activatedFilterlistIds = profile.internetAccessRestrictionMode === 1 ?
                profile.accessibleSitesPackages : profile.inaccessibleSitesPackages;
            for (let i = 0; i < activatedFilterlistIds.length; i++) {
                let filterlist = filterlistMap[activatedFilterlistIds[i]];
                if (angular.isDefined(filterlist)) {
                    activatedFilterlists.push(filterlist);
                }
            }
        }
        return activatedFilterlists;
    }

    function getContingentDay(contingent) {
        return AccessContingentService.getContingentDay(contingent);
    }

    function getContingentDisplayTime(minutesFromMidnight) {
        return AccessContingentService.getContingentDisplayTime(minutesFromMidnight);
    }

    function deleteAccessContingent(profile, contingent) {
        let removed = -1;
        for (let i = 0; i < profile.internetAccessContingents.length; i++) {
            if (profile.internetAccessContingents[i].id === contingent.id) {
                removed = i;
            }
        }
        if (removed >= 0) {
            profile.internetAccessContingents.splice(removed, 1);
        }
        // renumber remaining contingents
        for (let i = 0; i < profile.internetAccessContingents.length; i++) {
            profile.internetAccessContingents[i].id = i;
        }


        if (profile.internetAccessContingents.length === 0) {
            profile.controlmodeTime = false;
        }

        // Notify eBlocker about changes
        doSaveProfile(profile);
    }

    function addAccessContingent(profile) {
        let module = {
            id: profile.internetAccessContingents.length,
            onDay: 1,
            fromMinutes: 0,
            tillMinutes: 1440,
            isNew: true
        };
        addAddAccessContingentDialog(module, profile, undefined);
    }

    function editAccessContingent(profile, contingent) {
        let module = {
            id: contingent.id,
            onDay: contingent.onDay,
            fromMinutes: contingent.fromMinutes,
            tillMinutes: contingent.tillMinutes,
            isNew: false
        };
        addAddAccessContingentDialog(module, profile, contingent);
    }

    function addAddAccessContingentDialog(module, profile, contingent) {
        $mdDialog.show({
            controller: 'EditContingentDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/parentalControl/access-contingent-edit.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false,
            locals: {
                'module': module
            }
        }).then(function(module){
            savedContingent(module, profile, contingent);
        }, function() {
            cancelledContingent(profile);
        });
    }

    function editAccessUsage(profile) {
        let usageByDay = angular.copy(profile.normalizedMaxUsageTimeByDay);
        vm.dialogOpen = true;
        $mdDialog.show({
            controller: 'EditUsageDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/parentalControl/access-usage-edit.dialog.tmpl.html',
            clickOutsideToClose:false,
            locals: {
                'usageByDay': usageByDay
            }
        }).then(function(){
            profile.normalizedMaxUsageTimeByDay = usageByDay;
            doSaveProfile(profile);
            vm.dialogOpen = false;
        }, function() {
            vm.dialogOpen = false;
        });
    }


    function cancelledContingent(profile) {
        if (profile.internetAccessContingents.length === 0) {
            profile.controlmodeTime = false;
        }
    }

    function savedContingent(module, profile, contingent) {
        // If contingent is defined, that is the version prior to editing
        let replaced = -1;
        if (angular.isDefined(contingent)) {
            for (let i = 0; i < profile.internetAccessContingents.length; i++) {
                if (profile.internetAccessContingents[i].id === contingent.id) {
                    replaced = i;
                }
            }
        }
        if (replaced >= 0) {
            profile.internetAccessContingents[replaced] = module;
        } else {
            profile.internetAccessContingents.push(module);
        }
        // Save profile to eBlocker
        doSaveProfile(profile);
    }

}
