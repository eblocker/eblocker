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
    templateUrl: 'app/components/parentalControl/userProfiles/user-profile-details.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        users: '<',
        profiles: '<',
        devices: '<',
        dnsEnabled: '<',
        sslEnabled: '<'
    }
};

function Controller(logger, UserProfileService, $interval, $mdDialog, // jshint ignore: line
                    FilterService, AccessContingentService, StateService, STATES,
                    ArrayUtilsService, $stateParams, DialogService, UserService) { // jshint ignore: line
    'ngInject';
    'use strict';

    const vm = this;

    vm.loading = false;

    vm.descMaxLength = 150;

    vm.updates = [];

    vm.showSslDnsWarningMessage = false;

    vm.editProfileName = editProfileName;
    vm.editProfileDescription = editProfileDescription;

    vm.goToUsers = goToUsers;

    vm.onChangeUrlRestrictions = onChangeUrlRestrictions;
    vm.editAccessRestrictions = editAccessRestrictions;

    vm.onChangeTimeRestrictions = onChangeTimeRestrictions;
    vm.editAccessContingent = editAccessContingent;
    vm.deleteAccessContingent = deleteAccessContingent;
    vm.addAccessContingent = addAccessContingent;

    vm.onChangeUsageRestrictions = onChangeUsageRestrictions;
    vm.editAccessUsage = editAccessUsage;

    vm.getActivatedFilterlists = getActivatedFilterlists;
    vm.hasActivatedExceptionFilterLists = hasActivatedExceptionFilterLists;
    vm.getActivatedExceptionFilterlists = getActivatedExceptionFilterlists;
    vm.isProfileBeingUpdated = isProfileBeingUpdated;

    vm.getContingentDay = getContingentDay;
    vm.getContingentDisplayTime = getContingentDisplayTime;

    let checkUpdateStatus;

    let blacklists = [];
    let whitelists = [];
    let filterlistMap = {};

    vm.backState = STATES.USER_PROFILES;
    vm.stateParams = $stateParams;

    vm.$onInit = function() {
        if (angular.isObject($stateParams.param) &&
            angular.isObject($stateParams.param.entry)) {
            vm.profile = $stateParams.param.entry;
        }

        if (!angular.isObject(vm.profile)) {
            StateService.goToState(vm.backState);
            return;
        }
        vm.profileName = {
            value: vm.profile.name
        };
        vm.profileDescription = {
            value: vm.profile.description
        };

        vm.profile.internetAccessContingents = ArrayUtilsService.
        sortByProperty(vm.profile.internetAccessContingents, 'onDay');

        setup();
        loadAllFilterLists();
        startCheckUpdateStatus();
        // vm.showSslDnsWarningMessage = !vm.dnsEnabled && !vm.sslEnabled;
    };

    function goToUsers() {
        StateService.goToState(STATES.USERS);
    }

    function editProfileDescription(event, entry) {
        if (!entry.builtin) {

            const msgKeys = {
                title: 'ADMINCONSOLE.DIALOG.EDIT_PROFILE_DESCRIPTION.TITLE',
                label: 'ADMINCONSOLE.DIALOG.EDIT_PROFILE_DESCRIPTION.LABEL'
            };
            const subject = {value: entry.description};

            // event, subject, msgKeys, okAction, isUnique
            DialogService.
            openEditDialog(event, subject, msgKeys , editProfileDescriptionAction, null, 150, 0).
            then(function success(subject) {
                // ** we need to update the label's value as well
                vm.profileDescription.value = subject.value;
            });
        }
    }

    function editProfileDescriptionAction(value) {
        vm.profile.description = value;
        return doSaveProfile(vm.profile);
    }

    function editProfileName(event, entry) {
        if (!entry.builtin) {

            const msgKeys = {
                title: 'ADMINCONSOLE.DIALOG.EDIT_PROFILE_NAME.TITLE',
                label: 'ADMINCONSOLE.DIALOG.EDIT_PROFILE_NAME.LABEL'
            };
            const subject = {value: entry.name};

            // event, subject, msgKeys, okAction, isUnique
            DialogService.
            openEditDialog(event, subject, msgKeys , editProfileNameAction, editUserNameIsUnique, 50, 1).
            then(function success(subject) {
                // ** we need to update the label's value as well
                vm.profileName.value = subject.value;
                UserService.invalidateCache();
            });
        }
    }

    function editUserNameIsUnique(name, id) {
        return UserProfileService.uniqueName(name, id);
    }

    function editProfileNameAction(name) {
        vm.profile.name = name;
        return doSaveProfile(vm.profile);
    }

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
        for (let i = 0; i < vm.profiles.length; i++) {
            let profile = vm.profiles[i];
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
        for(let i = 0; i < vm.profiles.length; ++i) {
            updateSslWarning(vm.profiles[i]);
        }
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
            updateSslWarning(profile);
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

    function isElementInList(idValue, list){
        for (let i = 0; i < list.length; i++){
            let obj = list[i];
            if (obj === idValue) {
                return true;
            }
        }
        return false;
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
            blacklisted.active = isElementInList(blacklisted.id, profile.inaccessibleSitesPackages);
            module.blacklisted.push(blacklisted);
        }
        // Copy whitelist into module
        for (let i = 0; i < whitelists.length; i++){
            let whitelisted = whitelists[i];
            whitelisted.active = isElementInList(whitelisted.id, profile.accessibleSitesPackages);
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

    /*
     * Add and edit access contingents
     */
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

    function doSaveProfile(profile) {
        profile.internetAccessContingents = ArrayUtilsService.
        sortByProperty(profile.internetAccessContingents, 'onDay');
        return UserProfileService.saveProfile(profile);
    }

    function cancelledContingent(profile) {
        if (profile.internetAccessContingents.length === 0) {
            profile.controlmodeTime = false;
        }
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

    function hasActivatedExceptionFilterLists(profile) {
        let activatedFilterlistIds = profile.internetAccessRestrictionMode === 1 ?
            profile.accessibleSitesPackages : profile.inaccessibleSitesPackages;
        return activatedFilterlistIds.length > 0;
    }

    function getActivatedExceptionFilterlists(profile) {
        let activatedFilterlists = [];
        let activatedFilterlistIds = profile.internetAccessRestrictionMode === 1 ?
            profile.accessibleSitesPackages : profile.inaccessibleSitesPackages;
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
