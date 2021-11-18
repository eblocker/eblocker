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
    templateUrl: 'app/components/filters/overview/filter-overview.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        updateStatus: '<',
        devices: '<',
        dnsEnabled: '<',
        sslEnabled: '<'
    }
};

function Controller(logger, TableService, FILTER_TYPE, RegistrationService, DnsStatistics, // jshint ignore: line
                    FilterService, $q, StateService, STATES, BLOCKER_TYPE, BLOCKER_CATEGORY) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.resetCounters = resetCounters;

    vm.$onInit = function() {

        vm.detailsState = STATES.FILTER_DETAILS;
        vm.domainDetailsParams = {
            getTableData: function() {
                return vm.filteredDomainTableData;
            }
        };
        vm.patternDetailsParams = {
            getTableData: function() {
                return vm.filteredPatternTableData;
            }
        };

        vm.numberOfBlockedAds = '-';
        vm.numberOfBlockedTrackers = '-';
        vm.numberOfBlockedMalwareReqs = '-';
        vm.numberOfBlockedPatternAds = '-';
        vm.numberOfBlockedPatternTrackers = '-';
        vm.numberOfBlockedPatternMalwareReqs = '-';
        vm.numberOfBlockedPatternContentReqs = '-';

        getDnsStatistics();
        getPatternStatistics();

        vm.domainTableData = generateDomainBlockerTableData(vm.devices);
        vm.filteredDomainTableData = angular.copy(vm.domainTableData);

        vm.patternTableData = generatePatternBlockerTableData(vm.devices);
        vm.filteredPatternTableData = angular.copy(vm.patternTableData);
    };

    // ** START: TABLE
    vm.domainTableId = TableService.getUniqueTableId('filters-domain-overview-table');
    vm.patternTableId = TableService.getUniqueTableId('filters-pattern-overview-table');
    vm.tableHeaderConfig = [
        {
            label: 'ADMINCONSOLE.FILTER_OVERVIEW.TABLE.COLUMN.STATUS',
            isSortable: false,
            flex: 15,
            sortingKey: ''
        },
        {
            label: 'ADMINCONSOLE.FILTER_OVERVIEW.TABLE.COLUMN.FILTER_TYPE',
            isSortable: false,
            flexGtXs: 25,
            sortingKey: ''
        },
        {
            label: 'ADMINCONSOLE.FILTER_OVERVIEW.TABLE.COLUMN.DEVICES',
            isSortable: false,
            showOnSmallTable: false, // dont show on small tables, but show 4 header items to skip over this one.
            sortingKey: ''
        },
        {
            label: 'ADMINCONSOLE.FILTER_OVERVIEW.TABLE.COLUMN.NUM_BLOCKED',
            isSortable: false,
            flexGtXs: 20,
            sortingKey: ''
        },
        {
            label: '',
            isSortable: false,
            showOnLargeTable: false
        }
    ];
    vm.searchProps = ['name', 'devices', 'numBlocked'];
    // ### TABLE END

    function resetCounters() {
        logger.debug('Dummy function: reset counters.');
    }

    function getPatternStatistics() {
        DnsStatistics.getTotalStatistics('pattern').then(function success(response) {
            if (angular.isObject(response.summary) && angular.isObject(response.summary.blockedQueriesByReason)) {
                vm.numberOfBlockedPatternAds = response.summary.blockedQueriesByReason.ADS || '-';
                vm.numberOfBlockedPatternTrackers = response.summary.blockedQueriesByReason.TRACKERS || '-';
                vm.numberOfBlockedPatternMalwareReqs = response.summary.blockedQueriesByReason.MALWARE || '-';
                vm.numberOfBlockedPatternContentReqs = response.summary.blockedQueriesByReason.CONTENT || '-';
            }
        }, function error(response) {
            logger.error('No Pattern summary found ', response);
        });
    }


    function getDnsStatistics() {
        DnsStatistics.getTotalStatistics('dns').then(function success(response) {
            if (angular.isObject(response.summary) && angular.isObject(response.summary.blockedQueriesByReason)) {
                vm.numberOfBlockedAds = response.summary.blockedQueriesByReason.ADS || '-';
                vm.numberOfBlockedTrackers = response.summary.blockedQueriesByReason.TRACKERS || '-';
                vm.numberOfBlockedMalwareReqs = response.summary.blockedQueriesByReason.MALWARE || '-';
            }
        }, function error(response) {
            // handle rejection..
            logger.debug('No DNS summary found.');
        });
    }

    function setPatternBlockerStatistics(dev, names, counter) {
        names[FILTER_TYPE.PATTERN].push(dev);
        counter[FILTER_TYPE.PATTERN]++;
        if (dev.malwareFilterEnabled) {
            names[BLOCKER_CATEGORY.MALWARE_PATTERN].push(dev);
            counter[BLOCKER_CATEGORY.MALWARE_PATTERN]++;
        }
        names[BLOCKER_CATEGORY.CONTENT].push(dev);
        counter[BLOCKER_CATEGORY.CONTENT]++;
    }

    function setDomainBlockerStatistics(dev, names, counter) {
        if (dev.filterAdsEnabled) {
            names[FILTER_TYPE.DNS].ADS.push(dev);
            counter[FILTER_TYPE.DNS].ADS++;
        }
        if (dev.filterTrackersEnabled) {
            names[FILTER_TYPE.DNS].TRACKERS.push(dev);
            counter[FILTER_TYPE.DNS].TRACKERS++;
        }
        if (dev.malwareFilterEnabled) {
            names[BLOCKER_CATEGORY.MALWARE_DOMAIN].push(dev);
            counter[BLOCKER_CATEGORY.MALWARE_DOMAIN]++;
        }
    }

    function getBlockerDeviceStatus(devices) {
        const names = {};
        names[FILTER_TYPE.DNS] = {
            ADS: [],
            TRACKERS: []
        };
        names[FILTER_TYPE.PATTERN] = [];
        names[FILTER_TYPE.NONE] = [];
        names[BLOCKER_CATEGORY.MALWARE_PATTERN] = [];
        names[BLOCKER_CATEGORY.MALWARE_DOMAIN] = [];
        names[BLOCKER_CATEGORY.CONTENT] = [];

        const counter = {};
        counter[FILTER_TYPE.DNS] = {
            ADS: 0,
            TRACKERS: 0
        };
        counter[FILTER_TYPE.PATTERN] = 0;
        counter[FILTER_TYPE.NONE] = 0;
        counter[BLOCKER_CATEGORY.MALWARE_PATTERN] = 0;
        counter[BLOCKER_CATEGORY.MALWARE_DOMAIN] = 0;
        counter[BLOCKER_CATEGORY.CONTENT] = 0;

        devices.forEach((dev) => { // jshint ignore: line
            if ((names.hasOwnProperty(dev.filterMode) || dev.filterMode === FILTER_TYPE.AUTOMATIC) &&
                !dev.isEblocker && !dev.isGateway && (dev.enabled || dev.paused)) {

                if (dev.filterMode === FILTER_TYPE.DNS) {
                    setDomainBlockerStatistics(dev, names, counter);
                } else if (dev.filterMode === FILTER_TYPE.AUTOMATIC) {
                    if (!vm.sslEnabled || !dev.sslEnabled) {
                        // auto mode and SSL global or SS device is off, we use DNS filter / plug and play
                        setDomainBlockerStatistics(dev, names, counter);
                    } else {
                        // SSL global AND device SSL is on, we use Pattern filter
                        setPatternBlockerStatistics(dev, names, counter);
                    }
                } else if (dev.filterMode === FILTER_TYPE.PATTERN) {
                    setPatternBlockerStatistics(dev, names, counter);
                } else {
                    logger.warn('Unknown filter mode ' + dev.filterMode + ' for device ', dev);
                }
            }
        });
        return {
            names: names,
            counter: counter
        };
    }

    function getSslDisabledDevices(devices) {
        let noSsl = '';
        devices.forEach((dev, index) => {
            const devName = angular.isString(dev.name) ? dev.name : dev.vendor + ' ' + index;
            if (!dev.sslEnabled && dev.filterMode === FILTER_TYPE.PATTERN &&
                !dev.isEblocker && !dev.isGateway &&
                (dev.enabled || dev.paused)) {
                const name = noSsl === '' ? devName : ', ' + devName;
                noSsl = noSsl.concat(name);
            }
        });
        return noSsl;
    }

    function getSslDisabledDevicesAsList(devices) {
        let noSsl = [];
        devices.forEach((dev, index) => {
            if (!dev.sslEnabled && dev.filterMode === FILTER_TYPE.PATTERN &&
                !dev.isEblocker && !dev.isGateway &&
                (dev.enabled || dev.paused)) {
                noSsl.push(dev);
            }
        });
        return noSsl;
    }


    function generateDomainBlockerTableData(devices) {
        const tableData = [];

        const both = getBlockerDeviceStatus(devices);
        const counter = both.counter;
        const names = both.names;

        tableData.push(
            {
                id: 1, // required for paginator
                name: 'ADMINCONSOLE.FILTER_OVERVIEW.FILTER.DOMAIN_ADS',
                type: BLOCKER_TYPE.DOMAIN,
                category: BLOCKER_CATEGORY.ADS,
                usedBy: counter[FILTER_TYPE.DNS].ADS,
                devices: names[FILTER_TYPE.DNS].ADS,
                numBlocked: function () { return  vm.numberOfBlockedAds; },
                isLicensed: RegistrationService.hasProductKey('PRO'),
                dnsEnabled: vm.dnsEnabled,
                needsDns: true,
                template: 'app/components/filters/overview/help-filters-plug-and-play.template.html'
            }
        );

        tableData.push(
            {
                id: 2, // required for paginator
                name: 'ADMINCONSOLE.FILTER_OVERVIEW.FILTER.DOMAIN_TRACKERS',
                type: BLOCKER_TYPE.DOMAIN,
                category: BLOCKER_CATEGORY.TRACKER,
                usedBy: counter[FILTER_TYPE.DNS].TRACKERS,
                devices: names[FILTER_TYPE.DNS].TRACKERS,
                numBlocked: function () { return  vm.numberOfBlockedTrackers; },
                isLicensed: RegistrationService.hasProductKey('PRO'),
                dnsEnabled: vm.dnsEnabled,
                needsDns: true,
                template: 'app/components/filters/overview/help-filters-plug-and-play.template.html'
            }
        );

        tableData.push(
            {
                id: 3, // required for paginator
                name: 'ADMINCONSOLE.FILTER_OVERVIEW.FILTER.DOMAIN_MALWARE',
                type: BLOCKER_TYPE.DOMAIN,
                category: BLOCKER_CATEGORY.MALWARE,
                usedBy: counter[BLOCKER_CATEGORY.MALWARE_DOMAIN],
                devices: names[BLOCKER_CATEGORY.MALWARE_DOMAIN],
                numBlocked: function () { return vm.numberOfBlockedMalwareReqs; },
                isLicensed: RegistrationService.hasProductKey('PRO'),
                dnsEnabled: vm.dnsEnabled,
                needsDns: true,
                template: 'app/components/filters/overview/help-filters-malware.template.html'
            }
        );
        return tableData;
    }

    function generatePatternBlockerTableData(devices) {
        const tableData = [];

        const both = getBlockerDeviceStatus(devices);
        const counter = both.counter;
        const names = both.names;

        const deviceWithNoSsl = getSslDisabledDevices(devices);

        tableData.push(
            {
                id: 1, // required for paginator
                name: 'ADMINCONSOLE.FILTER_OVERVIEW.FILTER.PATTERN_ADS',
                type: BLOCKER_TYPE.PATTERN,
                category: BLOCKER_CATEGORY.ADS,
                usedBy: counter[FILTER_TYPE.PATTERN],
                devices: names[FILTER_TYPE.PATTERN],
                numBlocked: function () { return  vm.numberOfBlockedPatternAds; },
                isLicensed: RegistrationService.hasProductKey('PRO'),
                dnsEnabled: vm.dnsEnabled,
                sslStatus: deviceWithNoSsl === '',
                sslDisabledDevices: getSslDisabledDevicesAsList(devices),
                sslGloballyDisabled: !vm.sslEnabled,
                needsSsl: true,
                template: 'app/components/filters/overview/help-filters-pattern.template.html'
            }
        );

        tableData.push(
            {
                id: 2, // required for paginator
                name: 'ADMINCONSOLE.FILTER_OVERVIEW.FILTER.PATTERN_TRACKERS',
                type: BLOCKER_TYPE.PATTERN,
                category: BLOCKER_CATEGORY.TRACKER,
                usedBy: counter[FILTER_TYPE.PATTERN],
                devices: names[FILTER_TYPE.PATTERN],
                numBlocked: function () { return  vm.numberOfBlockedPatternTrackers; },
                isLicensed: RegistrationService.hasProductKey('PRO'),
                dnsEnabled: vm.dnsEnabled,
                sslStatus: deviceWithNoSsl === '',
                sslDisabledDevices: getSslDisabledDevicesAsList(devices),
                sslGloballyDisabled: !vm.sslEnabled,
                needsSsl: true,
                template: 'app/components/filters/overview/help-filters-pattern.template.html'
            }
        );
        tableData.push(
            {
                id: 3, // required for paginator
                name: 'ADMINCONSOLE.FILTER_OVERVIEW.FILTER.PATTERN_MALWARE',
                type: BLOCKER_TYPE.PATTERN,
                category: BLOCKER_CATEGORY.MALWARE,
                usedBy: counter[BLOCKER_CATEGORY.MALWARE_PATTERN],
                devices: names[BLOCKER_CATEGORY.MALWARE_PATTERN],
                numBlocked: function () { return vm.numberOfBlockedPatternMalwareReqs; },
                isLicensed: RegistrationService.hasProductKey('PRO'),
                dnsEnabled: vm.dnsEnabled,
                needsDns: true,
                template: 'app/components/filters/overview/help-filters-malware.template.html'
            }
        );
        tableData.push(
            {
                id: 4, // required for paginator
                name: 'ADMINCONSOLE.FILTER_OVERVIEW.FILTER.PATTERN_CONTENT',
                type: BLOCKER_TYPE.PATTERN,
                category: BLOCKER_CATEGORY.CONTENT,
                usedBy: counter[BLOCKER_CATEGORY.CONTENT],
                devices: names[BLOCKER_CATEGORY.CONTENT],
                numBlocked: function () { return vm.numberOfBlockedPatternContentReqs; },
                isLicensed: RegistrationService.hasProductKey('PRO'),
                dnsEnabled: vm.dnsEnabled,
                needsDns: true,
                template: 'app/components/filters/overview/help-filters-content.template.html'
            }
        );

        return tableData;
    }

}
