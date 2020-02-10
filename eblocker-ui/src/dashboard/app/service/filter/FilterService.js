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
export default function FilterService(logger, $http, $q) {
    'ngInject';

    const PATH = '/api/dashboard/filterlists';
    const PATH_CUSTOM = '/api/dashboard/user/customdomainfilter';

    function getFilterLists() {
        return $http.get(PATH).then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Unable to get filter lists', response);
            return $q.reject(response);
        });
    }

    function getCurrentUserCustomDomainFilter() {
        return $http.get(PATH_CUSTOM).then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Unable to get current user\'s custom filter', response);
            return $q.reject(response);
        });
    }

    function setCurrentUserCustomDomainFilter(filter) {
        return $http.put(PATH_CUSTOM, filter).then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Unable to set current user\'s custom filter', response);
            return $q.reject(response);
        });
    }

    function deleteFilterList(id){
        return $http.delete(PATH + '/' + id).then(function success(response){
            return response;
        }, function error(response) {
            logger.error('Unable to delete filterlist', response);
            return $q.reject(response);
        });
    }

    return {
        getFilterLists: getFilterLists,
        deleteFilterList: deleteFilterList,
        getCurrentUserCustomDomainFilter: getCurrentUserCustomDomainFilter,
        setCurrentUserCustomDomainFilter: setCurrentUserCustomDomainFilter
    };

}
