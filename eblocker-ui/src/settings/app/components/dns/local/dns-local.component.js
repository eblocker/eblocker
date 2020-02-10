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
    templateUrl: 'app/components/dns/local/dns-local.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        featureToggleIp6: '<'
    }
};

function Controller(logger, DnsService, StateService, STATES, $q, DialogService, NotificationService, // jshint ignore: line
                    TableService) { // jshint ignore: line
    'ngInject';
    'use strict';

    const vm = this;
    vm.newLocalDnsEntry = newLocalDnsEntry;

    // ** START: TABLE
    vm.isSelectable = isSelectable;
    vm.isDeletable = isDeletable;
    vm.bulkDelete = bulkDelete;
    vm.tableData = [];
    vm.filteredTableData = [];
    vm.searchProps = ['name', 'ipAddress', 'ip6Address'];
    vm.templateCallback = {
        edit: edit,
        isIp6FeatureEnabled: isIp6FeatureEnabled
    };
    vm.tableId = TableService.getUniqueTableId('dns-local-network-table');
    vm.tableHeaderConfig = [
        {
            label: '',
            isSortable: true,
            isXsColumn: true,
            sortingKey: 'enabled'
        },
        {
            label: 'ADMINCONSOLE.DNS_LOCAL.TABLE.COLUMN.NAME',
            isSortable: true,
            sortingKey: 'name'
        },
        {
            label: 'ADMINCONSOLE.DNS_LOCAL.TABLE.COLUMN.IP4',
            isSortable: true,
            sortingKey: 'ipAddress'
        },
        {
            label: 'ADMINCONSOLE.DNS_LOCAL.TABLE.COLUMN.IP6',
            isSortable: true,
            sortingKey: 'ip6Address',
            showHeader: isIp6FeatureEnabled
        },
        {
            label: '',
            flex: 5,
            flexXs: 10
        }
    ];

    function isSelectable(value) {
        return isDeletable(value);
    }

    function isDeletable(value) {
        return !value.builtin;
    }

    function bulkDelete(values) {
        values.forEach((entry) => {
            if (!entry.builtin) {
                delete vm.records[entry.name];
            }
        });

        return DnsService.saveDnsRecords(vm.records).then(function(records) {
            setRecordsAndUpdateTable(records);
            return {data: values.length};
        });
    }
    // END TABLE SERVER


    vm.$onInit = function() {

        vm.configuration = {
            dnsModeListStrategy: 'default'
        };
        vm.loading = true;
        $q.all([
            loadDnsRecords()
        ]).finally(function notLoading() {
            vm.loading = false;
        });

    };

    function isIp6FeatureEnabled() {
        return angular.isDefined(vm.featureToggleIp6) ? vm.featureToggleIp6 : false;
    }

    function newLocalDnsEntry(event) {
        DialogService.dnsAddEditRecord({}, isRecordNotUnique, saveDnsRecord, vm.featureToggleIp6, vm.event);
    }

    function edit(event, localEntry) {
        if (localEntry.builtin) {
            return;
        }
        DialogService.dnsAddEditRecord(localEntry, isRecordNotUnique, saveDnsRecord,  vm.featureToggleIp6, event);
    }

    function isRecordNotUnique (name) {
        return (name in vm.records);
    }

    function saveDnsRecord(working, record) {
        vm.records[working.name] = working;
        if (angular.isDefined(record.name) && working.name !== record.name) {
            delete vm.records[record.name];
        }
        return DnsService.saveDnsRecords(vm.records).then(function(records) {
            setRecordsAndUpdateTable(records);
            return records;
        });
    }

    function loadDnsRecords() {
        return DnsService.loadDnsRecords().then(function(records) {
            setRecordsAndUpdateTable(records);
            return records;
        });
    }

    function setRecordsAndUpdateTable(records) {
        vm.records = records;
        updateLocalTableData(records);
    }

    function updateLocalTableData(records) {
        vm.tableData.length = 0;
        if (angular.isObject(records)) {
            for (const record in records) {
                if (records.hasOwnProperty(record)) {
                    vm.tableData.push(records[record]);
                }
            }
            vm.filteredTableData = angular.copy(vm.tableData);
            vm.hasBeenFlushed = false; // allow to flush again
        }
    }
}
