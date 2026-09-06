/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License.
 */
/* global jasmine */
import 'angular-mocks';

describe('App settings; WireGuard status component controller', function() {
    beforeEach(angular.mock.module('template.settings.app'));
    beforeEach(angular.mock.module('eblocker.adminconsole'));

    let ctrl;
    let $componentController;
    let $q;
    let $rootScope;
    let $httpBackend;
    let WireGuardService;
    let DeviceService;
    let NotificationService;

    beforeEach(angular.mock.module(function(
        $provide,
        $translateProvider) {

        NotificationService = {
            info: jasmine.createSpy('info'),
            error: jasmine.createSpy('error')
        };

        $provide.value(
            'NotificationService',
            NotificationService
        );

        $translateProvider.translations('en', {});
    }));

    beforeEach(inject(function(
        _$componentController_,
        _$q_,
        _$rootScope_,
        _$httpBackend_) {

        $componentController = _$componentController_;
        $q = _$q_;
        $rootScope = _$rootScope_;
        $httpBackend = _$httpBackend_;

        // Request performed by unrelated Settings bootstrap code.
        // Keep this component spec focused on WireGuard behavior.
        $httpBackend.whenGET('/api/adminconsole/console/ip')
            .respond(200, 'http://127.0.0.1');

        $httpBackend.whenGET('/api/adminconsole/systemstatus')
            .respond(200, {
                executionState: 'RUNNING'
            });

        $httpBackend.whenGET('/api/settings')
            .respond(200, {});

        WireGuardService = {
            getStatus: jasmine.createSpy('getStatus'),
            enable: jasmine.createSpy('enable'),
            disable: jasmine.createSpy('disable'),
            getPeers: jasmine.createSpy('getPeers'),
            createPeerForDevice:
                jasmine.createSpy('createPeerForDevice'),
            getClientConfig:
                jasmine.createSpy('getClientConfig'),
            getQrCode:
                jasmine.createSpy('getQrCode'),
            getEndpoint: jasmine.createSpy('getEndpoint'),
            setEndpoint: jasmine.createSpy('setEndpoint'),
            setRouting: jasmine.createSpy('setRouting')
        };

        DeviceService = {
            getAll: jasmine.createSpy('getAll')
        };

        WireGuardService.getStatus.and.returnValue(
            $q.when({
                data: {
                    enabled: true,
                    runtime: {
                        iface: 'up',
                        service: 'active',
                        wg: 'ready',
                        peers: 2,
                        error: null,
                        peerTelemetry: [
                            {
                                publicKey: 'peer-public-one',
                                latestHandshakeEpochSeconds:
                                    Math.floor(Date.now() / 1000) - 60,
                                rxBytes: 1234,
                                txBytes: 5678
                            },
                            {
                                publicKey: 'peer-public-two',
                                latestHandshakeEpochSeconds: 0,
                                rxBytes: 90,
                                txBytes: 120
                            }
                        ]
                    }
                }
            })
        );

        WireGuardService.getPeers.and.returnValue(
            $q.when({
                data: [
                    {
                        id: 2,
                        name: 'Phone peer',
                        publicKey: 'peer-public-one',
                        allowedIp: '10.13.13.2/32',
                        deviceId: 'device:test-phone',
                        allowLanAccess: false
                    },
                    {
                        id: 3,
                        name: 'Manual peer',
                        publicKey: 'peer-public-two',
                        allowedIp: '10.13.13.3/32',
                        deviceId: null,
                        allowLanAccess: true
                    }
                ]
            })
        );

        DeviceService.getAll.and.returnValue(
            $q.when({
                data: [
                    {
                        id: 'device:test-phone',
                        name: 'Phone',
                        displayName: 'Phone (192.0.2.20)'
                    }
                ]
            })
        );

        WireGuardService.getEndpoint.and.returnValue(
            $q.when({
                data: {
                    type: 'DYN_DNS',
                    host: 'vpn.example.test'
                }
            })
        );

        ctrl = $componentController(
            'wireGuardStatusComponent',
            {
                WireGuardService: WireGuardService,
                DeviceService: DeviceService,
                NotificationService: NotificationService
            },
            {}
        );
    }));

    it('creates the controller', function() {
        expect(angular.isDefined(ctrl)).toBe(true);
    });

    it('loads status and endpoint on init', function() {
        ctrl.$onInit();
        $rootScope.$digest();

        expect(WireGuardService.getStatus)
            .toHaveBeenCalled();
        expect(WireGuardService.getPeers)
            .toHaveBeenCalled();
        expect(DeviceService.getAll)
            .toHaveBeenCalled();
        expect(WireGuardService.getEndpoint)
            .toHaveBeenCalled();

        expect(ctrl.status.enabled).toBe(true);
        expect(ctrl.endpoint.type).toBe('DYN_DNS');
        expect(ctrl.endpoint.host)
            .toBe('vpn.example.test');

        expect(ctrl.interfaceConfig.value).toBe('up');
        expect(ctrl.serviceConfig.value).toBe('active');
        expect(ctrl.wgConfig.value).toBe('ready');
        expect(ctrl.peersConfig.value).toBe(2);
        expect(ctrl.portConfig.value).toBe('UDP 51820');

        expect(ctrl.peerRows.length).toBe(2);

        expect(ctrl.peerRows[0].deviceDisplayName)
            .toBe('Phone (192.0.2.20)');

        expect(ctrl.peerRows[1].deviceDisplayName)
            .toBe('ADMINCONSOLE.WIREGUARD.PEERS.UNBOUND');

        expect(ctrl.peerRows[0].activityState)
            .toBe('ACTIVE');
        expect(ctrl.peerRows[0].rxBytes)
            .toBe(1234);
        expect(ctrl.peerRows[0].txBytes)
            .toBe(5678);

        expect(ctrl.peerRows[1].activityState)
            .toBe('NEVER_CONNECTED');
        expect(ctrl.peerRows[1].latestHandshakeEpochSeconds)
            .toBe(0);
        expect(ctrl.peerRows[1].rxBytes)
            .toBe(90);
        expect(ctrl.peerRows[1].txBytes)
            .toBe(120);

        expect(ctrl.provisioning.selectedDeviceId)
            .toBe('device:test-phone');
        expect(ctrl.provisioning.selectedPeer.id)
            .toBe(2);
    });

    it('creates a device-bound peer for the selected device', function() {
        ctrl.$onInit();
        $rootScope.$digest();

        ctrl.provisioning.selectedDeviceId = 'device:new-phone';
        ctrl.selectProvisioningDevice();

        WireGuardService.createPeerForDevice.and.returnValue(
            $q.when({
                data: {
                    id: 8,
                    name: 'New phone',
                    publicKey: 'peer-public-new',
                    allowedIp: '10.13.13.8/32',
                    deviceId: 'device:new-phone',
                    allowLanAccess: false
                }
            })
        );

        ctrl.createProvisionedPeer();
        $rootScope.$digest();

        expect(WireGuardService.createPeerForDevice)
            .toHaveBeenCalledWith('device:new-phone');
        expect(ctrl.provisioning.selectedPeer.id).toBe(8);
        expect(ctrl.provisioning.selectedPeer.deviceId)
            .toBe('device:new-phone');
        expect(ctrl.provisioning.isCreating).toBe(false);
        expect(NotificationService.info).toHaveBeenCalled();
    });

    it('reports device provisioning failures', function() {
        ctrl.provisioning.selectedDeviceId = 'device:new-phone';

        WireGuardService.createPeerForDevice.and.returnValue(
            $q.reject({status: 500})
        );

        ctrl.createProvisionedPeer().catch(angular.noop);
        $rootScope.$digest();

        expect(NotificationService.error).toHaveBeenCalled();
        expect(ctrl.provisioning.isCreating).toBe(false);
    });

    it('refreshes WireGuard data internally without changing server state', function() {
        ctrl.$onInit();
        $rootScope.$digest();

        WireGuardService.getStatus.calls.reset();
        WireGuardService.getPeers.calls.reset();
        WireGuardService.getEndpoint.calls.reset();
        DeviceService.getAll.calls.reset();

        ctrl.reload();
        $rootScope.$digest();

        expect(WireGuardService.getStatus.calls.count()).toBe(1);
        expect(WireGuardService.getPeers.calls.count()).toBe(1);
        expect(WireGuardService.getEndpoint.calls.count()).toBe(1);
        expect(DeviceService.getAll.calls.count()).toBe(1);

        expect(WireGuardService.enable).not.toHaveBeenCalled();
        expect(WireGuardService.disable).not.toHaveBeenCalled();
        expect(WireGuardService.setEndpoint).not.toHaveBeenCalled();
        expect(WireGuardService.setRouting).not.toHaveBeenCalled();
        expect(ctrl.isLoading).toBe(false);
    });

    it('classifies an older non-zero handshake as recently seen', function() {
        WireGuardService.getStatus.and.returnValue(
            $q.when({
                data: {
                    enabled: true,
                    runtime: {
                        iface: 'wg0',
                        service: 'active',
                        wg: 'up',
                        peers: 1,
                        error: null,
                        peerTelemetry: [
                            {
                                publicKey: 'peer-public-old',
                                latestHandshakeEpochSeconds:
                                    Math.floor(Date.now() / 1000) - 181,
                                rxBytes: 10,
                                txBytes: 20
                            }
                        ]
                    }
                }
            })
        );

        WireGuardService.getPeers.and.returnValue(
            $q.when({
                data: [
                    {
                        id: 5,
                        name: 'Seen peer',
                        publicKey: 'peer-public-old',
                        allowedIp: '10.13.13.5/32',
                        deviceId: null,
                        allowLanAccess: false
                    }
                ]
            })
        );

        ctrl.$onInit();
        $rootScope.$digest();

        expect(ctrl.peerRows.length).toBe(1);
        expect(ctrl.peerRows[0].activityState)
            .toBe('RECENTLY_SEEN');
        expect(ctrl.peerRows[0].latestHandshakeEpochSeconds)
            .toBeGreaterThan(0);
        expect(ctrl.peerRows[0].rxBytes)
            .toBe(10);
        expect(ctrl.peerRows[0].txBytes)
            .toBe(20);
    });

    it('keeps activity unknown when runtime telemetry is unavailable', function() {
        WireGuardService.getStatus.and.returnValue(
            $q.when({
                data: {
                    enabled: true,
                    runtime: {
                        iface: 'wg0',
                        service: 'inactive',
                        wg: 'down',
                        peers: 0,
                        error: null,
                        peerTelemetry: []
                    }
                }
            })
        );

        WireGuardService.getPeers.and.returnValue(
            $q.when({
                data: [
                    {
                        id: 6,
                        name: 'Configured peer',
                        publicKey: 'peer-public-unavailable',
                        allowedIp: '10.13.13.6/32',
                        deviceId: null,
                        allowLanAccess: false
                    }
                ]
            })
        );

        ctrl.$onInit();
        $rootScope.$digest();

        expect(ctrl.peerRows.length).toBe(1);
        expect(ctrl.peerRows[0].activityState)
            .toBe(null);
        expect(ctrl.peerRows[0].latestHandshakeEpochSeconds)
            .toBe(null);
        expect(ctrl.peerRows[0].rxBytes)
            .toBe(null);
        expect(ctrl.peerRows[0].txBytes)
            .toBe(null);
    });

    it('formats peer traffic without adding a global byte filter', function() {
        expect(ctrl.formatBytes(null))
            .toBe('-');
        expect(ctrl.formatBytes(-1))
            .toBe('-');
        expect(ctrl.formatBytes(0))
            .toBe('0 B');
        expect(ctrl.formatBytes(1023))
            .toBe('1023 B');
        expect(ctrl.formatBytes(1234))
            .toBe('1.2 KiB');
        expect(ctrl.formatBytes(5678))
            .toBe('5.5 KiB');
        expect(ctrl.formatBytes(1048576))
            .toBe('1 MiB');
    });

    it('marks missing bound device metadata without exposing device id', function() {
        WireGuardService.getPeers.and.returnValue(
            $q.when({
                data: [
                    {
                        id: 4,
                        name: 'Old device peer',
                        allowedIp: '10.13.13.4/32',
                        deviceId: 'device:not-present',
                        allowLanAccess: false
                    }
                ]
            })
        );

        DeviceService.getAll.and.returnValue(
            $q.when({data: []})
        );

        ctrl.$onInit();
        $rootScope.$digest();

        expect(ctrl.peerRows.length).toBe(1);
        expect(ctrl.peerRows[0].deviceDisplayName)
            .toBe(
                'ADMINCONSOLE.WIREGUARD.PEERS.UNKNOWN_DEVICE'
            );

        expect(ctrl.peerRows[0].deviceDisplayName)
            .not.toContain('device:not-present');
    });

    it('keeps loading until status, peers, devices and endpoint requests are complete', function() {
        const statusDeferred = $q.defer();
        const peersDeferred = $q.defer();
        const devicesDeferred = $q.defer();
        const endpointDeferred = $q.defer();

        WireGuardService.getStatus.and.returnValue(
            statusDeferred.promise
        );

        WireGuardService.getPeers.and.returnValue(
            peersDeferred.promise
        );

        DeviceService.getAll.and.returnValue(
            devicesDeferred.promise
        );

        WireGuardService.getEndpoint.and.returnValue(
            endpointDeferred.promise
        );

        ctrl.$onInit();

        expect(ctrl.isLoading).toBe(true);

        endpointDeferred.resolve({
            data: {
                type: 'DYN_DNS',
                host: 'vpn.example.test'
            }
        });

        peersDeferred.resolve({data: []});
        devicesDeferred.resolve({data: []});

        $rootScope.$digest();

        expect(ctrl.isLoading).toBe(true);

        statusDeferred.resolve({
            data: {
                enabled: true,
                runtime: {
                    iface: 'up',
                    service: 'active',
                    wg: 'ready',
                    peers: 2,
                    error: null
                }
            }
        });
        $rootScope.$digest();

        expect(ctrl.isLoading).toBe(false);
    });

    it('enables server when switch target is enabled', function() {
        WireGuardService.enable.and.returnValue(
            $q.when({
                data: {
                    enabled: true,
                    runtime: {
                        iface: 'up',
                        service: 'active',
                        wg: 'ready',
                        peers: 2,
                        error: null
                    }
                }
            })
        );

        ctrl.status.enabled = true;
        ctrl.toggleServer();
        $rootScope.$digest();

        expect(WireGuardService.enable)
            .toHaveBeenCalled();
        expect(WireGuardService.disable)
            .not.toHaveBeenCalled();
        expect(ctrl.status.enabled).toBe(true);
    });

    it('disables server when switch target is disabled', function() {
        WireGuardService.disable.and.returnValue(
            $q.when({
                data: {
                    enabled: false,
                    runtime: {
                        iface: 'down',
                        service: 'inactive',
                        wg: 'down',
                        peers: 0,
                        error: null
                    }
                }
            })
        );

        ctrl.status.enabled = false;
        ctrl.toggleServer();
        $rootScope.$digest();

        expect(WireGuardService.disable)
            .toHaveBeenCalled();
        expect(WireGuardService.enable)
            .not.toHaveBeenCalled();
        expect(ctrl.status.enabled).toBe(false);
    });

    it('rolls switch state back when server change fails', function() {
        WireGuardService.enable.and.returnValue(
            $q.reject({status: 500})
        );

        ctrl.status.enabled = true;
        ctrl.toggleServer();
        $rootScope.$digest();

        expect(ctrl.status.enabled).toBe(false);
        expect(NotificationService.error)
            .toHaveBeenCalled();
    });

    it('requires host for fixed and dynamic DNS endpoints', function() {
        ctrl.endpoint.type = 'FIXED_IP';
        expect(ctrl.endpointHostRequired()).toBe(true);

        ctrl.endpoint.type = 'DYN_DNS';
        expect(ctrl.endpointHostRequired()).toBe(true);

        ctrl.endpoint.type = 'EBLOCKER_DYN_DNS';
        expect(ctrl.endpointHostRequired()).toBe(false);
    });

    it('saves explicit host for fixed endpoint', function() {
        const saved = {
            type: 'FIXED_IP',
            host: '203.0.113.10'
        };

        WireGuardService.setEndpoint.and.returnValue(
            $q.when({data: saved})
        );

        ctrl.endpoint = {
            type: 'FIXED_IP',
            host: '203.0.113.10'
        };

        ctrl.saveEndpoint();
        $rootScope.$digest();

        expect(WireGuardService.setEndpoint)
            .toHaveBeenCalledWith(saved);
        expect(ctrl.endpoint).toEqual(saved);
        expect(NotificationService.info)
            .toHaveBeenCalled();
    });

    it('clears host for eBlocker Dynamic DNS endpoint', function() {
        const expected = {
            type: 'EBLOCKER_DYN_DNS',
            host: null
        };

        WireGuardService.setEndpoint.and.returnValue(
            $q.when({data: expected})
        );

        ctrl.endpoint = {
            type: 'EBLOCKER_DYN_DNS',
            host: 'must-not-be-sent.example'
        };

        ctrl.saveEndpoint();
        $rootScope.$digest();

        expect(WireGuardService.setEndpoint)
            .toHaveBeenCalledWith(expected);
    });
    it('defaults legacy peers to full tunnel routing', function() {
        WireGuardService.getPeers.and.returnValue(
            $q.when({
                data: [{
                    id: 20,
                    name: 'Legacy peer',
                    publicKey: 'legacy-public',
                    allowedIp: '10.13.13.20/32',
                    deviceId: null,
                    allowLanAccess: false
                }]
            })
        );

        ctrl.$onInit();
        $rootScope.$digest();

        expect(ctrl.peerRows[0].tunnelMode)
            .toBe('FULL_TUNNEL');
        expect(ctrl.peerRows[0].customAllowedIps)
            .toEqual([]);
        expect(ctrl.routingEditor.selectedPeerId)
            .toBe(20);
    });

    it('selects custom routing without changing LAN authorization', function() {
        WireGuardService.getPeers.and.returnValue(
            $q.when({
                data: [{
                    id: 21,
                    name: 'Custom peer',
                    publicKey: 'custom-public',
                    allowedIp: '10.13.13.21/32',
                    deviceId: null,
                    allowLanAccess: false,
                    tunnelMode: 'CUSTOM',
                    customAllowedIps: [
                        '192.168.50.0/24',
                        '10.0.0.0/8'
                    ]
                }]
            })
        );

        ctrl.$onInit();
        $rootScope.$digest();

        expect(ctrl.routingEditor.tunnelMode)
            .toBe('CUSTOM');
        expect(ctrl.routingEditor.customAllowedIpsText)
            .toBe('192.168.50.0/24\n10.0.0.0/8');
        expect(ctrl.routingEditor.allowLanAccess)
            .toBe(false);
    });

    it('saves custom routes through the routing API', function() {
        WireGuardService.getPeers.and.returnValue(
            $q.when({
                data: [{
                    id: 22,
                    name: 'Editable peer',
                    publicKey: 'editable-public',
                    allowedIp: '10.13.13.22/32',
                    deviceId: null,
                    allowLanAccess: true,
                    tunnelMode: 'FULL_TUNNEL',
                    customAllowedIps: []
                }]
            })
        );

        ctrl.$onInit();
        $rootScope.$digest();

        ctrl.routingEditor.tunnelMode = 'CUSTOM';
        ctrl.routingEditor.customAllowedIpsText =
            '192.168.77.99/24\n10.23.45.67/8';

        WireGuardService.setRouting.and.returnValue(
            $q.when({
                data: {
                    id: 22,
                    tunnelMode: 'CUSTOM',
                    customAllowedIps: [
                        '192.168.77.0/24',
                        '10.0.0.0/8'
                    ]
                }
            })
        );

        ctrl.saveRouting();
        $rootScope.$digest();

        expect(WireGuardService.setRouting)
            .toHaveBeenCalledWith(
                22,
                {
                    tunnelMode: 'CUSTOM',
                    customAllowedIps: [
                        '192.168.77.99/24',
                        '10.23.45.67/8'
                    ]
                }
            );

        expect(ctrl.peerRows[0].customAllowedIps)
            .toEqual([
                '192.168.77.0/24',
                '10.0.0.0/8'
            ]);
        expect(NotificationService.info)
            .toHaveBeenCalledWith(
                'ADMINCONSOLE.WIREGUARD.ROUTING.NOTIFICATION.SAVED'
            );
    });

    it('shows LAN-only warning without granting LAN access', function() {
        WireGuardService.getPeers.and.returnValue(
            $q.when({
                data: [{
                    id: 23,
                    name: 'LAN peer',
                    publicKey: 'lan-public',
                    allowedIp: '10.13.13.23/32',
                    deviceId: null,
                    allowLanAccess: false,
                    tunnelMode: 'LAN_ONLY',
                    customAllowedIps: []
                }]
            })
        );

        ctrl.$onInit();
        $rootScope.$digest();

        expect(ctrl.showLanOnlyWarning()).toBe(true);
        expect(ctrl.peerRows[0].allowLanAccess).toBe(false);
    });

    it('restores persisted routing values when save fails', function() {
        WireGuardService.getPeers.and.returnValue(
            $q.when({
                data: [{
                    id: 24,
                    name: 'Rollback peer',
                    publicKey: 'rollback-public',
                    allowedIp: '10.13.13.24/32',
                    deviceId: null,
                    allowLanAccess: false,
                    tunnelMode: 'FULL_TUNNEL',
                    customAllowedIps: []
                }]
            })
        );

        ctrl.$onInit();
        $rootScope.$digest();

        ctrl.routingEditor.tunnelMode = 'CUSTOM';
        ctrl.routingEditor.customAllowedIpsText =
            '192.168.88.0/24';

        WireGuardService.setRouting.and.returnValue(
            $q.reject({status: 400})
        );

        ctrl.saveRouting().catch(angular.noop);
        $rootScope.$digest();

        expect(ctrl.routingEditor.tunnelMode)
            .toBe('FULL_TUNNEL');
        expect(ctrl.routingEditor.customAllowedIpsText)
            .toBe('');
        expect(NotificationService.error)
            .toHaveBeenCalledWith(
                'ADMINCONSOLE.WIREGUARD.ROUTING.NOTIFICATION.SAVE_FAILED',
                jasmine.any(Object)
            );
    });


});
