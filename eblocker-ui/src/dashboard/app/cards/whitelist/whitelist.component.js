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
    templateUrl: 'app/cards/whitelist/whitelist.component.html',
    controllerAs: 'vm',
    controller: WhitelistController,
    bindings: {
        cardId: '@'
    }
};

function WhitelistController(logger, $timeout, $interval, WhitelistService, ArrayUtilsService, DeviceService, // jshint ignore: line
                             CardService, DataService) {
    'ngInject';
    'use strict';

    const vm = this;

    const CARD_NAME = 'WHITELIST'; // 'card-5';

    vm.whitelist = [];
    vm.tableData = [];

    vm.searchProps = ['domain'];
    vm.searchTerm = '';

    vm.allowAllTrackers = false;
    vm.allowAllAds = false;

    const UPDATE_INTERVAL = 5000;
    let updateTimer;

    vm.getWhitelist = getWhitelist;
    vm.mergeLists = mergeLists;
    vm.updateWhitelistEntry = updateWhitelistEntry;
    vm.hasWhitelistedDomains = hasWhitelistedDomains;
    vm.isGloballyWhitelisted = isGloballyWhitelisted;
    vm.getNumberWhitelistedDomains = getNumberWhitelistedDomains;
    vm.isEntryWhitelisted = isEntryWhitelisted;
    vm.updateWhitelistConfig = updateWhitelistConfig;
    vm.isAddButtonDisabled = isAddButtonDisabled;
    vm.clickAddDomainButton = clickAddDomainButton;
    vm.getTableBodyStyle = getTableBodyStyle;

    vm.$onInit = function() {
        // getDevice();
        getData();
        startUpdateTimer();
        DataService.registerComponentAsServiceListener(CARD_NAME, 'DeviceService');
        DataService.registerComponentAsServiceListener(CARD_NAME, 'WhitelistService');
    };

    vm.$onDestroy = function() {
        stopUpdateTimer();
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'DeviceService');
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'WhitelistService');
    };

    vm.$postLink = function() {
        $timeout(function() {
            CardService.scrollToCard(CARD_NAME);
        }, 300);
    };

    function startUpdateTimer() {
        if (angular.isDefined(updateTimer)) {
            return;
        }
        updateTimer = $interval(getData, UPDATE_INTERVAL);
    }

    function stopUpdateTimer() {
        if (angular.isDefined(updateTimer)) {
            $interval.cancel(updateTimer);
            updateTimer = undefined;
        }
    }

    function getTableBodyStyle() {
        const px = (vm.tableData.length * 36) + 6;
        let value;
        if (px > 218) {
            value = '218';
        } else if (px < 42) {
            value = '42';
        } else {
            value = px + '';
        }
        return {
            height: value + 'px'
        };
    }

    function getData() {
        getWhitelist();
        getWhitelistConfig();
    }

    function clickAddDomainButton(domain) {
        if (!angular.isString(domain) ||
            domain === '' ||
            ArrayUtilsService.containsByProperty(vm.tableData, 'domain', domain) ||
            vm.addingDomain) {
            return;
        }
        vm.addingDomain = true;
        const entry = {
            domain: domain,
            ads: true,
            trackers: true
        };
        updateWhitelistEntry(entry).then(function success() {
            clearSearchField();
            return getWhitelist();
        }).finally(function done() {
            vm.addingDomain = false;
        });
    }

    function clearSearchField() {
        vm.searchTerm = '';
    }

    function isAddButtonDisabled() {
        return !angular.isArray(vm.tableData) ||
            ArrayUtilsService.containsByProperty(vm.tableData, 'domain', vm.searchTerm) ||
            vm.searchTerm === '' ||
            vm.addingDomain;
    }

    function isEntryWhitelisted(entry) {
        return entry.ads || entry.trackers;
    }

    function getNumberWhitelistedDomains() {
        return vm.whitelist.length;
    }

    function hasWhitelistedDomains() {
        return getNumberWhitelistedDomains() > 0;
    }

    function isGloballyWhitelisted() {
        return vm.allowAllTrackers || vm.allowAllAds || vm.deleteGlobalSetting;
    }

    function updateWhitelistEntry(entry) {
        return WhitelistService.updateWhitelistEntry(entry);
    }

    function updateWhitelistConfig() {
        vm.deleteGlobalSetting = !vm.allowAllTrackers && !vm.allowAllAds;
        const config = {
            blockTrackings: !vm.allowAllTrackers,
            blockAds: !vm.allowAllAds,
        };
        WhitelistService.updateWhitelistConfig(config);
    }

    function mergeLists(whiteList, deletedList) {
        const merged = angular.copy(whiteList);

        deletedList.forEach(function(deleted) {
            if (!ArrayUtilsService.containsByProperty(whiteList, 'domain', deleted.domain)) {
                deleted.trackers = false;
                deleted.ads = false;
                merged.push(deleted);
            }
        });
        return merged;
    }

    // function getDevice() {
    //     DeviceService.getDevice(false).then(function success(response) {
    //         if (angular.isObject(response.data)) {
    //             vm.device = response.data;
    //         }
    //     });
    // }

    function getWhitelistConfig() {
        WhitelistService.getWhitelistConfig(false).then(function success(response){
            vm.whitelistConfig = response.data;
            if (angular.isObject(vm.whitelistConfig)) {
                if ((vm.allowAllTrackers === true || vm.allowAllAds === true ) &&
                    (vm.whitelistConfig.blockTrackings === true && vm.whitelistConfig.blockAds === true )) {
                    // meaning: trackers OR ads were allowed AND after reload both are not allowed anymore
                    // --> set deleted flag, so that global checkboxes are shown.
                    // --> also makes sure that initial load does not set deleted flag
                    vm.deleteGlobalSetting = true;
                }

                // update model values for global checkboxes
                vm.allowAllTrackers = !vm.whitelistConfig.blockTrackings;
                vm.allowAllAds = !vm.whitelistConfig.blockAds;
            } else {
                logger.error('Error loading whitelist config. Received ', vm.whitelistConfig);
            }
        });
    }

    function getWhitelist() {
        vm.deletedEntries = angular.copy(vm.whitelist); // temp save of whitelist to keep deleted entries

       return WhitelistService.getWhitelist(false).then(function success(response) {
           const newList = processList(response.data);
           // ** make sure the list is only updated when there is actually a difference in the list, to avoid
           // flickering in UI due to change of tableData. Check against mergedLists to avoid flickering when
           // on server an entry is already deleted but kept in UI until reload for a better user experience.
           // Once the list is changed
           if (!diffList(mergeLists(newList, vm.deletedEntries), vm.whitelist)) {
               const tmp = mergeLists(newList, vm.deletedEntries); // will include deleted entries again
               vm.whitelist = ArrayUtilsService.sortByProperty(tmp, 'domain');
               vm.tableData = angular.copy(vm.whitelist);
           }
       });
    }

    function diffList(l1, l2) {
        if (l1.length !== l2.length) {
            return false;
        }
        let identical = true;
        l1.forEach((item1) => {
            const index = ArrayUtilsService.getIndexOf(l2, item1, 'domain');
            if (index > -1) {
                const item2 = l2[index];
                if (item2.ads !== item1.ads || item2.trackers !== item1.trackers) {
                    identical = false;
                }
            }
        });
        return identical;
    }

    function processList(data) {
        const tmp = [];
        Object.keys(data).forEach(function(key) {
            if (data.hasOwnProperty(key) &&
                (data[key].ads === true || data[key].trackers === true)) {
                const obj = {
                    domain: key,
                    ads: data[key].ads,
                    trackers: data[key].trackers
                };
                tmp.push(obj);
            }
        });
        return tmp;
    }
}
