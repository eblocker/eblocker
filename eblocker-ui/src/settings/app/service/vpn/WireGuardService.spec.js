/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License.
 */
import 'angular-mocks';

describe('App settings; WireGuardService', function() {
    beforeEach(angular.mock.module('template.settings.app'));
    beforeEach(angular.mock.module('eblocker.adminconsole'));

    beforeEach(angular.mock.module(function($translateProvider) {
        // Prevent the settings translation loader from issuing an
        // unrelated HTTP request during this service unit test.
        $translateProvider.translations('en', {});
    }));

    let service;
    let $httpBackend;

    const PATH = '/api/adminconsole/wireguard';

    beforeEach(inject(function(
        _WireGuardService_,
        _$httpBackend_) {

        service = _WireGuardService_;
        $httpBackend = _$httpBackend_;

        // Request performed by unrelated Settings bootstrap code.
        // Keep this spec focused on WireGuardService's API contract.
        $httpBackend.whenGET('/api/adminconsole/console/ip')
            .respond(200, 'http://127.0.0.1');

        $httpBackend.whenGET('/api/adminconsole/systemstatus')
            .respond(200, {
                executionState: 'RUNNING'
            });

        $httpBackend.whenGET('/api/settings')
            .respond(200, {});
    }));

    afterEach(function() {
        $httpBackend.verifyNoOutstandingExpectation();
        $httpBackend.verifyNoOutstandingRequest();
    });

    it('loads WireGuard server status', function() {
        const status = {
            enabled: true,
            runtime: {
                iface: 'up',
                service: 'active',
                wg: 'ready',
                peers: 2,
                error: null
            }
        };

        $httpBackend.expectGET(PATH + '/status')
            .respond(200, status);

        service.getStatus().then(function(response) {
            expect(response.data).toEqual(status);
        });

        $httpBackend.flush();
    });

    it('enables the WireGuard server with POST', function() {
        $httpBackend.expectPOST(PATH + '/enable')
            .respond(200, {enabled: true});

        service.enable().then(function(response) {
            expect(response.data.enabled).toBe(true);
        });

        $httpBackend.flush();
    });

    it('disables the WireGuard server with POST', function() {
        $httpBackend.expectPOST(PATH + '/disable')
            .respond(200, {enabled: false});

        service.disable().then(function(response) {
            expect(response.data.enabled).toBe(false);
        });

        $httpBackend.flush();
    });

    it('loads secret-free WireGuard peer overview', function() {
        const peers = [
            {
                id: 2,
                name: 'Phone',
                allowedIp: '10.13.13.2/32',
                deviceId: 'device:test-phone',
                allowLanAccess: false
            },
            {
                id: 3,
                name: 'Manual peer',
                allowedIp: '10.13.13.3/32',
                deviceId: null,
                allowLanAccess: true
            }
        ];

        $httpBackend.expectGET(PATH + '/peers')
            .respond(200, peers);

        service.getPeers().then(function(response) {
            expect(response.data).toEqual(peers);
        });

        $httpBackend.flush();
    });

    it('loads endpoint configuration', function() {
        const endpoint = {
            type: 'DYN_DNS',
            host: 'vpn.example.test'
        };

        $httpBackend.expectGET(PATH + '/endpoint')
            .respond(200, endpoint);

        service.getEndpoint().then(function(response) {
            expect(response.data).toEqual(endpoint);
        });

        $httpBackend.flush();
    });

    it('saves endpoint configuration with PUT', function() {
        const endpoint = {
            type: 'FIXED_IP',
            host: '203.0.113.10'
        };

        $httpBackend.expectPUT(
            PATH + '/endpoint',
            endpoint
        ).respond(200, endpoint);

        service.setEndpoint(endpoint)
            .then(function(response) {
                expect(response.data).toEqual(endpoint);
            });

        $httpBackend.flush();
    });

    it('loads all WireGuard device authorizations', function() {
        $httpBackend.expectGET(
            PATH + '/authorization/devices'
        ).respond(200, {
            globalEnabled: true,
            devices: []
        });

        service.getDeviceAuthorizations()
            .then(function(response) {
                expect(response.data.globalEnabled).toBe(true);
                expect(response.data.devices.length).toBe(0);
            });

        $httpBackend.flush();
    });

    it('loads WireGuard authorization for a device', function() {
        $httpBackend.expectGET(
            PATH + '/authorization/devices/device:1'
        ).respond(200, {allowed: true});

        service.getDeviceAuthorization('device:1')
            .then(function(response) {
                expect(response.data.allowed).toBe(true);
            });

        $httpBackend.flush();
    });

    it('sets WireGuard authorization for a device', function() {
        $httpBackend.expectPUT(
            PATH + '/authorization/devices/device:1',
            true
        ).respond(200, {deviceEnabled: true});

        service.setDeviceAuthorization('device:1', true)
            .then(function(response) {
                expect(response.data.deviceEnabled).toBe(true);
            });

        $httpBackend.flush();
    });

    it('sets WireGuard authorization for a user', function() {
        $httpBackend.expectPUT(
            PATH + '/authorization/users/7',
            false
        ).respond(200, false);

        service.setUserAuthorization(7, false)
            .then(function(response) {
                expect(response.data).toBe(false);
            });

        $httpBackend.flush();
    });

});
