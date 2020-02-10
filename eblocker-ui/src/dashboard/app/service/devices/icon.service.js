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
export default function IconService(logger, $http, $q) {
    'ngInject';
    'use strict';


    function getSettings() {
        return $http.get('/api/device/icon').then(function(response) {
            return response.data;
        }, function(response) {
            logger.error('Getting icon settings failed with status ' + response.status +
                ' - ' + response.data);
            return $q.reject(response.data);
        });
    }

    function setSettings(settings) {
        return $http.post('/api/device/icon', settings).then(function(response) {
            return response.data;
        }, function(response) {
            logger.error('Setting icon settings failed with status ' + response.status +
                ' - ' + response.data);
            return $q.reject(response.data);
        });
    }

    function resetSettings() {
        return $http.delete('/api/device/icon').then(function(response) {
            return response.data;
        }, function(response) {
            logger.error('Resetting icon settings failed with status ' + response.status +
                    ' - ' + response.data);
            return $q.reject(response.data);
        });
    }


    return {
        getSettings: getSettings,
        setSettings: setSettings,
        resetSettings: resetSettings
    };
}
