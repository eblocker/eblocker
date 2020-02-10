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
    templateUrl: 'app/components/cloaking/cloaking.component.html',
    controller: CloakingController,
    controllerAs: 'ctrl'
};

function CloakingController(logger, UserAgentService, ArrayUtilsService, $mdDialog, SslService, DeviceService) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.selectedUserAgent = 'Off';
    vm.closeDropdown = {};

    let customUserAgentSpec;

    vm.setCustomUserAgent = setCustomUserAgent;
    vm.setUserAgent = setUserAgent;
    vm.showSslWarning = showSslWarning;

    function showSslWarning() {
        return angular.isDefined(vm.globalSslStatus) && angular.isDefined(vm.deviceSslEnabled) ?
            !vm.globalSslStatus || !vm.deviceSslEnabled : true;
    }

    function getUserAgentList() {
        UserAgentService.getUserAgentList().then(function success(response) {
            if (angular.isArray(response.data)) {
                let profiles = [];
                response.data.forEach((name) => {
                    profiles.push({
                        name: name,
                        isMobile: (name === 'iPad' || name === 'iPhone' || name === 'Android'),
                        isActive: isActive
                    });
                });
                vm.userAgentList = ArrayUtilsService.sortByPropertyName(profiles);
            }
        });
    }

    function getCloakedUserAgent() {
        UserAgentService.getCloakedUserAgentForDevice().then(function success(response) {
            if (angular.isObject(response.data)) {
                vm.selectedUserAgent = response.data.agentName;
                customUserAgentSpec = response.data.agentSpec;
            } else {
                logger.error('Received invalid data for cloaked user agent ', response);
            }
        });
    }

    function setCustomUserAgent() {
        showCustomUserAgentDialog().then(function(value) {
            UserAgentService.setUserAgent('Custom', value).then(function success() {
                vm.selectedUserAgent = 'Custom';
            });
        }, function() {
            logger.info('User canceled custom user agent dialog.');
        });
        vm.closeDropdown.now();
    }

    function setUserAgent(userAgent) {
        if (isActive(userAgent) && userAgent === 'Off') {
            return;
        }
        const newAgent = isActive(userAgent) ? 'Off' : userAgent;
        UserAgentService.setUserAgent(newAgent).then(function success() {
            vm.selectedUserAgent = newAgent;
        });
    }

    function isActive(profile) {
        return vm.selectedUserAgent === profile;
    }

    function showCustomUserAgentDialog() {
        return $mdDialog.show({
            controller: 'CustomUserAgentDialogController',
            controllerAs: 'ctrl',
            templateUrl: 'app/dialogs/cloaking/custom-userAgent.dialog.html',
            parent: angular.element(document.body),
            clickOutsideToClose:true,
            locals: {
                customUserAgentSpec: customUserAgentSpec
            }
        });
    }

    vm.$onInit = function() {
        SslService.getSslStatus().then(function(response) {
            vm.globalSslStatus = response.data.globalSslStatus;
        });
        DeviceService.getDevice().then(function success(response) {
            vm.deviceSslEnabled = response.data.sslEnabled;
        });
        getUserAgentList();
        getCloakedUserAgent();
    };
}
