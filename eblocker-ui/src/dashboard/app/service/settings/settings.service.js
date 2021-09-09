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
export default function SettingsService(logger, $http, $q, $translate, $rootScope, $state) {
    'ngInject';
    'use strict';

    const PATH = '/api/settings';

    let locale = {
        name: 'Deutsch',
        country: 'DE',
        language: 'de',
        timezone: 'Europe/Berlin',
        clock24: true
    };

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
        logger.info('Settings loaded ', locale);
        return locale;
    }

    function error(response) {
        logger.error('Cannot load settings: ', response);
        return $q.reject(response);
    }

    function getHeaderTitleId() {
        let trans;
        const state = $state.$current + '';
        if (state.indexOf('app.redirect') > -1) {
            trans = 'APP.HEADER.TITLE_REDIRECT';
        } else if (state.indexOf('app.blocker') > -1) {
            trans = 'APP.HEADER.TITLE_BLOCKER';
        } else {
            trans = 'APP.HEADER.TITLE';
        }
        return trans;
    }

    function doSetLocale(value) {
        locale = value;
        $translate.use(locale.language).then(function() {
            setHeaderTitle();
        }, function(response) {
            logger.error('error setting language ', response);
        });
    }

    function setHeaderTitle() {
        $translate(getHeaderTitleId()).then(function(title) {
            $rootScope.title = title;
        }, function (response) {
            logger.error('translation error ', response);
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
        setHeaderTitle: setHeaderTitle
    };
}
