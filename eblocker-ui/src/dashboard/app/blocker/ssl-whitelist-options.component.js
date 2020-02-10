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
    templateUrl: 'app/blocker/ssl-whitelist-options.component.html',
    controllerAs: 'vm',
    controller: Controller,
    bindings: {
        productInfo: '<',
        locale: '<'
    }
};

function Controller($stateParams, $window, $timeout, DomainUtilsService, TorService, SslService) {
    'ngInject';

    const vm = this;

    vm.truncateDomain = DomainUtilsService.truncateDomain;
    vm.addAndContinue = addAndContinue;
    vm.back = back;
    vm.isBackButtonAvailable = $window.history.length > 1;

    activate();

    function activate() {
        vm.target = $stateParams['target'];
        vm.token = $stateParams['token'];

        // get application-specific error code:
        const appErrorCode = $stateParams['appErrorCode'];

        if (appErrorCode === 'SQUID_ERR_SSL_HANDSHAKE') {
            // This might really be an IP anonymization problem
            TorService.getDeviceConfig().then(function(response) {
                if (response.data.sessionUseTor) {
                    redirectToZeroSizeObjectError();
                }
            });
        }

        // get error message
        const openSslMessages = atob($stateParams['error']).split('|');
        vm.error = openSslMessages[0];

        // regex src: https://tools.ietf.org/html/rfc3986#appendix-B
        vm.cn = vm.target.match(/^(([^:\/?#]+):)?(\/\/([^\/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?/)[4];

        vm.newDomain = {
            label: vm.cn,
            domains: [ vm.cn ]
        };
    }

    function redirectToZeroSizeObjectError() {
        const newLocation = $window.location.toString().replace('/ERR_SECURE_CONNECT_FAIL', '/ERR_ZERO_SIZE_OBJECT');
        $window.location.replace(newLocation);
    }

    function addAndContinue() {
        vm.waiting = true;
        SslService.whitelistDomain(vm.newDomain).then(function success() {
            $timeout(function() {
                $window.location.replace(vm.target);
            }, 1000);
        });
    }

    function back() {
        $window.history.back();
    }
}
