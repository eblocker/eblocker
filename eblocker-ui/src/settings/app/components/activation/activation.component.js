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
    templateUrl: 'app/components/activation/activation.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        setupWizardInfo: '<',
        regions: '<'
    }
};

function Controller(logger, StateService, STATES, $translate, settings, TimezoneService, NotificationService, // jshint ignore: line
                    SetupService, TosService, RegistrationService, UrlService, $window, DialogService, LanguageService,
                    DeviceService, $timeout, $q) {
    'ngInject';

    const vm = this;
    vm.setLanguage = setLanguage;
    vm.getLanguage = getLanguage;
    vm.closeWizard = closeWizard;
    vm.nextStep = nextStep;
    vm.prevStep = prevStep;
    vm.isStepAllowed = isStepAllowed;
    vm.currentStep = 0;
    vm.isTosConfirmed = false;
    vm.isNoRegistrationConfirmed = false;
    vm.timezoneSet = false;
    vm.isAutoEnableNewDevices = false;
    vm.isAutoEnableNewDevicesSet = false;

    vm.languages = settings.getSupportedLanguageList();
    vm.locale = settings.locale();

    vm.$onInit = function() {
        vm.askForSerialNumber = vm.setupWizardInfo.needSerialNumber;
        vm.serialSample = vm.setupWizardInfo.serialNumberExample;
        // configuredSettings.serial = !vm.setupWizardInfo.needSerialNumber;
        vm.registrationAvailable = vm.setupWizardInfo.registrationAvailable;

        vm.registrationUserData = {
            licenseKey: 'FAMLFT-OPENSOURCE'
        };
        setTosContent();
    };

    // TAB TIMEZONE
    vm.setTimezone = setTimezone;
    vm.isTimezoneFormValid = isTimezoneFormValid;
    vm.setRegionAndGetCities = setRegionAndGetCities;
    vm.setCurrentTimeZone = setCurrentTimeZone;

    function setCurrentTimeZone() {
        const timezoneSplit = vm.locale.timezone.split('/');

        if (timezoneSplit.length === 2) {
            vm.region = timezoneSplit[0];
            vm.city = timezoneSplit[1];
            setRegionAndGetCities(vm.region);
        } else {
            logger.error('Error getting timezone from ' + vm.locale.timezone);
        }
    }

    vm.changeRegion = function(region) {
        vm.cityChanged = true;
        setRegionAndGetCities(region);
    };

    vm.changeCity = function() {
        vm.cityChanged = true;
        setTimezone();
    };

    function setRegionAndGetCities(region) {
        TimezoneService.setRegionAndGetCities(region).then(function success(response) {
            vm.cities = response.data;
            // ** reset city, if not valid (anymore); in case user selects different region
            // city should be empty, and not on invalid value.
            vm.city = isTimezoneValid(vm.city) ? vm.city : undefined;
            // Initially valid city means that timezone is set. On any changes we need to explicitly call setTimezone
            // by clicking the button. So vm.cityChanged is used to reflect those changes.
            vm.timezoneSet = angular.isDefined(vm.city) && !(vm.cityChanged);
        });
    }

    function isTimezoneFormValid() {
        return angular.isObject(vm.timezoneForm) && vm.timezoneForm.$valid && isTimezoneValid(vm.city);
    }

    function setTimezone() {
        if (!isTimezoneFormValid()) {
            return;
        }

        vm.locale.timezone = vm.region + '/' + vm.city;

        settings.setLocale(vm.locale).then(function success() {
            NotificationService.info('ADMINCONSOLE.TIME_LANGUAGE.NOTIFICATION.TIMEZONE_SET');
            vm.timezoneSet = true;
            vm.cityChanged = false;
        });
    }

    function isTimezoneValid(city) {
        return angular.isArray(vm.cities) && vm.cities.indexOf(city) > -1;
    }
    // END TAB TIMEZONE

    // TAB TOS
    function setTosContent(langKey) {
        if (angular.isUndefined(langKey)) {
            langKey = getLanguage();
        }
        vm.tosIsLoading = true;
        $q.all([TosService.getTos(langKey),
            TosService.getTosHtml(langKey)]).then(function success(response) {
            vm.tos = response[0];
            vm.tos.licenseDateDisplay = LanguageService.getDate(vm.tos.licenseDate,
                    $translate.instant('ADMINCONSOLE.ACTIVATION.TAB.TOS.DATE_FORMAT'));
            vm.tosPrint = response[1];
        }, function error(response) {
            logger.error('Unable to load TOS', response);
            vm.tosLoadingFailed = true;
        }).finally(function done() {
            vm.tosIsLoading = false;
        });
    }

    vm.readToPrint = function() {
        return vm.tosIsLoading !== true && angular.isObject(vm.tosPrint);
    };

    vm.isTosValid = function() {
        return angular.isObject(vm.tos) &&
            angular.isObject(vm.tos.licenseText) &&
            angular.isString(vm.tos.licenseVersion) && vm.tos.licenseVersion.length > 0 &&
            angular.isNumber(vm.tos.licenseDate);
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

    // TAB DEVICE
    vm.submitDeviceForm = submitDeviceForm;
    function submitDeviceForm() {
        if (vm.askForSerialNumber) {
            checkSerialNumber(vm.registrationUserData.serialNumber);
        } else if (vm.isTosValid() || vm.registrationAvailable) {
            vm.nextStep();
        } else {
            vm.registrationUserData.fallback = true;
            registerWithInformation();
        }
    }

    function checkSerialNumber(serialNumber) {
        vm.deviceForm.serialNumber.$setValidity('wrongFormat', true);
        if(serialNumber !== undefined) {
            SetupService.checkSerialNumber(serialNumber).then(function success(response) {
                const result = response.data;
                if(!result){
                    vm.deviceForm.serialNumber.$setValidity('wrongFormat', false);
                }
                else{
                    vm.deviceForm.serialNumber.$setValidity('wrongFormat', true);
                    if (vm.isTosValid() || vm.registrationAvailable) {
                        vm.nextStep();
                    } else {
                        vm.registrationUserData.fallback = true;
                        registerWithInformation();
                    }
                }
                return result;
            }, function error() {
                vm.deviceForm.serialNumber.$setValidity('wrongFormat', false);
            });
        }
        else{
            vm.deviceForm.serialNumber.$setValidity('wrongFormat', false);
        }
    }
    // END TAB DEVICE

    // TAB AUTO ENABLE NEW DEVICES

    vm.submitAutoEnableNewDevicesForm = submitAutoEnableNewDevicesForm;
    function submitAutoEnableNewDevicesForm() {
        DeviceService.setAutoEnableNewDevicesAfterActivation(vm.isAutoEnableNewDevices).then(function (response) {
            vm.isAutoEnableNewDevicesSet = true;
            nextStep();
        }, function (data) {
            logger.error('setAutoEnableNewDevicesAfterActivation failed ', data);
        });
    }

    // END TAB AUTO ENABLE NEW DEVICES

    // TAB LICENSE
    vm.submitLicenseForm = submitLicenseForm;
    function submitLicenseForm() {
        registerWithInformation();
    }

    function isStringNotEmpty(string){
        return string !== undefined && string !== '';
    }

    function validateEmailAddresses(emailAddress) {
        if(!isStringNotEmpty(emailAddress)){
            $translate('ADMINCONSOLE.ACTIVATION.TAB.LICENSE.ERROR_REGISTER_EMAIL_MISSING').then(function(translation) {
                vm.errorString = translation;
            });
            return false;
        }

        return true;
    }

    function registerWithInformation() {
        vm.registering = true;
        vm.licenseForm.licenseKey.$setValidity('invalidKey', true);

        //check the emailAddress
        if (!vm.registrationUserData.fallback && !validateEmailAddresses(vm.registrationUserData.emailAddress)) {
            vm.registering = false;
            return;
        }

        if (angular.isObject(vm.tos)) {
            vm.registrationUserData.tosVersion = vm.tos.licenseVersion;
        }

        //try registering
        const promise = RegistrationService.register(vm.registrationUserData);
        promise.then(function success(registrationInfo){
            logger.debug('Registration was successful!');
            vm.errorString = '';
            vm.registering = false;
            const param  = {
                postRegistrationInformation: registrationInfo.postRegistrationInformation
            };
            StateService.goToState(STATES.ACTIVATION_FINISH, param);
        },
        function error (errorString){
            if(errorString !== 'OK'){ //error while registering
                $translate('ADMINCONSOLE.ACTIVATION.TAB.LICENSE.ERROR.' + errorString).then(function success() {
                    vm.errorString = errorString;
                    vm.licenseForm.licenseKey.$setValidity('invalidKey', false);
                    vm.registering = false;
                    logger.error('Error while trying to register : ' + vm.errorString);
                }, function error() {
                    vm.errorString = 'REGISTRATION_NOT_POSSIBLE';
                    vm.licenseForm.licenseKey.$setValidity('invalidKey', false);
                    vm.registering = false;
                    logger.error('Error while trying to register : ' + vm.errorString);
                });

                // vm.errorString = errorString;
                // vm.licenseForm.licenseKey.$setValidity('invalidKey', false);
                // vm.registering = false;
                // logger.error('Error while trying to register : ' + vm.errorString);
            }
        });
    }
    // END TAB LICENSE

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
        var licenseAgreed = vm.isTosConfirmed && vm.isTosValid() ||
            !vm.registrationAvailable && vm.isNoRegistrationConfirmed;
        switch (nextStep) {
            case 1:
                return true;
            case 2:
                return licenseAgreed;
            case 3:
            case 4:
                return licenseAgreed && vm.timezoneSet;
            case 4:
                return licenseAgreed && vm.timezoneSet;
            case 5:
                return licenseAgreed && vm.timezoneSet && vm.isAutoEnableNewDevicesSet;
            default:
                return false;
        }
    }

    function closeWizard(event) {
        DialogService.setupWizardCloseConfirm(event, goBack, function() {});
    }

    function goBack() {
        return StateService.goToState(STATES.AUTH);
    }

    function setLanguage(id, lang) {
        const langKeys = lang.name.split('_');
        if (langKeys.length === 2) {
            vm.locale.language = langKeys[0];
            vm.locale.country = langKeys[1];
            vm.locale.name = lang.name;
            settings.setLocale(vm.locale).then(function success() {
                setTosContent(vm.locale.language);
                NotificationService.info('ADMINCONSOLE.TIME_LANGUAGE.NOTIFICATION.LANGUAGE_SET');
            });
        } else {
            logger.error('Unexpected format: unable to set language to ', lang,
                '. Required format \'en_US\' or \'de_DE\'.');
        }
    }

    function getLanguage() {
        return $translate.use();
    }

}
