/*
 * Copyright 2026 eBlocker Open Source
 *
 * Licensed under the EUPL, Version 1.2 or later.
 */
import 'angular-mocks';

/* global jasmine */

describe('App settings; VPN access component controller', function() {
    let $componentController;
    let $rootScope;
    let $q;
    let $httpBackend;

    let DeviceService;
    let UserService;
    let VpnHomeService;
    let WireGuardService;
    let NotificationService;

    let userPermissionEnabled;

    beforeEach(angular.mock.module('template.settings.app'));
    beforeEach(angular.mock.module('eblocker.adminconsole'));

    beforeEach(angular.mock.inject(function(
        _$componentController_, _$rootScope_, _$q_, _$httpBackend_
    ) {
        $componentController = _$componentController_;
        $rootScope = _$rootScope_;
        $q = _$q_;
        $httpBackend = _$httpBackend_;

        // Settings bootstrap may request multiple generated English
        // locale bundles. The component test does not test translations,
        // so satisfy the complete generated English locale family.
        $httpBackend.whenGET(
            /\/locale\/lang-[a-z-]+-en-[0-9]+\.json/
        ).respond(200, {});

        // Loading the complete admin-console module triggers these standard
        // bootstrap requests. They are unrelated to vpnAccessComponent
        // behavior and are stubbed in existing frontend service tests too.
        $httpBackend.whenGET('/api/adminconsole/console/ip')
            .respond(200, {});
        $httpBackend.whenGET('/api/adminconsole/systemstatus')
            .respond(200, {});
        $httpBackend.whenGET('/api/settings')
            .respond(200, {});

        userPermissionEnabled = true;

        DeviceService = {
            getAll: jasmine.createSpy('getAll').and.callFake(function() {
                return $q.when({
                    data: [
                        {
                            id: 'device:1',
                            name: 'Phone',
                            mobileState: true
                        },
                        {
                            id: 'device:2',
                            name: 'Tablet',
                            mobileState: false
                        },
                        {
                            id: 'device:gateway',
                            name: 'Gateway',
                            isGateway: true
                        },
                        {
                            id: 'device:eblocker',
                            name: 'eBlocker',
                            isEblocker: true
                        }
                    ]
                });
            }),
            setDisplayValues: jasmine.createSpy('setDisplayValues')
        };

        UserService = {
            getAll: jasmine.createSpy('getAll').and.callFake(function() {
                return $q.when({
                    data: [
                        {
                            id: 1,
                            name: 'System',
                            system: true
                        },
                        {
                            id: 7,
                            name: 'Alice',
                            system: false
                        }
                    ]
                });
            })
        };

        VpnHomeService = {
            loadStatus: jasmine.createSpy('loadStatus').and.callFake(function() {
                return $q.when({
                    data: {
                        isRunning: true
                    }
                });
            }),
            enableDevice: jasmine.createSpy('enableDevice').and.callFake(function() {
                return $q.when(true);
            }),
            disableDevice: jasmine.createSpy('disableDevice').and.callFake(function() {
                return $q.when(true);
            })
        };

        WireGuardService = {
            getDeviceAuthorization: jasmine
                .createSpy('getDeviceAuthorization')
                .and.callFake(function() {
                    throw new Error('N+1 authorization read is forbidden.');
                }),
            getDeviceAuthorizations: jasmine
                .createSpy('getDeviceAuthorizations')
                .and.callFake(function() {
                    return $q.when({
                        data: {
                            globalEnabled: true,
                            devices: [
                                {
                                    deviceId: 'device:1',
                                    globalEnabled: true,
                                    deviceEnabled: true,
                                    assignedUserId: 7,
                                    userPermissionRequired: true,
                                    userEnabled: userPermissionEnabled,
                                    allowed: true,
                                    reason: 'ALLOWED'
                                },
                                {
                                    deviceId: 'device:2',
                                    globalEnabled: true,
                                    deviceEnabled: true,
                                    assignedUserId: null,
                                    userPermissionRequired: false,
                                    userEnabled: null,
                                    allowed: true,
                                    reason: 'ALLOWED_NO_ASSIGNED_USER'
                                }
                            ]
                        }
                    });
                }),
            setDeviceAuthorization: jasmine
                .createSpy('setDeviceAuthorization')
                .and.callFake(function(deviceId, enabled) {
                return $q.when({
                    data: {
                        deviceId: deviceId,
                        globalEnabled: true,
                        deviceEnabled: enabled,
                        assignedUserId: 7,
                        userPermissionRequired: true,
                        userEnabled: userPermissionEnabled,
                        allowed: enabled || userPermissionEnabled,
                        reason: enabled || userPermissionEnabled ?
                            'ALLOWED' : 'DEVICE_DISABLED'
                    }
                });
            }),
            setUserAuthorization: jasmine.createSpy('setUserAuthorization').and.callFake(function(userId, enabled) {
                userPermissionEnabled = enabled;
                return $q.when(enabled);
            })
        };

        NotificationService = {
            error: jasmine.createSpy('error')
        };
    }));

    function createController() {
        return $componentController(
            'vpnAccessComponent',
            {
                $q: $q,
                DeviceService: DeviceService,
                UserService: UserService,
                VpnHomeService: VpnHomeService,
                WireGuardService: WireGuardService,
                NotificationService: NotificationService
            },
            {}
        );
    }

    function loadController() {
        const ctrl = createController();
        ctrl.$onInit();
        $rootScope.$digest();
        $httpBackend.flush();
        $rootScope.$digest();
        return ctrl;
    }

    afterEach(function() {
        $httpBackend.verifyNoOutstandingExpectation();
        $httpBackend.verifyNoOutstandingRequest();
    });

    it('loads only real devices and maps only a real assigned user', function() {
        const ctrl = loadController();

        expect(ctrl.rows.length).toBe(2);
        expect(DeviceService.setDisplayValues.calls.count()).toBe(2);
        expect(WireGuardService.getDeviceAuthorizations.calls.count()).toBe(1);
        expect(WireGuardService.getDeviceAuthorization).not.toHaveBeenCalled();

        expect(ctrl.openVpnGlobalEnabled).toBe(true);
        expect(ctrl.wireGuardGlobalEnabled).toBe(true);

        expect(ctrl.rows[0].assignedUserName).toBe('Alice');
        expect(ctrl.rows[0].authorization.userPermissionRequired).toBe(true);
        expect(ctrl.canToggleWireGuardUser(ctrl.rows[0])).toBe(true);

        expect(ctrl.rows[1].assignedUser).toBeUndefined();
        expect(ctrl.rows[1].authorization.userPermissionRequired).toBe(false);
        expect(ctrl.canToggleWireGuardUser(ctrl.rows[1])).toBe(false);
    });

    it('enables OpenVPN through the existing eBlocker Mobile API', function() {
        const ctrl = loadController();
        const row = ctrl.rows[1];

        row.openVpnEnabled = true;
        ctrl.setOpenVpnAccess(row);
        $rootScope.$digest();

        expect(VpnHomeService.enableDevice).toHaveBeenCalledWith('device:2');
        expect(VpnHomeService.disableDevice).not.toHaveBeenCalled();
        expect(row.busyOpenVpn).toBe(false);
    });

    it('changes the device permission only through the dedicated WireGuard API', function() {
        const ctrl = loadController();
        const row = ctrl.rows[0];

        row.authorization.deviceEnabled = false;
        ctrl.setWireGuardDeviceAccess(row);
        $rootScope.$digest();

        expect(WireGuardService.setDeviceAuthorization)
            .toHaveBeenCalledWith('device:1', false);

        expect(row.authorization.deviceEnabled).toBe(false);
        expect(row.authorization.allowed).toBe(true);
        expect(row.authorization.reason).toBe('ALLOWED');
        expect(row.busyWireGuardDevice).toBe(false);
    });

    it('changes a real-user permission and reloads all effective decisions', function() {
        const ctrl = loadController();
        const row = ctrl.rows[0];

        row.authorization.userEnabled = false;
        ctrl.setWireGuardUserAccess(row);
        $rootScope.$digest();

        expect(WireGuardService.setUserAuthorization)
            .toHaveBeenCalledWith(7, false);

        expect(DeviceService.getAll.calls.count()).toBe(2);
        expect(WireGuardService.getDeviceAuthorizations.calls.count()).toBe(2);
        expect(WireGuardService.getDeviceAuthorization).not.toHaveBeenCalled();

        expect(ctrl.rows[0].authorization.userEnabled).toBe(false);
        expect(ctrl.rows[0].authorization.allowed).toBe(true);
        expect(ctrl.rows[0].authorization.reason).toBe('ALLOWED');
    });

    it('rolls back a failed OpenVPN toggle and reports the error', function() {
        const ctrl = loadController();
        const row = ctrl.rows[1];

        VpnHomeService.enableDevice.and.callFake(function() {
            return $q.reject({
                status: 500
            });
        });

        row.openVpnEnabled = true;

        ctrl.setOpenVpnAccess(row).catch(angular.noop);
        $rootScope.$digest();

        expect(row.openVpnEnabled).toBe(false);
        expect(row.busyOpenVpn).toBe(false);
        expect(NotificationService.error).toHaveBeenCalledWith(
            'ADMINCONSOLE.VPN_ACCESS.NOTIFICATION.OPENVPN_ERROR',
            jasmine.any(Object)
        );
    });

    it('keeps global WireGuard state when no devices exist', function() {
        DeviceService.getAll.and.callFake(function() {
            return $q.when({
                data: []
            });
        });

        WireGuardService.getDeviceAuthorizations.and.callFake(function() {
            return $q.when({
                data: {
                    globalEnabled: false,
                    devices: []
                }
            });
        });

        const ctrl = loadController();

        expect(ctrl.rows.length).toBe(0);
        expect(ctrl.wireGuardGlobalEnabled).toBe(false);
        expect(WireGuardService.getDeviceAuthorizations.calls.count()).toBe(1);
        expect(WireGuardService.getDeviceAuthorization).not.toHaveBeenCalled();
    });

    it('maps effective authorization reasons without using an operating user', function() {
        const ctrl = loadController();

        expect(ctrl.reasonKey(ctrl.rows[0]))
            .toBe('ADMINCONSOLE.VPN_ACCESS.REASON.ALLOWED');

        expect(ctrl.reasonKey(ctrl.rows[1]))
            .toBe('ADMINCONSOLE.VPN_ACCESS.REASON.ALLOWED_NO_ASSIGNED_USER');

        expect(ctrl.reasonKey({authorization: {}}))
            .toBe('ADMINCONSOLE.VPN_ACCESS.UNKNOWN');
    });
    it('sorts VPN access by device and permission columns', function() {
        const ctrl = loadController();

        expect(ctrl.sortField).toBe('displayName');
        expect(ctrl.sortReverse).toBe(false);
        expect(ctrl.sortIndicator('displayName')).toBe('▲');

        ctrl.setSort('openVpnEnabled');
        expect(ctrl.sortField).toBe('openVpnEnabled');
        expect(ctrl.sortReverse).toBe(true);
        expect(ctrl.sortIndicator('openVpnEnabled')).toBe('▼');

        ctrl.setSort('openVpnEnabled');
        expect(ctrl.sortReverse).toBe(false);
        expect(ctrl.sortIndicator('openVpnEnabled')).toBe('▲');

        ctrl.setSort('authorization.deviceEnabled');
        expect(ctrl.sortField).toBe('authorization.deviceEnabled');
        expect(ctrl.sortReverse).toBe(true);
        expect(ctrl.sortIndicator('displayName')).toBe('');
    });

});
