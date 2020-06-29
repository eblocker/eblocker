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
export default function UpdateLicenseDialogController($mdDialog, RegistrationService, $translate, TosService,
                                                      UrlService, $window, $timeout, $q, LanguageService) {
    'ngInject';

    const vm = this;

    vm.processing = false;

    vm.backendErrorKey = undefined;

    vm.confirmed = false;

    vm.licenseKey = 'FAMLFT-OPENSOURCE';

    vm.emailAddress = '';

    setTosContent();

    // vm.licenseForm.licenseKey.$error = undefined;

    vm.licenseForm = {
        licenseKey: {
            $error: undefined
        }
    };

    // if server responds with needsConfirmation, doConfirm will update the Dialog's template
    // to show the confirm-dialog
    vm.doConfirm = false;
    vm.nextStep = nextStep;
    vm.prevStep = prevStep;
    vm.isStepAllowed = isStepAllowed;

    function nextStep() {
        if (isStepAllowed(vm.currentStep + 1)) {
            vm.currentStep++;
        }
    }

    function prevStep() {
        if (vm.currentStep > 0) {
            vm.currentStep--;
        }
    }

    function isStepAllowed(nextStep) {
        if (nextStep === 0) {
            return !vm.doConfirm;
        } else if (nextStep === 1) {
            // If no tos has been loaded user cannot proceed.
            return vm.isTosConfirmed && vm.isTosValid() && !vm.doConfirm;
        } else if (nextStep === 2)  {
            return vm.doConfirm && vm.isTosConfirmed && vm.isTosValid();
        }
        return true;
    }

    // TAB TOS
    function setTosContent() {
        const langKey = $translate.use();
        vm.tosIsLoading = true;
        $q.all([TosService.getTos(langKey),
            TosService.getTosHtml(langKey)]).then(function success(response) {
            vm.tos = response[0];
            vm.tos.licenseDateDisplay = LanguageService.getDate(vm.tos.licenseDate,
                    'ADMINCONSOLE.ACTIVATION.TAB.TOS.DATE_FORMAT');
            vm.tosPrint = response[1];
        }).finally(function done() {
            vm.tosIsLoading = false;
        });
    }

    vm.readToPrint = function() {
        return vm.tosIsLoading !== true && angular.isObject(vm.tosPrint);
    };

    vm.isTosValid = function() {
        return angular.isDefined(vm.tos.licenseText) &&
            angular.isDefined(vm.tos.licenseVersion) &&
            angular.isDefined(vm.tos.licenseDate);
    };

    vm.openPrintView = openPrintView;

    function openPrintView() {
        const param = {
            tosText: vm.tosPrint.licenseText,
            tosVersion: vm.tosPrint.licenseVersion,
            tosDate: vm.tosPrint.licenseDate,
            heading: 'ADMINCONSOLE.ACTIVATION.TAB.TOS.HEADING',
            templateUrl: 'app/components/activation/print-license.template.html'
        };
        const printUrl = UrlService.getPrintViewUrl(param);
        $timeout(function(){
            // ** timeout need for digest cycle to finish, so that localStorage is saved.
            // --> "Watch the watch" in https://github.com/gsklee/ngStorage
            // with 100 ms TS it works pretty reliably, with 0 TS it doesn't.
            $window.open(printUrl, '_blank');
        }, 100);
    }
    // END TAB TOS

    vm.cancel = function () {
        $mdDialog.cancel();
    };

    vm.ok = function () {

        // if vm.confirmed, then we have already validated the form and we can send it again.
        if (!vm.confirmed) {
            vm.licenseForm.licenseKey.$setValidity('backend', true);
            //
            // Any other form error?
            //
            if (!vm.licenseForm.$valid) {
                return;
            }
        }

        vm.processing = true;

        return RegistrationService.register({
            emailAddress: vm.emailAddress,
            licenseKey: vm.licenseKey,
            confirmed: vm.confirmed,
            tosVersion: vm.tos.licenseVersion
        }).then(function (response) {
            if (angular.isDefined(response.data) && response.data.needsConfirmation) {
                vm.confirmationMsgKeys = response.data.confirmationMsgKeys;
                vm.doConfirm = true;
                vm.processing = false;
                vm.confirmed = true;
                vm.nextStep();
            } else {
                $mdDialog.hide(response);
            }
        }, function (backendErrorKey) {
            // ** backendErrorKey is just the last part of the translation key, concated with rest in template
            vm.backendErrorKey = backendErrorKey;
            vm.licenseForm.licenseKey.$setValidity('backend', false);
            vm.processing = false;
            // jump back to license key tab
            vm.doConfirm = false;
            vm.currentStep = 1;
            vm.confirmed = false;
        });
    };
}
