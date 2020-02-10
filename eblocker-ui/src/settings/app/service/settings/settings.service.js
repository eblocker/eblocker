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
export default function SettingsService(logger, $rootScope, $http, $q, $localStorage, $translate, SUPPORTED_LOCALE) {
    'ngInject';

    const PATH = '/api/adminconsole/settings';
    const supportedLanguageList = setSupportedLanguageList();

    let locale = {
        name: 'Deutsch',
        country: 'DE',
        language: 'de',
        timezone: 'Europe/Berlin',
        clock24: true
    };

    function setSupportedLanguageList() {
        const languages = [];
        SUPPORTED_LOCALE.forEach((lang) => {
            languages.push({
                id: lang.id,
                name: lang.name,
                translationKey: lang.id.toUpperCase()
            });
        });
        return languages;
    }

    function getSupportedLanguageList() {
        return supportedLanguageList;
    }

    function getLocale() {
        return locale;
    }

    function load() {
        return $http.get(PATH).then(success, error);
    }

    function setLocale(locale) {
        return $http.put(PATH, locale).then(success, error);
    }

    function success(response) {
        doSetLocale(response.data);
        $localStorage.localeAdminConsole = locale;
        logger.info('Settings loaded ', locale);
        return locale;
    }

    function error(response) {
        logger.error('Cannot load settings: ', response);
        if (angular.isDefined($localStorage.localeAdminConsole)) {
            doSetLocale($localStorage.localeAdminConsole);
            logger.debug('Getting settings from local storage ', locale);
            return locale;
        }
        logger.debug('No locale found in locale storage.');
        return $q.reject(response);
    }

    function doSetLocale(value) {
        locale = value;
        $translate.use(locale.language).then(function() {
            // set browser title
            $translate('ADMINCONSOLE.TITLE').then(function(title) {
                $rootScope.title = title;
            }, function (error) {
                logger.error('translation error ', error);
            });
        }, function(error) {
            logger.error('error setting language ', error);
        });
    }

    function getDefaultLocale() {
        return {
            name: 'English',
            country: 'US',
            language: 'en',
            timezone: 'Europe/Berlin',
            clock24: true
        };
    }

    return {
        locale: getLocale,
        load: load,
        setLocale: setLocale,
        getDefaultLocale: getDefaultLocale,
        getSupportedLanguageList: getSupportedLanguageList
    };
}
