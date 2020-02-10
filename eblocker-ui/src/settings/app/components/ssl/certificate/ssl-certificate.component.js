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
    templateUrl: 'app/components/ssl/certificate/ssl-certificate.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        caOptions: '<'
    }
};

function Controller(SslService, $translate, DialogService) {
    'ngInject';
    'use strict';

    const vm = this;
    vm.openHelp = openHelp;
    vm.createNewRootCA = createNewRootCA;

    vm.$onInit = function() {
        vm.ssl = SslService.getSslSettings();
        updateDisplayValues(vm.ssl);
    };

    function updateDisplayValues(ssl) {
        vm.renewName = {
            value: ssl.display.renewalCn
        };
        vm.renewNotBefore = {
            value: ssl.display.renewalValidityNotBefore
        };
        vm.renewNotAfter = {
            value: ssl.display.renewalValidityNotAfter
        };
        vm.currentName = {
            value: ssl.display.cn
        };
        vm.currentNotBefore = {
            value: ssl.display.validityNotBefore
        };
        vm.currentNotAfter = {
            value: ssl.display.validityNotAfter
        };
    }


    function createNewRootCA(event) {
        // keep values in case user clicks cancel and
        // we need to reenable the buttons ..
        // FIXME: concept vm.ssl / SslSerivce.sslSettings not really working out.
        let step = vm.ssl.step;
        let certificatesReady = vm.ssl.certificatesReady;
        let generateCa = vm.ssl.generateCa;
        let renew = vm.ssl.renew;
        vm.ssl.step = 0;
        vm.ssl.certificatesReady = false;
        vm.ssl.generateCa = true;
        vm.ssl.renew = true;
        SslService.setSslSettings(vm.ssl); // FIXME: should not be set here
        SslService.getRootCa().then(function() {
            vm.ssl = SslService.getSslSettings();
            DialogService.sslStatusWizard(event, vm.ssl, vm.caOptions).then(function ok(ssl) {
                SslService.getUpdatedSettingsRenewalStatus().then(function(settings){
                    SslService.getSslCertStatus().then(function () {
                        vm.ssl = SslService.getSslSettings();
                        updateDisplayValues(vm.ssl);
                    });
                    // vm.ssl = settings;
                });
            }, function cancel(ssl) {
                vm.ssl = ssl;
                // on cancel, reset the values ..
                vm.ssl.step = step;
                vm.ssl.certificatesReady = certificatesReady;
                vm.ssl.generateCa = generateCa;
                vm.ssl.renew = renew;
                SslService.setSslSettings(vm.ssl); // FIXME: should not be set here
                updateDisplayValues(vm.ssl);
            });
        });
    }

    function openHelp() {
        const url = $translate.instant('ADMINCONSOLE.SSL.HELP_LINK');
        window.open(url, '_blank');
    }

}
