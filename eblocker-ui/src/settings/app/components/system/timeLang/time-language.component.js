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
    templateUrl: 'app/components/system/timeLang/time-language.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(logger, $translate, TimezoneService, settings, NotificationService) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.setLanguage = setLanguage;
    vm.getLanguage = getLanguage;
    vm.setRegionAndGetCities = setRegionAndGetCities;
    vm.setTimezone = setTimezone;
    vm.formInvalid = formInvalid;
    vm.cityChanged = cityChanged;

    vm.locale = settings.locale();
    vm.languages = settings.getSupportedLanguageList();

    const timezoneSplit = vm.locale.timezone.split('/');

    if (timezoneSplit.length === 2) {
        vm.region = timezoneSplit[0];
        vm.city = timezoneSplit[1];
        setRegionAndGetCities(vm.region);
    } else {
        logger.error('Error getting timezone from ' + vm.locale.timezone);
    }

    function setLanguage(id, lang) {
        const langKeys = lang.name.split('_');
        if (langKeys.length === 2) {
            vm.locale.language = langKeys[0];
            vm.locale.country = langKeys[1];
            vm.locale.name = lang.name;
            settings.setLocale(vm.locale).then(function success() {
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

    function cityChanged() {
        setTimezone();
    }

    function setTimezone() {
        if (!vm.timezoneForm.$valid || !isTimezoneValid(vm.city)) {
            return;
        }

        vm.locale.timezone = vm.region + '/' + vm.city;

        settings.setLocale(vm.locale).then(function success() {
            NotificationService.info('ADMINCONSOLE.TIME_LANGUAGE.NOTIFICATION.TIMEZONE_SET');
        });
    }

    function getAllRegions() {
        TimezoneService.getRegions().then(function success(response) {
            vm.regions = response.data;
        });
    }

    function setRegionAndGetCities(region) {
        TimezoneService.setRegionAndGetCities(region).then(function success(response) {
            vm.cities = response.data;
            // ** reset city, if not valid (anymore); in case user selects different region
            // city should be empty, and not on invalid value.
            vm.city = isTimezoneValid(vm.city) ? vm.city : undefined;
        });
    }

    function isTimezoneValid(city) {
        return angular.isArray(vm.cities) && vm.cities.indexOf(city) > -1;
    }

    function formInvalid() {
        return angular.isUndefined(vm.region) || angular.isUndefined(vm.city) || !isTimezoneValid(vm.city);
    }

    vm.$onInit = function() {
        getAllRegions();
    };

}
