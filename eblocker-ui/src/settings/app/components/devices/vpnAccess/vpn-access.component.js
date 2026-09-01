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

    vm.rows = [];
    vm.loading = false;
    vm.searchText = '';
    vm.openVpnGlobalEnabled = undefined;
    vm.wireGuardGlobalEnabled = undefined;

    vm.reload = reload;
    vm.setOpenVpnAccess = setOpenVpnAccess;
    vm.setWireGuardDeviceAccess = setWireGuardDeviceAccess;
    vm.setWireGuardUserAccess = setWireGuardUserAccess;
    vm.canToggleWireGuardUser = canToggleWireGuardUser;
    vm.reasonKey = reasonKey;

    vm.$onInit = reload;

    function reload() {
        vm.loading = true;

        return $q.all([
            DeviceService.getAll(true),
            UserService.getAll(),
            VpnHomeService.loadStatus()
        ]).then(function(result) {
            const devices = angular.isArray(result[0].data) ? result[0].data : [];
            const users = angular.isArray(result[1].data) ? result[1].data : [];
            const userMap = buildRealUserMap(users);
            const status = result[2].data || {};

            vm.openVpnGlobalEnabled = readOpenVpnGlobalState(status);

            const loads = devices
                .filter(isRealDevice)
                .map(function(device) {
                    if (angular.isFunction(DeviceService.setDisplayValues)) {
                        DeviceService.setDisplayValues(device);
                    }

                    return WireGuardService.getDeviceAuthorization(device.id)
                        .then(function(response) {
                            return buildRow(device, response.data, userMap);
                        });
                });

            return $q.all(loads);
        }).then(function(rows) {
            vm.rows = rows;
            vm.wireGuardGlobalEnabled = rows.length > 0 ?
                rows[0].authorization.globalEnabled === true : undefined;
            return rows;
        }, function(response) {
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
            authorization: authorization,
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
                row.authorization = response.data;
                vm.wireGuardGlobalEnabled =
                    response.data.globalEnabled === true;
                return response.data;
            }, function(response) {
                row.authorization.deviceEnabled = !desired;
                NotificationService.error(
                    'ADMINCONSOLE.VPN_ACCESS.NOTIFICATION.WIREGUARD_DEVICE_ERROR',
                    response
                );
                return $q.reject(response);
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
                row.authorization.userEnabled = !desired;
                NotificationService.error(
                    'ADMINCONSOLE.VPN_ACCESS.NOTIFICATION.WIREGUARD_USER_ERROR',
                    response
                );
                return $q.reject(response);
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

    function reasonKey(row) {
        if (!row || !row.authorization || !row.authorization.reason) {
            return 'ADMINCONSOLE.VPN_ACCESS.UNKNOWN';
        }
        return 'ADMINCONSOLE.VPN_ACCESS.REASON.' +
            row.authorization.reason;
    }
}
