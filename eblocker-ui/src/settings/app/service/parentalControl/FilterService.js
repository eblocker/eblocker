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
export default function FilterService(logger, $http, $q, $translate, NotificationService,
                                      DataCachingService, LanguageService) {
    'ngInject';

    const PATH = '/api/adminconsole/filterlists';
    const PATH_META = PATH + '/meta';

    let dateFormat = 'ADMINCONSOLE.SERVICE.FILTER_SERVICE.DATE_FORMAT';

    /**
     * Functions for filter lists
     */
     function normalizeFilterList(filterlist) {
        let lang = $translate.use();
        if (angular.isDefined(filterlist.builtin) && !filterlist.builtin){
            filterlist.localizedName = angular.isDefined(filterlist.customerCreatedName) ?
                filterlist.customerCreatedName : '';
            filterlist.localizedDescription = angular.isDefined(filterlist.customerCreatedDescription) ?
                filterlist.customerCreatedDescription : '';
        } else {
            if (angular.isDefined(filterlist.name)) {
                // TODO: What to do if empty string or {}?
                filterlist.localizedName = $translate.instant(filterlist.name[lang]);
            }else{
                filterlist.localizedName = filterlist.customerCreatedName;
            }
            if (angular.isDefined(filterlist.description)) {
                filterlist.localizedDescription = $translate.instant(filterlist.description[lang]);
            }else{
                filterlist.localizedDescription = filterlist.customerCreatedDescription;
            }
        }
        // Prepare date for displaying
        if (angular.isDefined(filterlist.lastUpdate)){
            filterlist.displayLastUpdate = LanguageService.getDate(filterlist.lastUpdate, dateFormat);
        }

        // TODO: check if customerCreatedX is defined? What to do if undefined?
        return filterlist;
    }

    function getFilterMetaData() {
         return $http.get(PATH_META).then(function success(response) {
             return response;
         }, function error(response) {
             return response;
         });
    }

    let filterCache;

    function getAllFilterLists(reload){
        filterCache = DataCachingService.loadCache(filterCache, PATH, reload).then(function(response){
            // OK, got filter lists
            let filterlists = response.data;
            for (let index = 0; index < filterlists.length; index++){
                normalizeFilterList(filterlists[index]);
            }
            return $q.resolve({data: filterlists});

        }, function error(response){
            NotificationService.error('ADMINCONSOLE.SERVICE.FILTER_SERVICE.ERROR_SERVICE_PARENTAL_CONTROL_GET_FILTERLISTS', response); // jshint ignore: line
            $q.reject(response);
        });
        return filterCache;
    }

    function invalidateCache() {
        filterCache = undefined;
    }

    function parseAndSaveFilterList(filterlist, filterType){
        filterlist.domains=[];
        if (angular.isDefined(filterlist.filteredDomains)){
            let lines = filterlist.filteredDomains.split('\n');
            for (let i = 0; i < lines.length; i++){
                let domain = lines[i].trim();
                if (domain.length > 0) {
                    filterlist.domains.push(domain);
                }
            }
        }
        filterlist.customerCreatedName = filterlist.localizedName;
        filterlist.customerCreatedDescription = filterlist.localizedDescription;

        return saveFilterList(filterlist, filterType);
    }

    function saveFilterList(filterlist, filterType) {
        if (angular.isDefined(filterlist.id)){
            // Update
            return $http.put(PATH + '/' + filterlist.id + '/update?filterType=' + filterType, filterlist).
            then(function success (response){
                return response.data;
            }, function error (response){
                NotificationService.error('ADMINCONSOLE.SERVICE.FILTER_SERVICE.ERROR_SERVICE_FILTERLIST_UPDATE', response); // jshint ignore: line
                $q.reject(response);
            }).finally(function() {
                invalidateCache();
            });
        }else{
            // New
            return $http.post(PATH + '?filterType=' + filterType, filterlist).then(function(response){
                // List saved, now save domain list
                return response.data;
            }, function(response){
                NotificationService.error('ADMINCONSOLE.SERVICE.FILTER_SERVICE.ERROR_SERVICE_FILTERLIST_CREATE', response); // jshint ignore: line
                $q.reject(response);
            }).finally(function() {
                invalidateCache();
            });
        }
    }

    function deleteFilterList(id){
        return $http.delete(PATH + '/' + id).then(function(){
            return true;
        }, function(){
            return false;
        }).finally(function() {
            invalidateCache();
        });
    }

    function getDomainList(id){
        return $http.get(PATH + '/' + id + '/domains')
            .then(function(response){
                // Success
                // TODO: Workaround - empty String should only happen in cases of errors
                if (response.data === ''){return '';}
                return response.data.join('\n');
            }, function(response){
                // Failure
                NotificationService.error('ADMINCONSOLE.SERVICE.FILTER_SERVICE.ERROR_SERVICE_FILTERLIST_DOMAINS_GET', response); // jshint ignore: line
            });
    }

    function uniqueName(name, id, filterType) {
        let def = $q.defer();

        $http.get(PATH + '/unique?name=' + encodeURIComponent(name) + (angular.isDefined(id) ? '&id=' + id : '') +
            '&filterType=' + encodeURIComponent(filterType))
            .then(function(){
                def.resolve();
            }, function(response) {
                if (response.status === 409) {
                    return def.reject(response);
                } else {
                    return def.resolve(response);
                }
            });
        return def.promise;
    }

    return {
        normalizeFilterList: normalizeFilterList,
        getAllFilterLists: getAllFilterLists,
        getFilterMetaData: getFilterMetaData,
        parseAndSaveFilterList: parseAndSaveFilterList,
        deleteFilterList: deleteFilterList,
        getDomainList: getDomainList,
        uniqueName: uniqueName
    };
}
