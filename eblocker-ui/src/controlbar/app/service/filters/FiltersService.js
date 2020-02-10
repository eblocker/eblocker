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
export default function FilterService(logger, $http, PageContextService, $q) {
    'ngInject';

    let pageContextId = PageContextService.getPageContext();
    let PATH = '/api/filter';

    const PATH_META = PATH + '/meta';

    let filterConfig, cachedFilterStats, cachedFilterConfig;

    function getFilterStats() {
        if (angular.isUndefined(cachedFilterStats)) {
            cachedFilterStats = $http.get(PATH + '/stats/' + pageContextId).then(
                function success(response) {
                    return response;
                }, function error(response) {
                    logger.error('Unable to get filter stats ', response);
                    return $q.reject(response);
                });
        }
        return cachedFilterStats;
    }

    function getFilterConfig() {
        if (angular.isUndefined(cachedFilterConfig)) {
            cachedFilterConfig = $http.get(PATH + '/config').then(
                function success(response) {
                    filterConfig = response.data;
                    return response;
                }, function error(response) {
                    logger.error('Error loading filter config: ', response);
                    return $q.reject(response);
                });
        }
        return cachedFilterConfig;
    }

    function setFilterConfigBlockTracker(bool) {
        if (angular.isUndefined(filterConfig)) {
            logger.error('Unable to set config for trackers; config is not defined.');
            return;
        }
        filterConfig.blockTrackings = bool;
    }

    function setFilterConfigBlockAds(bool) {
        if (angular.isUndefined(filterConfig)) {
            logger.error('Unable to set config for ads; confg is not defined.');
            return;
        }
        filterConfig.blockAds = bool;
    }

    function saveFilterConfig() {
        if (angular.isUndefined(filterConfig)) {
            let deferred = $q.defer();
            deferred.reject('Filter config is not defined.');
            return deferred.promise;
        }
        return $http.put(PATH + '/config', filterConfig).then(
            function success(response) {
                return response;
        }, function error(response) {
            logger.error('Unable to set filter config ', response);
            return $q.reject(response);
        });
    }

    function getBlockedAds() {
        return $http.get(PATH + '/blockedAds/' + pageContextId).then(
            function success(response) {
                return response;
        }, function error(response) {
            logger.error('Unable to get blocked ads ', response);
            return $q.reject(response);
        });
    }

    function getBlockedTrackers() {
        return $http.get(PATH + '/blockedTrackings/' + pageContextId).then(
            function success(response) {
                return response;
        }, function error(response) {
            logger.error('Unable to get blocked trackings ', response);
            return $q.reject(response);
        });
    }

    function processUrls(urls) {
        let array = {};
        urls.forEach(function(url) {
            let parts = url.split('/');
            let domain = parts[2];

            if(angular.isUndefined(array[domain])){
                array[domain] = 1;
            }
            else{
                let count = array[domain];
                count++;
                array[domain] = count;
            }
        });
        let ret = [];
        Object.keys(array).forEach(function(key) {
            if (array.hasOwnProperty(key)) {
                ret.push({
                    url: key,
                    countBlocked: array[key]
                });
            }
        });
        return ret;
    }

    function getFilterMetaData() {
        return $http.get(PATH_META).then(function success(response) {
            return response;
        }, function error(response) {
            return response;
        });
    }

    return {
        getBlockedAds: getBlockedAds,
        getBlockedTrackers: getBlockedTrackers,
        getFilterStats: getFilterStats,
        getFilterConfig: getFilterConfig,
        saveFilterConfig: saveFilterConfig,
        setFilterConfigBlockTracker: setFilterConfigBlockTracker,
        setFilterConfigBlockAds: setFilterConfigBlockAds,
        processUrls: processUrls,
        getFilterMetaData: getFilterMetaData
    };
}
