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
 * Header definition example:
 * {
 *     label: '',               // translation key
 *     isSortable: true,        // when sortable: show cursor pointer and change sort key on click and show arrow.
 *     sortingKey: 'enabled',   // name of sorting property of objects within array
 *     isReversed: false,       // default reversed order
 *     showHeader: true,        // condition when to show header, e.g. showHeader: !isFamily
 *     defaultSorting: true,    // sort by this header by default
 *     tooltip: '',             // translation key for tooltip
 * }
 *
 * ALL PROPETIES FOR HEADER DEFINITION:
 *  icon: '../rate.svg'      // allows to set icon instead of label in header
 *  label: '',               // translation key
 *  isSortable: true,        // when sortable: show cursor pointer and change sort key on click and show arrow.
 *  sortingKey: 'enabled',   // name of sorting property of objects within array
 *  isReversed: false,       // default reversed order
 *  showHeader: true,        // condition when to show header, e.g. showHeader: !isFamily
 *  defaultSorting: true,    // sort by this header by default
 *  secondSorting: true,     // allows sorting by second prop. if equal by defaultSorting (or auto selected sortingKey)
 *  flex: 20,                // allows to set flex attribute (MUST be the same as in row-template!)
 *  tooltip: '',             // translation key for tooltip
 *  isXsColumn: true,        // pre-defined small column (50px, MUST be also set in row-template!)
 *
 * templateCallback:
 * We need to access our components controller sometimes. Since the row-templates / <td> definition template
 * is within the table's scope, we can use templateCallback to pass functions and properties into the
 * table's scope. These functions are then called from within the template.
 */

export default {
    templateUrl: 'app/components/table/table.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        tableData: '=',             // ** Array, data to be displayed in table, 2-way-data-binding for selection
        tableHeader: '<',           // ** Definition of headers: description see above
        tableTemplate: '@',         // ** URL to template with <td></td> definitions
        tableId: '<',               // ** For num-of-visible items. Other directive needs to identify *this* table
                                    // using the TableService.
        templateCallback: '<',      // ** Callbacks, special properties used in template (since we are in table scope)
        tableEditCallback: '&?',    // ** Callback fn e.g. to open edit dialog instead of details page (for blocker)
        tableDetailsStateName: '@', // ** State name of table details, e.g. 'devicesdetails'
        tableDetailsParams: '<',    // ** Params for details state.
        tableEmptyMsg: '<',         // ** Custom empty message (OPTIONAL)
        editMode: '<',              // ** If table is in edit mode; rows are selectable
        isEntrySelectable: '&?',    // ** Callback to check if an entry can be selected
        noSelection: '<',           // ** Prevents the row hover effect; defaults to false
        tableCallback: '<',          // ** Object that is filled with control-functions by and for the table
                                    // This is just until the concept of data-sharing between directives is done.
        smallTableHeaderLimit: '<'
    }
};

function Controller(logger, $scope, $window, $translate, StateService, TableService, ArrayUtilsService, // jshint ignore: line
                    WindowEventService, $interval) {
    'ngInject';

    const vm = this;

    const DEFAULT_XS_HEADER_LIMIT = 3;

    vm.checkAll = checkAll;
    vm.checkEntry = checkEntry;
    vm.someNotAllEntriesChecked = someNotAllEntriesChecked;
    vm.allEntriesChecked = allEntriesChecked;

    vm.changeOrder = changeOrder;
    vm.openDetails = openDetails;
    vm.isReducedWidth = isReducedWidth;

    /**
     * By default the small table only has DEFAULT_XS_HEADER_LIMIT columns. The xx-table.template.html must therefore
     * make sure that columns > DEFAULT_XS_HEADER_LIMIT are hidden (hide-xs). The table property 'smallTableHeaderLimit'
     * can be used to increase this number. Also, to skip some columns and show columns that are defined at the end
     * of the table, in the column definition each column object can have a property 'showOnSmallTable' set to false,
     * to hide that specific column. Then the table-template needs to hide that column (hide-xs) as well.
     * When skipping column the header limit (attribute 'small-table-header-limit' on table directive in HTML) must
     * be set to entire table. TODO simplify: this has grown over time and became way too complicated...
     */
    vm.showHeaderOnCurrentSizedTable = showHeaderOnCurrentSizedTable;
    vm.getHeaderLimit = getHeaderLimit;

    vm.isSmallTable = isSmallTable;

    function isSmallTable() {
        return $window.innerWidth <= 600;
    }


    // ** Check / Uncheck entries

    vm.someChecked = false;

    function checkAll(list) {
        let nextOverallState = noEntriesChecked(list);
        setAllEntries(list, nextOverallState);
    }

    function setAllEntries(list, bool) {
        list.forEach((entry) => {
            if (!bool || getEntrySelectionState(entry)) {
                entry._checked = bool;
            }
        });
    }

    function getUncheckedEntries(list) {
        return angular.isArray(list) ? list.filter(function(e) {
            return !e._checked;
        }) : [];
    }

    function someNotAllEntriesChecked(list) {
        const ret = getUncheckedEntries(list);
        return ret.length > 0 && ret.length < list.length;
    }

    function allEntriesChecked(list) {
        const ret = getUncheckedEntries(list);
        return ret.length === 0 && list.length > 0;
    }

    function noEntriesChecked(list) {
        const ret = getUncheckedEntries(list);
        return ret.length === list.length;
    }


    function checkEntry(entry) {
        if (getEntrySelectionState(entry)) {
            entry._checked = !entry._checked;
        }
    }

    function getEntrySelectionState(entry) {
        return vm.editMode && (!angular.isFunction(vm.isEntrySelectable) ||
            angular.isFunction(vm.isEntrySelectable) && vm.isEntrySelectable({value: entry}));
    }


    // **

    function changeOrder(key) {
        const doReverse = key === vm.orderKey;
        if (doReverse) {
            vm.reverseOrder = !vm.reverseOrder;
        } else {
            vm.oldOrderKey = vm.orderKey;
            vm.orderKey = key;
        }
        vm.tableData = updateTable(doReverse, vm.oldOrderKey, vm.tableData);
    }

    function updateTable(reverse, oldOrderKey, list) {
        let ret = list;
        if (angular.isArray(ret)) {

            ret = sortTableData(ret, reverse, oldOrderKey);

            setStripedTable(ret);

            // ** need to fire resize, so that angular-material renders the table correctly.
            // Otherwise only first couple of entries are visible (height is set correctly)

            WindowEventService.fireEvent('resize');
            $interval(function () {
                // On Safari another resize event is necessary
                WindowEventService.fireEvent('resize');
            }, 300);
        }
        return ret;
    }

    function sortTableData(list, reverse, oldOrderKey) {
        let ret = angular.copy(list);
        if (!reverse) {
            const fallBackOrder = angular.isString(oldOrderKey) ? oldOrderKey : vm.orderKey;
            ret = ArrayUtilsService.sortByPropertyWithFallback(ret, vm.orderKey, fallBackOrder);
        }

        if (reverse || vm.reverseOrder) {
            // Issue: if all properties by which the array is sorted are the same the
            // sorting function does not change the array. If array was reversed and
            // is then sorted (props are the same), the reversed array is returned.
            // So in the UI it appears as if the array is only reversed every second click --
            // when vm.reverseOrder is set to true.
            // Solution: We reverse the array if 1) the same column has been clicked again
            // (reverse variable) or 2) if the reverse flag is still set to true (vm.reverseOrder)
            ret.reverse();
        }
        return ret;
    }

    function openDetails(entry) {
        const params = {
            entry: entry
        };
        if (angular.isDefined(vm.tableDetailsParams)) {
            for (const param in vm.tableDetailsParams) {
                if (vm.tableDetailsParams.hasOwnProperty(param)) {
                    params[param] = vm.tableDetailsParams[param];
                }
            }
        }
        // allow to open dialog and to open details state
        if (angular.isFunction(vm.tableEditCallback)) {
            vm.tableEditCallback({value: entry});
        } else {
            StateService.goToState(vm.tableDetailsStateName, params);
        }
    }

    // ** Must be a function, so that angular-material notices the change on resize (workaround, see below)
    // once the number of visible items changes.
    vm.getTableStyle = function() {
        return vm.isSmallTable() ? vm.smallTableStyle || {} : vm.largeTableStyle || {};
    };

    vm.getEntryStyle = function() {

        return vm.isSmallTable() ? vm.smallEntryStyle : vm.largeEntryStyle;
    };

    vm.getTableRowHeight = function() {
        return vm.isSmallTable() ? vm.smallTableRowHeight : vm.largeTableRowHeight;
    };

    function getHeaderLimit() {
        return vm.isSmallTable() ? vm.smallTableHeaderLimit || DEFAULT_XS_HEADER_LIMIT : vm.tableHeader.length;
    }


    vm.$doCheck = function() {
        let largeTableNumVisible = TableService.tableNumVisibleItems(vm.tableId);
        let smallTableNumVisible = TableService.tableNumVisibleItems(vm.tableId);

        setLargeTableHeight(largeTableNumVisible);
        setSmallTableHeight(smallTableNumVisible);
        fixTableSize(largeTableNumVisible, smallTableNumVisible);
    };

    function fixTableSize(largeTableNumVisible, smallTableNumVisible) {
        if (vm.oldLargeVisibleItem !== largeTableNumVisible || vm.oldSmallVisibleItem !== smallTableNumVisible) {
            // ** Force scrollable/virtual table to re-render.
            // Workaround for issue with angular-material: changing visible-items-per-page did change the
            // height of the scrollable container, but angular-material did not correctly update the number
            // of rendered items. Not sure what causes this bug. Resize event forces re-render and fixes the issue.
            WindowEventService.fireEvent('resize');
            $interval(function () {
                // On Safari another resize event is necessary
                WindowEventService.fireEvent('resize');
            }, 300);

            vm.oldLargeVisibleItem = largeTableNumVisible;
            vm.oldSmallVisibleItem = smallTableNumVisible;
        }
    }

    function setSmallTableHeight(smallTableNumVisible) {
        // in case array is not yet defined
        if (!angular.isArray(vm.tableData) || vm.tableData.length <= 0 ) {
            vm.smallTableStyle = {
                height: '0px'
            };
            return;
        }

        vm.smallTableRowHeight = TableService.getSmallTableRowHeight();

        if (smallTableNumVisible === 'ALL' ||
            (angular.isArray(vm.tableData) && smallTableNumVisible > vm.tableData.length)) {
            // Either we want to show all entries or there are less entries in tableData then we want to show,
            // so that we need to reduce the visible area to avoid showing white space.
            smallTableNumVisible = vm.tableData.length;
        }

        // ** calculate height of small entry
        vm.smallTableStyle = {
            height: getTableHeight(vm.smallTableRowHeight, smallTableNumVisible),
            maxHeight: getTableHeight(vm.smallTableRowHeight, smallTableNumVisible)
        };
    }

    function setLargeTableHeight(largeTableNumVisible) {
        // in case array is not yet defined
        if (!angular.isArray(vm.tableData) || vm.tableData.length <= 0 ) {
            vm.largeTableStyle = {
                height: '0px'
            };
            return;
        }

        vm.largeTableRowHeight = TableService.getLargeTableRowHeight();

        if (largeTableNumVisible === 'ALL' ||
            (angular.isArray(vm.tableData) && largeTableNumVisible > vm.tableData.length)) {
            // Either we want to show all entries or there are less entries in tableData than we want to show,
            // so that we need to reduce the visible area to avoid showing white space.
            largeTableNumVisible = vm.tableData.length;
        }

        // ** Set height of scrollable container based on numOfVisible entries (set in other component)
        // and size of each row.
        vm.largeTableStyle = {
            height: getTableHeight(vm.largeTableRowHeight, largeTableNumVisible),
            maxHeight: getTableHeight(vm.largeTableRowHeight, largeTableNumVisible)
        };
    }

    function getTableHeight(rowHeight, numVisible) {
        // numVisible + 1 to account for header, which counts a one entry, if header scrolls with table
        // + 1 pixel to account for ... ?
        // + 2 pixel to account for 2px padding (to move sorting arrow up, away from bottom border in small tables)
        return (rowHeight * (numVisible + 1) + 1 + 2) + 'px';
    }

    vm.$onInit = function() {

        initLargeTableStyling();
        initSmallTableStyling();

        if (angular.isObject(vm.tableCallback)) {
            // Object passed from controller and initialized with table-control functions here. This allows to reload
            // the table from the calling controller.
            vm.tableCallback.reload = function () {
                vm.tableData = updateTable(vm.reverseOrder, vm.oldOrderKey, vm.tableData);
            };
        }

        vm.hasDetails = angular.isString(vm.tableDetailsStateName) || angular.isFunction(vm.tableEditCallback);

        // set default function for eb-check-entry component (see table template)
        if (!angular.isFunction(vm.isEntrySelectable)) {
            vm.isEntrySelectable = function() { return true; };
        }

        // ** Find default sorting
        vm.tableHeader.forEach((header, index) => {
            if (index === 1 && angular.isUndefined(vm.orderKey)) {
                // defaults to second header, which is usually the one after the icon.
                vm.orderKey = header.sortingKey;
                vm.reverseOrder = angular.isDefined(header.isReversed) ? header.isReversed : false;
            }
            if (header.defaultSorting) {
                vm.orderKey = header.sortingKey;
                vm.reverseOrder = angular.isDefined(header.isReversed) ? header.isReversed : false;
            }
            if (header.secondSorting) {
                vm.oldOrderKey = header.sortingKey;
            }
        });

        // ** Set table's initial sorting.
        // We need to do sorting manually, so that we can reset the striped flags.
        // Same row index should have same background color (stripes)
        vm.otw = $scope.$watch(function() {
            return angular.isArray(vm.tableData) ? vm.tableData.length : -1;
        }, function(newValue, oldValue) {
            if (newValue > 0 || oldValue > 0) {
                if (vm.reverseOrder) {
                    // XXX quickfix: otherwise we cannot correctly display initial reverse order with updateTable
                    // array is not sorted due to condition (!reversed), which in turn is required to sort table
                    // correctly in other cases. So here we sort initially and then continue with workflow.
                    const fallBackOrder = angular.isString(vm.oldOrderKey) ? vm.oldOrderKey : vm.orderKey;
                    vm.tableData = ArrayUtilsService.sortByPropertyWithFallback(vm.tableData, vm.orderKey, fallBackOrder);  // jshint ignore: line
                }
                vm.tableData = updateTable(vm.reverseOrder, vm.oldOrderKey, vm.tableData);
            }
        });
    };

    function initLargeTableStyling() {
        const largeTableRowHeight = TableService.getLargeTableRowHeight();
        vm.largeTableRowHeight = largeTableRowHeight; // for angular-material vs-repeat

        // ** get height of each row from tableService
        vm.largeEntryStyle = {
            height: largeTableRowHeight + 'px',
            minHeight: largeTableRowHeight + 'px',
            maxHeight: largeTableRowHeight + 'px'
            // lineHeight: largeTableRowHeight + 'px'
        };
    }

    function initSmallTableStyling() {
        // ** get height of each row from tableService, for each row
        const smallTableRowHeight = TableService.getSmallTableRowHeight();
        vm.smallTableRowHeight = smallTableRowHeight; // for angular-material vs-repeat

        vm.smallEntryStyle = {
            height: smallTableRowHeight + 'px',
            minHeight: smallTableRowHeight + 'px',
            maxHeight: smallTableRowHeight + 'px'
        };
    }

    vm.$onDestroy = function() {
        vm.otw();
    };

    function setStripedTable(list) {
        if (angular.isArray(list)) {
            list.forEach((item, index) => {
                item.striped = index % 2 !== 0;
            });
        }
    }

    function isReducedWidth(isEditMode, hasDetailsView) {
        return isEditMode || hasDetailsView;
    }

    function showHeaderOnCurrentSizedTable(header) {
        if (angular.isFunction(header.showHeader)) {
            return (header.showHeader(header) !== false &&
                ((!vm.isSmallTable() && header.showOnLargeTable !== false) ||
                    (vm.isSmallTable() && header.showOnSmallTable !== false)));
        }
        return (header.showHeader !== false && ((!vm.isSmallTable() && header.showOnLargeTable !== false) ||
            (vm.isSmallTable() && header.showOnSmallTable !== false)));
    }

    vm.getSortingStyle = getSortingStyle;

    function getSortingStyle(label, icon) {
        let len;
        if (angular.isDefined(icon)) {
            // if icon, we just add margin of 11px
            len = 11;
        } else {
            const trans = $translate.instant(label);
            // divided by 2: we want arrow to be in the middle
            // minus one:
            // multiply by 10: 10px approx skip one letter
            len = (Math.floor(trans.length / 2) - 1) * 10;
        }
        return {
            'margin-left': len + 'px'
        };
    }

}
