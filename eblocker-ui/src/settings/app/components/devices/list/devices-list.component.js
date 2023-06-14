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
    templateUrl: 'app/components/devices/list/devices-list.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        users: '<',
        profiles: '<',
        dnsEnabled: '<',
        sslEnabled: '<'
    }
};

function Controller(logger, $filter, $stateParams, StateService, VpnService, RegistrationService,
                    DeviceService, TableService, VpnHomeService, ArrayUtilsService) {
    'ngInject';
    'use strict';

    const vm = this;
    vm.isFamily = RegistrationService.hasProductKey('FAM');

    vm.reloadTable = function() {
        vm.searchTerm = '';
        getAll(true);
    };

    vm.$onInit = function() {
        loadMobileStatus();
        // Need correct value for vm.sslEnabled, so initialize callbacks in onInit
        vm.templateCallback = {
            isFamily: vm.isFamily,
            getUserName: getUserName,
            sslEnabled: vm.sslEnabled,
            isMobile: vm.getIsMobileEnabled,
            isVpnActive: isVpnActive,
            onChangeEnabled: onChangeEnabled
        };
        vm.filteredTableData = [];
        vm.detailsParams = {
            getTableData: function() {
                return vm.filteredTableData;
            }
        };

        getAll(true).finally(function done() {
            const id = StateService.getIdFromParam($stateParams);
            const entry = ArrayUtilsService.getItemBy(vm.filteredTableData, 'id', id);
            if (angular.isObject(entry)) {
                vm.detailsParams.entry = entry;
                StateService.goToState(vm.detailsState, vm.detailsParams);
            }
        });
    };

    vm.getUserName = getUserName;
    vm.goToCurrentDevice = goToCurrentDevice;

    vm.getIsMobileEnabled = function() {
        return vm.isMobileEnabled;
    };

    function loadMobileStatus() {
        VpnHomeService.loadStatus().then(function(response) {
            vm.isMobileEnabled = response.data.isRunning;
        });
    }

    function getUserName(id) {
        // '1' is old, single standard user.
        // '2' is virtual user for locked devices.
        if (id === 1 || id === 2) {
            return null;
        }
        let ret = '';
        if (angular.isArray(vm.users)) {
            vm.users.forEach((user) => {
                // If user is system user, do not return the name (as for the old standard user).
                if (user.system) {
                    return null;
                }
                if (user.id === id) {
                    ret = user.name;
                }
            });
        }
        return ret;
    }

    // ** START: TABLE

    vm.tableId = TableService.getUniqueTableId('devices-table');

    vm.tableHeaderConfig = [
        {
            label: '',
            icon: '/img/icons/ic_computer_black.svg',
            isSortable: true,
            isXsColumn: true,
            sortingKey: 'enabled',
            showHeader: true,
        },
        {
            label: 'ADMINCONSOLE.DEVICES_LIST.TABLE.COLUMN.IP',
            isSortable: true,
            showOnSmallTable: false,
            sortingKey: 'sortingKeyIpAddress',
            showHeader: true,
        },
        {
            label: 'ADMINCONSOLE.DEVICES_LIST.TABLE.COLUMN.NAME',
            isSortable: true,
            secondSorting: true,
            flexGtXs: 20,
            sortingKey: 'name',
            showHeader: true,
        },
        {
            label: '',
            icon: '/img/icons/ic_star_rate_black.svg',
            isSortable: true,
            defaultSorting: true,
            isReversed: false,
            isXsColumn: true,
            sortingKey: 'tmpIsCurrentDevice',
            tooltip: 'ADMINCONSOLE.DEVICES_LIST.TOOLTIP.GO_TO_CURRENT',
            showHeader: true,
        },
        {
            label: 'ADMINCONSOLE.DEVICES_LIST.TABLE.COLUMN.STATUS',
            isSortable: true,
            sortingKey: 'isOnline',
            showHeader: true,
        },
        {
            label: 'ADMINCONSOLE.DEVICES_LIST.TABLE.COLUMN.ONLINE',
            isSortable: true,
            sortingKey: 'isOnline',
            showHeader: true,
        },
        {
            label: 'ADMINCONSOLE.DEVICES_LIST.TABLE.COLUMN.USER_ASSIGN',
            isSortable: true,
            sortingKey: 'tmpUser',
            showHeader: vm.isFamily,
        }
    ];

    vm.detailsState = 'devicedetails';
    // ** Search filter by these properties
    vm.searchProps = ['name', 'vendor', 'hardwareAddress', 'displayIpAddresses', 'tmpUser'];
    vm.searchTerm = '';

    vm.selectMultipleEntries = 'NONE';

    vm.isDeletable = isDeletable;
    vm.deleteSingleEntry = deleteSingleEntry;
    vm.onSingleDeleteDone = onSingleDeleteDone;
    vm.selectMultiple = selectMultiple;
    vm.isSelectable = isSelectable;

    function selectMultiple(mode) {
        vm.filteredTableData.forEach((entry) => {
            entry._checked = false;
            if (mode === 'NO_IP' && entry.displayIpAddresses === '') {
                entry._checked = isSelectable(entry);
            } else if (mode === 'OFFLINE' && !entry.isOnline) {
                entry._checked = isSelectable(entry);
            } else if (mode === 'ALL') {
                entry._checked = isSelectable(entry);
            }
        });
    }

    function isSelectable(value) {
        return isDeletable(value);
    }

    function isDeletable(value) {
        return !value.isEblocker && !value.isGateway && !value.isOnline;
    }

    function deleteSingleEntry(value) {
        return DeviceService.deleteDevice(value.id);
    }

    function onSingleDeleteDone() {
        getAll(true);
    }
    // ## END: TABLE

    function goToCurrentDevice() {
        // We need a sorted copy, so that we have the same sorting as the table
        // and we do not change the original array.
        const tmp = $filter('orderBy')(vm.filteredTableData, vm.orderKey, vm.reverseOrder);
        const index = tmp.findIndex((el) => {
            return el.isCurrentDevice;
        });
        if (index > -1) {
            // Mark the actual object in the actual filter
            vm.filteredTableData.forEach((device, i) => {
                if (device.isCurrentDevice)    {
                    device._checked = true;
                }
            });
            // Find page of current device
            const floorIt = index / vm.query.limit;
            vm.query.page = Math.floor(floorIt) + 1;
        }
    }

    function getAll(reload) {
        vm.filteredTableData = [];
        vm.loading = true;
        return DeviceService.getAll(reload).then(function success(response) {
            vm.tableData = response.data;
            vm.tableData.forEach((device) => {
                if (vm.isFamily) {
                    // To allow filtering and sorting by user name
                    const tmp = getUserName(device.assignedUser);
                    device.tmpUser = angular.isString(tmp) ? tmp : '~'; // tilde is highest sorting ascii char
                }
                device.tmpIsCurrentDevice = device.isCurrentDevice ? 0 : 1;
                DeviceService.setDisplayValues(device);
            });
            vm.filteredTableData = angular.copy(vm.tableData);
            return response;
        }).finally(function done() {
            vm.loading = false;
        });
    }

    /**
     * Fixes an VPN display issue where the device flag (useAnonymizationService and useVPNProfileID) are falsely set.
     * By loading the VPN config for the device we make sure that the VPN is actually connected.
     * @param entry
     * @returns {*}
     */
    function isVpnActive(entry) {
        if (entry.useAnonymizationService && !entry.routeThroughTor && !entry.vpnPending &&
            angular.isUndefined(entry.vpnIsActuallyUp)) {
            entry.vpnPending = true;
            loadVpnConfig(entry).finally(function () {
                delete entry.vpnPending;
            });
        }
        return entry.vpnIsActuallyUp;
    }

    function onChangeEnabled(entry) {
        DeviceService.update(entry.id, entry);
    }

    function loadVpnConfig(device) {
        return VpnService.getVpnStatusByDeviceId(device.id).then(function success(response) {
            device.vpnIsActuallyUp = response.data.up === true;
            if (!device.vpnIsActuallyUp) {
                logger.warn('Device ' + device.id + ' has inconsistent VPN state.');
            }
            return response;
        }, function error(response) {
            logger.error('Error loading VPN status', response);
        });
    }
}
