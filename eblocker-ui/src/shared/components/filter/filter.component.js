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
    templateUrl: 'components/filter/filter.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        filteredData: '=',
        originalData: '<',
        filterProperties: '<',
        searchTerm: '=',
        placeholderLabel: '@',
        iconPath: '@',
        keyUp: '&?'
    }
};

function Controller($filter, $timeout) {
    'ngInject';

    const vm = this;

    vm.$onInit = function() {
        if (angular.isUndefined(vm.searchTerm)) {
            vm.internSearchTerm = '';
        }
    };

    vm.searchUpdate = function() {
        // TODO: https://docs.angularjs.org/guide/component:
        // "The general rule should therefore be to never change an object or array property in the component scope."
        vm.filteredData = $filter('ebfilter')(vm.originalData, vm.searchTerm, vm.filterProperties);
    };

    vm.internSearchUpdate = function() {
        // TODO: https://docs.angularjs.org/guide/component:
        // "The general rule should therefore be to never change an object or array property in the component scope."


        // ** Not working: when vm.filteredData.length stays the same the table's watcher is not fired (condition
        // is not fulfilled), so that ordering is stripes are overridden by originalData.
        // vm.filteredData = $filter('ebfilter')(vm.originalData, vm.internSearchTerm, vm.filterProperties);


        // ** In the table (table.component.js) is a watcher that reacts on table-array size.
        // When the user types in a string in the search field that does not change the tables-array size,
        // the actual table data is overridden with the original table data, thus removing the stripes and the
        // sorting. So here we make sure that the table data size changes, so that the table data is updated no
        // matter what.
        vm.filteredData.length = 0;
        $timeout(applySearch, 0); // TO of zero: just wait digest cycle to finish after length reset.
    };

    function applySearch() {
        vm.filteredData = $filter('ebfilter')(vm.originalData, vm.internSearchTerm, vm.filterProperties);
    }

    vm.clear = function() {
        if (angular.isString(vm.searchTerm)) {
            vm.searchTerm = '';
            vm.searchUpdate();
        } else {
            vm.internSearchTerm = '';
            vm.internSearchUpdate();
        }
    };

    vm.inputKeyUp = function(event) {
        const ENTER = 13;
        if (angular.isFunction(vm.keyUp) && event.keyCode === ENTER) {
            // wait a little longer than debounce value, so that model is set  before callback is called.
            $timeout(keyUpCallback, 230);
        }
    };

    function keyUpCallback() {
        vm.keyUp({value: vm.searchTerm});
    }
}
