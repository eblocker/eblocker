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

describe('App: dashboard; WireGuardDashboardService', function() {

    beforeEach(angular.mock.module('template.dashboard.app'));
    beforeEach(angular.mock.module('eblocker.dashboard'));

    let service;
    let $httpBackend;
    let $q;

    const mockCardService = {
        getDashboardData: function() {
            return $q.when(false);
        },

        getFilterCards: function() {
            return [];
        }
    };

    const mockRegistrationInfo = {
        loadProductInfo: function() {
            const deferred = $q.defer();
            deferred.resolve({
                data: {
                    productInfo: {
                        productFeatures: []
                    }
                }
            });
            return deferred.promise;
        },

        getProductInfo: function() {
            return {
                productFeatures: []
            };
        },

        getRegistrationInfo: function() {
            return {
                productInfo: {
                    productFeatures: []
                }
            };
        }
    };

    const DEVICE_ID = 'device:001122334455';
    const ENCODED_DEVICE_ID = 'device%3A001122334455';
    const BASE_PATH =
        '/api/dashboard/wireguard/' + ENCODED_DEVICE_ID;

    const PATH =
        BASE_PATH + '/peer';

    const STATUS_PATH =
        BASE_PATH + '/status';

    beforeEach(angular.mock.module(function($provide) {
        // Match the established dashboard test pattern: registration
        // is unrelated to WireGuardDashboardService and must not turn
        // this unit test into a RegistrationService integration test.
        $provide.value(
            'registration',
            mockRegistrationInfo
        );

        $provide.value(
            'CardService',
            mockCardService
        );
    }));

    beforeEach(inject(function(
        _WireGuardDashboardService_,
        _$httpBackend_,
        _$q_) {

        service = _WireGuardDashboardService_;
        $httpBackend = _$httpBackend_;
        $q = _$q_;

        // Requests performed by dashboard bootstrap services.
        // Dashboard uses both its own and the shared translation file.
        // The loader appends a revision/hash to each generated filename.
        $httpBackend.whenGET(
            /^\/locale\/lang-(dashboard|shared)-en-[0-9]+\.json$/
        ).respond(200, {});

        $httpBackend.when('GET', '/api/token/DASHBOARD')
            .respond(200, {});
        $httpBackend.when('GET', '/api/settings')
            .respond(200, {});
        $httpBackend.when('GET', '/controlbar/console/ip')
            .respond(200, {});
        $httpBackend.when('GET', '/api/device')
            .respond(200, {});
        $httpBackend.when('GET', '/api/dashboard/users')
            .respond(200, {});
        $httpBackend.when('GET', '/api/dashboardcard')
            .respond(200, []);
    }));

    afterEach(function() {
        $httpBackend.verifyNoOutstandingExpectation();
    });

    it('should create a service instance', function() {
        expect(angular.isDefined(service)).toBe(true);
    });

    it('gets persisted WireGuard enabled status by device id only', function() {
        $httpBackend.expectGET(STATUS_PATH)
            .respond(200, true);

        service.getStatus(DEVICE_ID)
            .then(function(response) {
                expect(response.data).toBe(true);
            });

        $httpBackend.flush();
    });

    it('gets peer by device id only', function() {
        const peer = {
            id: 7,
            name: 'Phone',
            allowedIp: '10.13.13.2/32',
            allowLanAccess: false
        };

        $httpBackend.expectGET(PATH)
            .respond(200, peer);

        service.getPeer(DEVICE_ID).then(function(response) {
            expect(response.data).toEqual(peer);
        });

        $httpBackend.flush();
    });

    it('creates peer without client supplied peer data', function() {
        const peer = {
            id: 7,
            name: 'Phone'
        };

        $httpBackend.expectPOST(PATH, null)
            .respond(201, peer);

        service.createPeer(DEVICE_ID).then(function(response) {
            expect(response.status).toBe(201);
            expect(response.data).toEqual(peer);
        });

        $httpBackend.flush();
    });

    it('deletes peer by device id only', function() {
        $httpBackend.expectDELETE(PATH)
            .respond(204);

        service.deletePeer(DEVICE_ID);

        $httpBackend.flush();
    });

    it('updates per-device LAN access', function() {
        const peer = {
            id: 7,
            allowLanAccess: true
        };

        $httpBackend.expectPUT(
            PATH + '/lanAccess',
            true
        ).respond(200, peer);

        service.setLanAccess(
            DEVICE_ID,
            true
        ).then(function(response) {
            expect(response.data.allowLanAccess).toBe(true);
        });

        $httpBackend.flush();
    });

    it('loads sensitive client config through device path', function() {
        const config = {
            peerId: 7,
            configuration: '[Interface]\n...'
        };

        $httpBackend.expectGET(
            PATH + '/clientConfig'
        ).respond(200, config);

        service.getClientConfig(DEVICE_ID)
            .then(function(response) {
                expect(response.data.peerId).toBe(7);
                expect(response.data.configuration)
                    .toBe('[Interface]\n...');
            });

        $httpBackend.flush();
    });

    it('loads QR code as binary arraybuffer', function() {
        const png = new ArrayBuffer(4);

        $httpBackend.expectGET(
            PATH + '/qrcode'
        ).respond(
            200,
            png,
            {
                'Content-Type': 'image/png'
            }
        );

        service.getQrCode(DEVICE_ID)
            .then(function(response) {
                expect(response.data instanceof ArrayBuffer)
                    .toBe(true);
            });

        $httpBackend.flush();
    });
});
