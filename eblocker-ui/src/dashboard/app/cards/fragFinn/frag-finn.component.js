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
    templateUrl: 'app/cards/fragFinn/frag-finn.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        cardId: '@',
        userId: '<'
    }
};

function Controller(logger, $sce, $timeout, $q, $interval, CardService, UserProfileService, DataService,
                    LocalTimestampService, onlineTime) {
    'ngInject';
    'use strict';

    const vm = this;

    const CARD_NAME = 'FRAG_FINN';

    let cssLink;

    vm.$onInit = function() {
        vm.initialLoading = true;
        DataService.registerComponentAsServiceListener(CARD_NAME, 'onlineTime');
        UserProfileService.getSearchEngineConfig().then(function(response) {
            // fragFINN is german sites only, so use the 'de' property
            vm.searchEngineConfig = response.data.de;
            vm.searchEngineConfig.iframe = $sce.trustAsHtml(vm.searchEngineConfig.iframe);
        });
        vm.isOnline = false;
        vm.contingents = {};
        vm.dailyTime = {};
        // ** add static css file to add a margin to the fragFINN search button
        // cssLink = getCssLink();
        // addIframeCss();
        // vm.interval = $interval(addIframeCss, 2000);
        update();
        vm.dataInterval = $interval(update, 3000);
    };

    vm.$onDestroy = function() {
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'onlineTime');
        if (angular.isDefined(vm.dataInterval)) {
            $interval.cancel(vm.dataInterval);
        }
    };

    vm.$postLink = function() {
        $timeout(function() {
            CardService.scrollToCard(CARD_NAME);
        }, 300);
    };

    function update() {
        return $q.all([
            updateUserOnlineTime(),
            getUserProfile(false)
        ]).then(function success() {
            updateContingents();
            vm.isOnline = onlineTime.isOnline(vm.dailyTime) && onlineTime.isInContingent(vm.contingents);
            vm.initialLoading = false;
        });
    }

    function updateContingents() {
        LocalTimestampService.getLocalTimestamp().then(function(response) {
            const timestamp = response.data;
            vm.contingents.statusDisplay = onlineTime.computeRemainingContingents(vm.contingents.contingents,
                    timestamp, vm.dailyTime);
        });
    }

    function updateUserOnlineTime() {
        return onlineTime.getRemainingTime().then(function success(response) {
            const hasDaily = angular.isObject(vm.dailyTime) ? vm.dailyTime.hasDailyTime : false;
            vm.dailyTime = onlineTime.computeDailyTime(response.data);
            vm.dailyTime.hasDailyTime = hasDaily;
            return response;
        }, function error(response) {
            return $q.reject(response);
        });
    }

    function getUserProfile(reload) {
        return UserProfileService.getCurrentUsersProfile(reload).then(function(response) {
            // Store data about contingents
            vm.contingents.hasContingent = response.data.controlmodeTime;
            vm.contingents.contingents = response.data.internetAccessContingents;
            // Store data about daily time
            vm.dailyTime.hasDailyTime = response.data.controlmodeMaxUsage;
            return response;
        }, function error(response) {
            logger.error('Could not get user profile: ' + JSON.stringify(response));
            // Set flags such as to not display buttons or anything
            vm.dailyTime.hasDailyTime = false;
            vm.contingents.hasContingent = false;
            return $q.reject(response);
        });
    }

    function addIframeCss() {
        // const iframe = document.getElementById('frag-finn-iframe');
        const iframe = window.frames['frag-finn-iframe']; //.getElementById('frag-finn-iframe');
        /*
        if (angular.isObject(iframe)) {
            document.getElementById('frag-finn-iframe')
            frames['iframe1'].document.head.appendChild(cssLink);
            $interval.cancel(vm.interval);
        } else {
            console.log('iframe: ', iframe);
        }
        */

    }

    function getCssLink() {
        const cssLink = document.createElement('link');
        cssLink.href = '/dashboard/styles/iframe.css';
        cssLink.rel = 'stylesheet';
        cssLink.type = 'text/css';
        return cssLink;
    }

}
