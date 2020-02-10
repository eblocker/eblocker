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
export default function WhitelistService(logger, $http, PageContextService, $q) {
    'ngInject';

    let path = '/api/whitelist/' + PageContextService.getPageContext();
    let whitelist, cachedWhiteList;

    function getWhitelist() {
        if (angular.isUndefined(cachedWhiteList)) {
            cachedWhiteList = $http.get(path).then(
                function success(response) {
                    whitelist = response.data;
                    return response;
                },
                function error(response) {
                    logger.error('Error loading whitelist: ', response);
                    return $q.reject(response);
                }
            );
        }
        return cachedWhiteList;
    }

    function setWhitelistTracker(bool) {
        if (angular.isUndefined(whitelist)) {
            logger.error('Unable to set whitelist for trackers; whitelist is not defined.');
            return;
        }
        whitelist.trackers = bool;
    }

    function setWhitelistAds(bool) {
        if (angular.isUndefined(whitelist)) {
            logger.error('Unable to set whitelist for ads; whitelist is not defined.');
            return;
        }
        whitelist.ads = bool;
    }

    function setWhitelist() {
        if (angular.isUndefined(whitelist)) {
            let deferred = $q.defer();
            deferred.reject('Whitelist is not defined.');
            return deferred.promise;
        }
        return $http.put(path, whitelist).then(
            function success(response) {
                return response;
            },
            function error(response) {
                logger.error('Error loading whitelist: ', response);
                return $q.reject(response);
            }
        );
    }

    return {
        'getWhitelist': getWhitelist,
        'setWhitelistTracker': setWhitelistTracker,
        'setWhitelistAds': setWhitelistAds,
        'setWhitelist': setWhitelist
    };
}
