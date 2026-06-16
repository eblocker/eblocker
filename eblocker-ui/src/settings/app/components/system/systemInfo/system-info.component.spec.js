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
import 'angular-mocks';

/* global spyOn */

describe('App settings; system info component controller', function() {
    beforeEach(angular.mock.module('template.settings.app'));
    beforeEach(angular.mock.module('eblocker.adminconsole'));

    let ctrl, $componentController, $httpBackend, $interval, ConsoleService, StateService, SystemService;

    ConsoleService = {
        init: function() {
            return responsePromise({});
        },
        initiallyShowNavBar: function() {
            return false;
        },
        isGlobalSpinner: function() {
            return false;
        },
        isInitialized: function() {
            return true;
        },
        isPageSpinner: function() {
            return false;
        },
        showDashboardButton: function() {
            return false;
        }
    };

    StateService = {
        getWorkflowState: function() {},
        isStateValid: function() {
            return true;
        },
        getSubStates: function() {
            return [];
        },
        setStates: function() {}
    };

    SystemService = {
        loadSystemStatus: function() {
            return responsePromise({});
        },
        loadSystemParameters: function() {}
    };

    beforeEach(angular.mock.module(function($provide, $translateProvider) {
        $provide.value('ConsoleService', ConsoleService);
        $provide.value('StateService', StateService);
        $provide.value('SystemService', SystemService);
        // Workaround angular-translate issue:
        // https://angular-translate.github.io/docs/#/guide/22_unit-testing-with-angular-translate
        $translateProvider.translations('en', {});
    }));

    beforeEach(inject(function(_$componentController_, _$httpBackend_, _$interval_, _SystemService_) {
        $componentController = _$componentController_;
        $httpBackend = _$httpBackend_;
        $interval = _$interval_;
        SystemService = _SystemService_;
        $httpBackend.whenGET('/api/settings').respond(200, {});
        ctrl = $componentController('systemInfoComponent', {}, {});
    }));

    afterEach(function() {
        ctrl.$onDestroy();
    });

    it('loads and formats live system information and specs', function() {
        spyOn(SystemService, 'loadSystemParameters').and.returnValue(responsePromise({
            cpuTemperatureCelsius: 52.375,
            loadAverage1Minute: 0.11,
            loadAverage5Minutes: 0.22,
            loadAverage15Minutes: 0.33,
            memoryAvailableBytes: 2 * 1024 * 1024 * 1024,
            memoryTotalBytes: 8 * 1024 * 1024 * 1024,
            swapFreeBytes: 0,
            swapTotalBytes: 1024 * 1024 * 1024,
            rootDiskAvailableBytes: 32 * 1024 * 1024 * 1024,
            rootDiskTotalBytes: 64 * 1024 * 1024 * 1024,
            uptimeSeconds: 90061,
            hardwareModel: 'Raspberry Pi 5 Model B Rev 1.0',
            operatingSystemName: 'Debian GNU/Linux 12 (bookworm)',
            kernelVersion: '6.12.34+rpt-rpi-v8',
            architecture: 'aarch64',
            cpuCoreCount: 4
        }));

        ctrl.$onInit();

        expect(ctrl.systemParameters.hardwareModel).toBe('Raspberry Pi 5 Model B Rev 1.0');
        expect(ctrl.formatTemperature(ctrl.systemParameters.cpuTemperatureCelsius)).toBe('52.4 °C');
        expect(ctrl.formatLoad(ctrl.systemParameters)).toBe('0.11 / 0.22 / 0.33');
        expect(ctrl.formatMemory(ctrl.systemParameters)).toBe('2.0 GB / 8.0 GB');
        expect(ctrl.formatSwap(ctrl.systemParameters)).toBe('0 MB / 1.0 GB');
        expect(ctrl.formatDisk(ctrl.systemParameters)).toBe('32.0 GB / 64.0 GB');
        expect(ctrl.formatUptime(ctrl.systemParameters.uptimeSeconds)).toBe('1d 1h 1m');
        expect(ctrl.formatValue(ctrl.systemParameters.architecture)).toBe('aarch64');
        expect(SystemService.loadSystemParameters.calls.count()).toBe(1);
    });

    it('refreshes system information periodically', function() {
        spyOn(SystemService, 'loadSystemParameters').and.returnValue(responsePromise({
            memoryAvailableBytes: 1,
            memoryTotalBytes: 2
        }));

        ctrl.$onInit();
        $interval.flush(5000);

        expect(SystemService.loadSystemParameters.calls.count()).toBe(2);
    });

    function responsePromise(data) {
        return {
            then: function(success) {
                return success({data: data});
            }
        };
    }
});
