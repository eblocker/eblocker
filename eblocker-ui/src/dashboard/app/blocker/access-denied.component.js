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
    templateUrl: 'app/blocker/access-denied.component.html',
    controllerAs: 'vm',
    controller: Controller,
    bindings: {
        locale: '<'
    }
};

function Controller(logger, $stateParams, $window, $sce, onlineTime, DialogService, UserService, UserProfileService,
                    DomainUtilsService, FilterService) {
    'ngInject';

    const vm = this;

    vm.tryAgain = tryAgain;
    vm.back = back;
    vm.isBackButtonAvailable = $window.history.length > 1;
    vm.onChangeInternetAccess = onChangeInternetAccess;
    vm.changeUser = changeUser;
    vm.smallWindow = smallWindow;
    vm.truncateDomain = DomainUtilsService.truncateDomain;

    const lockedUser = {
        id: 2,
        containsPin: true
    };

    vm.$onInit = function() {
        activate();
    };

    function smallWindow() {
        return $window.innerWidth <= 600;
    }

    function activate() {  // jshint ignore: line
        vm.target = $stateParams['target'];
        vm.targetShort = vm.truncateDomain($stateParams['target']);

        vm.reason = {
            domain: false,
            timeFrame: false,
            maxUsage: false,
            usageDisabled: false,
            internetBlocked: false
        };

        vm.userList = [];

        // access denied by domain name
        if ($stateParams['externalAclMessage']) {
            const externalAclMessages = $stateParams['externalAclMessage'].split(',');
            vm.profileId = externalAclMessages[0];
            vm.listId = externalAclMessages[1];
            vm.domain = externalAclMessages[2];
            vm.domainShort = vm.truncateDomain(externalAclMessages[2]);
            vm.userId = externalAclMessages[3];
            vm.reason.domain = true;
        } else if ($stateParams['profileId']) {
            vm.profileId = $stateParams['profileId'];
            vm.userId = $stateParams['userId'];
            const restrictions = $stateParams['restrictions'];
            if (restrictions) {
                if (restrictions.indexOf('INTERNET_ACCESS_BLOCKED') !== -1) {
                    vm.reason.internetBlocked = true;
                } else if (restrictions.indexOf('TIME_FRAME') !== -1) {
                    vm.reason.timeFrame = true;
                } else if (restrictions.indexOf('MAX_USAGE_TIME') !== -1) {
                    vm.reason.maxUsage = true;
                } else if (restrictions.indexOf('USAGE_TIME_DISABLED') !== -1) {
                    vm.reason.usageTimeDisabled = true;
                    onlineTime.getRemainingTime().then(function (response) {
                        const usage = response.data;
                        const totalTime = usage.maxUsageTime;
                        let remainingTime = totalTime - usage.usedTime;
                        // Frequency of measuring the online time in the backend may
                        // allow the used time to be a few seconds over the allowed
                        // time
                        if (remainingTime < 0) {
                            remainingTime = 0;
                        }
                        vm.internetAccessEnabled = usage.active;
                        vm.usage = onlineTime.computeRemainingOnlineTime({
                            total: totalTime,
                            remaining: remainingTime
                        });
                    });
                }
            } else {
                vm.listId = $stateParams['listId'];
                vm.domain = $stateParams['domain'];
                vm.domainShort = vm.truncateDomain($stateParams['domain']);
                vm.reason.domain = true;
            }
        } else {
            // There is no message from the external ACL helper and no profile ID,
            // therefore, this error probably has nothing to do with parental controls:
            redirectToGenericAccessDeniedPage();
        }

        if (vm.listId) {
            FilterService.getFilterLists().then(function success(response) {
                const list = response.data.find(function (list) {
                    return list.id === Number(vm.listId);
                });

                if (angular.isObject(list)) {
                    if (angular.isDefined(list.category) && list.category === 'CUSTOM') {
                        redirectToCustomDomainBlockedPage(vm.domain, vm.listId, vm.target);
                    }
                    if (angular.isDefined(list.builtin) && !list.builtin) {
                        vm.listName = angular.isDefined(list.customerCreatedName) ? list.customerCreatedName : '';
                    } else {
                        if (angular.isDefined(list.name)) {
                            vm.listName = list.name[vm.locale.language] || '';
                        } else {
                            vm.listName = list.customerCreatedName || '';
                        }
                    }
                } else {
                    logger.warn('List is not defined within response ', response);
                }
            });
        }

        if (vm.profileId) {
            UserProfileService.getUserProfiles().then(function success(response) {
                vm.profile = response.data.find(function (profile) {
                    return profile.id === Number(vm.profileId);
                });
            });
        }

        // Get all users to display as login-options and get current user to display for which user the
        // access is denied
        UserService.getUsers().then(function success(response) {
            const allUsers = response.data;
            vm.userList = [];
            allUsers.forEach((user) => {
                if (isUserThatCausedBlocking(user)) {
                    vm.user = user;
                } else if (user.containsPin && !user.system) {
                    vm.userList.push(user);
                }
            });
        });

        if (vm.reason.domain) {
            UserProfileService.getSearchEngineConfig().then(function(response) {
                vm.searchEngineConfig = response.data[vm.locale.language];
                vm.searchEngineConfig.iframe = $sce.trustAsHtml(vm.searchEngineConfig.iframe);
            });
        }
    }

    function isUserThatCausedBlocking(user) {
        return (angular.isDefined(vm.userId) && user.id.toString() === vm.userId) ||
            (angular.isDefined(vm.profileId) && user.associatedProfileId.toString() === vm.profileId );
    }

    function tryAgain() {
        if (!angular.isDefined(vm.waitToChange)) {
            actualTryAgain();
        } else {
            vm.waitToChange.then(function succ(response){
                actualTryAgain();
            }, function err(response){
                actualTryAgain();
            });
        }
    }

    function actualTryAgain() {
        $window.location.replace(vm.target);
    }

    function onChangeInternetAccess() {
        if (vm.internetAccessEnabled) {
            vm.waitToChange = onlineTime.startUsingRemainingTime();
        } else {
            vm.waitToChange = onlineTime.stopUsingRemainingTime();
        }
    }

    function back() {
        $window.history.back();
    }

    function changeUser(){
        DialogService.userProvidePin(vm.userList[0],
            vm.user.containsPin && vm.profileId !== 4,
            'selectUser', function(user, pin) {
                return UserService.setOperatingUser(user, pin);
            }, function() {
                return UserService.setOperatingUser(lockedUser);
            }, vm.userList);
    }

    function redirectToGenericAccessDeniedPage() {
        const newLocation = $window.location.toString().replace('/ERR_ACCESS_DENIED', '/GENERIC_ACCESS_DENIED');
        $window.location.replace(newLocation);
    }

    function redirectToCustomDomainBlockedPage(domain, listId, target) {
        let location = $window.location.toString().replace(/ERR_ACCESS_DENIED.*/, 'EBLKR_BLOCKED_ADS_TRACKERS');
        location += '?category=CUSTOM&domain=' + domain + '&listId=' + listId + '&target=' +
            $window.encodeURIComponent(target);
        $window.location.replace(location);
    }

}
