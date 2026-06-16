/*
 * Copyright 2026 eBlocker Open Source UG (haftungsbeschraenkt)
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
var REFRESH_INTERVAL_MILLISECONDS = 5000;
var BYTES_PER_MEGABYTE = 1024 * 1024;
var BYTES_PER_GIGABYTE = 1024 * 1024 * 1024;

export default {
    templateUrl: 'app/components/system/systemInfo/system-info.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(SystemService, $interval, logger) {
    'ngInject';
    'use strict';

    var vm = this;
    var refreshPromise;

    vm.refresh = refresh;
    vm.formatTemperature = formatTemperature;
    vm.formatLoad = formatLoad;
    vm.formatMemory = formatMemory;
    vm.formatSwap = formatSwap;
    vm.formatDisk = formatDisk;
    vm.formatUptime = formatUptime;
    vm.formatValue = formatValue;

    vm.$onInit = function() {
        refresh();
        refreshPromise = $interval(refresh, REFRESH_INTERVAL_MILLISECONDS);
    };

    vm.$onDestroy = function() {
        if (angular.isDefined(refreshPromise)) {
            $interval.cancel(refreshPromise);
        }
    };

    function refresh() {
        return SystemService.loadSystemParameters().then(function success(response) {
            vm.systemParameters = response.data;
            vm.lastUpdated = new Date();
            return vm.systemParameters;
        }, function error(reason) {
            logger.warn('Could not load system parameters', reason);
            vm.systemParameters = null;
            return null;
        });
    }

    function formatTemperature(value) {
        return angular.isNumber(value) ? value.toFixed(1) + ' °C' : '-';
    }

    function formatLoad(parameters) {
        if (!angular.isObject(parameters) ||
            !angular.isNumber(parameters.loadAverage1Minute) ||
            !angular.isNumber(parameters.loadAverage5Minutes) ||
            !angular.isNumber(parameters.loadAverage15Minutes)) {
            return '-';
        }
        return parameters.loadAverage1Minute.toFixed(2) + ' / ' +
            parameters.loadAverage5Minutes.toFixed(2) + ' / ' +
            parameters.loadAverage15Minutes.toFixed(2);
    }

    function formatMemory(parameters) {
        if (!angular.isObject(parameters)) {
            return '-';
        }
        return formatBytePair(parameters.memoryAvailableBytes, parameters.memoryTotalBytes);
    }

    function formatSwap(parameters) {
        if (!angular.isObject(parameters)) {
            return '-';
        }
        return formatBytePair(parameters.swapFreeBytes, parameters.swapTotalBytes);
    }

    function formatDisk(parameters) {
        if (!angular.isObject(parameters)) {
            return '-';
        }
        return formatBytePair(parameters.rootDiskAvailableBytes, parameters.rootDiskTotalBytes);
    }

    function formatBytePair(availableBytes, totalBytes) {
        if (!angular.isNumber(availableBytes) || !angular.isNumber(totalBytes)) {
            return '-';
        }
        return formatBytes(availableBytes) + ' / ' + formatBytes(totalBytes);
    }

    function formatBytes(bytes) {
        if (!angular.isNumber(bytes)) {
            return '-';
        }
        if (bytes >= BYTES_PER_GIGABYTE) {
            return (bytes / BYTES_PER_GIGABYTE).toFixed(1) + ' GB';
        }
        return Math.round(bytes / BYTES_PER_MEGABYTE) + ' MB';
    }

    function formatUptime(seconds) {
        var days;
        var hours;
        var minutes;

        if (!angular.isNumber(seconds)) {
            return '-';
        }
        days = Math.floor(seconds / 86400);
        hours = Math.floor((seconds % 86400) / 3600);
        minutes = Math.floor((seconds % 3600) / 60);
        if (days > 0) {
            return days + 'd ' + hours + 'h ' + minutes + 'm';
        }
        if (hours > 0) {
            return hours + 'h ' + minutes + 'm';
        }
        return minutes + 'm';
    }

    function formatValue(value) {
        return angular.isDefined(value) && value !== null && value !== '' ? value : '-';
    }
}
