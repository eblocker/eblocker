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
export default function TableService(logger, $window) {
    'ngInject';

    const LARGE_TABLE_ROW_HEIGHT = 50;
    const SMALL_TABLE_ROW_HEIGHT = 40;
    const LARGE_TABLE_DEFAULT_NUM_VISIBLE = 10;
    const SMALL_TABLE_DEFAULT_NUM_VISIBLE = 5;

    const LARGE_TABLE_PAGINATOR_OPTIONS = [
        { label: 5, value: 5 },
        { label: 10, value: 10 },
        { label: 20, value: 20 },
        { label: 'ADMINCONSOLE.TABLE.PAGINATOR.OPTION.ALL', value: 'ALL' }
    ];

    const SMALL_TABLE_PAGINATOR_OPTIONS = [
        { label: 5, value: 5 },
        { label: 10, value: 10 },
        { label: 15, value: 15 },
        { label: 20, value: 20 },
        { label: 'ADMINCONSOLE.TABLE.PAGINATOR.OPTION.ALL', value: 'ALL' }
    ];

    const largeTableVisibleItems = {};
    const smallTableVisibleItems = {};
    const prefixCounter = {};

    let idCounter = 1;

    /**
     * Return only options that "make sense" with respect to the table size. Offering an option for
     * 20 item per page for table with only 15 items does not make sense.
     * @param tableSize
     * @returns {Array}
     */
    function getTablePaginatorOptions(tableSize) {
        const ret = [];
        const options = isSmallScreen() ? SMALL_TABLE_PAGINATOR_OPTIONS : LARGE_TABLE_PAGINATOR_OPTIONS;
        options.forEach((item) => {
            // always show ALL and 5 (--> don't show at all when < 5 respectively)
            // if ((tableSize && item.value <= tableSize) || (item.value === 'ALL' || item.value === 5)) {
            if ((tableSize && item.value < tableSize) || (item.value === 'ALL')) {
                ret.push(item);
            } else if (!tableSize) {
                ret.push(item);
            }
        });
        return ret;
    }

    function getUniqueTableId(prefix) {
        let tableId;
        // FIXME for this impl counter is not necessary anymore
        if (angular.isDefined(prefixCounter[prefix])) {
            tableId = prefix + prefixCounter[prefix];
        } else {
            tableId = angular.isString(prefix) ? prefix + idCounter : 'table-' + idCounter;
            largeTableVisibleItems[tableId] =  LARGE_TABLE_DEFAULT_NUM_VISIBLE;
            smallTableVisibleItems[tableId] =  SMALL_TABLE_DEFAULT_NUM_VISIBLE;
            prefixCounter[prefix] = idCounter;
            idCounter++;
        }
        return tableId;
    }

    function getLargeTableRowHeight() {
        return LARGE_TABLE_ROW_HEIGHT;
    }

    function getSmallTableRowHeight() {
        return SMALL_TABLE_ROW_HEIGHT;
    }

    /**
     *
     * @param tableId
     * @param options all possible number-of-visible-items with respect to table size
     * @returns {*}
     */
    function getNumOfVisibleItems(tableId, options) {
        // ** current number of visible items: may be OK or maybe too large
        const numVisible = isSmallScreen() ? smallTableVisibleItems[tableId] : largeTableVisibleItems[tableId];

        if (isContainedInOptions(options, numVisible)) {
            // is contained in options: OK
            return numVisible;
        } else {
            // is not contained in options: USE LARGEST OPTION
            return 'ALL';
        }
    }

    function isSmallScreen() {
        return $window.innerWidth <= 600;
    }

    function isContainedInOptions(options, num) {
        let ret = false;
        options.forEach((item) => {
            if (item.value === num) {
                ret = true;
            }
        });
        return ret;
    }

    function tableNumVisibleItems(tableId, num) {
        // throw error to make sure that developer gets/sets table ID using this service
        if (!smallTableVisibleItems.hasOwnProperty(tableId)) {
            throw new Error('Table ID \'' + tableId + '\' has not been registered with TableService!');
        }
        if (angular.isDefined(num)) {
            if (isSmallScreen()) {
                smallTableVisibleItems[tableId] = num;
            } else {
                largeTableVisibleItems[tableId] = num;
            }

        }
        return isSmallScreen() ? smallTableVisibleItems[tableId] : largeTableVisibleItems[tableId];
    }

    return {
        getUniqueTableId: getUniqueTableId,
        getLargeTableRowHeight: getLargeTableRowHeight,
        getSmallTableRowHeight: getSmallTableRowHeight,
        tableNumVisibleItems: tableNumVisibleItems,
        getTablePaginatorOptions: getTablePaginatorOptions,
        getNumOfVisibleItems: getNumOfVisibleItems
    };
}
