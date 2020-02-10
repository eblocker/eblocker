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
    templateUrl: 'app/components/trackers/trackers.component.html',
    controller: TrackersController,
    controllerAs: 'ctrl'
};

function TrackersController(logger, FilterService, WhitelistService, DeviceService, DnsStatistics, SslService) {
    'ngInject';

    let vm = this;

    vm.isMenuOpen = false;
    vm.toggleMenu = toggleMenu;

    vm.blockGloballyFn = blockGloballyFn;
    vm.allowLocallyFn = allowLocallyFn;

    vm.blockingVars = {
        blockGlobally: true,
        allowLocally: false
    };

    vm.numBlocked = 0;

    vm.blockedTrackers = [];

    vm.getTooltip = getTooltip;

    function getTooltip() {
        if(vm.filterMode === 'NONE') {
            return 'CONTROLBAR.MENU.ADS.TOOLTIP_NO_FILTER';
        } else if (!vm.blockingVars.blockGlobally) {
            return 'CONTROLBAR.MENU.TRACKERS.TOOLTIP_ALLOW_GLOBAL';
        } else if (vm.filterMode === 'ADVANCED') {
            if (vm.blockingVars.allowLocally) {
                return 'CONTROLBAR.MENU.TRACKERS.TOOLTIP_ALLOW_LOCAL';
            }
            return 'CONTROLBAR.MENU.TRACKERS.TOOLTIP_BLOCK';
        } else if (vm.filterMode === 'PLUG_AND_PLAY') {
            return 'CONTROLBAR.MENU.TRACKERS.TOOLTIP_BLOCK_DNS';
        }
    }

    function blockGloballyFn(value) {
        if (vm.filterMode === 'PLUG_AND_PLAY') {
            DeviceService.updatePlugAndPlayTrackersEnabledStatus(vm.device.id, value);
        } else if (vm.filterMode === 'ADVANCED') {
            FilterService.setFilterConfigBlockTracker(value);
            FilterService.saveFilterConfig().then(function success() {
                vm.blockingVars.blockGlobally = value;
            }, function error(response) {
                logger.error('Not updating global filter config due to server error: ', response);
            });
        }
    }

    function allowLocallyFn(value) {
        WhitelistService.setWhitelistTracker(value);
        WhitelistService.setWhitelist().then(function success() {
            vm.blockingVars.allowLocally = value;
        }, function error(response) {
            logger.error('Not updating local whitelist setting due to server error: ', response);
        });
    }

    function toggleMenu() {
        vm.isMenuOpen = !vm.isMenuOpen;
    }

    /*
        List of blocked trackers
     */
    function getBlockedTrackers() {
        FilterService.getBlockedTrackers().then(function success(response) {
            vm.blockedTrackers = FilterService.processUrls(response.data);
        }, function error() {
            // handle rejection
        });
    }

    /*
        Get number of blocked trackers.
    */
    function getFilterStats() {
        FilterService.getFilterStats().then(function success(response) {
            vm.filterStats = response.data;
            vm.numBlocked = angular.isDefined(vm.filterStats) ? vm.filterStats.trackingsBlockedOnPage : null;
        }, function error() {
            // handle rejection
        });
    }

    /*
        Global whitelist settings
     */
    function getFilterConfig() {
        FilterService.getFilterConfig().then(function success(response) {
            if (angular.isDefined(response) && angular.isDefined(response.data)) {
                vm.filterConfig = response.data;
                vm.blockingVars.blockGlobally = angular.isDefined(vm.filterConfig) ?
                    vm.filterConfig.blockTrackings : null;
            } else {
                logger.error('Unable to get filter config, response is undefined.');
            }
        }, function error(response) {
            logger.error('Error loading filters config: ', response);
        });
    }

    /**
     * Local whitelist setting for current domain
     */
    function getWhitelist() {
        WhitelistService.getWhitelist().then(function success(response) {
            vm.whitelistConfig = response.data;
            vm.blockingVars.allowLocally = angular.isDefined(vm.whitelistConfig) ? vm.whitelistConfig.trackers : null;
        }, function error(response) {
            logger.error('Unable to get whitelist: ', response);
        });
    }

    vm.$onInit = function() {
        SslService.getSslStatus().then(function success(response){
            vm.globalSslStatus = response.data.globalSslStatus;
            getFilterStatistics();
        });

    };

    function getFilterStatistics() {
        DeviceService.getDevice().then(function success(response) {
            vm.device = response.data;
            vm.filterMode = DeviceService.getFilterMode(vm.device.filterMode, vm.globalSslStatus, vm.device);
            if (vm.filterMode === 'PLUG_AND_PLAY_NO_SSL') {

                vm.blockingVars.blockGlobally = vm.device.filterPlugAndPlayTrackersEnabled;

                DnsStatistics.getStatistics(21, 1, 'dns').then(function success(response) {
                    vm.statistics = response;
                    if (angular.isObject(vm.statistics.summary)) {
                        vm.blockedQueriesByReason = vm.statistics.summary.blockedQueriesByReason;
                        const lastTwenty = DnsStatistics.
                        getBlockedInLastMinutes(vm.statistics.bins, 20, 'TRACKERS');

                        const lastTen = DnsStatistics.
                        getBlockedInLastMinutes(vm.statistics.bins, 10, 'TRACKERS');

                        const lastMin = DnsStatistics.
                        getBlockedInLastMinutes(vm.statistics.bins, 1, 'TRACKERS');

                        vm.dnsBlockings = {
                            twenty: lastTwenty,
                            ten: lastTen,
                            one: lastMin
                        };
                    }
                } , function error(response) {
                    // handle rejection..
                    logger.error('No DNS summary found.', response);
                });
            } else {
                getWhitelist();       // sets vm.blockingVars.allowLocally
                getFilterConfig();    // sets vm.blockingVars.blockGlobally
                getFilterStats();     // sets vm.numBlocked
                getBlockedTrackers(); // sets vm.blockedTrackers
            }
        });
    }
}
