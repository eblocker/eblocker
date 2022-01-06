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
    templateUrl: 'app/reminder/reminder.component.html',
    controllerAs: 'vm',
    controller: Controller,
    bindings: {
        locale: '<',
        device: '<',
        productInfo: '<'
    }
};

function  Controller(logger, $window, $translate, $filter, $http, $sce, $q, DeviceService, NotificationService, // jshint ignore: line
                     ConsoleService, RegistrationService, LoadingService, DialogService, moment, LanguageService,
                     AutoCloseService) {
    'ngInject';

    const vm = this;

    vm.close = close;
    vm.buttonEnterLicenseKeyClicked = buttonEnterLicenseKeyClicked;
    vm.buttonBuyLicenseKeyClicked = buttonBuyLicenseKeyClicked;
    vm.buttonSetRemindAgain = buttonSetRemindAgain;
    vm.changeShowReminder = changeShowReminder;
    vm.stopCountdown = stopCountdown;

    let purchaseUrl;

    vm.$onInit = function() {

        // allow to dynamically load reminder iFrame images
        angular.element($window).on('resize', resizeWatcher);

        vm.registrationInfo = RegistrationService.getRegistrationInfo();

        const validDays = determineValidDays();
        // 'E0', 'E1', 'E2', 'E3'
        vm.licensePhase = determineLicensePhase(validDays);

        $translate('REMINDER.OVERLAY.URL.PROMO').then(function(url) {
            vm.url = url;
            resizeWatcher();
        });

        $translate('REMINDER.OVERLAY.URL.PURCHASE').then(function(url) {
            purchaseUrl = url;
        });

        vm.context = {
            days: Math.abs(validDays),
            date: vm.registrationInfo.licenseNotValidAfter,
            displayDate: LanguageService.getDate(vm.registrationInfo.licenseNotValidAfter, 'REMINDER.FORMAT.DATE')
        };

        // By default let reminder reappear tomorrow.
        vm.remindWhenAgain = 'day';
        // By default the reminder only shown once. EB1-2131: on SmartTV a 'Mediathek' will show this reminder as well,
        // so we do not want to annoy the customer.
        vm.doNotShowAgain = true;
        saveRemindWhenAgain();

        vm.countdownConfig = {seconds: AutoCloseService.getSeconds(), expiredFn: close};
        AutoCloseService.startCountdown(vm.countdownConfig);
    };

    vm.$onDestroy = function() {
        AutoCloseService.stopCountdown();
        angular.element($window).off('resize', resizeWatcher);
    };

    vm.$postLink = function() {
        LoadingService.isLoading(false);
    };

    function stopCountdown() {
        AutoCloseService.stopCountdown();
        vm.countdownStopped = true;
    }

    let xsDone, smDone, lgDone;
    function resizeWatcher() {
        if ($window.innerWidth < 600 && !xsDone) {
            xsDone = true;
            displayIframe(vm.url, 'xs');
        } else if ($window.innerWidth < 960 && !smDone) {
            smDone = true;
            displayIframe(vm.url, 'sm');
        } else if (!lgDone) {
            lgDone = true;
            displayIframe(vm.url);
        }
    }

    function close() {
        ConsoleService.close();
    }

    function confirmationDialogOk() {
        return saveRemindWhenAgain();
    }

    function confirmationDialogCancel() {
        // do nothing - set state back to false
        vm.doNotShowAgain = false;
    }

    function changeShowReminder(event) {
        // Show dialog and ask for confirmation when the user checked the checkbox
        if (vm.doNotShowAgain) {
            DialogService.confirmDoNotShowReminderAgain(event, confirmationDialogOk, confirmationDialogCancel);
        } else {
            // Do not show dialog, but save 'show reminder' anyway
            saveRemindWhenAgain();
        }
    }

    function buttonEnterLicenseKeyClicked(){
        $window.open('/#/general', '_blank');
    }

    function buttonBuyLicenseKeyClicked(){
        $window.open(purchaseUrl, '_blank');
    }

    function buttonSetRemindAgain(){
        saveRemindWhenAgain().then(function() {
            // tell reminder to close
            const data = {'type':'close-eblocker-overlay'};
            $window.parent.postMessage(data, '*');
        });
    }

    function saveRemindWhenAgain() {
        const data = {selection: vm.remindWhenAgain, doNotShowAgain: vm.doNotShowAgain};
        // also tell backend when to remind again
        return $http.post('/api/advice/reminder', data).then(function(){
            // success
        }, function() {
            // failure
            logger.error('Unable to save reminder.');
            return $q.reject();
        });
    }

    function displayIframe(url, screenSize){
        let width, height, iframeId;
        if (screenSize === 'xs') {
            width = '300px';
            height = '240px';
            iframeId = 'eblocker-reminder-iframe-container-xs';
        } else if (screenSize === 'sm') {
            width = '500px';
            height = '281px';
            iframeId = 'eblocker-reminder-iframe-container-sm';
        } else {
            width = '800px';
            height = '450px';
            iframeId = 'eblocker-reminder-iframe-container';
        }

        const urlTrusted = $sce.trustAsResourceUrl(url);
        // Create iframe
        const iframe = document.createElement('iframe');
        iframe.setAttribute('id', 'eblocker-reminder-iframe');
        iframe.setAttribute('src', urlTrusted);
        iframe.setAttribute('width', width);
        iframe.setAttribute('height', height);
        iframe.setAttribute('style', 'border:none;');

        // Insert it into reminder
        document.getElementById(iframeId).appendChild(iframe);
    }

    function determineValidDays() {
        if (!angular.isDefined(vm.registrationInfo)) {
            return 14;
        }
        if (!angular.isDefined(vm.registrationInfo.licenseNotValidAfter)) {
            return 14;
        }
        return Math.floor((vm.registrationInfo.licenseNotValidAfter.getTime() - new Date().getTime()) / 86400000);
    }

    function determineLicensePhase(validDays) {
        if (validDays > 31) {
            return 'E0';
        }
        if (validDays > 7) {
            return 'E1';
        }
        if (validDays > 0) {
            return 'E2';
        }
        if (validDays > -90) {
            return 'E3';
        }
        return 'E4';
    }

}
