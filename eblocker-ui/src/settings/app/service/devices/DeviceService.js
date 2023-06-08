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
export default function DeviceService($http, $q, DataCachingService, IpUtilsService) {
    'ngInject';

    const PATH = '/api/adminconsole/devices';
    const PATH_ALL = PATH + '/all/';
    const PATH_SCAN = PATH + '/scanningInterval';
    const PATH_SCAN_DEV = PATH + '/scan';
    const PATH_AUTO_ENABLE_NEW_DEVICES = PATH + '/autoEnableNewDevices';
    const PATH_AUTO_ENABLE_NEW_DEVICES_AFTER_ACTIVATION = PATH + '/autoEnableNewDevicesAfterActivation';

    let devicesCache;

    function update(id, device) {
        return $http.put(PATH + '/' + id, device).then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        }).finally(function() {
            invalidateCache();
        });
    }

    function getById(id) {
        return $http.get(PATH + '/' + id).then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        });
    }

    function getAll(reload) {
        devicesCache = DataCachingService.loadCache(devicesCache, PATH, reload).then(function success(response) {
            let devices = response.data;
            for (let i = 0; i < devices.length; i++) {
                let device = devices[i];
                device.displayIpAddresses = device.ipAddresses.join(', ');
                device.displayName = (angular.isDefined(device.name) && device.name !== '' ?
                    device.name + ' (' + device.displayIpAddresses + ')' : device.displayIpAddresses);
            }
            return $q.resolve({data: devices});
        }, function error(response) {
            return $q.reject(response);
        });
        return devicesCache;
    }

    function invalidateCache() {
        devicesCache = undefined;
    }

    function deleteSeveralDevices(mode) {
        return $http.delete(PATH_ALL + mode).then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        }).finally(function() {
            invalidateCache();
        });
    }

    function deleteDevice(deviceId) {
        return $http.delete(PATH + '/' + deviceId).then(function success(response) {
            return response;
        }, function error(response) {
            $q.reject(response);
        }).finally(function() {
            invalidateCache();
        });
    }

    function resetDevice(device) {
        var deviceId = device.id;
        return $http.put(PATH + '/reset/' + deviceId, {}).then(function success(response) {
            return response;
        }, function error(response) {
            $q.reject(response);
        });
    }

    function setScanningInterval(seconds) {
        return $http.post(PATH_SCAN, seconds).then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        });
    }

    function getScanningInterval() {
        return $http.get(PATH_SCAN).then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        });
    }

    function isScanningAvailable() {
        return $http.get(PATH_SCAN_DEV).then(function(response) {
            return response;
        }, function(response) {
            $q.reject(response);
        });
    }

    function isAutoEnableNewDevices() {
        return $http.get(PATH_AUTO_ENABLE_NEW_DEVICES).then(function(response) {
            return response;
        }, function(response) {
            $q.reject(response);
        });
    }

    function setAutoEnableNewDevices(autoEnableNewDevices) {
        return $http.post(PATH_AUTO_ENABLE_NEW_DEVICES, autoEnableNewDevices).then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        });
    }

    function setAutoEnableNewDevicesAfterActivation(autoEnableNewDevices) {
        return $http.post(PATH_AUTO_ENABLE_NEW_DEVICES_AFTER_ACTIVATION, autoEnableNewDevices)
            .then(function success(response) {
                return response;
            }, function error(response) {
                return $q.reject(response);
            });
    }

    function scan() {
        return $http.post(PATH_SCAN_DEV).then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        }).finally(function () {
            invalidateCache();
        });
    }

    function setDisplayValues(device) { // jshint ignore: line
        let ipAddress = '-';
        device.displayIpAddresses = '';
        device.sortingKeyIpAddress = 0;

        if (angular.isArray(device.ipAddresses) && device.ipAddresses.length > 0) {
            const ipAddresses = IpUtilsService.sortByVersion(device.ipAddresses);
            device.sortingKeyIpAddress = IpUtilsService.sortingKey(ipAddresses[0]);
            device.displayIpAddresses = ipAddresses.join('\n');
            ipAddress = ipAddresses[0];
        }

        device.displayIpAddress = ipAddress;
        device.displayMacAddress = angular.isDefined(device.hardwareAddress) ?
            device.hardwareAddress.toUpperCase() : '-';
        device.displayVendor = angular.isDefined(device.vendor) ? device.vendor : '-';
        device.displayName =
            angular.isDefined(device.name) ? device.name :
                angular.isDefined(device.vendor) ? device.vendor :
                    ipAddress;

        // Icon display mode
        if (device.iconMode === 'OFF'){
            device.displayIconOn = false;
            device.displayIconFiveSeconds = false;
            device.displayIconBrowserOnly = false;
        } else if (device.iconMode === 'ON_ALL_DEVICES') {
            device.displayIconOn = true;
            device.displayIconFiveSeconds = false;
            device.displayIconBrowserOnly = false;
        } else if (device.iconMode === 'ON') {// =ON_BROWSER_ONLY
            device.displayIconOn = true;
            device.displayIconFiveSeconds = false;
            device.displayIconBrowserOnly = true;
        } else if (device.iconMode === 'FIVE_SECONDS') {
            device.displayIconOn = true;
            device.displayIconFiveSeconds = true;
            device.displayIconBrowserOnly = false;
        } else if (device.iconMode === 'FIVE_SECONDS_BROWSER_ONLY') {
            device.displayIconOn = true;
            device.displayIconFiveSeconds = true;
            device.displayIconBrowserOnly = true;
        }
    }

    function getDevicesAssignedOrOperatedByUser(users, devices) {
        const deviceList = [];
        const userIds = users.map(user => user.id);
        devices.forEach(dev => {
            if (userIds.indexOf(dev.operatingUser) > -1 || userIds.indexOf(dev.assignedUser) > -1) {
                deviceList.push(dev);
            }
        });
        return deviceList;
    }

    function reassignDefaultUserToDevices(users, devices) {
        const userIds = users.map(user => user.id);
        const promises = [];
        devices.forEach(dev => {
            /*
                1. assigned to deleted user, but operated by existing user  -> set ASS to DEFAULT
                2. assigned to deleted user and operated by deleted user    -> set BOTH to DEFAULT
                3. assigned to existing user, operated by deleted user      -> set OP to ASSIGNED
                4. assigned to existing user, operated by existing user     -> no op
             */
            if (userIds.indexOf(dev.assignedUser) > -1) {
                dev.assignedUser = dev.defaultSystemUser;
            }
            if (userIds.indexOf(dev.operatingUser) > -1) {
                dev.operatingUser = dev.assignedUser !== dev.defaultSystemUser ?
                    dev.assignedUser : dev.defaultSystemUser;
            }
            promises.push(update(dev.id, dev));
        });
        return $q.all(promises);
    }

    return {
        getAll: getAll,
        getById: getById,
        deleteDevice: deleteDevice,
        deleteSeveralDevices: deleteSeveralDevices,
        setScanningInterval: setScanningInterval,
        getScanningInterval: getScanningInterval,
        update: update,
        scan: scan,
        isScanningAvailable: isScanningAvailable,
        isAutoEnableNewDevices: isAutoEnableNewDevices,
        setAutoEnableNewDevices: setAutoEnableNewDevices,
        setAutoEnableNewDevicesAfterActivation: setAutoEnableNewDevicesAfterActivation,
        invalidateCache: invalidateCache,
        setDisplayValues: setDisplayValues,
        resetDevice: resetDevice,
        getDevicesAssignedOrOperatedByUser: getDevicesAssignedOrOperatedByUser,
        reassignDefaultUserToDevices: reassignDefaultUserToDevices
    };
}
