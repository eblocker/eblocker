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
export default function DnsService(logger, $q, $http, $interval, DataCachingService) {
    'ngInject';

    const PATH = '/api/dashboard/dns/status';
    const PATHRESOLVERS = '/api/adminconsole/dns/stats?hours=24';

    let dnsStatusCache, dnsResolverCache, syncTimer;

    function getStatus(reload) {
        dnsStatusCache = DataCachingService.loadCache(dnsStatusCache, PATH, reload).then(function success(response) {
            return response;
        }, function(response) {
            logger.error('Getting the DNS status failed with status ' + response.status + ' - ' + response.data);
            return $q.reject(response);
        });
        return dnsStatusCache;
    }

    function getResolvers(reload) {
        dnsResolverCache = DataCachingService.loadCache(dnsResolverCache, PATHRESOLVERS, reload)
        .then(function success(response) {
            return response;
        }, function(response) {
            logger.error('Getting the DNS resolvers failed with status ' + response.status + ' - ' + response.data);
            return $q.reject(response);
        });
        return dnsResolverCache;
    }

    function startSyncTimer(interval) {
        if (!angular.isDefined(syncTimer) && angular.isNumber(interval)) {
            syncTimer = $interval(syncData, interval);
        } else if (!angular.isNumber(interval)) {
            logger.warn('Cannot start synch timer with interval ', interval);
        }
    }

    function stopSyncTimer() {
        if (angular.isDefined(syncTimer)) {
            $interval.cancel(syncTimer);
            syncTimer = undefined;
        }
    }

    function syncData() {
        getStatus(true);
    }

    return {
        start: startSyncTimer,
        stop: stopSyncTimer,
        getStatus: getStatus,
        getResolvers: getResolvers
    };
}
