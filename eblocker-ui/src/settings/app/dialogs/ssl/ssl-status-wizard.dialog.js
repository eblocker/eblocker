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
export default function SslStatusWizardController(logger, $mdDialog, ssl, caOptions, SslService, $translate,
        LanguageService) {
    'ngInject';

    const vm = this;

    vm.ssl = ssl;
    vm.caOptions = caOptions || {
        validityInMonths: 24,
        distinguishedName: {
            commonName: ''
        }
    };

    vm.certName = {
        value: ''
    };

    vm.certNotAfter = {
        value: ''
    };

    if (vm.ssl.renew && angular.isObject(vm.ssl.rootCertificate)) {
        const dn = vm.ssl.rootCertificate.distinguishedName;
        vm.certName.value = angular.isObject(dn) && angular.isDefined(dn.commonName) ? dn.commonName : '';
        vm.certNotAfter.value = vm.ssl.rootCertificate.notAfter;
    }

    vm.hide = function() {
        $mdDialog.hide(vm.ssl);
    };

    vm.cancel = function() {
        $mdDialog.cancel(vm.ssl);
    };

    // vm.save = function() {
    //     const ssl = vm.ssl;
    //     // VpnService.updateProfile(ssl).then(function(response) {
    //     //     $mdDialog.hide(response.data);
    //     // });
    // };

    vm.reEnableSSL = function(){
        vm.ssl.enabled = true;
        sendSSLStateToBackend(vm.ssl.enabled);
        // closeDialog(warningDialog);
        vm.hide();
    };

    vm.okEnableSSL = function(){
        if (!vm.newCertForm.$valid) {
            return;
        }
        vm.ssl.enabled = true;
        vm.ssl.waitingForCertificates = true;
        sendSSLStateToBackend(vm.ssl.enabled, vm.caOptions).then(function success() {
            SslService.getRootCa().then(vm.showSummary);
        });
    };

    vm.generateNewCa = function(){
        if (!vm.newCertForm.$valid) {
            vm.newCertForm.$submitted = true;
            return;
        }
        vm.ssl.waitingForCertificates = true;
        SslService.setRootCa(vm.caOptions).then(vm.showSummary, function(response) {
            vm.ssl.waitingForCertificates = false;
            vm.errorRootCa = true; // TODO show in UI
            logger.error('Unable to set root ca ', response);
        });
    };

    vm.showSummary = function(response) {
        const certificate = response.data;
        // If a Renewal Certificate was available, reload its status to hide its button/text
        // loadRenewalCertificateStatus();
        vm.ssl.rootCertificate = certificate;
        vm.ssl.certificatesReady = true;
        vm.ssl.waitingForCertificates = false;
        vm.ssl.step += 1;

        // format dates
        const dateFormat = $translate.instant('ADMINCONSOLE.DIALOG.SSL_STATUS_WIZARD.DATE_TIME_FORMAT');
        vm.ssl.rootCertificate.notBefore = LanguageService.getDate(vm.ssl.rootCertificate.notBefore, dateFormat);
        vm.ssl.rootCertificate.notAfter = LanguageService.getDate(vm.ssl.rootCertificate.notAfter, dateFormat);

        // insert line break into sha-256
        vm.ssl.rootCertificate.fingerprintSha256 =
            vm.ssl.rootCertificate.fingerprintSha256.substring(0, 48) + ' \n' +
            vm.ssl.rootCertificate.fingerprintSha256.substring(48);


        const dn = vm.ssl.rootCertificate.distinguishedName;
        vm.summaryCertName = {
            value: angular.isObject(dn) && angular.isDefined(dn.commonName) ? dn.commonName : ''
        };
        vm.summaryFingerprint = {
            value: vm.ssl.rootCertificate.fingerprintSha256
        };
        vm.summaryNotBefore = {
            value: vm.ssl.rootCertificate.notBefore
        };
        vm.summaryNotAfter = {
            value: vm.ssl.rootCertificate.notAfter
        };
    };

    vm.gotoStep = function(step) {
        vm.ssl.step = step;
    };

    function sendSSLStateToBackend(sslState, caOptions) {
        return SslService.setStatus({enabled: sslState, caOptions: caOptions});
    }
}
