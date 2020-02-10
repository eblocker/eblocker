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
export default function DnsStatistics(logger, $http, $q) {
    'ngInject';

    const PATH = '/api/controlbar/stats/filter';

    function getStatistics(numberOfBins, binSizeMinutes, type) {
        return $http({
            method: 'GET',
            url: PATH,
            params: {
                numberOfBins: numberOfBins,
                binSizeMinutes: binSizeMinutes,
                device: null,
                type: type
            }
        }).then(function(response) {
            return response.data;
        }, function(response) {
            logger.error('Getting filter statistics failed with status ' + response.status +
                ' - ' + response.data);
            return $q.reject(response.data);
        });
    }

    function getBlockedInLastMinutes(bins, minutes, listType) {
        const until = bins.length - minutes;
        let blocked = 0;
        for (let i = bins.length - 1; i >= until; i--) {
            if (bins[i].blockedQueriesByReason.hasOwnProperty(listType)) {
                blocked = blocked + bins[i].blockedQueriesByReason[listType];
            }
        }
        return blocked;
    }

    return {
        getStatistics: getStatistics,
        getBlockedInLastMinutes: getBlockedInLastMinutes
    };
}
