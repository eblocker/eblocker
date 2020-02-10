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
/**
 * ** required for paginator (example devices details):
 * - possibility to update details section (see setupDevice here, called from onChange)
 * - need tableData to be passed from list-state into details-state (see $stateParams.param.getTableData())
 * - detailsParams needs to be passed into table (table-details-params="vm.detailsParams")
 * - the current entry (vm.device passed into details)
 */

export default {
    templateUrl: 'app/components/table/details-paginator.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        tableData: '<',
        currentEntry: '<',
        tooltipProperty: '@',
        onChange: '&'
    }
};

function Controller() {
    'ngInject';

    const vm = this;
    vm.getNextEntry = getNextEntry;
    vm.getPreviousEntry = getPreviousEntry;
    vm.next = next;
    vm.prev = prev;
    vm.goToFirst = goToFirst;
    vm.goToLast = goToLast;

    vm.$onInit = function() {
        if (angular.isObject(vm.currentEntry) && angular.isArray(vm.tableData)) {
            setValues(vm.currentEntry);
        }
    };

    // we need to update the index, nextEntry, prevEntry values, because we do not reload the state
    // this directive maintains the states of nextEntry, prevEntry... internally.
    function setValues(currentEntry) {
        vm.currentIndex = getCurrentIndex(vm.tableData, currentEntry);
        vm.nextEntry = getNextEntry(vm.tableData, vm.currentIndex);
        vm.prevEntry = getPreviousEntry(vm.tableData, vm.currentIndex);
        vm.first = vm.tableData[0];
        vm.last = vm.tableData[vm.tableData.length - 1];
    }

    function next() {
        onNextOrPrev(vm.nextEntry);
        setValues(vm.nextEntry);
    }

    function prev() {
        onNextOrPrev(vm.prevEntry);
        setValues(vm.prevEntry);
    }

    function goToFirst() {
        onNextOrPrev(vm.first);
        setValues(vm.first);
    }

    function goToLast() {
        onNextOrPrev(vm.last);
        setValues(vm.last);
    }

    function onNextOrPrev(entry) {
        vm.onChange({entry: entry});
    }

    function getPreviousEntry(list, currentIndex) {
        if (currentIndex > 0) {
            return list[currentIndex - 1];
        }
        // prevent wrap around
        return undefined;
    }

    function getNextEntry(list, currentIndex) {
        const size = list.length - 1;
        if (currentIndex < size) {
            return list[currentIndex + 1];
        }
        // prevent wrap around
        return undefined;
    }

    function getCurrentIndex(list, entry) {
        let index = -1;
        list.forEach((item, i) => {
            if (item.id === entry.id) {
                index = i;
            }
        });
        return index;
    }
}
