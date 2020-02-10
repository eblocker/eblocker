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
    templateUrl: 'app/welcome/welcome.component.html',
    controllerAs: 'vm',
    controller: MainController,
    bindings: {
        locale: '<',
        device: '<',
        productInfo: '<'
    }
};

function  MainController($window, AutoCloseService, DeviceService, NotificationService, ConsoleService,
                         RegistrationService, LoadingService) {
    'ngInject';

    const vm = this;

    let closeDialogPromise;

    vm.onChange = onChange;
    vm.goToDashboard = goToDashboard;
    vm.stopCountdown = stopCountdown;
    vm.close = close;

    vm.$onInit = function() {
        vm.isBas = isBase();
        vm.isFamOrPro = isFamOrPro();

        // default is not to show the dialog again. So we need to update the device as well.
        vm.notAgain = true;
        onChange();

        vm.countdownConfig = {seconds: AutoCloseService.getSeconds(), expiredFn: close};
        AutoCloseService.startCountdown(vm.countdownConfig);
    };

    vm.$postLink = function() {
        LoadingService.isLoading(false);
    };

    vm.$onDestroy = function() {
        AutoCloseService.stopCountdown();
    };

    function stopCountdown() {
        AutoCloseService.stopCountdown();
        vm.countdownStopped = true;
    }

    function close() {
        ConsoleService.close();
    }

    function isBase() {
        return RegistrationService.hasProductKey('BAS') && // has BAS
              !RegistrationService.hasProductKey('FAM') && // but not FAM
              !RegistrationService.hasProductKey('PRO');   // and not PRO
    }

    function isFamOrPro() {
        return RegistrationService.hasProductKey('FAM') || RegistrationService.hasProductKey('PRO');
    }

    function goToDashboard() {
        ConsoleService.goToDashboard(false);
    }

    function onChange() {
        vm.device.showWelcomePage = !vm.notAgain;
        let flags = {
            showWelcomePage: vm.device.showWelcomePage,
            showBookmarkDialog: vm.device.showBookmarkDialog
        };
        DeviceService.updateShowWelcomeFlags(flags).then(function success() {
            let note = 'ADVICE.NOTIFICATION.';
            note = vm.device.showWelcomePage ? note + 'UPDATE_HINT' :  note + 'UPDATE_NO_HINT';
            NotificationService.info(note);
        }, function error(response) {
            NotificationService.error('ADVICE.NOTIFICATION.UPDATE_ERROR', response);
        });
    }
}
