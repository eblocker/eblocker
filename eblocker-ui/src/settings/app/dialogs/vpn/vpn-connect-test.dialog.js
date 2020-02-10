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
export default function VpnConnectionTestController(logger, $mdDialog, $interval, profile, VpnService) {
    'ngInject';

    const vm = this;

    const cancelPoller = function() {
        if (vm.vpnTest.poller != null) {
            $interval.cancel(vm.vpnTest.poller);
            vm.vpnTest.poller = null;
        }
    };

    vm.close = function() {
        cancelPoller();
        $mdDialog.hide();
    };

    vm.vpnTest = {
        profile: profile,
        elapsed: 0,
        timeout: 60000,
        interval: 2000
    };

    VpnService.setVpnStatus(profile, {active: true}).then(function success() {
        vm.vpnTest.poller = $interval(function() {
            vm.vpnTest.elapsed += vm.vpnTest.interval;
            if (vm.vpnTest.elapsed >= vm.vpnTest.timeout) {
                cancelPoller();
                vm.vpnTest.status = 'timeout';
                VpnService.setVpnStatus(profile, {active: false});
            } else {
                VpnService.getVpnStatus(profile).then(function success(response) {
                    const vpnStatus = response.data;
                    vm.vpnTest.vpnStatus = vpnStatus;
                    vm.vpnTest.errors = angular.isDefined(vpnStatus.errors) ? vpnStatus.errors.join('\n') : '';
                    if (vpnStatus.up) {
                        cancelPoller();
                        vm.vpnTest.status = 'success';
                        VpnService.setVpnStatus(profile, {active: false});
                    } else if (vpnStatus.exitStatus != null) {
                        cancelPoller();
                        vm.vpnTest.status = 'failed';
                    } else if (hasAuthenticationFailure(vm.vpnTest.errors)) {
                        cancelPoller();
                        vm.vpnTest.status = 'auth_failed';
                    }
                }, function error(response) {
                    logger.error('Error while getting VPN status from server ', response);
                    cancelPoller();
                    vm.vpnTest.status = 'failed';
                });
            }
        }, vm.vpnTest.interval);
    });

    function hasAuthenticationFailure(errors) {
        return errors.indexOf('AUTH_FAILED') > -1;
    }

    vm.hasErrors = function() {
        return angular.isString(vm.vpnTest.errors) && vm.vpnTest.errors.length > 0;
    };

    vm.$onDestroy = function() {
        cancelPoller();
    };
}
