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
export default function TrustedAppsService($http, $q, $translate, DataCachingService) {
    'ngInject';

    const PATH = '/api/adminconsole/trustedapps';
    const PATH_ID = PATH + '/id';
    const PATH_GET_ALL = PATH + '/all';
    const PATH_ENABLE = PATH + '/enable';

    function get(id) {
        return $http.get(PATH_ID + '/' + id).then(standardSuccess, standardError);
    }

    let trustedAppsCache;

    function getAll(reload) {
        trustedAppsCache = DataCachingService.loadCache(trustedAppsCache, PATH_GET_ALL, reload)
            .then(function(response) {
                const processedModules = initModules(response.data);
                return $q.resolve({data: processedModules});
        }, standardError);
        return trustedAppsCache;
    }

    function invalidateCache() {
        trustedAppsCache = undefined;
    }

    function initModules(modules) {
        const ret = [];
        for (let i = 0; i < modules.length; i++) {
            ret.push(initModule(modules[i]));
        }
        return ret;
    }

    function initModule(param) {
        const module = angular.copy(param);
        module.domainsIps = module.whitelistedDomainsIps.join('\n');
        const langId = $translate.use();
        if (angular.isDefined(module.description) && angular.isDefined(module.description[langId])) {
            module.localizedDescription = module.description[langId];
        }
        return module;
    }

    function deleteById(id) {
        return deleteOrReset(id, false);
    }

    function reset(id) {
        return deleteOrReset(id, true);
    }

    function deleteOrReset(id, reset) {
        return $http.delete(PATH_ID + '/' + id).then(function success(response) {
            invalidateCache();
            return response;
        }, standardError);
    }

    function toggleAppModule(module) {
        return $http.put(PATH_ENABLE, {
            setEnabled: '' + module.enabled,
            id: '' + module.id
        }).then(standardSuccess, standardError);
    }

    function parseAndSave(module) { // jshint ignore: line
        module.whitelistedDomainsIps = [];
        if (angular.isDefined(module.domainsIps)){
            const lines = module.domainsIps.split('\n');
            for (let i = 0; i < lines.length; i++) {
                const domainIp = lines[i].trim();
                if (domainIp.length > 0) {
                    module.whitelistedDomainsIps.push(domainIp);
                }
            }
        }

        if (!angular.isDefined(module.description)) {
            module.description = {};
        }
        // save description using the proper language
        module.description[$translate.use()] = module.localizedDescription;
        //
        // Set description in all known languages to the given text, if that language is not yet defined.
        //
        if (!angular.isDefined(module.description.en) || module.description.en === '') {
            module.description.en = module.localizedDescription;
        }
        if (!angular.isDefined(module.description.de) || module.description.de === '') {
            module.description.de = module.localizedDescription;
        }
        return save(module);
    }

    function save(module) {
        if (angular.isDefined(module.id)) {
            // update
            return $http.put(PATH_ID + '/' + module.id, module).then(saveSuccess, standardError);
        } else {
            // create
            return $http.post(PATH_ID, module).then(saveSuccess, standardError);
        }
    }

    return {
        get: get,
        getAll: getAll,
        deleteById: deleteById,
        reset: reset,
        toggleAppModule: toggleAppModule,
        parseAndSave: parseAndSave,
        save: save
    };

    function standardSuccess(response) {
        return response;
    }

    function standardError(response) {
        return $q.reject(response);
    }

    function saveSuccess(response) {
        const ret = response;
        ret.data = initModule(response.data);
        invalidateCache();
        return ret;
    }
}
