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
describe('App controlbar; Component: IP anon', function() {
    // ** template cache: so that calls for templates get the 'real' HTML file.
    beforeEach(angular.mock.module('template.controlbar.app'));
    beforeEach(angular.mock.module('eblocker.controlbar'));

    let $componentController, $q, $rootScope, $httpBackend, $mdDialog, ctrl,
        mockLoggerService, mockVpnProfiles, mockTorConfig, mockVpnStatus, mockNotificationService;

    mockLoggerService = {
        info: function(param) {
            // nothing to do for now
        },
        error: function(param) {
            // nothing to do for now
        }
    };

    mockNotificationService = {
        info: function() {},
        warning: function() {},
        error: function() {}
    };

    beforeEach(angular.mock.module(function($provide, $translateProvider) {
        $provide.value('logger', mockLoggerService);
        $provide.value('NotificationService', mockNotificationService);
        // Workaround angular-translate issue:
        // https://angular-translate.github.io/docs/#/guide/22_unit-testing-with-angular-translate
        $translateProvider.translations('en', {});
    }));

    beforeEach(inject(function(_$componentController_, _$q_, _$rootScope_, _$httpBackend_, _$mdDialog_) {
        $httpBackend = _$httpBackend_;
        $componentController = _$componentController_;
        $q = _$q_;
        $rootScope = _$rootScope_;
        $mdDialog = _$mdDialog_;

        mockVpnProfiles = [
            {
                id: 1,
                name: 'mock VPN profile',
                enabled: true,
                deleted: false,
                temporary: false,
                loginCredentials: {
                    username: 'mockUser',
                    password: '**********'
                },
                configurationFileVersion: 2
            },
            {
                id: 2,
                name: 'temporary mock profile',
                enabled: true,
                deleted: false,
                temporary: true,
                loginCredentials: {
                    username: 'mockUser',
                    password: '**********'
                },
                configurationFileVersion: 2
            },
            {
                id: 3,
                name: 'deactivated mock profile',
                enabled: false,
                deleted: false,
                temporary: false,
                loginCredentials: {
                    username: 'mockUser',
                    password: '**********'
                },
                configurationFileVersion: 2
            }
        ];

        mockTorConfig = {
            sessionUseTor: false
        };

        mockVpnStatus = {
            profileId: 1,
            active: true,
            up: false
        };

        $httpBackend.when('GET', '/api/vpn/profiles').respond(200, mockVpnProfiles);
        $httpBackend.when('GET', '/api/tor/config').respond(200, mockTorConfig);
        $httpBackend.when('GET', '/api/vpn/profiles/status/me').respond(200, mockVpnStatus);

        ctrl = $componentController('ipAnon', { $mdDialog: $mdDialog }, {});
        ctrl.closeDropdown = {
            now: function() {
            // do nothing.
            }
        };

        $httpBackend.flush();
    }));

    describe('initially', function() {
        beforeEach(function() {
            // We have to make angular resolve the promise (WhitelistService), since it is not done
            // automatically because no template is involved:
            // https://stackoverflow.com/questions/24211312/angular-q-when-is-not-resolved-in-karma-unit-test
            $rootScope.$apply();
        });

        it('controller should be defined', function() {
            expect(angular.isDefined(ctrl)).toBe(true);
        });

        it('torConfig should be set', function() {
            expect(angular.isDefined(ctrl.torConfig)).toBe(true);
            expect(ctrl.torConfig.sessionUseTor).toBe(false);
        });

        it('profiles should contain non-temporary and active profiles', function() {
            expect(angular.isArray(ctrl.profiles)).toBe(true);
            expect(angular.isArray(ctrl.profiles)).toBe(true);
            expect(ctrl.profiles.length).toEqual(1); // one VPN profile
        });
    });

    describe('isAnonActive', function() {
        it('should return true if VPN is active', function () {
            ctrl.torConfig.sessionUseTor = false;
            ctrl.vpnStatus = {
                profileId: 1,
                active: true,
                up: true
            };
            expect(ctrl.isAnonActive()).toBe(true);
        });
        it('should return true if Tor is active', function () {
            ctrl.torConfig.sessionUseTor = true;
            ctrl.vpnStatus = {
                profileId: 1,
                active: false,
                up: false
            };
            expect(ctrl.isAnonActive()).toBe(true);
        });
        it('should return false if neither Tor nor VPN is active', function () {
            ctrl.torConfig.sessionUseTor = false;
            ctrl.vpnStatus = {
                profileId: 1,
                active: false,
                up: false
            };
            expect(ctrl.isAnonActive()).toBe(false);
        });
    });

    describe('toggleTor', function() {
        // it('should open Tor confirmation dialog, if Tor not yet active and showWarning is true', function () {
        //     /* jshint ignore:start */
        //     spyOn($mdDialog, 'show').and.callThrough();
        //     ctrl.torConfig.sessionUseTor = false;
        //     $httpBackend.expect('GET', '/api/tor/device/showwarnings').respond(200, true);
        //     ctrl.toggleTor(); // the actual function call
        //     $httpBackend.flush();
        //     expect($mdDialog.show).toHaveBeenCalled();
        //     /* jshint ignore:end */
        // });

        it('should NOT open Tor confirmation dialog, if Tor not yet active and showWarning is false', function () {
            /* jshint ignore:start */
            spyOn($mdDialog, 'show').and.callThrough();
            spyOn(ctrl, 'saveTorConfig');
            ctrl.vpnStatus = {active: false};
            ctrl.torConfig.sessionUseTor = false;
            $httpBackend.expect('GET', '/api/tor/device/showwarnings').respond(200, false);
            ctrl.toggleTor(); // the actual function call
            $httpBackend.flush();
            expect($mdDialog.show).not.toHaveBeenCalled();
            expect(ctrl.saveTorConfig).toHaveBeenCalled();
            /* jshint ignore:end */
        });

        it('should disable Tor, if already active', function () {
            ctrl.torConfig.sessionUseTor = true;
            $httpBackend.expect('PUT', '/api/tor/config').respond(200);
            ctrl.toggleTor();
            $httpBackend.flush();
            expect(ctrl.torConfig.sessionUseTor).toBe(false);
        });

        it('should disable VPN if active', function () {
            ctrl.torConfig.sessionUseTor = false;
            ctrl.vpnStatus = {
                profileId: 1,
                active: true,
                up: true
            };
            $httpBackend.expect('GET', '/api/tor/device/showwarnings').respond(200, false);
            $httpBackend.expect('PUT', '/api/vpn/profiles/1/status/me').respond(200); // disables VPN
            $httpBackend.expect('PUT', '/api/tor/config').respond(200); // enables Tor

            ctrl.toggleTor();

            $httpBackend.flush();
            expect(ctrl.torConfig.sessionUseTor).toBe(true);
        });
    });

    describe('toggleVpn', function() {
        it('should open VPN dialog', function () {
            /* jshint ignore:start */
            spyOn($mdDialog, 'show').and.callThrough();
            ctrl.torConfig.sessionUseTor = false;

            ctrl.toggleVpn({
                vpnProfile: {
                    id: 2
                }
            }); // the actual function call

            expect($mdDialog.show).toHaveBeenCalled();
            /* jshint ignore:end */
        });

        it('should disble Tor before activating VPN', function () {
            /* jshint ignore:start */
            spyOn($mdDialog, 'show');
            ctrl.torConfig.sessionUseTor = true;
            $httpBackend.expect('PUT', '/api/tor/config').respond(200);

            ctrl.toggleVpn({
                vpnProfile: {
                    id: 2
                }
            }); // the actual function call

            $httpBackend.flush();
            expect($mdDialog.show).toHaveBeenCalled();
            /* jshint ignore:end */
        });
    });
});
