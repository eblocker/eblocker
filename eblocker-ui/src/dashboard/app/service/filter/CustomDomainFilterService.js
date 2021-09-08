/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
export default function CustomDomainFilterService(logger, $http, $q, DeviceService, DialogService, DataCachingService,
                                                  security, DeviceSelectorService) {
    'ngInject';

    const PATH = '/api/dashboard/customdomainfilter';
    const config = {timeout: 3000};

    let filterCache = {};
    let customDomainFilter = emptyFilter();
    let device = {};

    DeviceService.getDevice().then(function(response) {
        device = response.data;
    });

    function getCustomDomainFilter(reload) {
        let path = PATH + '/' + getUserId();
        filterCache[path] = DataCachingService.loadCache(filterCache[path], path, reload, config).then(
            function success(response) {
                customDomainFilter.blacklistedDomains = response.data.blacklistedDomains;
                customDomainFilter.whitelistedDomains = response.data.whitelistedDomains;
                return response;
            }, function error(response) {
                logger.error('Unable to get custom domain filter', response);
                return $q.reject(response);
            });
        return filterCache[path];
    }

    function setCustomDomainFilter(filter) {
        let path = PATH + '/' + getUserId();
        return $http.put(path, filter).then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Unable to save custom domain filter', response);
            return $q.reject(response);
        });
    }

    function getUserId() {
        let device = DeviceSelectorService.getSelectedDevice();
        return device.operatingUser;
    }

    function emptyFilter() {
        return {
            blacklistedDomains: [],
            whitelistedDomains: []
        };
    }

    function updateBlocklist(domains) {
        customDomainFilter.blacklistedDomains = domains;
        return save();
    }

    function updatePasslist(domains) {
        customDomainFilter.whitelistedDomains = domains;
        return save();
    }

    function save() {
        if (!device.showDnsFilterInfoDialog) {
            return setCustomDomainFilter(customDomainFilter).then(function(response) {
                return response;
            });
        } else {
            return DialogService.dnsFilterChangeInfo(undefined, saveDevice, device).finally(function() {
                return setCustomDomainFilter(customDomainFilter).then(function(response) {
                    return response;
                });
            });
        }
    }

    function saveDevice(device, checkboxValue) {
        device.showDnsFilterInfoDialog = !checkboxValue;
        return DeviceService.update(device);
    }

    return {
        emptyFilter: emptyFilter,
        updateBlocklist: updateBlocklist,
        updatePasslist: updatePasslist,
        getCustomDomainFilter: getCustomDomainFilter,
        setCustomDomainFilter: setCustomDomainFilter
    };

}
