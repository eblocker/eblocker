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
    templateUrl: 'app/components/parentalControl/blacklists/blacklists.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(logger, $translate, $interval, BlockerService, LanguageService, StateService, UserProfileService,
                    DialogService, TableService, ArrayUtilsService) {
    'ngInject';
    'use strict';

    let vm = this;

    vm.filterInUse = {};

    vm.newBlacklist = newBlacklist;
    vm.deleteFilterList = deleteFilterList;
    vm.onSingleDeleteDone = onSingleDeleteDone;
    vm.isFilterlistDeletable = isFilterlistDeletable;

    let filterlistMap = {};
    let statusUpdateInterval;

    vm.$onInit = function() {
        UserProfileService.getAll(true).then(function success(response) {
            vm.profiles = response.data;
            loadAllFilterLists();
        });
        startStatusUpdateInterval();
    };

    vm.$onDestroy = function() {
        stopStatusUpdateInterval();
    };

    function startStatusUpdateInterval() {
        if (angular.isUndefined(statusUpdateInterval)) {
            statusUpdateInterval = $interval(updateStatus, 3000);
        }
    }

    function stopStatusUpdateInterval() {
        if (angular.isDefined(statusUpdateInterval)) {
            $interval.cancel(statusUpdateInterval);
            statusUpdateInterval = undefined;
        }
    }

    function updateStatus() {
        const config = {
            params: {
                type: 'DOMAIN',
                category: 'PARENTAL_CONTROL'
            }
        };

        BlockerService.getBlockers(true, config).then(function(response) {
            response.data.forEach((blocker) => {
                if (angular.isDefined(blocker.url)) {
                    const item = ArrayUtilsService.getItemBy(vm.filteredTableData, 'id', blocker.id);
                    if (angular.isObject(item)) {
                        item.updateStatus = blocker.updateStatus;
                    }
                }
            });
        });
    }

    // ** START: TABLE
    vm.tableId = TableService.getUniqueTableId('parentalcontrol-blacklist-table');
    vm.detailsState = 'blacklistdetails';
    vm.tableHeaderConfig = [
        {
            label: '',
            icon: '/img/icons/eblocker-blocked-24px-2.svg',
            isSortable: true,
            isXsColumn: true,
            sortingKey: 'assignedToProfile'
        },
        {
            label: 'ADMINCONSOLE.BLACKLISTS.TABLE.COLUMN.NAME',
            isSortable: true,
            flexGtXs: 25,
            sortingKey: 'localizedName'
        },
        {
            label: 'ADMINCONSOLE.BLACKLISTS.TABLE.COLUMN.DESCRIPTION',
            isSortable: true,
            sortingKey: 'localizedDescription'
        },
        {
            label: 'ADMINCONSOLE.BLACKLISTS.TABLE.COLUMN.LAST_MODIFIED',
            isSortable: true,
            flex: 15,
            sortingKey: 'lastUpdate'
        }
    ];

    vm.isSelectable = isSelectable;
    // Filtering following props of table entry
    vm.searchProps = ['localizedName', 'localizedDescription', 'lastUpdate'];

    function isSelectable(value) {
        return isFilterlistDeletable(value);
    }

    function isFilterlistDeletable(value) {
        return !value.providedByEblocker && value.assignedToProfile.length === 0 &&
            value.filterType === 'blacklist';
    }

    function deleteFilterList(filterlist) {
        return BlockerService.deleteBlocker(filterlist.id);
    }

    function onSingleDeleteDone() {
        loadAllFilterLists();
    }
    // *** END table stuff

    // Get all blacklists
    function loadAllFilterLists() {
        vm.loading = true;

        const config = {
            params: {
                type: 'DOMAIN',
                category: 'PARENTAL_CONTROL'
            }
        };

        BlockerService.getBlockers(true, config).then(function(response) {
            const filterLists = response.data.filter(function(el) {
                return el.category === 'PARENTAL_CONTROL' && el.filterType === 'blacklist';
            });
            vm.tableData = [];
            vm.filteredTableData = [];
            filterlistMap = {};
            const dateFormat = 'ADMINCONSOLE.BLACKLISTS.DATE_FORMAT';

            filterLists.forEach((list) => {
                if (angular.isObject(list.name) && angular.isDefined(list['name'][$translate.use()])) {
                    list.localizedName = list['name'][$translate.use()];
                    list.localizedDescription = list['description'][$translate.use()];
                } else {
                    list.localizedName = list.name;
                    list.localizedDescription = list.description;
                }
                list.assignedToProfile = [];
                list.lastUpdateDisplay = LanguageService.getDate(list.lastUpdate, dateFormat);
                filterlistMap[list.id] = list;
                vm.tableData.push(list);
            });

            vm.profiles.forEach((profile) => {
                profile.accessibleSitesPackages.forEach((filter) => {
                    if (angular.isDefined(filterlistMap[filter])) {
                        filterlistMap[filter].assignedToProfile.push(profile);
                    }
                });
                profile.inaccessibleSitesPackages.forEach((filter) => {
                    if (angular.isDefined(filterlistMap[filter])) {
                        filterlistMap[filter].assignedToProfile.push(profile);
                    }
                });
            });

        }).finally(function disableSpinner() {
            vm.loading = false;
            vm.filteredTableData = ArrayUtilsService.sortByProperty(vm.tableData, 'localizedName');
        });
    }

    function newBlacklist(event){
        newFilterList('blacklist', event);
    }

    function newFilterList(filterType, event){
        const newBlockerList = {
            category: 'PARENTAL_CONTROL',
            providedByEblocker: false,
            enabled: true,
            filterType: filterType
        };
        addNewFilterListDialog(newBlockerList, event);
    }

    function addNewFilterListDialog(module, event){
        DialogService.filterNewEdit(module, event, 'blacklist').then(loadAllFilterLists, function() {});
    }
}
