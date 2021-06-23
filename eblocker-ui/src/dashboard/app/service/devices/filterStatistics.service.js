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
export default function FilterStatistics(logger, $http, $q) {
    'ngInject';
    'use strict';

    function getStatistics(deviceId, numberOfBins, binSizeMinutes) {
        return $http({
            method: 'GET',
            url: '/api/filter/stats/device/' + deviceId,
            params: {
                numberOfBins: numberOfBins,
                binSizeMinutes: binSizeMinutes
            }
        }).then(function(response) {
            return response.data;
        }, function(response) {
            logger.error('Getting filter statistics failed with status ' + response.status +
                ' - ' + response.data);
            return $q.reject(response.data);
        });
    }

    function getTotalStatistics() {
        return $http.get('/api/filter/totalStats').then(function(response) {
            return response.data;
        }, function(response) {
            logger.error('Getting filter total statistics failed with status ' + response.status +
                ' - ' + response.data);
            return $q.reject(response.data);
        });
    }

    function getFilterNames() {
        return $http.get('/filterlists').then(function(response) {
            return response.data;
        }, function(response) {
            logger.error('Getting filter list names failed with status ' + response.status +
                ' - ' + response.data);
            return $q.reject(response.data);
        });
    }

    function getBlockedDomainsStatistic(deviceId) {
        return $http.get('/api/filter/blockeddomains/' + deviceId).then(function(response) {
            return response.data;
        }, function(response) {
            logger.error('Getting stats for blocked domains failed with status ' + response.status +
                ' - ' + response.data);
            return $q.reject(response.data);
        });
    }

    function resetBlockedDomainsStatistic(deviceId) {
        return $http.delete('/api/filter/blockeddomains/' + deviceId).then(function(response) {
            return response.data;
        }, function(response) {
            logger.error('Resetting stats for blocked domains failed with status ' + response.status +
                ' - ' + response.data);
            return $q.reject(response.data);
        });
    }

    return {
        getStatistics: getStatistics,
        getTotalStatistics: getTotalStatistics,
        getFilterNames: getFilterNames,
        getBlockedDomainsStatistic: getBlockedDomainsStatistic,
        resetBlockedDomainsStatistic: resetBlockedDomainsStatistic
    };
}
