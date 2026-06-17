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
/* global jasmine */

describe('App settings; VPN connection test dialog controller', function() {
    beforeEach(angular.mock.module('template.settings.app'));
    beforeEach(angular.mock.module('eblocker.adminconsole'));

    var $controller, $httpBackend, $q, $rootScope, VpnService, profile;

    beforeEach(angular.mock.module(function($provide, $translateProvider) {
        $provide.value('ConsoleService', {
            init: function() {
                return $q.resolve({});
            },
            isInitialized: function() {
                return true;
            },
            initiallyShowNavBar: function() {
                return false;
            },
            isGlobalSpinner: function() {
                return false;
            },
            isPageSpinner: function() {
                return false;
            }
        });
        $provide.value('StateService', {
            setStates: function() {}
        });
        $provide.value('SystemService', {
            loadSystemStatus: function() {
                return $q.resolve({});
            }
        });
        $provide.value('$mdDialog', {
            hide: function() {}
        });
        $translateProvider.translations('en', {});
    }));

    beforeEach(inject(function(_$controller_, _$httpBackend_, _$q_, _$rootScope_) {
        $controller = _$controller_;
        $httpBackend = _$httpBackend_;
        $q = _$q_;
        $rootScope = _$rootScope_;
        $httpBackend.whenGET('/api/settings').respond(200, {});
        profile = {id: 7, name: 'Cloudflare'};
        VpnService = {
            getVpnStatus: jasmine.createSpy('getVpnStatus'),
            setVpnStatus: jasmine.createSpy('setVpnStatus')
        };
    }));

    function createController() {
        return $controller('VpnConnectionTestController', {
            profile: profile,
            VpnService: VpnService
        });
    }

    it('reports success for an already active WireGuard connection without stopping it', function() {
        var ctrl;
        VpnService.getVpnStatus.and.returnValue($q.resolve({
            data: {
                active: true,
                up: true,
                devices: ['device:0123456789ab']
            }
        }));

        ctrl = createController();
        $rootScope.$digest();

        expect(ctrl.vpnTest.status).toBe('success');
        expect(VpnService.setVpnStatus).not.toHaveBeenCalled();
    });

    it('starts a temporary test when the profile is not already active', function() {
        var ctrl;
        VpnService.getVpnStatus.and.returnValue($q.resolve({
            data: {
                active: false,
                up: false,
                devices: []
            }
        }));
        VpnService.setVpnStatus.and.returnValue($q.resolve({}));

        ctrl = createController();
        $rootScope.$digest();

        expect(VpnService.setVpnStatus).toHaveBeenCalledWith(profile, {active: true});
        ctrl.$onDestroy();
    });
});
