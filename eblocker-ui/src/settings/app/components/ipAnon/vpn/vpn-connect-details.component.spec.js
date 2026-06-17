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

describe('App settings; VPN connect details component controller', function() {
    beforeEach(angular.mock.module('template.settings.app'));
    beforeEach(angular.mock.module('eblocker.adminconsole'));

    let ctrl, $componentController, $httpBackend, $q, $rootScope, ConsoleService, DialogService,
        StateService, SystemService, VpnService;

    function resolved(response) {
        return {
            then: function(success) {
                if (angular.isFunction(success)) {
                    success(response);
                }
                return resolved(response);
            },
            finally: function(callback) {
                if (angular.isFunction(callback)) {
                    callback();
                }
                return resolved(response);
            }
        };
    }

    StateService = {
        goToState: function() {},
        setStates: function() {}
    };

    ConsoleService = {
        init: function() {
            return resolved({});
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
    };

    SystemService = {
        loadSystemStatus: function() {
            return resolved({});
        }
    };

    DialogService = {
        vpnConnectionEdit: jasmine.createSpy('vpnConnectionEdit')
    };

    VpnService = {
        getProfile: jasmine.createSpy('getProfile'),
        getProfileConfig: jasmine.createSpy('getProfileConfig'),
        getVpnStatus: jasmine.createSpy('getVpnStatus'),
        updateProfile: jasmine.createSpy('updateProfile')
    };

    beforeEach(angular.mock.module(function($provide, $translateProvider) {
        $provide.value('ConsoleService', ConsoleService);
        $provide.value('StateService', StateService);
        $provide.value('SystemService', SystemService);
        $provide.value('DialogService', DialogService);
        $provide.value('VpnService', VpnService);
        $translateProvider.translations('en', {});
    }));

    beforeEach(inject(function(_$componentController_, _$httpBackend_, _$q_, _$rootScope_) {
        $componentController = _$componentController_;
        $httpBackend = _$httpBackend_;
        $q = _$q_;
        $rootScope = _$rootScope_;
        $httpBackend.whenGET('/api/settings').respond(200, {});
        DialogService.vpnConnectionEdit.and.callFake(function(dialog, isProfileNew, profile) {
            return $q.resolve(profile);
        });
        ctrl = $componentController('vpnConnectDetailsComponent', {
            $stateParams: {param: {}}
        }, {});
    }));

    it('reopens the assistant for a temporary profile without loading a missing config file', function() {
        const dialog = {requiredFileError: {}};
        const profile = {
            id: 1,
            temporary: true,
            loginCredentials: {},
            configurationFileVersion: 1
        };
        ctrl.dialog = dialog;

        ctrl.editProfile(profile);
        $rootScope.$digest();

        expect(VpnService.getProfile).not.toHaveBeenCalled();
        expect(VpnService.getProfileConfig).not.toHaveBeenCalled();
        expect(DialogService.vpnConnectionEdit).toHaveBeenCalledWith(dialog, true, profile, null);
    });

    it('does not show an unused WireGuard profile as connected before status has loaded', function() {
        const deferred = $q.defer();
        const profile = {id: 7, name: 'Cloudflare'};
        VpnService.getVpnStatus.and.returnValue(deferred.promise);
        ctrl = $componentController('vpnConnectDetailsComponent', {
            $stateParams: {param: {entry: profile}}
        }, {});

        ctrl.$onInit();

        expect(ctrl.connected).toBe(false);
        expect(ctrl.profileUsername.value).toBeUndefined();
        expect(ctrl.profilePasswordSet.value).toBeUndefined();
        ctrl.$onDestroy();
    });

    it('only reports profile usage when the VPN status contains assigned devices', function() {
        const profile = {id: 7, name: 'Cloudflare', loginCredentials: {}};
        VpnService.getVpnStatus.and.returnValue($q.resolve({
            data: {
                active: true,
                up: true,
                devices: []
            }
        }));
        ctrl = $componentController('vpnConnectDetailsComponent', {
            $stateParams: {param: {entry: profile}}
        }, {});

        ctrl.$onInit();
        $rootScope.$digest();

        expect(ctrl.connected).toBe(false);
        ctrl.$onDestroy();
    });

    it('reports profile usage when at least one device is assigned', function() {
        const profile = {id: 7, name: 'Cloudflare', loginCredentials: {}};
        VpnService.getVpnStatus.and.returnValue($q.resolve({
            data: {
                active: true,
                up: true,
                devices: ['device:0123456789ab']
            }
        }));
        ctrl = $componentController('vpnConnectDetailsComponent', {
            $stateParams: {param: {entry: profile}}
        }, {});

        ctrl.$onInit();
        $rootScope.$digest();

        expect(ctrl.connected).toBe(true);
        ctrl.$onDestroy();
    });
});
