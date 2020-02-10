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
    templateUrl: 'app/components/ssl/trustedApps/trustedApps.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(TrustedAppsService, $filter, $stateParams, DialogService,
                    TableService, StateService, ArrayUtilsService) {
    'ngInject';
    'use strict';

    const vm = this;
    vm.newAppDefinition = newAppDefinition;

    // ** START: TABLE
    vm.tableId = TableService.getUniqueTableId('ssl-trusted-apps-table');

    vm.tableHeaderConfig = [
        {
            label: '',
            icon: '/img/icons/ic_apps_black.svg',
            isSortable: true,
            isXsColumn: true,
            sortingKey: 'enabled',
        },
        {
            label: 'ADMINCONSOLE.TRUSTED_APPS.TABLE.COLUMN.NAME',
            isSortable: true,
            flexGtXs: 20,
            sortingKey: 'name',
        },
        {
            label: 'ADMINCONSOLE.TRUSTED_APPS.TABLE.COLUMN.DESCRIPTION',
            isSortable: true,
            sortingKey: 'localizedDescription',
        },
        {
            label: 'ADMINCONSOLE.TRUSTED_APPS.TABLE.COLUMN.DOMAINS',
            isSortable: true,
            sortingKey: 'domainsIps',
        },
        {
            label: 'ADMINCONSOLE.TRUSTED_APPS.TABLE.COLUMN.STATUS',
            isSortable: true,
            flex: 10,
            flexSm: 15,
            sortingKey: 'builtin',
            showHeader: true,
        }
    ];

    vm.templateCallback = {
            onChangeEnabled: onChangeEnabled
    };

    vm.detailsState = 'trustedappsdetails';

    vm.isSelectable = isSelectable;
    vm.isDeletable = isDeletable;
    vm.deleteSingleEntry = deleteSingleEntry;
    vm.onSingleDeleteDone = onSingleDeleteDone;

    // Filtering following props of table entry
    vm.searchProps = ['name', 'localizedDescription', 'domainsips'];

    function isSelectable(value) {
        return isDeletable(value);
    }

    function isDeletable(value) {
        // return !value.builtin && !value.hidden;
        return (!value.builtin && !value.hidden) || (value.builtin && value.modified);
    }

    function deleteSingleEntry(value) {
        if (value.builtin) {
            return TrustedAppsService.reset(value.id);
        }
        return TrustedAppsService.deleteById(value.id);
    }

    function onSingleDeleteDone() {
        getAllModules();
    }
    // ## END: TABLE


    function newAppDefinition(event) {
        const module = {
            blacklistedDomains: [],
            builtin: false,
            domainUrls: [],
            whitelistedIPs: [],
            enabledPerDefault: false,
            hidden: false,
            enabled: false,
            modified: true
        };
        DialogService.trustedAppAdd(event, module).then(function success() {
            getAllModules();// get all, updates available ??
        });
    }

    vm.$onInit = function() {
        getAllModules(true).finally(function done() {
            const id = StateService.getIdFromParam($stateParams);
            const entry = ArrayUtilsService.getItemBy(vm.filteredTableData, 'id', id);
            if (angular.isObject(entry)) {
                const params = {
                    entry: entry
                };
                StateService.goToState(vm.detailsState, params);
            }
        });
    };

    function getAllModules(reload) {
        return TrustedAppsService.getAll(reload).then(function success(response) {
            const modules = response.data;
            vm.tableData = $filter('filter')(modules, function(filter) {
                return !filter.hidden;
            });
            let i = 0;
            let updatesAvailable = false;
            while( i < vm.tableData.length && !updatesAvailable) {
                if (modules[i].updatedVersionAvailable && !modules[i].hidden) {
                    updatesAvailable = true;
                }
                ++i;
            }
            vm.updatesAvailable = updatesAvailable;
            vm.filteredTableData = angular.copy(vm.tableData);
            return response;
        });
    }

    function onChangeEnabled(entry) {
        TrustedAppsService.toggleAppModule(entry);
    }

}
