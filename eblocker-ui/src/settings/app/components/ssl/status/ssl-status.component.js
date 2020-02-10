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
    templateUrl: 'app/components/ssl/status/ssl-status.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        caOptions: '<',
        devices: '<'
    }
};

function Controller(DialogService, SslService, $translate, $filter, SslSuggestionsService, StateService, STATES) {
    'ngInject';

    const vm = this;

    vm.setSSLState = setSSLState;
    vm.openHelp = openHelp;

    vm.ssl = SslService.getSslSettings();

    function setSSLState(event){
        const isDeviceUsePatternFilter = deviceUsePatternFilter(vm.devices);
        const isDeviceControlBarOn = deviceControlBarOn(vm.devices);

        if(vm.ssl.enabled){
            vm.ssl.step = 0;
            vm.ssl.generateCa = !vm.ssl.certificatesReady;
            vm.ssl.renew = false;
            showWarningDialog(event);
        } else if (isDeviceUsePatternFilter || isDeviceControlBarOn) {
            const msgKeys = {
                additionalText: 'ADMINCONSOLE.DIALOG.SSL_DISABLE_WARNING.TEXT_CLOAKING_ON'
            };
            if (isDeviceUsePatternFilter && !isDeviceControlBarOn) {
                msgKeys.text = 'ADMINCONSOLE.DIALOG.SSL_DISABLE_WARNING.TEXT_IS_PATTERN';
            } else if (isDeviceControlBarOn && !isDeviceUsePatternFilter) {
                msgKeys.text = 'ADMINCONSOLE.DIALOG.SSL_DISABLE_WARNING.TEXT_HAS_ICON';
            } else {
                msgKeys.text = 'ADMINCONSOLE.DIALOG.SSL_DISABLE_WARNING.TEXT_BOTH';
            }
            msgKeys.title = 'ADMINCONSOLE.DIALOG.SSL_DISABLE_WARNING.TITLE';
            msgKeys.okButton = 'ADMINCONSOLE.DIALOG.SSL_DISABLE_WARNING.ACTION.OK';
            DialogService.sslDisableWarning(event, sendSSLStateToBackend, vm.ssl.enabled, msgKeys);
        } else {
            sendSSLStateToBackend(vm.ssl.enabled);
        }
    }

    function deviceUsePatternFilter(devices) {
        let ret = false;
        devices.forEach((dev) => {
            if ((dev.enabled || dev.paused) && dev.filterMode === 'ADVANCED' && !dev.isEblocker && !dev.isGateway) {
                ret = true;
            }
        });
        return ret;
    }

    function deviceControlBarOn(devices) {
        let ret = false;
        devices.forEach((dev) => {
            if ((dev.enabled || dev.paused) && dev.iconMode !== 'OFF' && !dev.isEblocker && !dev.isGateway) {
                ret = true;
            }
        });
        return ret;
    }

    function loadRenewalCertificateStatus() {
        SslService.getUpdatedSettingsRenewalStatus().then(function(settings){
            vm.ssl = settings;
        });
    }

    function showWarningDialog(event) {
        return DialogService.sslStatusWizard(event, vm.ssl, vm.caOptions).then(function success() {
            loadRenewalCertificateStatus();
            // User updated SSL setting, so update service as well
            SslService.setSslSettings(vm.ssl); // FIXME: should not be set here
        }, function cancel() {
            // user canceled dialog, reset local SSL setting
            vm.ssl = SslService.getSslSettings();
        });
    }

    function sendSSLStateToBackend(sslState, caOptions) {
        return SslService.setStatus({enabled: sslState, caOptions: caOptions});
    }

    function openHelp() {
        const url = $translate.instant('ADMINCONSOLE.SSL.HELP_LINK');
        window.open(url, '_blank');
    }
}
