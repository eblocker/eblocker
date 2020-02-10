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
export default {
    templateUrl: 'app/components/ssl/fails/ssl-fails.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller($timeout, SslSuggestionsService, SslService, TrustedAppsService,
        DialogService, TableService, LanguageService) {
    'ngInject';
    'use strict';

    const vm = this;
    vm.toggleSslErrorRecording = toggleSslErrorRecording;
    vm.clearErrors = clearErrors;

    vm.domainActionSelect = undefined;

    vm.domainAction = domainAction;

    function domainAction(type) {
        if (type === 'NEW') {
            newTrustedApp();
        } else if (type === 'APP') {
            addDomainToApp();
        } else if (type === 'WHITELIST') {
            addToExceptionList();
        }
        vm.domainActionSelect = undefined;
     }

    vm.$onInit = function () {
        vm.ssl = SslService.getSslSettings();
        TrustedAppsService.getAll().then(function success(response) {
            vm.appModules = response.data;
        });
        SslSuggestionsService.loadSuggestions().then(function success(suggestions) {
            vm.suggestions = suggestions;
            vm.appFilteredTableData = translateDateTimes(angular.copy(vm.suggestions.modules));
            vm.domainFilteredTableData = translateDateTimes(angular.copy(vm.suggestions.domainsIps));
        });
    };

    function translateDateTimes(errorList) {
        const dateTimeFormat = 'ADMINCONSOLE.SSL_FAILS.DATE_TIME_FORMAT';
        errorList.forEach((e) => {
            e.translatedDateTime = LanguageService.getDate(e.lastOccurrence, dateTimeFormat);
        });
        return errorList;
    }

    // ** START: TABLE APP
    vm.tableAppId = TableService.getUniqueTableId('ssl-fails-app-table');
    vm.tableAppHeaderConfig = [
        {
            label: '',
            icon: '/img/icons/ic_list_black.svg',
            isSortable: true,
            isXsColumn: true,
            showOnSmallTable: false,
            sortingKey: 'enabled'
        },
        {
            label: 'ADMINCONSOLE.SSL_FAILS.TABLE_APP.COLUMN.APP',
            isSortable: true,
            showOnSmallTable: false,
            sortingKey: 'name'
        },
        {
            label: 'ADMINCONSOLE.SSL_FAILS.TABLE_APP.COLUMN.DEVICES',
            isSortable: true,
            sortingKey: 'devices'
        },
        {
            label: 'ADMINCONSOLE.SSL_FAILS.TABLE_APP.COLUMN.DOMAINS',
            isSortable: true,
            sortingKey: 'domainsIps'
        },
        {
            label: 'ADMINCONSOLE.SSL_FAILS.TABLE_APP.COLUMN.LAST_SEEN',
            isSortable: true,
            showOnSmallTable: false,
            sortingKey: 'lastOccurrence'
        },
        {
            label: 'ADMINCONSOLE.SSL_FAILS.TABLE_APP.COLUMN.TRUSTED',
            flex: 15,
            isSortable: false
        }
    ];

    vm.isAppSelectable = isAppSelectable;
    vm.getNumOfSelectedEntries = getNumOfSelectedEntries;
    vm.getNumOfSelectedUntrusted = getNumOfSelectedUntrusted;
    vm.markAsTrusted = markAsTrusted;
    vm.searchPropsApp = ['name', 'devices', 'domainsIps'];

    function isAppSelectable(value) {
        return !value.enabled;
    }

    function getNumOfSelectedEntries(array) {
        let num = 0;
        if (angular.isArray(array)) {
            array.forEach((item) => {
                if (item._checked) {
                    num++;
                }
            });
        }
        return num;
    }

    function getNumOfSelectedUntrusted(array) {
        let num = 0;
        if (angular.isArray(array)) {
            array.forEach((item) => {
                if (item._checked && !item.enabled) {
                    num++;
                }
            });
        }
        return num;
    }

    function markAsTrusted() {
        vm.appFilteredTableData.forEach((item) => {
            if (item._checked) {
                item.enabled = true;
                TrustedAppsService.toggleAppModule(item).then(function success() {
                    item._checked = false;
                });
            }
        });
    }
    // TABLE APP END


    // TABLE DOMAIN START
    vm.tableDomainId = TableService.getUniqueTableId('ssl-fails-domain-table');
    vm.domainTableCallback = {};
    vm.tableDomainHeaderConfig = [
        {
            label: '',
            isSortable: true,
            isXsColumn: true,
            sortingKey: 'enabled',
            showOnSmallTable: false
        },
        {
            label: 'ADMINCONSOLE.SSL_FAILS.TABLE_DOMAIN.COLUMN.DOMAIN',
            isSortable: true,
            sortingKey: 'domainIp'
        },
        {
            label: 'ADMINCONSOLE.SSL_FAILS.TABLE_DOMAIN.COLUMN.DEVICES',
            isSortable: true,
            sortingKey: 'devices'
        },
        {
            label: 'ADMINCONSOLE.SSL_FAILS.TABLE_DOMAIN.COLUMN.LAST_SEEN',
            isSortable: true,
            showOnSmallTable: false,
            sortingKey: 'lastOccurrence'
        },
        {
            label: 'ADMINCONSOLE.SSL_FAILS.TABLE_DOMAIN.COLUMN.ACTION',
            isSortable: false
        }
    ];
    vm.searchPropsDomain = ['domainIp', 'devices'];

    // TABLE END


    function clearErrors() {
        SslService.clearErrors().then(function success() {
            SslSuggestionsService.resetSuggestions();
            vm.suggestions = SslSuggestionsService.getSuggestions();
        });
    }

    function getSelectedConnections() {
        return vm.domainFilteredTableData.filter(function(connection) {
            return connection._checked;
        });
    }

    function getSelectedDomains(connections) {
        return connections.map(function(connection) {
            return connection.domainIp;
        }).join('\n');
    }

    function sameDomain(a, b) {
        if (a.indexOf('.') === -1 || b.indexOf('.') === -1) {
            return false;
        }
        return a === b || a.endsWith('.' + b);
    }

    function updateDomainSuggestions(domainsIps, savedModuleName) {
        vm.suggestions.domainsIps.forEach(function(connection) {
            if (domainsIps.find(function(domainIp) {
                    return sameDomain(connection.domainIp, domainIp);
                })) {
                if (savedModuleName) {
                    connection.enabledModule = savedModuleName;
                } else {
                    connection.enabledWhitelist = true;
                }
                connection.selected = false;
            }
        });
        vm.domainFilteredTableData = translateDateTimes(angular.copy(vm.suggestions.domainsIps));
        if (angular.isFunction(vm.domainTableCallback.reload)) {
            $timeout(vm.domainTableCallback.reload, 0);
        }
    }

    function newTrustedApp(event) {
        const connections = getSelectedConnections();
        const domainsAndIps = getSelectedDomains(connections);
        const tmpDomains = [];
        const tmpIps = [];
        const IP_ADDRESS_REGEX = /^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)){3}$/; // jshint ignore: line

        const entries = domainsAndIps.split(/\s+/g);
        for (let x = 0; x < entries.length; x++) {
            const entry = entries[x];
            if (IP_ADDRESS_REGEX.test(entry)) {
                tmpIps.push(entry);
            } else {
                tmpDomains.push(entry);
            }
        }
        // Turn both lists into newline-separated strings
        const ips = tmpIps.join('\n');
        const domains = tmpDomains.join('\n');
        const module = {
            domains: domains+'\n'+ips,
            domainsIps: domains+'\n'+ips,
            builtin: false,
            enabledPerDefault: false,
            hidden: false,
            enabled: false,
            modified: true
        };

        DialogService.trustedAppAdd(event, module).then(function success(response) {
            const savedModule = response.data;
            updateDomainSuggestions(savedModule.whitelistedDomainsIps, savedModule.name);
        });
    }

    function addDomainToApp(event) {
        const selectedDomains = getSelectedConnections();
        DialogService.addDomainToApp(event, vm.appModules, vm.suggestions, selectedDomains).
        then(function success(savedModule) {
            updateDomainSuggestions(savedModule.whitelistedDomainsIps, savedModule.name);
        });
    }

    function addToExceptionList(event) {
        const connections = getSelectedConnections();
        const domainsIps = getSelectedDomains(connections);
        const module = {
            label: '',
            domainsText: domainsIps
        };
        DialogService.trustedDomainAddEdit(event, module).then(function() {
            updateDomainSuggestions(module.domainsText.split('\n'));
        });
    }

    function toggleSslErrorRecording() {
        SslService.setSslErrorRecordingEnabled(vm.ssl.recordingEnabled).then(function() {
            vm.ssl = SslService.getSslSettings();
            SslSuggestionsService.resetSuggestions();
        });
    }

}
