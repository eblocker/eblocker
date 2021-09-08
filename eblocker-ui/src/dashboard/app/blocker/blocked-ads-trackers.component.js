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
    templateUrl: 'app/blocker/blocked-ads-trackers.component.html',
    controllerAs: 'vm',
    controller: Controller
};

function Controller(logger, $stateParams, $window, DomainUtilsService, DialogService, CustomDomainFilterService,
                    PauseService) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.truncateDomain = DomainUtilsService.truncateDomain;

    vm.waiting = true;
    vm.customdomainfilter = {
        blacklistedDomains: [],
        whitelistedDomains: []
    };
    vm.isBackButtonAvailable = $window.history.length > 1;

    vm.$onInit = function () {
        getCustomDomainFilter();
        vm.target = $stateParams['target'];
        vm.category = $stateParams['category'];
        vm.domain = $stateParams['domain'];
    };


    vm.back = function() {
        $window.history.back();
    };

    vm.whitelistDomain = function() {
        vm.waiting = true;
        vm.customdomainfilter.whitelistedDomains.push(vm.domain);
        CustomDomainFilterService.setCustomDomainFilter(vm.customdomainfilter).then(function success() {
            setTimeout(function () {
                $window.location.replace(vm.target);
            }, 10000);
        });
    };

    vm.openPauseDialog = function(event) {
        DialogService.confirmPauseAndContinue(event, pauseAndContinue);
    };

    function pauseAndContinue() {
        vm.waitingForRedirect = true;
        vm.errorPause = false;
        return PauseService.setPause(300).then(function success(response) {
            setTimeout(function () {
                vm.waitingForRedirect = false;
                $window.location.replace(vm.target);
            }, 1000);
            return response;
        }, function error(response) {
            vm.waitingForRedirect = false;
            vm.errorPause = true;
        });
    }

    vm.removeDomain = function() {
        vm.waiting = true;
        let domain = vm.domain;
        if (domain[0] === '.') {
            domain = domain.substring(1);
        }
        let index = vm.customdomainfilter.blacklistedDomains.indexOf(domain);
        if (index !== -1) {
            vm.customdomainfilter.blacklistedDomains.splice(index, 1);
            CustomDomainFilterService.setCustomDomainFilter(vm.customdomainfilter).then(function success() {
                setTimeout(function () {
                    $window.location.replace(vm.target);
                }, 10000);
            });
        }
    };

    vm.retry = function() {
        $window.location.replace(vm.target);
    };

    function getCustomDomainFilter() {
        return CustomDomainFilterService.getCustomDomainFilter().then(function success(response) {
            vm.customdomainfilter = response.data;
            const whitelistedDomains = vm.customdomainfilter.whitelistedDomains || [];
            vm.alreadyWhitelisted = false;
            for(let i = 0; i < whitelistedDomains.length; ++i) {
                if (whitelistedDomains[i] === vm.domain) {
                    vm.alreadyWhitelisted = true;
                    break;
                }
            }
            return response;
        }).finally(function done() {
            vm.waiting = false;
        });
    }

}
