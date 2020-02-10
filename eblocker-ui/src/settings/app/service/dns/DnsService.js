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
export default function DnsService($http, $translate, $q, DataCachingService) {
    'ngInject';

    const PATH = '/api/adminconsole/dns';
    const PATH_STATUS = PATH + '/status';
    const PATH_CACHE = PATH + '/cache';
    const PATH_CONFIG_RESOLVE = PATH + '/config/resolvers';
    const PATH_CONFIG_RECORD = PATH + '/config/records';
    const PATH_CONFIG_STATS = PATH + '/stats';

    let dnsStatusCache, dnsStatus;

    function loadDnsStatus(reload) {
        dnsStatusCache = DataCachingService.loadCache(dnsStatusCache, PATH_STATUS, reload).then(function(response) {
            dnsStatus = response.data;
            return response;
        }, function(response) {
            return $q.reject(response);
        });
        return dnsStatusCache;
    }

    /*
     * to quickly receive dns update set by saveDnsStatus() function, required e.g. to quickly disable tab
     * for DNS page after DNS switch has been turned off.
     */
    function getStatus() {
        return dnsStatus;
    }

    function setDnsStatusLocally(bool) {
        dnsStatus = bool;
    }

    function invalidateCache() {
        dnsStatusCache = undefined;
    }

    function saveDnsStatus(status) {
        return $http.put(PATH_STATUS, '' + status).then(function(response) {
            // Ok, saved status
            dnsStatus = response.data;
            return response.data;
        }, function(response) {
            return $q.reject(response);
        }).finally(function() {
            invalidateCache();
        });
    }

    function flushDnsCache() {
        return $http.delete(PATH_CACHE).then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        }).finally(function() {
            invalidateCache();
        });
    }

    let dnsConfig;
    function loadDnsConfiguration() {
        return $http.get(PATH_CONFIG_RESOLVE).then(function success(response) {
            dnsConfig = normalizeConfiguration(response.data);
            return dnsConfig;
        }, function error(response) {
            return $q.reject(response);
        });
    }

    function getDnsConfig() {
        return dnsConfig;
    }

    function saveDnsConfiguration(configuration) {
        return $http.put(PATH_CONFIG_RESOLVE, stripDownConfiguration(configuration)).then(function(response) {
            dnsConfig = normalizeConfiguration(response.data);
            return dnsConfig;
        },function(response) {
            return $q.reject(response);
        }).finally(function() {
            invalidateCache();
        });
    }

    function loadDnsRecords() {
        return $http.get(PATH_CONFIG_RECORD).then(function(response) {
            return normalizeDnsRecords(response.data);
        },function(response) {
            return $q.reject(response);
        });
    }

    function saveDnsRecords(records) {
        return $http.put(PATH_CONFIG_RECORD, stripDownDnsRecords(records)).then(function(response) {
            return normalizeDnsRecords(response.data);
        },function(response) {
            return $q.reject(response);
        }).finally(function() {
            invalidateCache();
        });
    }

    function loadDnsStats(hours) {
        return $http.get(PATH_CONFIG_STATS, {params: {'hours': hours}}).then(function (response) {
            return createStatsByNameServerMap(response.data);
        }, function (response) {
            return $q.reject(response);
        });
    }

    function normalizeDnsRecords(data) {
        const records = {};
        for (let i = 0; i < data.length; i++) {
            let record = data[i];
            records[record.name] = {
                name: record.name,
                ipAddress: record.ipAddress,
                ip6Address: record.ip6Address,
                builtin: record.builtin
            };
        }
        return records;
    }

    function stripDownDnsRecords(records) {
        const data = [];
        for (let name in records) {
            if (records.hasOwnProperty(name)) {
                let record = records[name];
                let ipAddress = record.ipAddress || undefined;
                let ip6Address = record.ip6Address || undefined;
                let builtin = record.builtin;
                if (!builtin && angular.isDefined(name) &&
                    (angular.isDefined(ipAddress) || angular.isDefined(ip6Address))) {
                    data.push({name: name, ipAddress: ipAddress, ip6Address: ip6Address});
                }
            }
        }
        return data;
    }

    function stripDownConfiguration(configuration) {
        if (!configuration) {
            return {};
        }
        return {
            defaultResolver:configuration.selectedDnsMode,
            customResolverMode:configuration.dnsModeListStrategy,
            customNameServers: configuration.customNameServers,
            localDnsRecords: configuration.localDnsRecords
        };

    }

    function normalizeConfiguration(configuration) { // jshint ignore: line
        if (!angular.isDefined(configuration)) {
            return configuration;
        }
        const normConf = {};
        normConf.selectedDnsMode = configuration.defaultResolver;// tor/dhcp/custom
        if (angular.isDefined(configuration.customResolverMode)) {
            normConf.dnsModeListStrategy = configuration.customResolverMode;// default/round_robin/random
        } else {
            normConf.dnsModeListStrategy = 'given';// This is a default
        }
        // IPs of servers
        normConf.customNameServers = configuration.customNameServers;
        normConf.dhcpNameServers = configuration.dhcpNameServers;
        normConf.ips = configuration.customNameServers.join('\n');
        // Explanation of mode
        if (configuration.defaultResolver === 'dhcp') {
            normConf.modeDisplay = $translate.instant('DHCP');
            normConf.nameServers = configuration.dhcpNameServers;
        } else if (configuration.defaultResolver === 'tor') {
            normConf.modeDisplay = $translate.instant('TOR');
            normConf.nameServers = ['127.0.0.1'];
        } else if (configuration.defaultResolver === 'custom') {
            normConf.nameServers = configuration.customNameServers;
            if (configuration.customResolverMode === 'default') {
                normConf.modeDisplay = $translate.instant('CUSTOM_DEFAULT');
            } else if (configuration.customResolverMode === 'round_robin') {
                normConf.modeDisplay = $translate.instant('CUSTOM_ROUND_ROBIN');
            } else if (configuration.customResolverMode === 'random') {
                normConf.modeDisplay = $translate.instant('CUSTOM_RANDOM');
            }
        }
        normConf.localDnsRecords = configuration.localDnsRecords;
        return normConf;
    }

    function createStatsByNameServerMap(stats) {
        let statsByNameServer = {};
        for(let i = 0; i < stats.nameServerStats.length; ++i) {
            statsByNameServer[stats.nameServerStats[i].nameServer] = stats.nameServerStats[i];
        }
        return statsByNameServer;
    }

    /*
     * When an entry is moved up in list (smaller order number),
     * all entries with equal or larger than new order number will be moved down.
     *
     * When an entry is moved down in list (greater order number),
     * all entries with equal or smaller than new order number will be moved up.
     *
     * When an entry is added to the list
     * all entries with equal or larger than new order number will be moved down.
     */
    function updateOrderNumbers(list, newEntryId, newEntryOrderNumber, oldOrderNumber) {

        const isMoveDown = newEntryOrderNumber > oldOrderNumber;
        const ret = [];

        list.forEach((original) => {
            const entry = angular.copy(original);
            if (entry.id !== newEntryId) {
                if (isMoveDown && entry.orderNumber <= newEntryOrderNumber &&
                    entry.orderNumber > oldOrderNumber) {
                    entry.orderNumber = entry.orderNumber - 1;
                } else if (!isMoveDown && entry.orderNumber >= newEntryOrderNumber &&
                    entry.orderNumber < oldOrderNumber) {
                    entry.orderNumber = entry.orderNumber + 1;
                }
            }
            ret.push(entry);
        });
        return ret;
    }

    function updateDnsServer(list, newEntry) {
        const ret = angular.copy(list);
        ret.forEach((entry) => {
            if (entry.id === newEntry.id) {
                entry.orderNumber = newEntry.orderNumber;
                entry.server = newEntry.server;
            }
        });
        return ret;
    }

    function getOrderNumber(list, newEntryId) {
        let oldOrder = -1;
        list.forEach((entry) => {
            if (entry.id === newEntryId) {
                oldOrder = entry.orderNumber;
            }
        });
        return oldOrder;
    }

    return {
        loadDnsStatus: loadDnsStatus,
        getStatus: getStatus,
        saveDnsStatus: saveDnsStatus,
        flushDnsCache: flushDnsCache,
        loadDnsConfiguration: loadDnsConfiguration,
        saveDnsConfiguration: saveDnsConfiguration,
        getDnsConfig: getDnsConfig,
        loadDnsRecords: loadDnsRecords,
        saveDnsRecords: saveDnsRecords,
        loadDnsStats: loadDnsStats,
        invalidateCache: invalidateCache,
        updateOrderNumbers: updateOrderNumbers,
        updateDnsServer: updateDnsServer,
        getOrderNumber: getOrderNumber,
        setDnsStatusLocally: setDnsStatusLocally
    };
}
