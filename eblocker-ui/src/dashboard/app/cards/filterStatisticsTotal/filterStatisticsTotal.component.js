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
    templateUrl: 'app/cards/filterStatisticsTotal/filterStatisticsTotal.component.html',
    controller: FilterStatisticsTotalController,
    controllerAs: 'vm',
    bindings: {
        cardId: '@'
    }
};

function FilterStatisticsTotalController($filter, $interval, $q, $translate, // jshint ignore: line
                                    $timeout, $sce, CardService, DeviceService, DialogService, DataService,
                                    filterStatistics, LanguageService, NumberUtilsService) {
    'ngInject';
    'use strict';

    const vm = this;

    const CARD_NAME = 'BLOCKER_STATISTICS_TOTAL'; //'card-14';

    vm.getDisplayValue = getDisplayValue;

    /*
     * &#8201; thin space
     * &#8200; punctuation space
     */
    function getDisplayValue(input) {
        return $sce.trustAsHtml(NumberUtilsService.convertToStringWithSeparator(input, '&#8200;'));
    }

    vm.selectedTabIndex = 0;

    vm.blockedDomainsNumberEntries = 25;
    vm.blockedDomainsLastReset = LanguageService.getCurrentDate('BLOCKER_STATISTICS_TOTAL.CARD.FORMAT_DATE_TIME');
    vm.blockedDomainsCategories = [ 'TRACKERS', 'ADS' ];
    vm.blockedDomainsStatisticEmpty = {};
    vm.blockedDomainsStatistic = {};

    vm.displayBin = {
        queries: 0,
        blockedQueries: 0,
        blockedQueriesByReason: {}
    };

    const UPDATE_INTERVAL = 2000;
    let updateTimer;

    function updateStats(response) {
        var summary = {
            queries: 0,
            blockedQueries: 0,
            blockedQueriesByReason: {
                ADS: 0,
                CUSTOM: 0,
                PARENTAL_CONTROL: 0,
                TRACKERS: 0
            }
        };

        summary.queries = response.summary.queries;
        summary.blockedQueriesByReason.ADS = response.summary.blockedQueriesByReason.ADS || 0;
        summary.blockedQueriesByReason.CUSTOM = response.summary.blockedQueriesByReason.CUSTOM || 0;
        summary.blockedQueriesByReason.PARENTAL_CONTROL = response.summary.blockedQueriesByReason.PARENTAL_CONTROL || 0;
        summary.blockedQueriesByReason.TRACKERS = response.summary.blockedQueriesByReason.TRACKERS || 0;
        summary.blockedQueries = summary.blockedQueriesByReason.ADS +
            summary.blockedQueriesByReason.CUSTOM +
            summary.blockedQueriesByReason.PARENTAL_CONTROL +
            summary.blockedQueriesByReason.TRACKERS;
        summary.percentageBlocked = (summary.blockedQueries / summary.queries * 100).toFixed(1);

        vm.displayBin = summary;
        vm.blockedDomainsLastReset = LanguageService.getDate(response.begin * 1000,
                $translate.instant('BLOCKER_STATISTICS_TOTAL.CARD.FORMAT_DATE_TIME'));
    }

    function updateBlockedDomains(response) {
        vm.blockedDomainsStatistic = {};
        let empty = true;
        Object.keys(response.stats).forEach(function(k) {
            vm.blockedDomainsStatistic[k] = response.stats[k].slice(0, vm.blockedDomainsNumberEntries);
            /*jshint bitwise: false */
            empty &= vm.blockedDomainsStatistic[k].length === 0;
            /*jshint bitwise: true */
        });
        vm.blockedDomainsStatisticEmpty = empty;
    }

    vm.$onInit = function() {
        filterStatistics.getTotalStatistics().then(updateStats);
        filterStatistics.getBlockedDomainsStatistic().then(updateBlockedDomains);
        checkConfigChange();
        startUpdateTimer();
        DataService.registerComponentAsServiceListener(CARD_NAME, 'DeviceService');
    };

    vm.$onDestroy = function() {
        stopUpdateTimer();
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'DeviceService');
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
        updateTimer = $interval(checkConfigChange, UPDATE_INTERVAL);
    }

    function stopUpdateTimer() {
        if (angular.isDefined(updateTimer)) {
            $interval.cancel(updateTimer);
            updateTimer = undefined;
        }
    }

    vm.filterMode = undefined;
    function checkConfigChange() {
        DeviceService.getDevice().then(function success(response) {
            if (response.data.filterMode !== vm.filterMode) {
                vm.filterMode = response.data.filterMode;
                // TODO: refresh diagrams
            }
        });
    }

    vm.resetBlockedDomainsStatistic = function() {
        filterStatistics.resetBlockedDomainsStatistic().then(updateBlockedDomains);
    };
}
