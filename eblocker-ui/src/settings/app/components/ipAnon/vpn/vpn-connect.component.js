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
    templateUrl: 'app/components/ipAnon/vpn/vpn-connect.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(VpnService, StateService, STATES, DialogService, TableService) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.newVpnProfile = newVpnProfile;

    let dialog = {
        step: 0,
        profile: {
            loginCredentials: {}
        },
        parsedOptions: null,
        isProfileNew: null,
        configurationComplete: false,
        requiredFileError: {}
    };

    // ** START: TABLE
    vm.tableId = TableService.getUniqueTableId('ip-anon-vpn-table');
    vm.detailsState = 'vpnconnectdetails';
    vm.detailsParams = {
        dialog: dialog
    };
    vm.tableHeaderConfig = [
        {
            label: '',
            icon: '/img/icons/ic_security_black_outline.svg',
            isSortable: true,
            isXsColumn: true,
            sortingKey: 'enabled'
        },
        {
            label: 'ADMINCONSOLE.VPN_CONNECT.TABLE.COLUMN.NAME',
            isSortable: true,
            flexGtXs: 35,
            sortingKey: 'name'
        },
        {
            label: 'ADMINCONSOLE.VPN_CONNECT.TABLE.COLUMN.DESCRIPTION',
            isSortable: true,
            sortingKey: 'description'
        }
   ];

    vm.isDeletable = isDeletable;
    vm.deleteSingleEntry = deleteSingleEntry;
    vm.onSingleDeleteDone = onSingleDeleteDone;

    // Filtering following props of table entry
    vm.searchProps = ['name', 'localizedDescription', 'domains', 'ips'];

    function isDeletable(value) {
        return true;
    }

    function deleteSingleEntry(value) {
        return VpnService.deleteProfile(value);
    }

    function onSingleDeleteDone() {
        getAllProfiles();
    }
    // END TABLE STUFF

    vm.$onInit = function() {
        getAllProfiles();
    };

    function getAllProfiles() {
        vm.loading = true;
        return VpnService.getProfiles().then(function(r) {
            const loadedProfiles = r.data.filter(function(profile) {
                // TODO switch this if we really want to ignore tmp profiles
                return true; //!profile.temporary;
            });
            vm.tableData = loadedProfiles;
            vm.filteredTableData = angular.copy(vm.tableData);
        }).finally(function () {
            vm.loading = false;
        });
    }

    function newVpnProfile() {
        const profile = {
            enabled: true,
            temporary: true
        };
        VpnService.createProfile(profile).then(function(response) {
            showEditDialog(true, response.data, null);
        });
    }

    function showEditDialog(isProfileNew, profile, parsedOptions) {
        DialogService.vpnConnectionEdit(dialog, isProfileNew, profile, parsedOptions).then(function success() {
            getAllProfiles();
        }, function cancel(profile) {
            if (profile.temporary) {
                VpnService.deleteProfile(profile);
            }
        });
    }
}
