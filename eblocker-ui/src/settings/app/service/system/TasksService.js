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
export default function TasksService(logger, $http, $q) {
    'ngInject';

    const LOG_PATH = '/api/adminconsole/tasks/log';
    const CONFIG_PATH = '/api/adminconsole/tasks/viewConfig';
    const STATS_PATH = '/api/adminconsole/tasks/stats';

    function getLog() {
        return $http.get(LOG_PATH).then(function(response){
            return response.data;
        }, function(response) {
            logger.error('Error getting events', response);
            return $q.reject(response);
        });
    }

    function getConfig() {
        return $http.get(CONFIG_PATH).then(function(response) {
            return response.data;
        }, function(response) {
            logger.error('Error getting config', response);
            return $q.reject(response);
        });
    }

    function setConfig(config) {
        return $http.put(CONFIG_PATH, config).then(function(response) {
            return response.data;
        }, function(response) {
            logger.error('Error setting config', response);
            return $q.reject(response);
        });
    }

    function getStats() {
        return $http.get(STATS_PATH).then(function(response){
            return response.data;
        }, function(response) {
            logger.error('Error getting stats', response);
            return $q.reject(response);
        });
    }

    return {
        getLog: getLog,
        getConfig: getConfig,
        setConfig: setConfig,
        getStats: getStats
    };
}
