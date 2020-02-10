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
    templateUrl: 'app/components/ipAnon/vpn/vpn-connect-details.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

// jshint ignore: line
function Controller(logger, $interval, $q, StateService, STATES, ArrayUtilsService, $stateParams,
                    DialogService, VpnService) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.testConnection = testConnection;
    vm.editProfile = editProfile;
    vm.editable = editable;
    vm.editName = editName;
    vm.editDescription = editDescription;
    vm.editUserName = editUserName;
    vm.editPassword = editPassword;
    vm.editKeepAlivePingTarget = editKeepAlivePingTarget;
    vm.connected = true;

    vm.backState = STATES.VPN_CONNECT;
    vm.stateParams = $stateParams;

    vm.$onInit = function() {
        if (angular.isObject($stateParams.param) &&
            angular.isObject($stateParams.param.entry)) {
            vm.profile = $stateParams.param.entry;
            vm.dialog = $stateParams.param.dialog;
        }

        if (!angular.isObject(vm.profile)) {
            StateService.goToState(STATES.VPN_CONNECT);
            return;
        }
        updateDisplayData(vm.profile);
        startVpnStatusInterval();
    };

    vm.$onDestroy = function() {
        stopVpnStatusInterval();
    };

    function updateDisplayData(profile) {
        vm.profileName = {
            value: profile.name
        };

        vm.profileDescription = {
            value: profile.description
        };

        vm.profileUsername = {
            value: profile.loginCredentials.username
        };

        vm.profilePasswordSet = {
            value: profile.loginCredentials.password
        };

        vm.profileConfigVersion = {
            value: profile.configurationFileVersion
        };

        vm.keepAlivePingTarget = {
            value: profile.keepAlivePingTarget
        };


        vm.enabled = profile.enabled;
        vm.nameServersEnabled = profile.nameServersEnabled;
    }

    function editable(entry) {
        return true;
    }

    function editPassword(event, entry) {
        if (editable(entry)) {

            const msgKeys = {
                label: 'ADMINCONSOLE.VPN_CONNECT.DETAILS.LABEL_PASSWORD' // XXX
            };
            // Remove password; if password is edited, user needs to enter entire password -- it is masked anyway,
            // so editing is not really possible.
            const subject = {value: '', id: entry.id}; // XXX

            // event, subject, msgKeys, okAction, isUnique
            DialogService.
            openEditDialog(event, subject, msgKeys , editPasswordActionOk, undefined, undefined, undefined, true).
            then(function success(subject) {
                // Mask password
                vm.profile.loginCredentials.password = subject.value.replace(/./g,'*');
                // ** we need to update the label's value as well
                updateDisplayData(vm.profile);
            });
        }
    }

    function editPasswordActionOk(password) {
        vm.profile.loginCredentials.password = password; // XXX
        return VpnService.updateProfile(vm.profile);
    }

    function editUserName(event, entry) {
        if (editable(entry)) {

            const msgKeys = {
                label: 'ADMINCONSOLE.VPN_CONNECT.DETAILS.LABEL_USERNAME' // XXX
            };
            const subject = {value: entry.loginCredentials.username, id: entry.id}; // XXX

            // event, subject, msgKeys, okAction, isUnique
            DialogService.
            openEditDialog(event, subject, msgKeys , editUserNameActionOk, undefined, undefined, undefined). // XXX
            then(function success(subject) {
                // ** we need to update the label's value as well
                updateDisplayData(vm.profile);
            });
        }
    }

    function editUserNameActionOk(username) {
        vm.profile.loginCredentials.username = username; // XXX
        return VpnService.updateProfile(vm.profile);
    }

    function editDescription(event, entry) {
        if (editable(entry)) {

            const msgKeys = {
                label: 'ADMINCONSOLE.VPN_CONNECT.DETAILS.LABEL_DESCRIPTION' // XXX
            };
            const subject = {value: entry.description, id: entry.id}; // XXX

            // event, subject, msgKeys, okAction, isUnique
            DialogService.
            openEditDialog(event, subject, msgKeys , editDescriptionActionOk, undefined, undefined, undefined, false).
            then(function success(subject) {
                // ** we need to update the label's value as well
                updateDisplayData(vm.profile);
            });
        }
    }

    function editDescriptionActionOk(description) {
        vm.profile.description = description; // XXX
        return VpnService.updateProfile(vm.profile);
    }

    function editName(event, entry) {
        if (editable(entry)) {

            const msgKeys = {
                label: 'ADMINCONSOLE.VPN_CONNECT.DETAILS.LABEL_NAME'
            };
            const subject = {value: entry.name, id: entry.id};

            // event, subject, msgKeys, okAction, isUnique
            DialogService.
            openEditDialog(event, subject, msgKeys , editNameActionOk, undefined, undefined, 1).
            then(function success(subject) {
                // ** we need to update the label's value as well
                updateDisplayData(vm.profile);
            });
        }
    }

    function editNameActionOk(name) {
        vm.profile.name = name;
        return VpnService.updateProfile(vm.profile);
    }

    function editKeepAlivePingTarget(event, entry) {
        if (editable(entry)) {

            const msgKeys = {
                label: 'ADMINCONSOLE.VPN_CONNECT.DETAILS.KEEP_ALIVE.TARGET'
            };
            const subject = {value: entry.keepAlivePingTarget, id: entry.id};

            // event, subject, msgKeys, okAction, isUnique
            DialogService.
            openEditDialog(event, subject, msgKeys , editKeepAlivePingTargetOk, undefined, undefined, 1).
            then(function success(subject) {
                // ** we need to update the label's value as well
                updateDisplayData(vm.profile);
            });
        }
    }

    function editKeepAlivePingTargetOk(target) {
        vm.profile.keepAlivePingTarget = target;
        return VpnService.updateProfile(vm.profile);
    }

    vm.updateKeepAliveMode = function() {
        VpnService.updateProfile(vm.profile).then(function(response) {
            vm.keepAlivePingTarget = {
                value: response.data.keepAlivePingTarget
            };
        });
    };

    function testConnection(profile) {
        // interrupt status interval while connection test is running
        stopVpnStatusInterval();
        DialogService.vpnTestConnection(profile).finally(startVpnStatusInterval);
    }

    let statusInterval;
    function startVpnStatusInterval() {
        if (angular.isUndefined(statusInterval)) {
            getVpnStatus();
            statusInterval = $interval(getVpnStatus, 2000);
        }
    }

    function stopVpnStatusInterval() {
        if (angular.isDefined(statusInterval)) {
            $interval.cancel(statusInterval);
            statusInterval = undefined;
        }
    }

    function getVpnStatus() {
        VpnService.getVpnStatus(vm.profile).then(function success(response) {
            vm.connected = response.data.active;
        });
    }

    function editProfile(profile) {
        $q.all([
            VpnService.getProfile(profile),
            VpnService.getProfileConfig(profile)
        ]).then(function(responses) {
            // isProfileNew, profile, parsedOptions
            DialogService.vpnConnectionEdit(vm.dialog, false, responses[0].data, responses[1].data).
            then(function success(profile) {
                updateDisplayData(profile);
                vm.profile = profile;
            });
        });
    }

    vm.updateProfile = updateProfile;
    function updateProfile() {
        return VpnService.updateProfile(vm.profile);
    }
}
