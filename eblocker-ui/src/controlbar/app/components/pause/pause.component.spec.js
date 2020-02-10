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
import 'angular-mocks';

/* jshint expr:true */
describe('App controlbar; Component: Pause', function() {
    // ** template cache: so that calls for templates get the 'real' HTML file.
    beforeEach(angular.mock.module('template.controlbar.app'));
    beforeEach(angular.mock.module('eblocker.controlbar'));

    let $componentController, $q, $rootScope, ctrl, mockLoggerService, $httpBackend, $mdDialog,
        mockPauseStatus, mockConsoleService;

    mockLoggerService = {
        info: function(param) {
            // nothing to do for now
        },
        error: function(param) {
            // nothing to do for now
        }
    };

    mockConsoleService = {
        goToPausedDashboard: function() {
            // do nothing
        },
        getDashboardPausedUrl: function() {
            return 'dummy-link';
        }
    };

    beforeEach(angular.mock.module(function($provide, $translateProvider) {
        $provide.value('logger', mockLoggerService);
        $provide.value('ConsoleService', mockConsoleService);
        // Workaround angular-translate issue:
        // https://angular-translate.github.io/docs/#/guide/22_unit-testing-with-angular-translate
        $translateProvider.translations('en', {});
    }));

    beforeEach(inject(function(_$componentController_, _$q_, _$rootScope_, _$httpBackend_, _$mdDialog_) {
        $componentController = _$componentController_;
        $q = _$q_;
        $rootScope = _$rootScope_;
        $httpBackend = _$httpBackend_;
        $mdDialog = _$mdDialog_;
        ctrl = $componentController('pause', {$mdDialog: $mdDialog}, {});
    }));

    describe('initially', function() {
        it('controller should be defined', function() {
            expect(angular.isDefined(ctrl)).toBe(true);
        });
    });

    describe('show Dialog due to show-dialog-flag', function() {
        beforeEach(function() {
            mockPauseStatus = {
                pausing: 0,
                pausingAllowed: true
            };
            $httpBackend.when('GET', '/api/device/dialogStatus').respond(200, true);
            $httpBackend.when('GET', '/api/device/pauseStatus').respond(200, mockPauseStatus);
            $httpBackend.when('GET', '/api/device/dialogStatusDoNotShowAgain').respond(200, true);
            $httpBackend.flush();
        });

        it('should open dialog', function() {
            expect(ctrl.showDialog()).toBe(true);
        });

        it('should open dialog when handleClick is called', function() {
            /* jshint ignore:start */
            spyOn($mdDialog, 'show').and.callThrough();
            ctrl.handleClick(); // the actual function call
            expect($mdDialog.show).toHaveBeenCalled();
            /* jshint ignore:end */
        });
    });

    describe('don\'t show Dialog due to show-dialog-flag', function() {
        beforeEach(function() {
            mockPauseStatus = {
                pausing: 0,
                pausingAllowed: true
            };
            $httpBackend.when('GET', '/api/device/dialogStatus').respond(200, false);
            $httpBackend.when('GET', '/api/device/pauseStatus').respond(200, mockPauseStatus);
            $httpBackend.when('GET', '/api/device/dialogStatusDoNotShowAgain').respond(200, true);
            $httpBackend.flush();
        });

        it('should not open dialog', function() {
            expect(ctrl.showDialog()).toBe(false);
        });

        it('should not open dialog when handleClick is called', function() {
            /* jshint ignore:start */
            spyOn($mdDialog, 'show').and.callThrough();
            ctrl.handleClick(); // the actual function call
            expect($mdDialog.show).not.toHaveBeenCalled();
            /* jshint ignore:end */
        });
    });

    describe('don\'t show Dialog due to active pause', function() {
        beforeEach(function() {
            mockPauseStatus = {
                pausing: 2000,
                pausingAllowed: true
            };
            $httpBackend.when('GET', '/api/device/dialogStatus').respond(200, true);
            $httpBackend.when('GET', '/api/device/pauseStatus').respond(200, mockPauseStatus);
            $httpBackend.when('GET', '/api/device/dialogStatusDoNotShowAgain').respond(200, true);
            $httpBackend.flush();
        });

        it('should not open dialog', function() {
            expect(ctrl.showDialog()).toBe(false);
        });

        it('should not open dialog when handleClick is called', function() {
            /* jshint ignore:start */
            spyOn($mdDialog, 'show').and.callThrough();
            ctrl.handleClick(); // the actual function call
            expect($mdDialog.show).not.toHaveBeenCalled();
            /* jshint ignore:end */
        });

        it('should redirect to dashboard', function() {
            /* jshint ignore:start */
            spyOn(mockConsoleService, 'goToPausedDashboard');
            ctrl.handleClick(); // the actual function call
            expect(mockConsoleService.goToPausedDashboard).toHaveBeenCalled();
            /* jshint ignore:end */
        });
    });
});
