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
export default function SslSuggestionsService($q, TrustedAppsService, DeviceService, SslService) {
    'ngInject';

    let suggestions;

    function createIdMap(collection) {
        return createMap('id', collection);
    }

    function createMap(property, collection) {
        const map = new Map();
        for(let i = 0; i < collection.length; ++i) {
            map[collection[i][property]] = collection[i];
        }
        return map;
    }

    function setSuggestions(data, devicesById, modulesById) {
        const getDeviceNameById = function(id) {
            const device = devicesById[id];
            return device ? device.displayName : id;
        };

        const domainsIps = [];
        for(const domainIp in data.domains) {
            if (data.domains.hasOwnProperty(domainIp)) {
                domainsIps.push({
                    domainIp: domainIp,
                    devices: data.domains[domainIp].deviceIds.map(getDeviceNameById).join('\n'),
                    lastOccurrence: data.domains[domainIp].lastOccurrence * 1000 // Unix TS to milliseconds
                });
            }
        }

        const modules = [];
        for(const id in data.modules) {
            if (data.modules.hasOwnProperty(id)) {
                modules.push({
                    id: id,
                    name: modulesById[id].name,
                    enabled: modulesById[id].enabled,
                    devices: data.modules[id].deviceIds.map(getDeviceNameById).join('\n'),
                    domainsIps: data.modules[id].domains.join('\n'),
                    lastOccurrence: data.modules[id].lastOccurrence * 1000 // Unix TS to milliseconds
                });
            }
        }
        return {
            modules: modules,
            domainsIps: domainsIps
        };
    }

    function getSuggestions() {
        return suggestions;
    }

    function loadSuggestions() {
        return $q.all([
            TrustedAppsService.getAll().then(function success(response) {
                return createIdMap(response.data);
            }),
            DeviceService.getAll().then(function success(response) {
                return createIdMap(response.data);
            }),
            SslService.getErrors()
        ]).then(function(responses) {
            const modulesById = responses[0];
            const devicesById = responses[1];
            suggestions = setSuggestions(responses[2].data, devicesById, modulesById);
            return suggestions;
        }, standardError);
    }

    function resetSuggestions() {
        suggestions = {
            domainsIps: [],
            modules: []
        };
    }

    return {
        loadSuggestions: loadSuggestions,
        resetSuggestions: resetSuggestions,
        getSuggestions: getSuggestions

    };

    function standardSuccess(response) {
        return response;
    }

    function standardError(response) {
        return $q.reject(response);
    }
}
