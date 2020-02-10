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
    templateUrl: 'app/components/ssl/manualRecording/manual-recording.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(logger, ManualRecordingService, TrustedAppsService, DialogService, NotificationService, // jshint ignore: line
                    SslService, DeviceService, TableService, $q, $translate) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.hasDisabledRule = hasDisabledRule;

    // ** START: TABLE
    vm.templateCallback = {
        getWhitelistedByAsString: getWhitelistedByAsString,
        updateTesting: updateTesting,
        editDomainIPRangeDialog: editDomainIPRangeDialog
    };
    vm.tableId = TableService.getUniqueTableId('ssl-expert-tools-table');
    vm.tableHeaderConfig = [
        {
            label: '',
            icon: '/img/icons/ic_list_black.svg',
            isSortable: true,
            isXsColumn: true,
            showOnSmallTable: false,
            sortingKey: 'currentConfig'
        },
        {
            label: 'ADMINCONSOLE.MANUAL_RECORDING.TABLE.COLUMN.DOMAIN',
            isSortable: true,
            sortingKey: 'recordedDomain'
        },
        {
            label: 'ADMINCONSOLE.MANUAL_RECORDING.TABLE.COLUMN.IP',
            isSortable: true,
            sortingKey: 'recordedIp'
        },
        {
            label: 'ADMINCONSOLE.MANUAL_RECORDING.TABLE.COLUMN.PROTOCOL',
            showOnSmallTable: false,
            isSortable: false
        },
        {
            label: 'ADMINCONSOLE.MANUAL_RECORDING.TABLE.COLUMN.CURRENT_RULE',
            isSortable: false
        },
        {
            label: 'ADMINCONSOLE.MANUAL_RECORDING.TABLE.COLUMN.RECOMMENDED_RULE',
            showOnSmallTable: false,
            isSortable: false
        },
        {
            label: 'ADMINCONSOLE.MANUAL_RECORDING.TABLE.COLUMN.TEMP_RULE',
            isSortable: false
        },
        {
            label: '',
            isSortable: false
        }
    ];

    vm.isDeletable = isDeletable;
    vm.deleteSingleEntry = deleteSingleEntry;
    vm.onSingleDeleteDone = onSingleDeleteDone;

    // Filtering following props of table entry
    vm.searchProps = ['recordedDomain', 'recordedIp'];

    function isDeletable() {
        return true;
    }

    function deleteSingleEntry(value) {
        vm.removeUrlFromRecordedList(value.recordedDomain);
        return $q.resolve({data: true});
    }

    function onSingleDeleteDone() {
        vm.getRecordedUrls();
    }
    // TABLE END

    vm.$onDestroy = function() {
        shutdown();
    };

    let whitelistedDomainsIps = {}; // in this dic, we store what apps a domain/IP is whitelisted by

    vm.$onInit = function () {
        initialize();
        activatePolling();

        SslService.getAllWhitelistedDomains().then(function(result) {
            // this is a dictionary with 'domains' as keys and 'appnames' as values
            // where the 'domain' or 'IP' is whitelisted by an app with the name 'appname'
            whitelistedDomainsIps = result;
        });

        DeviceService.getAll().then(function(response) {
            const devices = response.data;
            vm.devices = [];
            for (let i = 0; i < devices.length; i++) {
                const device = devices[i];
                if (!angular.isDefined(device.name) &&
                    (!angular.isDefined(device.ipAddresses) ||
                        device.ipAddresses.length === 0)){
                    continue;
                }

                device.displayName = (angular.isDefined(device.name) &&
                device.name !== '' ? device.name + ' (' + device.ipAddresses[0] + ')' : device.ipAddresses[0]);
                if (device.isCurrentDevice) {
                    vm.selectedDevice = device;
                }
                vm.devices.push(device);
            }
        });
    };

    vm.tableData = [];
    vm.filteredTableData = [];
    vm.recordingStatus = false;
    vm.testingRuleSet = {};

    // Flags to steer UX: Parts of the UI should be disabled while initialising
    // or updating
    vm.initializing = true;
    vm.updating = false;

    vm.toggleTesting = function(){
        TrustedAppsService.toggleAppModule(vm.testingRuleSet).catch(function error() {
            // Error case:
            vm.testingRuleSet.enabled = !vm.testingRuleSet.enabled;
        });
    };

    function updateTesting() { //jshint ignore: line
        vm.updating = true;

        const testing = {
            id: 0,
            whitelistedDomains: [],
            blacklistedDomains: [],
            whitelistedIPs: []
            // FUTURE: if we want to blacklist IPs, create a list here
        };
        for (let i = 0; i < vm.filteredTableData.length; i++) {
            const recordedUrl = vm.filteredTableData[i];
            if (recordedUrl.testingConfig === 'FILTER') {
                if (recordedUrl.flag === 'domain') {
                    // add domain to blacklist
                    if (angular.isDefined(recordedUrl.selectedDomain)) {
                        testing.blacklistedDomains.push(recordedUrl.selectedDomain);
                    } else {
                        // if no domain has been selected, fall back to the recorded domain
                        testing.blacklistedDomains.push(recordedUrl.recordedDomain);
                    }
                } else if (recordedUrl.flag === 'iprange') {
                    // add ip(range) to blacklist
                    if (angular.isDefined(recordedUrl.selectedIp)) { //jshint ignore: line
                        // FUTURE: if an ip(-range) needs to be blacklisted, do it here
                    } else { //jshint ignore: line
                        // if no IP has been selected, fall back to the recorded IP
                        // FUTURE: if an ip(-range) needs to be blacklisted, do it here
                    }
                } else {
                    logger.warning('No flag is set (config = FILTER), this should not happen.');
                    // no flag set, this should not happen
                }
            } else if (recordedUrl.testingConfig === 'ALLOW') {
                if (recordedUrl.flag === 'domain') {
                    // add domain to whitelist
                    if (angular.isDefined(recordedUrl.selectedDomain)) {
                        testing.whitelistedDomains.push(recordedUrl.selectedDomain);
                    } else {
                        // if no domain has been selected, fall back to the recorded domain
                        testing.whitelistedDomains.push(recordedUrl.recordedDomain);
                    }
                } else if (recordedUrl.flag === 'iprange') {
                    // add ip(range) to whitelist
                    if (angular.isDefined(recordedUrl.selectedIp)) {
                        testing.whitelistedIPs.push(recordedUrl.selectedIp+'/'+recordedUrl.iprangemask);
                    } else {
                        testing.whitelistedIPs.push(recordedUrl.recordedIp+'/'+recordedUrl.iprangemask);
                    }
                } else {
                    logger.warning('No flag is set, this should not happen.');
                    // no flag set, this should not happen
                }
            }
        }
        TrustedAppsService.save(testing).finally(function() {
            vm.updating = false;
        });
    }

    vm.startRecording = function(){
        const recordingContext = {
            deviceId: angular.isObject(vm.selectedDevice) ? vm.selectedDevice.id : null,
            timeLimit:vm.timeLimit*60,// To transmit seconds
            sizeLimit:vm.sizeLimit
        };
        activatePolling();
        ManualRecordingService.start(recordingContext).then(function(started) {
            vm.recordingStatus = started;
        });
    };

    vm.stopRecording = function(){
        ManualRecordingService.stop().then(function(stopped) {
            vm.recordingStatus = !stopped;
            vm.getRecordedUrls();
        });
    };

    function getWhitelistedByAsString(whitelistedBy) {
        if (angular.isArray(whitelistedBy) && whitelistedBy.length > 0) {
            whitelistedBy.forEach((item, index) => {
                if (angular.isArray(item) &&
                    item.length > 0 &&
                    item[0] === 'INTERNAL_USE_ONLY_SINGLE_ENTRIES_USERDEFINED') {
                    item[0] = $translate.instant('ADMINCONSOLE.SERVICE.MANUAL_RECORDING.USER_DEFINED_APP');
                    whitelistedBy[index] = item;
                }
            });
            return whitelistedBy.join(',');
        }
    }

    vm.updating = false;
    vm.getRecordedUrls = function() {
        vm.updating = true;
        vm.loading = true;
        return ManualRecordingService.getResult(vm.filteredTableData, whitelistedDomainsIps).
        then(function (recordedUrls) {
            vm.tableData = recordedUrls;
            vm.filteredTableData = angular.copy(vm.tableData);
        }).finally(function() {
            vm.updating = false;
            vm.loading = false;
        });
    };

    vm.clearList = function(){
        vm.filteredTableData = [];
        vm.tableData = [];
        vm.updateTesting();
    };

    vm.recordingAvailable = function() {
        return vm.filteredTableData.length > 0;
    };

    vm.removeUrlFromRecordedList = function(domain){
        // remove from original data
        vm.tableData = vm.tableData.filter((entry) => {
            return entry.recordedDomain !== domain;
        });
        // remove from original filtered data: in case user has typed content into search field, we do
        // not want to override the filteredData here.
        vm.filteredTableData = vm.filteredTableData.filter((entry) => {
            return entry.recordedDomain !== domain;
        });
    };

    // provide recorded domain and IP (if known) for a connection to be displayed
    vm.getDisplayDomainIP = function(recordedUrl) {
        if (!angular.isDefined(recordedUrl)) {
            return '(null)';
        }
        if (angular.isDefined(recordedUrl.recordedDomain)) {
            let displayDomainIP = '';
            displayDomainIP += recordedUrl.recordedDomain;
            if (angular.isDefined(recordedUrl.recordedIp)) {
                displayDomainIP += ' (' + recordedUrl.recordedIp + ')';
            }
            return displayDomainIP;
        } else {
            if (angular.isDefined(recordedUrl.recordedIp)) {
                return recordedUrl.recordedIp;
            } else {
                return '(unknown)';
            }
        }
    };

    // provide user-selected domain/IP (if selected) for a connection to be displayed
    vm.getDisplaySelectedDomainIP = function(recordedUrl) {
        if (!angular.isDefined(recordedUrl)) {
            return '(null)';
        }
        if (!angular.isDefined(recordedUrl.flag)) {
            // user has made no edit yet
            return 'N/A';
        }
        if (recordedUrl.flag === 'domain') {
            if (angular.isDefined(recordedUrl.selectedDomain)) {
                return recordedUrl.selectedDomain;
            } else {
                // this should not happen
                return 'N/A';
            }
        } else if (recordedUrl.flag === 'iprange') {
            if (angular.isDefined(recordedUrl.selectedIp) &&
                angular.isDefined(recordedUrl.iprangemask)) {
                return recordedUrl.selectedIp + '/' + recordedUrl.iprangemask;
            } else {
                // this should not happen
                return 'N/A';
            }
        }
        // this should not happen
        return 'N/A';
    };

    vm.getRuleDestination = function(module) {
        if (module.flag === 'iprange') {
            return module.selectedIp + '/' + module.iprangemask;
        } else {
            return module.selectedDomain;
        }
    };

    /*
	 * Save as App Configuration
	 */
    vm.save = function(event) {
        const domainUrls = [];
        const ipList = [];
        vm.filteredTableData.forEach((recordedUrl) => {
            // TEMP rule has to be set to ALLOW
            if (recordedUrl.testingConfig === 'ALLOW') {
                if (recordedUrl.flag === 'iprange') {
                    ipList.push(recordedUrl.selectedIp + '/' + recordedUrl.iprangemask);
                } else if (recordedUrl.flag === 'domain') {
                    domainUrls.push(recordedUrl.selectedDomain);
                } else {
                    // This should not happen
                    logger.warning('Found unknown flag during save \'' + recordedUrl.flag + '\'');
                }
            }
        });

        const module = {
            builtin: false,
            enabledPerDefault: false,
            hidden: false,
            enabled: false,
            modified: true,
            domainsIps: domainUrls.join('\n')+'\n'+ipList.join('\n')
        };
        addEditDialog(module, event);
    };

    vm.addToExistingApp = function(event) {
        const domainUrls = [];

        vm.filteredTableData.forEach((recordedUrl) => {
            // TEMP rule has to be set to ALLOW
            if (recordedUrl.testingConfig === 'ALLOW') {
                if (recordedUrl.flag === 'domain') {
                    // make compatible with dialog
                    domainUrls.push({domainIp: recordedUrl.selectedDomain});
                } else {
                    // This should not happen
                    logger.warning('Found unknown flag during addToExistingApp \'' + recordedUrl.flag + '\'');
                }
            }
        });
        DialogService.addDomainToApp(event, vm.appModules, null, domainUrls).
        then(function success() {
            vm.appModules = loadTrustedApps();
        }, angular.noop);

    };

    function addEditDialog(module, event) {
        DialogService.trustedAppAdd(event, module).then(saved, function() { /* cancel */ }).finally(function done() {
            vm.appModules = loadTrustedApps();
        }, angular.noop);
    }

    function saved(module) {
        return NotificationService.info('ADMINCONSOLE.MANUAL_RECORDING.NOTIFICATION.INFO_SAVED_APP',
            {name: module.name});
    }

    function hasDisabledRule() {
        let ret = false;
        vm.filteredTableData.forEach((recordedUrl) => {
            if (recordedUrl.testingConfig === 'ALLOW') {
                ret = true;
            }
        });
        return ret;
    }

    /*
	 * Edit details of the recorded Domain/IP: Parent domains, IP range
	 */
    function editDomainIPRangeDialog(module, event) {
        // here, the list of parent domains is prepared
        let parentDomains = [module.recordedDomain];
        let remainingDomain = module.recordedDomain;

        let dotpos = remainingDomain.search('\\.');
        while (dotpos > 0) {
            // +1 to avoid heading dot
            let parentDomain = remainingDomain.substring(dotpos+1, remainingDomain.length);
            parentDomains.push(parentDomain);
            remainingDomain = parentDomain;
            dotpos = remainingDomain.search('\\.');
        }
        if (parentDomains[parentDomains.length] === 'uk') {
            // there may be more!
            // remove last element, if top level domain consists of two domains (e.g. ".co.uk"
            parentDomains.pop();
        }
        // remove last element, it was just the top level domain (or the remaining ".co" of ".co.uk")
        parentDomains.pop();
        module.parentDomains=parentDomains;

        addDomainIPRangeDialog(module, event);
    }

    function addDomainIPRangeDialog(module, event) {
        DialogService.recordingDomainIpRangeEdit(event, module).then(domainIPRangeSaved);
    }

    function domainIPRangeSaved(module) {
        // also call updateTesting to tell the server if the IP range or the
        // domain was changed
        vm.updateTesting();
    }

    //
    // Start up initializations
    //

    //
    // Initialization:
    // --------------------
    // Load testing module
    // Delete all domains/IPs from testing module
    // Enable testing module
    // --> Do not allow "Refresh", until this is done.
    //
    function initialize() {
        vm.initializing = true;

        vm.appModules = loadTrustedApps();

        TrustedAppsService.get(0).then(function(response) {
            const module = response.data;
            //module.domainUrls = [];
            module.whitelistedDomains=[];
            module.blacklistedDomains = [];
            module.whitelistedIPs = [];
            vm.testingRuleSet = module;
            return TrustedAppsService.save(module);

        }).then(function saveSuccess() {
            vm.testingRuleSet.enabled = true;
            return TrustedAppsService.toggleAppModule(vm.testingRuleSet);

        }).then(function toggleSuccess() {
            vm.initializing = false;
        });
    }

    function loadTrustedApps() {
        return TrustedAppsService.getAll().then(function success(response) {
            return response.data;
        });
    }

    function shutdown() {
        vm.testingRuleSet.enabled = false;
        TrustedAppsService.toggleAppModule(vm.testingRuleSet);
        ManualRecordingService.deactivateStatusCheck();
    }

    function activatePolling() {
        ManualRecordingService.activateStatusCheck(function(status) {
            if (!status && vm.recordingStatus) {
                vm.recordingStatus = status;
                vm.getRecordedUrls();
                ManualRecordingService.deactivateStatusCheck();
            } else {
                vm.recordingStatus = status;
            }
        });
    }
}
