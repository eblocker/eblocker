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
export default function SettingsService(logger, $http, $q, $translate) {
    'ngInject';

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
        return $http.get('/api/settings').then(
            function(response) {
                locale = response.data;
                doSetLocale(locale);
                logger.info('Settings loaded ', locale);
                return locale;

            }, function(response) {
                logger.error('Cannot load settings: ', response);
                return $q.reject(response);
            }
        );
    }

    function doSetLocale(value) {
        locale = value;
        $translate.use(locale.language);
    }

    return {
        locale: getLocale,
        load: load
    };
}
