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
export default function DataCachingService(logger, $q, $http) {
    'ngInject';

    function isRejected(promise) {
        if (angular.isObject(promise) && angular.isObject(promise.$$state)) {
            return promise.then(function resolved() {
                return false;
            }, function rejected() {
                return true;
            });
        }
        // If cache is not an object or $$state is not defined, then we want to reload the data. Therefore we
        // resolve the promise to true, which is equivalent to being rejecting, thus resulting in a new HTTP call.
        const deferred = $q.defer();
        deferred.resolve(true);
        return deferred.promise;
    }

    function loadCache(cache, PATH, forceReload, config) {
        if (!angular.isObject(cache) || forceReload) {
            // logger.debug('Reload data. Cache: ', cache, ' / force: ', forceReload);
            return setCache(cache, PATH, true, config);
        } else {
            // logger.debug('Use cached data ', cache);
            return isRejected(cache).then(function success(isRejected) {
                // logger.debug('isRejected: ' + isRejected);
                return setCache(cache, PATH, isRejected, config);
            });
        }
    }

    // let statusLoopCounter = 0;
    // const statusLoopLimit = 5;

    function setCache(cache, PATH, bool, config) {
        const configuration = config || {};
        if (bool) {
            return $http.get(PATH, configuration).then(function success(response) {
                return response;
            }, function error(response) {
                // if (angular.isObject(response) && response.status <= 0 && statusLoopCounter <= statusLoopLimit) {
                //     logger.warn('#### HTTP status: ' + response.status +
                //         ' (' + PATH + ' / ' + statusLoopCounter + ') ####');
                //     statusLoopCounter++;
                //     StateService.goToState(STATES.STAND_BY);
                // } else {
                //     statusLoopCounter = 0;
                // }
                return $q.reject(response);
            });
        }
        return angular.copy(cache);
    }

    return {
        loadCache: loadCache
    };
}
