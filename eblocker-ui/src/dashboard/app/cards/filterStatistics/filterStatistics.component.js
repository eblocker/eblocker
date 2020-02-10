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
    templateUrl: 'app/cards/filterStatistics/filterStatistics.component.html',
    controller: FilterStatisticsController,
    controllerAs: 'vm',
    bindings: {
        cardId: '@'
    }
};

function FilterStatisticsController($filter, $interval, $q, $translate, $timeout, CardService, DeviceService,
                                    DataService) {
    'ngInject';
    'use strict';

    const vm = this;

    const CARD_NAME = 'DNS_STATISTICS'; //'card-8';

    vm.selectedTabIndex = 0;

    vm.chartRanges = [
        {
            numberOfBins: 31,
            binSizeMinutes: 2,
            label: 'LAST_HOUR'
        },
        {
            numberOfBins: 25,
            binSizeMinutes: 60,
            label: 'DAY'
        },
        {
            numberOfBins: 31,
            binSizeMinutes: 360,
            label: 'WEEK'
        },
    ];
    vm.chartRange = vm.chartRanges[0];

    const UPDATE_INTERVAL = 2000;
    let updateTimer;

    vm.$onInit = function() {
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

}
