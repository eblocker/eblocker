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
    templateUrl: 'app/components/filters/overview/filter-details.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

// jshint ignore: line
function Controller(logger, $stateParams, $interval, $translate, STATES, BlockerService, StateService, // jshint ignore: line
                    DialogService, TableService, LanguageService, ArrayUtilsService) {
    'ngInject';
    'use strict';

    const vm = this;

    let listUpdateInterval;
    const BLOCKER_LIST_INTERVAL = 5000;

    vm.onChangePaginator = onChangePaginator;
    vm.updateCustomList = updateCustomList;
    vm.newCustomList = newCustomList;
    vm.isSelectable = isSelectable;
    vm.isDeletable = isDeletable;
    vm.deleteEntry = deleteEntry;
    vm.onDeleteDone = onDeleteDone;
    vm.goToDevice = goToDevice;
    vm.goToHttps = goToHttps;

    function deleteEntry(value) {
        BlockerService.deleteBlocker(value.id);
    }

    function onDeleteDone() {
        updateDisplayData(vm.blocker, true);
    }

    function isDeletable(entry) {
        return isSelectable(entry);
    }

    function isSelectable(entry) {
        return !entry.providedByEblocker;
    }

    function newCustomList() {
        const newBlockerList = {
            category: vm.blocker.category,
            type: vm.blocker.type,
            providedByEblocker: false,
            enabled: true
        };
        openUpdateListDialog(newBlockerList, false, false);
    }

    function updateCustomList(blockerList) {
        const isProcessing = blockerList.updateStatus === 'INITIAL_UPDATE' || blockerList.updateStatus === 'UPDATE';
        openUpdateListDialog(blockerList, true, isProcessing);
    }

    function openUpdateListDialog(blockerList, isEdit, isProcessing) {
        DialogService.
        updateCustomBlockerList(vm.blocker.name, vm.formatList, isEdit, blockerList, isProcessing, saveBlockerList).
        then(function saveClicked() {
            // get fresh blocker lists of vm.blocker and set table data.
            updateDisplayData(vm.blocker, true);
        }, angular.noop);
    }

    function saveBlockerList(blockerList) {
        return angular.isDefined(blockerList.id) ? BlockerService.updateBlocker(blockerList) :
            BlockerService.createBlocker(blockerList);
    }

    function onChangePaginator(entry) {
        vm.blockerListsTableData.length = 0;
        vm.filteredTableData.length = 0;
        updateDisplayData(entry, true);
    }

    function onChangeEnabled(entry) {
        saveBlockerList(entry);
    }

    function getFormattedDate(date) {
        const format = $translate.instant('ADMINCONSOLE.FILTER_DETAILS.DATE_TIME_FORMAT');
        const updateUnknown = 'ADMINCONSOLE.FILTER_DETAILS.LABEL.UPDATE_UNKNOWN';
        return date ? LanguageService.getDate(date, format) : updateUnknown;
    }

    function startListUpdateInterval() {
        if (angular.isUndefined(listUpdateInterval)) {
            listUpdateInterval = $interval(updateBlockerLists, BLOCKER_LIST_INTERVAL);
        }
    }
    function stopListUpdateInterval() {
        if (angular.isDefined(listUpdateInterval)) {
            $interval.cancel(listUpdateInterval);
            listUpdateInterval = undefined;
        }
    }

    // TABLE
    vm.tableHeaderConfig = [
        {
            label: '',
            icon: '/img/icons/eblocker-blocked-24px-2.svg',
            isSortable: false,
            isXsColumn: true
        },
        {
            label: 'ADMINCONSOLE.FILTER_DETAILS.TABLE.COLUMN.NAME',
            isSortable: true,
            sortingKey: 'localizedName'
        },
        {
            label: 'ADMINCONSOLE.FILTER_DETAILS.TABLE.COLUMN.URL',
            isSortable: true,
            sortingKey: 'url'
        },
        {
            label: 'ADMINCONSOLE.FILTER_DETAILS.TABLE.COLUMN.LAST_UPDATED',
            isSortable: true,
            showOnSmallTable: false,
            sortingKey: 'lastUpdate'
        },
        {
            label: '',
            isSortable: false,
            isXsColumn: true
        },
    ];
    vm.tableId = TableService.getUniqueTableId('blockers-details-blocked-lists-table');
    vm.searchProps = ['name', 'url'];
    vm.tableCallback = {};
    vm.templateCallback = {
        onChangeEnabled: onChangeEnabled,
        getFormattedDate: getFormattedDate
    };

    vm.$onInit = function() {
        vm.backState = STATES.FILTER_OVERVIEW;
        vm.stateParams = $stateParams;

        if (angular.isObject($stateParams.param) &&
            angular.isObject($stateParams.param.entry) &&
            angular.isFunction($stateParams.param.getTableData)) {
            const blocker = $stateParams.param.entry;
            vm.tableData = $stateParams.param.getTableData();

            vm.blockerListsTableData = [];
            vm.filteredTableData = [];

            updateDisplayData(blocker, true);
            startListUpdateInterval();
        } else {
            StateService.goToState(vm.backState);
        }
    };

    vm.$onDestroy = function() {
        stopListUpdateInterval();
    };

    function updateBlockerLists() {
        if (angular.isObject(vm.blocker)) {
            const config = {
                params: {
                    type: vm.blocker.type,
                    category: vm.blocker.category
                }
            };
            BlockerService.getBlockers(true, config).then(function success(response) {
                response.data.forEach((blocker) => {
                    const item = ArrayUtilsService.getItemBy(vm.filteredTableData, 'id', blocker.id);
                    if (angular.isObject(item)) {
                        item.updateStatus = blocker.updateStatus;
                    }
                });
                setStatusImageClass(vm.filteredTableData);
            }, function error(response) {
                logger.error('Error loading blocker for update ', response);
            });
        }
    }

    function setBlockerListsTableData(blocker, reload) {
        const config = {
            params: {
                type: blocker.type,
                category: blocker.category
            }
        };
        return BlockerService.getBlockers(reload, config).then(function success(response) {
            vm.blockerListsTableData = response.data;

            vm.filteredTableData.length = 0; // keep table reference
            vm.blockerListsTableData.forEach((list) => {
                if (angular.isDefined(list.name[$translate.use()])) {
                    list.localizedName = list.name[$translate.use()];
                }
                vm.filteredTableData.push(list);
            });
            vm.tableCallback.reload(); // makes sure table-stripes are displayed

            setStatusImageClass(vm.filteredTableData);

            return response;
        }, function error(response) {
            logger.error('Error loading blocker ', response);
        });
    }

    function getFormatList(blocker) {
        return BlockerService.getFormatList(blocker.type);
    }

    function setStatusImageClass(blockerList) {
        if (angular.isObject(vm.status)) {
            vm.status.imageClass = atLeastOneActivatedAndRunning(blockerList) ?
                'orange' : 'disabled';
        }
    }

    function updateDisplayData(blocker, reload) {
        vm.blocker = blocker;
        vm.formatList = getFormatList(vm.blocker);

        setBlockerListsTableData(blocker, reload).finally(function success() {
            vm.status = {
                imagePath: '/img/icons/eblocker-blocked-24px-2.svg',
                imageClass: atLeastOneActivatedAndRunning(vm.filteredTableData) ? 'orange' : 'disabled'
            };

            vm.devices = {
                value: vm.blocker.devices.length > 0 ? vm.blocker.devices.map(d => d.name).join(', ') : '-',
                truncate: true
            };

            vm.blocked= {
                value: vm.blocker.numBlocked()
            };
        });
    }

    function atLeastOneActivatedAndRunning(blockerList) {
        let oneActive = false;
        blockerList.forEach((filterList) => {
            oneActive = oneActive || (filterList.enabled &&
                (filterList.updateStatus === 'READY' || filterList.updateStatus === 'UPDATE_FAILED'));
        });
        return oneActive;
    }

    function goToDevice(device) {
        StateService.goToState(STATES.DEVICES, {id: device.id});
    }

    function goToHttps() {
        StateService.goToState(STATES.HTTPS);
    }

}
