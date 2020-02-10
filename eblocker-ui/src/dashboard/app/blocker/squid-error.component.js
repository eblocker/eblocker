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
    templateUrl: 'app/blocker/squid-error.component.html',
    controllerAs: 'vm',
    controller: Controller
};

function Controller(logger, $stateParams, $window, $translate, $timeout, DomainUtilsService, TorService, VpnService) {
    'ngInject';

    const vm = this;

    vm.truncateDomain = DomainUtilsService.truncateDomain;
    vm.torEnabled = false;
    vm.vpnEnabled = false;
    vm.torConfig = {};
    vm.vpnStatus = {};
    vm.waiting = false;
    vm.reasons = [];
    vm.tryAgain = tryAgain;
    vm.disableTorAndTryAgain = disableTorAndTryAgain;
    vm.disableVpnAndTryAgain = disableVpnAndTryAgain;

    vm.$onInit = function() {
        activate();
    };

    function activate() {
        vm.target = $stateParams['target'];
        vm.token = $stateParams['token'];
        vm.error = $stateParams['error'];

        try {
            vm.errorDetails = atob($stateParams['errorDetails']);
        } catch(error) {
            logger.error('Unable to decode error details: ', error);
        }
        if (!angular.isDefined(vm.errorDetails) || vm.errorDetails === '') {
            vm.errorDetails = '-';
        }

        $translate('SQUID_BLOCKER.SQUID_ERROR.REASON.' + vm.error).then(function(translation) {
            vm.reasons = translation.split('\n');
        });

        TorService.getDeviceConfig().then(function(response) {
            logger.debug('Current device config: ', response.data);
            vm.torConfig = response.data;
            vm.torEnabled = response.data.sessionUseTor;
        });

        VpnService.getVpnStatusByDevice('me').then(function success(response) {
            logger.debug('vpn status', response.data);
            vm.vpnStatus = response.data;
            vm.vpnEnabled = response.data !== '';
        });
    }

    function tryAgain() {
        $window.location.replace(vm.target);
    }

    function timedOut() {
        $window.location.replace(vm.target);
    }

    function disableTorAndTryAgain() {
        logger.debug('disabling tor and retrying...');
        vm.waiting = true;
        vm.torConfig.sessionUseTor = false;
        logger.debug('setting conf', vm.torConfig);
        TorService.setDeviceConfig(vm.torConfig).then(function() {
            logger.debug('Updated anonymization configuration: ', vm.torConfig);
            $timeout(timedOut, 1000);
        });
    }

    function disableVpnAndTryAgain() {
        logger.debug('disabling vpn and retrying...');
        logger.debug('current status', vm.vpnStatus);
        vm.waiting = true;

        VpnService.setVpnThisDeviceStatus(vm.vpnStatus.profileId).then(function success() {
            $timeout(timedOut, 2000);
        });
    }

}
