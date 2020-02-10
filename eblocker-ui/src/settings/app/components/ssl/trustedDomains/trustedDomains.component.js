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
    templateUrl: 'app/components/ssl/trustedDomains/trustedDomains.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(TrustedDomainsService, StateService, TableService, DialogService) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.newDomain = newDomain;

    function newDomain(event) {
        const module = {
            name: '',
            url: ''
        };
        DialogService.trustedDomainAddEdit(event, module).then(function success() {
            TrustedDomainsService.invalidateCache();
            getEnabledDomains();
        }, function cancel() {
            // cancel
        });
    }


    vm.$onInit = function() {
        getEnabledDomains(true);
    };

    function getEnabledDomains(reload) {
        TrustedDomainsService.getEnabledDomains(reload).then(function success(response) {
            vm.tableData = [];
            const enabledAppModules = response.data;
            //parse appWhitelistModule information to whitelistUrl information
            enabledAppModules.forEach(function (enabledModule) {
                //add to whitelistUrl-array to show them in ssl exemption list
                enabledModule.whitelistedDomainsIps.forEach(function (whitelistedDomainIp) {
                    let name;
                    if (angular.isDefined(enabledModule.labels) &&
                        angular.isDefined(enabledModule.labels[whitelistedDomainIp])) {
                        name = enabledModule.labels[whitelistedDomainIp];
                    } else {
                        name = enabledModule.name;
                    }
                    let trustedAppName;
                    if (enabledModule.hidden) {
                        trustedAppName = undefined;
                    } else {
                        trustedAppName = enabledModule.name;
                    }

                    let isSingleEntriesModule = TrustedDomainsService.isSingleEntriesModule(enabledModule);

                    vm.tableData.push({
                        name: name,
                        url: whitelistedDomainIp,
                        trustedAppName: trustedAppName,
                        deletable: isSingleEntriesModule,
                        enabled: enabledModule.enabled,
                        statusSort: enabledModule.builtin && !isSingleEntriesModule ? 1 : 0,
                        builtin: enabledModule.builtin && // manually change to not-builtin if user-defined
                                 ! isSingleEntriesModule
                    });
                });
            });
            vm.filteredTableData.length = 0;
            vm.tableData.forEach((e) => {
                vm.filteredTableData.push(e);
            });
        });
    }

    // ** START: TABLE
    vm.filteredTableData = [];
    vm.tableId = TableService.getUniqueTableId('ssl-trusted-domains-table');
    vm.tableHeaderConfig = [
        {
            label: '',
            icon: '/img/icons/ic_web_asset_black.svg',
            isSortable: true,
            isXsColumn: true,
            sortingKey: 'deletable'
        },
        {
            label: 'ADMINCONSOLE.TRUSTED_DOMAINS.TABLE.COLUMN.NAME',
            isSortable: true,
            showOnSmallTable: false,
            sortingKey: 'name'
        },
        {
            label: 'ADMINCONSOLE.TRUSTED_DOMAINS.TABLE.COLUMN.DOMAIN',
            isSortable: true,
            sortingKey: 'url'
        },
        {
            label: 'ADMINCONSOLE.TRUSTED_DOMAINS.TABLE.COLUMN.APP',
            isSortable: true,
            showOnSmallTable: false,
            sortingKey: 'trustedAppName'
        },
        {
            label: 'ADMINCONSOLE.TRUSTED_DOMAINS.TABLE.COLUMN.STATUS',
            flexXs: 20,
            flex: 10,
            flexSm: 15,
            isSortable: true,
            sortingKey: 'statusSort'
        }
    ];

    vm.isSelectable = isSelectable;
    vm.isDeletable = isDeletable;
    vm.deleteSingleEntry = deleteSingleEntry;
    vm.bulkDelete = bulkDelete;
    vm.getEnabledDomains = getEnabledDomains;

    // Filtering following props of table entry
    vm.searchProps = ['name', 'url', 'trustedAppName'];

    function isSelectable(value) {
        return isDeletable(value);
    }

    function isDeletable(value) {
        return value.deletable;
    }

    function deleteSingleEntry(value) {
        if (!value.builtin) {
            return TrustedDomainsService.deleteDomain(value);
        }
    }

    function bulkDelete(values) {
        const param = [];
        // clean all unknown properties (like '_checked')
        values.forEach((value) => {
            param.push({
                name: value.name,
                url: value.url
            });
        });
        return TrustedDomainsService.deleteAllDomains(param);
    }

    // ## END: TABLE
}
