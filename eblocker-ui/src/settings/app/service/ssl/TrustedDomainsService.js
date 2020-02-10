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
export default function TrustedDomainsService($http, $q, DataCachingService) {
    'ngInject';

    const PATH = '/api/adminconsole/trusteddomains/';
    const PATH_GET = PATH + 'onlyenabled';
    const PATH_DEL = PATH + 'delete';
    const PATH_DEL_ALL = PATH + 'deleteall';

    const SINGLE_ENTRIES_USER_DEFINED_MODULE_ID = 9998; // see also: "appmodules.id.user" in ICAP server configuration

    let trustedDomainsCache;

    function getOnlyEnabled(reload) {
        trustedDomainsCache = DataCachingService.loadCache(trustedDomainsCache, PATH_GET, reload)
            .then(standardSuccess, standardError);
        return trustedDomainsCache;
    }

    function invalidateCache() {
        trustedDomainsCache = undefined;
    }

    function deleteDomain(url) {
        return $http.put(PATH_DEL, url).then(standardSuccess, standardError).finally(function () {
            invalidateCache();
        });
    }

    function deleteAllDomains(urls) {
        return $http.put(PATH_DEL_ALL, urls).then(standardSuccess, standardError).finally(function (response) {
            invalidateCache();
            return response;
        });
    }

    function isSingleEntriesModule(module) {
        return module.id === SINGLE_ENTRIES_USER_DEFINED_MODULE_ID;
    }

    return {
        getEnabledDomains: getOnlyEnabled,
        deleteDomain: deleteDomain,
        deleteAllDomains: deleteAllDomains,
        invalidateCache: invalidateCache,
        isSingleEntriesModule: isSingleEntriesModule
    };

    function standardSuccess(response) {
        return response;
    }

    function standardError(response) {
        return $q.reject(response);
    }
}
