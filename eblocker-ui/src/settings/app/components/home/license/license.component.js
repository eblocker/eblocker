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
    templateUrl: 'app/components/home/license/license.component.html',
    controller: LicenseController,
    controllerAs: 'vm',
    bindings: {
        setupInfo: '<'
    }
};

function LicenseController(logger, $translate, $filter, DialogService, RegistrationService,
                           StateService, STATES, NotificationService, LanguageService) {
    'ngInject';

    const vm = this;
    const PRO_DEMO_STRING = 'EBL30DAYSTEST';

    vm.openLicenseDialog = openLicenseDialog;
    vm.openSetupWizard = openSetupWizard;
    vm.showSetupWizardButton = showSetupWizardButton;

    vm.$onInit = function() {
        setValues();
    };

    vm.licenseType = {
        value: ''
    };

    vm.registrationState = {
        value: ''
    };

    vm.validFrom = {
        value: ''
    };

    vm.validTill = {
        value: ''
    };

    vm.registeredBy = {
        value: ''
    };

    vm.deviceName = {
        value: ''
    };

    function setValues() {
        let registrationInfo = RegistrationService.getRegistrationInfo() || {};
        vm.showActivationButton = registrationInfo.registrationState !== 'NEW';
        vm.showValidTill = !registrationInfo.licenseLifetime;

        vm.licenseType.value = getLicenseType(registrationInfo.productInfo);
        vm.registrationState.value = getRegistrationState(registrationInfo);
        vm.validFrom.value = getDateFrom(registrationInfo.deviceRegisteredAt);
        vm.validTill.value = getDateTill(registrationInfo.licenseLifetime, registrationInfo.licenseNotValidAfter);
        vm.registeredBy.value = getRegisteredBy(registrationInfo.deviceRegisteredBy);
        vm.deviceName.value = getDeviceName(registrationInfo.deviceName);
    }

    function getLicenseType(productInfo) {
        let productType = 'WOL';
        if (angular.isDefined(productInfo) &&
            angular.isDefined(productInfo.productFeatures)) {
            if (RegistrationService.hasProductKey('EVL_FAM')) {
                productType = 'FAM_DEMO';
            } else if (RegistrationService.hasProductKey('FAM')) {
                productType = 'FAM';
            } else if (RegistrationService.hasProductKey('PRO')) {
                // It can be a regular Pro-License or a 30 day demo license
                productType = angular.isDefined(productInfo.productId) &&
                productInfo.productId === PRO_DEMO_STRING ? 'PRO_DEMO' : 'PRO';
            } else if (RegistrationService.hasProductKey('BAS')) {
                productType = 'BAS';
            }

        }
        return 'ADMINCONSOLE.LICENSE.LICENSE_TYPE.PRODUCT_TYPE.' + productType;
    }

    function getDateFrom(validFrom) {
        const dateFormat =  $translate.instant('ADMINCONSOLE.LICENSE.VALID_FROM.DATE_FORMAT');
        return angular.isDefined(validFrom) ?
            LanguageService.getDate(validFrom, dateFormat) :
            'ADMINCONSOLE.LICENSE.VALID_FROM.UNREGISTERED';
    }

    function getDateTill(lifetime, validTill) {
        const dateFormatTill =  $translate.instant('ADMINCONSOLE.LICENSE.VALID_TILL.DATE_FORMAT');
        if (lifetime) {
            return 'ADMINCONSOLE.LICENSE.VALID_TILL.LIFETIME';
        } else if (angular.isDefined(validTill)) {
            return LanguageService.getDate(validTill, dateFormatTill);
        } else {
            return 'ADMINCONSOLE.LICENSE.VALID_TILL.UNREGISTERED';
        }
    }

    function getRegisteredBy(registeredBy) {
        if (angular.isDefined(registeredBy)) {
            return registeredBy;
        } else {
            return 'ADMINCONSOLE.LICENSE.REGISTERED_BY.UNREGISTERED';
        }
    }

    function getDeviceName(deviceName) {
        if (angular.isDefined(deviceName)) {
            return deviceName;
        } else {
            return 'ADMINCONSOLE.LICENSE.REGISTERED_BY.UNREGISTERED';
        }
    }

    function getRegistrationState(registrationInfo) {
        if (registrationInfo.licenseExpired) {
            return 'ADMINCONSOLE.LICENSE.REGISTRATION_STATE.STATES.EXPIRED';
        } else if (registrationInfo.registrationState === 'REVOKED') {
            return 'ADMINCONSOLE.LICENSE.REGISTRATION_STATE.STATES.REVOKED';
        } else if (registrationInfo.licenseAboutToExpire) {
            return 'ADMINCONSOLE.LICENSE.REGISTRATION_STATE.STATES.ABOUT_TO_EXPIRE';
        } else {
            return 'ADMINCONSOLE.LICENSE.REGISTRATION_STATE.STATES.' +
                registrationInfo.registrationState;
        }
    }

    function showSetupWizardButton() {
        return angular.isObject(vm.setupInfo) && vm.setupInfo.setupRequired;
    }

    function openSetupWizard() {
        StateService.goToState(STATES.ACTIVATION);
    }

    function openLicenseDialog(event) {
        return DialogService.licenseUpdate(event).then(function success() {
            RegistrationService.loadRegistrationInfo(true).then(function success(response){
                setValues();
                NotificationService.info('ADMINCONSOLE.LICENSE.NOTIFICATION.INFO_ENTER_LICENSE',
                        {'validNotAfter': vm.validTill.value});
                StateService.goToState(STATES.AUTH);
            });
        });
    }
}
