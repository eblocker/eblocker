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
    templateUrl: 'app/components/dns/server/dns-server.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(logger, DnsService, $q, $timeout, DialogService, NotificationService,
                    TableService, ArrayUtilsService) { // jshint ignore: line
    'ngInject';
    'use strict';

    const vm = this;

    vm.newDnsEntry = newDnsEntry;
    vm.saveDnsSettings = saveDnsSettings;

    // ** START: TABLE
    vm.isCustomMode = isCustomMode;
    vm.isSelectable = isSelectable;
    vm.isDeletable = isDeletable;
    vm.bulkDelete = bulkDelete;
    vm.tableData = [];
    vm.tableCallback = {};
    vm.filteredTableData = [];
    vm.searchProps = ['server'];
    vm.tableId = TableService.getUniqueTableId('dns-server-list-table');
    vm.tableHeaderConfig = [
        {
            label: '',
            icon: '/img/icons/ic_dns_black.svg',
            isSortable: true,
            isXsColumn: true,
            showOnSmallTable: false,
            sortingKey: 'showStats'
        },
        {
            label: 'ADMINCONSOLE.DNS_SERVER.TABLE.COLUMN.ORDER',
            isSortable: true,
            flex: 15,
            defaultSorting: true,
            sortingKey: 'orderNumber'
        },
        {
            label: 'ADMINCONSOLE.DNS_SERVER.TABLE.COLUMN.SERVER',
            isSortable: true,
            sortingKey: 'server'
        },
        {
            label: 'ADMINCONSOLE.DNS_SERVER.TABLE.COLUMN.RESPONSE_TIME',
            isSortable: true,
            showOnSmallTable: false,
            sortingKey: 'responseTimeRating'
        },
        {
            label: 'ADMINCONSOLE.DNS_SERVER.TABLE.COLUMN.RELIABILITY',
            isSortable: true,
            showOnSmallTable: false,
            sortingKey: 'reliabilityRating'
        },
        {
            label: 'ADMINCONSOLE.DNS_SERVER.TABLE.COLUMN.RATING',
            isSortable: true,
            sortingKey: 'ratingSortingKey'
        },
        {
            label: '',
            flexXs: 10,
            flexGtXs: 5
        }
    ];

    function isCustomMode() {
        return vm.configuration.selectedDnsMode === 'custom';
    }

    function atLeastOneNotSelected() {
        let count = 0;
        vm.filteredTableData.forEach((entry) => {
            if (entry._checked) {
                count++;
            }
        });
        return count < vm.tableData.length - 1;
    }

    function isSelectable(value) {
        return isDeletable(value);
    }

    function isDeletable(value) {
        return isCustomMode() && (value._checked || atLeastOneNotSelected());
    }

    function bulkDelete(values) {
        values.forEach((server) => {
            const index = vm.configuration.nameServers.indexOf(server.server);
            if (index !== -1) {
                vm.configuration.nameServers.splice(index, 1);
            } else {
                logger.error('Could not find selected DNS server in config list ', server);
            }
        });

        vm.configuration.ips = vm.configuration.nameServers.join('\n');

        return DnsService.saveDnsConfiguration(vm.configuration).then(function success(configuration) {
            vm.configuration = configuration;
            loadDnsStats().then(function success() {
                updateTableData(vm.configuration.nameServers);
            });
            return {data: values.length};
        }, function error(response) {
            logger.error('Unable to update DNS config ', response);
        });
    }

    // END TABLE SERVER

    vm.$onInit = function() {
        vm.loading = true;
        vm.configuration = DnsService.getDnsConfig();
        $q.all([
            loadDnsStats()
        ]).then(function() {
            updateTableData(vm.configuration.nameServers);
            vm.templateCallback = {
                configuration: vm.configuration,
                isCustom: isCustomMode,
                edit: edit
            };
        }).finally(function notLoading() {
            vm.loading = false;
        });
    };

    function getHighestOrderNumber() {
        let num = -1;
        vm.tableData.forEach((entry) => {
            if (entry.orderNumber > num) {
                num = entry.orderNumber;
            }
        });
        return num;
    }

    function newDnsEntry(event) {
        const orderNumber = getHighestOrderNumber() + 1;
        const entry = {
            id: -1,
            orderNumber: orderNumber,
            server: ''
        };
        DialogService.dnsAddEditServer(event, entry, false).then(function success(newEntry) {
            let newTableData = angular.copy(vm.tableData);
            newTableData.push(newEntry);
            newTableData = DnsService.updateOrderNumbers(newTableData, newEntry.id, newEntry.orderNumber, orderNumber);
            saveDnsSettings(newTableData);
        });
    }

    function edit(event, entry) {
        DialogService.dnsAddEditServer(event, entry, true).then(function success(newEntry) {
            const oldOrderNumber = DnsService.getOrderNumber(vm.tableData, newEntry.id);
            let newTableData = DnsService.updateDnsServer(vm.tableData, newEntry);
            newTableData = DnsService.
            updateOrderNumbers(newTableData, newEntry.id, newEntry.orderNumber, oldOrderNumber);
            saveDnsSettings(newTableData);
        });
    }

    function saveDnsSettings(newTableData) {
        const serverList = [];
        // sort by orderNumber, so that new order of entries is saved.
        const data = ArrayUtilsService.sortByProperty(newTableData, 'orderNumber');
        data.forEach((entry) => {
            serverList.push(entry.server);
        });
        vm.configuration.customNameServers = serverList;
        vm.configuration.nameServers = serverList;
        return DnsService.saveDnsConfiguration(vm.configuration).then(function success(configuration) {
            loadDnsStats().then(function success() {
                updateTableData(configuration.nameServers);
            });
            NotificationService.info('ADMINCONSOLE.DNS_SERVER.NOTIFICATION.INFO_SAVED_DNS_CONFIGURATION');
        });
    }

    function updateTableData(servers) {
        vm.tableData.length = 0;
        if (angular.isArray(servers) && servers.length > 0) {
            servers.forEach((server, index) => {
                let entry;
                if (vm.dnsStats[server]) {
                    entry = {
                        server: server,
                        showStats: true,
                        responseTimeRating: vm.dnsStats[server].responseTimeRating,
                        responseTimeAverage: vm.dnsStats[server].responseTimeAverage,
                        responseTimeMedian: vm.dnsStats[server].responseTimeMedian,
                        responseTimeMin: vm.dnsStats[server].responseTimeMin,
                        responseTimeMax: vm.dnsStats[server].responseTimeMax,
                        reliabilityRating: vm.dnsStats[server].reliabilityRating,
                        valid: vm.dnsStats[server].valid,
                        invalid: vm.dnsStats[server].invalid,
                        timeout: vm.dnsStats[server].timeout,
                        error: vm.dnsStats[server].error,
                        rating: vm.dnsStats[server].rating
                    };
                    if (entry.rating === 'GOOD') {
                        entry.ratingSortingKey = 1;
                    } else if (entry.rating === 'MEDIUM') {
                        entry.ratingSortingKey = 2;
                    } else {
                        entry.ratingSortingKey = 3;
                    }
                } else {
                    entry = {
                        showStats: false,
                        server: server
                    };
                }
                entry.id = index; // required to edit entries, since we only have a list from server
                entry.orderNumber = index; // required to edit entries, since we only have a list from server
                vm.tableData.push(entry);
            });
            vm.filteredTableData = angular.copy(vm.tableData);
            if (angular.isFunction(vm.tableCallback.reload)) {
                $timeout(vm.tableCallback.reload, 0);
            }
        }
    }

    function loadDnsStats() {
        return DnsService.loadDnsStats(24).then(function(dnsStats) {
            vm.dnsStats = dnsStats;
        });
    }
}
