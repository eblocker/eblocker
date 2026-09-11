/*
 * Copyright 2026 eBlocker Open Source
 *
 * Licensed under the EUPL, Version 1.2 or later.
 */
export default {
    templateUrl: 'app/components/devices/vpnAccess/vpn-access.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {}
};

function Controller($q, DeviceService, UserService, VpnHomeService,
                    WireGuardService, NotificationService) {
    'ngInject';

    const vm = this;

    vm.authorizationUnknown = false;
    vm.rows = [];
    vm.loading = false;
    vm.searchText = '';
    vm.openVpnGlobalEnabled = undefined;
    vm.wireGuardGlobalEnabled = undefined;
    vm.sortField = 'displayName';
    vm.sortReverse = false;

    vm.reload = reload;
    vm.setOpenVpnAccess = setOpenVpnAccess;
    vm.setWireGuardDeviceAccess = setWireGuardDeviceAccess;
    vm.setWireGuardUserAccess = setWireGuardUserAccess;
    vm.canToggleWireGuardUser = canToggleWireGuardUser;
    vm.reasonKey = reasonKey;
    vm.accessSourceKey = accessSourceKey;
    vm.setSort = setSort;
    vm.sortIndicator = sortIndicator;

    vm.$onInit = reload;

    function setSort(field) {
        if (vm.sortField === field) {
            vm.sortReverse = !vm.sortReverse;
            return;
        }

        vm.sortField = field;
        vm.sortReverse = field !== 'displayName';
    }

    function sortIndicator(field) {
        if (vm.sortField !== field) {
            return '';
        }
        return vm.sortReverse ? '▼' : '▲';
    }

    function reload() {
        vm.loading = true;

        return $q.all([
            DeviceService.getAll(true),
            UserService.getAll(),
            VpnHomeService.loadStatus(),
            WireGuardService.getDeviceAuthorizations()
        ]).then(function(result) {
            const devices = angular.isArray(result[0].data) ? result[0].data : [];
            const users = angular.isArray(result[1].data) ? result[1].data : [];
            const userMap = buildRealUserMap(users);
            const status = result[2].data || {};
            const overview = result[3].data || {};
            const authorizations = angular.isArray(overview.devices) ?
                overview.devices : [];
            const authorizationMap =
                buildAuthorizationMap(authorizations);

            vm.openVpnGlobalEnabled = readOpenVpnGlobalState(status);
            vm.wireGuardGlobalEnabled =
                overview.globalEnabled === true;

            return devices
                .filter(isRealDevice)
                .map(function(device) {
                    if (angular.isFunction(DeviceService.setDisplayValues)) {
                        DeviceService.setDisplayValues(device);
                    }

                    const authorization =
                        authorizationMap[device.id];

                    if (!authorization) {
                        throw new Error(
                            'Missing WireGuard authorization for device ' +
                            device.id
                        );
                    }

                    return buildRow(
                        device,
                        authorization,
                        userMap
                    );
                });
        }).then(function(rows) {
            vm.authorizationUnknown = false;
            vm.rows = rows;
            return rows;
        }, function(response) {
            vm.authorizationUnknown = true;
            vm.rows = [];
            vm.openVpnGlobalEnabled = undefined;
            vm.wireGuardGlobalEnabled = undefined;
            NotificationService.error(
                'ADMINCONSOLE.VPN_ACCESS.NOTIFICATION.LOAD_ERROR',
                response
            );
            return $q.reject(response);
        }).finally(function() {
            vm.loading = false;
        });
    }

    function buildRealUserMap(users) {
        const map = {};
        users.forEach(function(user) {
            if (user && user.system !== true && angular.isDefined(user.id)) {
                map[user.id] = user;
            }
        });
        return map;
    }

    function buildAuthorizationMap(authorizations) {
        const map = {};

        authorizations.forEach(function(authorization) {
            if (authorization &&
                angular.isDefined(authorization.deviceId)) {

                map[authorization.deviceId] = authorization;
            }
        });

        return map;
    }

    function isRealDevice(device) {
        return device &&
            device.isEblocker !== true &&
            device.isGateway !== true &&
            angular.isDefined(device.id);
    }

    function buildRow(device, authorization, userMap) {
        const assignedUser = authorization.userPermissionRequired === true ?
            userMap[authorization.assignedUserId] : undefined;

        return {
            device: device,
            displayName: device.displayName || device.name || device.id,
            assignedUser: assignedUser,
            assignedUserName: assignedUser ?
                (assignedUser.name || ('#' + assignedUser.id)) : '',
            openVpnEnabled: readOpenVpnDeviceState(device),
            authorization: angular.copy(authorization),
            confirmedAuthorization: angular.copy(authorization),
            busyOpenVpn: false,
            busyWireGuardDevice: false,
            busyWireGuardUser: false
        };
    }

    function readOpenVpnDeviceState(device) {
        if (angular.isDefined(device.eblockerMobileEnabled)) {
            return device.eblockerMobileEnabled === true;
        }
        if (angular.isDefined(device.mobileState)) {
            return device.mobileState === true;
        }
        return false;
    }

    function readOpenVpnGlobalState(status) {
        if (angular.isDefined(status.isRunning)) {
            return status.isRunning === true;
        }
        if (angular.isDefined(status.running)) {
            return status.running === true;
        }
        if (angular.isDefined(status.enabled)) {
            return status.enabled === true;
        }
        return undefined;
    }

    function setOpenVpnAccess(row) {
        const desired = row.openVpnEnabled === true;
        row.busyOpenVpn = true;

        const op = desired ?
            VpnHomeService.enableDevice(row.device.id) :
            VpnHomeService.disableDevice(row.device.id);

        return op.then(function() {
            return true;
        }, function(response) {
            row.openVpnEnabled = !desired;
            NotificationService.error(
                'ADMINCONSOLE.VPN_ACCESS.NOTIFICATION.OPENVPN_ERROR',
                response
            );
            return $q.reject(response);
        }).finally(function() {
            row.busyOpenVpn = false;
        });
    }

    function setWireGuardDeviceAccess(row) {
        const desired = row.authorization.deviceEnabled === true;
        row.busyWireGuardDevice = true;

        return WireGuardService
            .setDeviceAuthorization(row.device.id, desired)
            .then(function(response) {
                row.authorization = angular.copy(response.data);
                row.confirmedAuthorization = angular.copy(response.data);
                vm.wireGuardGlobalEnabled =
                    response.data.globalEnabled === true;
                return response.data;
            }, function(response) {
                // The write may have persisted before runtime reconciliation failed.
                // Discard every displayed decision until the backend confirms it.
                vm.authorizationUnknown = true;
                vm.rows = [];
                vm.openVpnGlobalEnabled = undefined;
                vm.wireGuardGlobalEnabled = undefined;
                NotificationService.error(
                    'ADMINCONSOLE.VPN_ACCESS.NOTIFICATION.WIREGUARD_DEVICE_ERROR',
                    response
                );
                return reload();
            }).finally(function() {
                row.busyWireGuardDevice = false;
            });
    }

    function setWireGuardUserAccess(row) {
        if (!canToggleWireGuardUser(row)) {
            return $q.reject('WireGuard user permission is not applicable.');
        }

        const desired = row.authorization.userEnabled === true;
        row.busyWireGuardUser = true;

        return WireGuardService
            .setUserAuthorization(row.authorization.assignedUserId, desired)
            .then(function() {
                // One user may own multiple devices: refresh all decisions.
                return reload();
            }, function(response) {
                // The write may have persisted before runtime reconciliation failed.
                // Discard every displayed decision until the backend confirms it.
                vm.authorizationUnknown = true;
                vm.rows = [];
                vm.openVpnGlobalEnabled = undefined;
                vm.wireGuardGlobalEnabled = undefined;
                NotificationService.error(
                    'ADMINCONSOLE.VPN_ACCESS.NOTIFICATION.WIREGUARD_USER_ERROR',
                    response
                );
                return reload();
            }).finally(function() {
                row.busyWireGuardUser = false;
            });
    }

    function canToggleWireGuardUser(row) {
        return angular.isObject(row) &&
            angular.isObject(row.authorization) &&
            row.authorization.userPermissionRequired === true &&
            angular.isNumber(row.authorization.assignedUserId);
    }

    function accessSourceKey(row) {
        const authorization = confirmedAuthorization(row);

        if (!authorization || authorization.allowed !== true) {
            return reasonKey(row);
        }

        const deviceEnabled = authorization.deviceEnabled === true;
        const userEnabled = authorization.userEnabled === true;

        if (deviceEnabled && userEnabled) {
            return 'ADMINCONSOLE.VPN_ACCESS.ACCESS_SOURCE.DEVICE_AND_USER';
        }
        if (deviceEnabled) {
            return 'ADMINCONSOLE.VPN_ACCESS.ACCESS_SOURCE.DEVICE';
        }
        if (userEnabled) {
            return 'ADMINCONSOLE.VPN_ACCESS.ACCESS_SOURCE.USER';
        }
        return 'ADMINCONSOLE.VPN_ACCESS.UNKNOWN';
    }

    function confirmedAuthorization(row) {
        if (!angular.isObject(row)) {
            return null;
        }
        if (angular.isObject(row.confirmedAuthorization)) {
            return row.confirmedAuthorization;
        }
        return angular.isObject(row.authorization) ? row.authorization : null;
    }

    function reasonKey(row) {
        const authorization = confirmedAuthorization(row);
        const knownReasons = [
            'GLOBAL_DISABLED', 'DEVICE_DISABLED', 'DEVICE_NOT_FOUND',
            'USER_NOT_FOUND', 'USER_INCONSISTENT', 'USER_DISABLED',
            'ALLOWED_NO_ASSIGNED_USER', 'ALLOWED'
        ];
        if (!authorization || !angular.isString(authorization.reason) ||
                knownReasons.indexOf(authorization.reason) === -1) {
            return 'ADMINCONSOLE.VPN_ACCESS.UNKNOWN';
        }
        return 'ADMINCONSOLE.VPN_ACCESS.REASON.' + authorization.reason;
    }
}
