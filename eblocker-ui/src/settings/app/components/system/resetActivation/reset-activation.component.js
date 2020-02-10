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
    templateUrl: 'app/components/system/resetActivation/reset-activation.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller($mdDialog, NotificationService, StateService, STATES, RegistrationService) {
    'ngInject';
    'use strict';

    const vm = this;
    const registrationInfo = RegistrationService.getRegistrationInfo();

    vm.openLicenseResetDialog = openLicenseResetDialog;
    vm.isRegistrationStateNew = isRegistrationStateNew;
    vm.isEvaluationLicense = isEvaluationLicense;

    function isRegistrationStateNew() {
        return registrationInfo.registrationState === 'NEW';
    }

    function openLicenseResetDialog() {
        return $mdDialog.show({
            controller: 'RegistrationResetDialogController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/reset/registration-reset.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false
        }).then(function() {
            NotificationService.info('ADMINCONSOLE.RESET_ACTIVATION.NOTIFICATION.SUCCESS');
            StateService.setWorkflowState(STATES.HOME);
            StateService.goToState(STATES.AUTH);
        });
    }

    function isEvaluationLicense() {
        return RegistrationService.isEvaluationLicense();
    }

}
