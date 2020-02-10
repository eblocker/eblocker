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
    templateUrl: 'app/cards/console/console.component.html',
    controller: ConsoleController,
    controllerAs: 'vm',
    bindings: {
        cardId: '@'
    }
};

function ConsoleController($timeout, LanguageService, RedirectService, NetworkService,
        LicenseService, CardService, registration) {
    'ngInject';
    'use strict';

    const vm = this;

    const CARD_NAME = 'CONSOLE'; // 'card-6'

    vm.getStatus = getStatus;
    vm.openConsole = openConsole;

    let isRegistered, isLicenseAboutToExpire;

    vm.$onInit = function() {

        const registrationInfo = registration.getRegistrationInfo();

        vm.isRegistered = angular.isUndefined(registrationInfo) ||
            angular.isUndefined(registrationInfo.isRegistered) ||
            registrationInfo.isRegistered;

        vm.isLicenseAboutToExpire = angular.isUndefined(registrationInfo) ||
            angular.isUndefined(registrationInfo.licenseAboutToExpire) ||
            registrationInfo.licenseAboutToExpire;

        NetworkService.getNetworkStatus().then(function success(response) {
            if (angular.isObject(response.data)) {
                vm.gateway = response.data.gateway;
                vm.eblocker = response.data.ipAddress;
                vm.device = response.data.userIpAddress;
            }
        });

        LicenseService.getLicenseStatus().then(function success(response) {
            if (angular.isObject(response.data)) {
                vm.license = response.data;
                vm.license.licenseNotValidAfterDisplay =
                    LanguageService.getDate(registrationInfo.licenseNotValidAfter, 'CONSOLE.CARD.LICENSE.FORMAT_DATE');
                if (vm.license.productInfo && vm.license.productInfo.productName) {
                    vm.license.displayProductName = vm.license.productInfo.productName.replace(/\s*\(.*\)\s*$/, '');
                }
            }
        });
    };

    vm.$postLink = function() {
        $timeout(function() {
            CardService.scrollToCard(CARD_NAME);
        }, 300);
    };

    function getStatus() {
        if (!vm.isRegistered) {
            return 'ERROR';
        } else if (vm.isLicenseAboutToExpire) {
            return 'WARN';
        }
        return '';
    }

    function openConsole() {
        RedirectService.toConsole(false);
    }
}
