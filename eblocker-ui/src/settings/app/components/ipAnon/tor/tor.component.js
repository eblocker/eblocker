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
    templateUrl: 'app/components/ipAnon/tor/tor.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        selectedTorCountries: '<',
        torCountries: '<'
    }
};

function Controller(logger, TorService, TableService, DialogService, $q) {
    'ngInject';
    'use strict';

    const vm = this;
    vm.tableData = [];
    vm.filteredTableData = [];
    vm.searchTerm = '';
    vm.isSearchFieldEmpty = isSearchFieldEmpty;
    vm.torModeChange = torModeChange;

    function torModeChange() {
        if (vm.torMode === 'MAN') {
            // auto open dialog, at least one country required for manual mode
            editCountries(null);
        } else {
            // reset to auto mode
            vm.selectedTorCountries = [];
            updateTorExitNodes(vm.selectedTorCountries);
            setTableData(vm.selectedTorCountries);
        }
    }

    function isSearchFieldEmpty() {
        return !angular.isString(vm.searchTerm) || vm.searchTerm === '';
    }

    vm.$onInit = function() {
        setTableDataAndTorMode();
    };

    function setTableDataAndTorMode() {
        setTableData(vm.selectedTorCountries);
        setTorMode();
    }

    function setTorMode() {
        if (vm.filteredTableData.length === 0) {
            vm.torMode = 'AUTO';
        } else {
            vm.torMode = 'MAN';
        }
    }

    function setTableData(selectedTorCountries) {
        vm.tableData.length = 0;
        selectedTorCountries.forEach((code) => {
            const entry = getCountryByCode(code, vm.torCountries);
            entry._checked = false;
            vm.tableData.push(entry);
        });
        vm.filteredTableData = angular.copy(vm.tableData);
    }

    function getCountryByCode(code, countryList) {
        const ret = countryList.filter(function(el) {
            return el.code === code;
        });
        if (ret.length === 1) {
            return ret[0];
        } else {
            logger.error('Too many country matches for code ' + code + ': ', ret);
        }
    }

    vm.editCountries = editCountries;

    // ** START: TABLE
    vm.tableId = TableService.getUniqueTableId('ip-anon-tor-table');
    vm.tableHeaderConfig = [
        {
            label: '',
            isSortable: false,
            isXsColumn: true
        },
        {
            label: 'ADMINCONSOLE.TOR.TABLE.COLUMN.NAME',
            isSortable: true,
            sortingKey: 'name'
        }
    ];

    vm.isDeletable = isDeletable;
    vm.deleteSingleEntry = deleteSingleEntry;
    vm.onSingleDeleteDone = onSingleDeleteDone;

    // Filtering following props of table entry
    vm.searchProps = ['name'];

    function isDeletable(value) {
        return true;
    }

    function deleteSingleEntry(value) {
        const index = vm.selectedTorCountries.indexOf(value.code);
        if (index > -1) {
            vm.selectedTorCountries.splice(index, 1);
            return updateTorExitNodes(vm.selectedTorCountries);
        }
        return $q.reject('Unable to find element');
    }

    function onSingleDeleteDone() {
        setTableDataAndTorMode();
    }

    //apply selected tor exit node countries
    function updateTorExitNodes(selectedTorCountries) {
        return TorService.updateSelectedTorExitNodes(selectedTorCountries).then(function success(response) {
            logger.info('Successfully updated Tor exit nodes.', response);
            return $q.resolve(response);
        }, function cancel(response) {
            logger.error('Error updating Tor exit nodes ', response);
            return $q.reject(response);
        });
    }

    function editCountries(event) {
        return DialogService.editTorCountryList(event, vm.torCountries, vm.selectedTorCountries).
        then(function success(newlist) {
            vm.selectedTorCountries = newlist;
            setTableDataAndTorMode();
            return newlist;
        }).finally(function done() {
            // make sure to reset to auto, if table data is empty
            setTorMode();
        });
    }

}
