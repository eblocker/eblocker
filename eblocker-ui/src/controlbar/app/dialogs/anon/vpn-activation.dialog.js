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
export default function TorActivationDialogController($scope, $mdDialog, $interval, VpnService, profile, logger) {
    'ngInject';

    const vm = this;

    vm.profile = profile;

    vm.vpnConnection = {
        poller: null,
        status: null,
        profile: profile,
        elapsed: 0,
        timeout: 60000,
        interval: 2000,
        isTimeout: function() {
            return this.elapsed >= this.timeout;
        }
    };

    function cancelPoller() {
        if (angular.isObject(vm.vpnConnection.poller)) {
            logger.info('canceling poller ', vm.vpnConnection.poller);
            $interval.cancel(vm.vpnConnection.poller);
            vm.vpnConnection.poller = undefined;
        }
    }

    let connectionDialogClose = function() {
        cancelPoller();
        $mdDialog.hide();
    };

    vm.connectionDialogCancel = function() {
        VpnService.setVpnDeviceStatus(profile.id, 'me', false);
        connectionDialogClose();
    };

    vm.connectionDialogContinue = function() {
        connectionDialogClose();
    };

    vm.connectionDialogBlankPage = function() {
        window.top.location.href = 'about:blank';
    };

    VpnService.setVpnDeviceStatus(profile.id, 'me', true).then(function success(){
        vm.vpnConnection.poller = $interval(function() {
            vm.vpnConnection.elapsed += vm.vpnConnection.interval;
            if (vm.vpnConnection.isTimeout()) {
                cancelPoller();
                VpnService.setVpnDeviceStatus(profile.id, 'me', false);
            } else {
                VpnService.getVpnStatus(profile.id).then(function success(response) {
                    let vpnStatus = response.data;
                    vm.vpnConnection.status = vpnStatus;
                    if (vpnStatus.up) {
                        cancelPoller();
                    } else if (angular.isObject(vpnStatus.exitStatus)) {
                        cancelPoller();
                    }
                }, function error(response) {
                    logger.error('Unable to get VPN status ', response);
                });
            }
        }, vm.vpnConnection.interval);
    });

    $scope.$on('$destroy', cancelPoller);
}
