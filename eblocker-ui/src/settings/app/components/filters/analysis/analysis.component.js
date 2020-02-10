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
    templateUrl: 'app/components/filters/analysis/analysis.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        captivePortal: '<',
        compressionMode: '<',
        doNotTrack: '<',
        referrer: '<',
        webRtc: '<'
    }
};

function Controller(logger, AnalysisToolService, DeviceService, $window, TableService, DialogService) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.$onDestroy = function(){
        shutdown();
    };

    vm.$onInit = function() {
        initialize();
        vm.recordedTransactions = [];
        vm.filteredTableData = [];
        // AnalysisToolService.getAll().then(function(recordedTransactions) {
        //     vm.recordedTransactions = recordedTransactions;
        //     vm.filteredTableData = angular.copy(vm.recordedTransactions);
        // });
    };

    // ** START: TABLE
    vm.templateCallback = {
        header: header
    };
    vm.tableId = TableService.getUniqueTableId('filter-analysis-tools-table');
    vm.tableHeaderConfig = [
        {
            label: '',
            icon: '/img/icons/ic_list_black.svg',
            isSortable: true,
            isXsColumn: true,
            sortingKey: 'currentConfig'
        },
        {
            label: 'ADMINCONSOLE.FILTER_ANALYSIS.TABLE.COLUMN.ID',
            isSortable: true,
            isXsColumn: true,
            sortingKey: 'id'
        },
        {
            label: 'ADMINCONSOLE.FILTER_ANALYSIS.TABLE.COLUMN.TS',
            isSortable: true,
            sortingKey: 'timestamp'
        },
        {
            label: 'ADMINCONSOLE.FILTER_ANALYSIS.TABLE.COLUMN.DOMAIN',
            isSortable: true,
            sortingKey: 'domain'
        },
        {
            label: 'ADMINCONSOLE.FILTER_ANALYSIS.TABLE.COLUMN.METHOD',
            isSortable: true,
            flex: 10,
            sortingKey: 'method'
        },
        {
            label: 'ADMINCONSOLE.FILTER_ANALYSIS.TABLE.COLUMN.URL',
            isSortable: true,
            sortingKey: 'url'
        },
        {
            label: 'ADMINCONSOLE.FILTER_ANALYSIS.TABLE.COLUMN.DECISION',
            flex: 10,
            isSortable: false
        },
        {
            label: 'ADMINCONSOLE.FILTER_ANALYSIS.TABLE.COLUMN.DECIDER',
            isSortable: false
        },
        {
            label: '',
            isSortable: false,
            isXsColumn: true
        }
    ];
    vm.searchTerm = '';
    vm.searchProps = ['id', 'domain', 'method', 'url', 'decision', 'decider'];
    vm.detailsState = 'analysisdetails';
    vm.isDeletable  = isDeletable;
    vm.deleteSingleEntry  = deleteSingleEntry;

    function isDeletable(value) {
        return true;
    }

    function deleteSingleEntry(value) {
        vm.recordedTransactions = vm.recordedTransactions.filter(function (el) {
            return el.id !== value.id;
        });
        vm.filteredTableData = angular.copy(vm.recordedTransactions);
    }

    // ## END: TABLE

    vm.initializing = true;

    vm.updating = false;

    vm.header = header;

    vm.transactionRecorderInfo = {
        deviceId: undefined,
        timeLimitSeconds: 300,
        sizeLimitBytes: 104857600,
        active: false,
        runningTime: 0,
        gatheredBytes: 0
    };

    vm.devices = [];

    vm.selectedDevice = undefined;

    vm.timeLimitSeconds = 300;

    vm.sizeLimitBytes = 104857600;

    vm.whatIfMode = false;

    vm.start = function(){
        vm.transactionRecorderInfo.deviceId = vm.selectedDevice.id;
        vm.transactionRecorderInfo.timeLimitSeconds = vm.timeLimitSeconds;
        vm.transactionRecorderInfo.sizeLimitBytes = vm.sizeLimitBytes;
        AnalysisToolService.start(vm.transactionRecorderInfo).then(function(started) {
            vm.transactionRecorderInfo.active = started;
        });
    };

    vm.stop = function(){
        AnalysisToolService.stop().then(function(stopped) {
            vm.update();
            vm.transactionRecorderInfo.active = !stopped;
        });
    };

    vm.clear = function(){
        vm.recordedTransactions = [];
        vm.filteredTableData = [];
    };

    vm.update = function() {
        AnalysisToolService.getAll().then(function(recordedTransactions) {
            vm.recordedTransactions = recordedTransactions;
            vm.filteredTableData = angular.copy(vm.recordedTransactions);
        });
    };

    vm.remove = function(id) {
        let toRemove = -1;
        for (let i = 0; i < vm.recordedTransactions.length; i++) {
            if (vm.recordedTransactions[i].id === id) {
                toRemove = i;
                break;
            }
        }
        if (toRemove >= 0) {
            vm.recordedTransactions.splice(toRemove, 1);
        }
    };

    function header(event, recordedTransaction) {
        let header = '';
        if (angular.isDefined(recordedTransaction.headers)) {
            for (let name in recordedTransaction.headers) {
                if (recordedTransaction.headers.hasOwnProperty(name)) {
                    let values = recordedTransaction.headers[name];
                    for (let j = 0; j < values.length; j++) {
                        header += name + ': ' + values[j] + '\n';
                    }
                }
            }
        }
        DialogService.analysisToolDetails(event, recordedTransaction, header) ;
    }

    vm.downloadCSV = function() {
        $window.location = '/recorder/results/csv';
    };

    function shutdown() {
        AnalysisToolService.deactivateStatusCheck();
    }

    vm.setWhatIfMode = function() {
        AnalysisToolService.setWhatIfMode(vm.whatIfMode);
    };

    //
    // Load all devices and activate status check
    //
    function initialize() {
        vm.initializing = true;

        DeviceService.getAll().then(function(response) {
            const devices = response.data;
            vm.devices = [];
            for (let i = 0; i < devices.length; i++) {
                let device = devices[i];
                if (!angular.isDefined(device.name) &&
                    (!angular.isDefined(device.ipAddresses) ||
                        device.ipAddresses.length === 0)) {
                    continue;
                }

                device.displayName = (angular.isDefined(device.name) && device.name !== '' ?
                    device.name + ' (' + device.ipAddresses[0] + ')' : device.ipAddresses[0]);
                if (device.isCurrentDevice) {
                    vm.selectedDevice = device;
                }
                vm.devices.push(device);
            }
        }).then(function() {
            AnalysisToolService.activateStatusCheck(function(transactionRecorderInfo) {
                vm.transactionRecorderInfo = transactionRecorderInfo;
            });
        }).then(function() {
            vm.initializing = false;

        });
        AnalysisToolService.getWhatIfMode().then(function(whatIfMode) {
            vm.whatIfMode = whatIfMode;
        });
    }

    vm.analysisAction = function(type) {
        if (type === 'CLEAR') {
            vm.clear();
        } else if (type === 'CVS') {
            vm.downloadCSV();
        }
    };
}
