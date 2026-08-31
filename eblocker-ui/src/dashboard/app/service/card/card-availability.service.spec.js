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

describe('App: dashboard; CardAvailabilityService WireGuard mobile availability', function() {

    beforeEach(angular.mock.module('eblocker.dashboard'));

    let service;
    let $q;
    let $rootScope;
    let $httpBackend;

    let device;
    let vpnHomeStatus;
    let wireGuardResponse;
    let wireGuardStatusResponse;
    let localDevice;

    const mobileCard = {
        name: 'MOBILE',
        requiredFeature: 'WOL'
    };

    const mockCardHtml = {
        MOBILE: '<dashboard-mobile></dashboard-mobile>'
    };

    beforeEach(angular.mock.module(function($provide, $translateProvider) {

        $provide.value('CARD_HTML', mockCardHtml);

        $provide.factory('DeviceService', function($q) {
            return {
                getDevice: function() {
                    return $q.when({
                        data: device
                    });
                }
            };
        });

        $provide.factory('SslService', function($q) {
            return {
                getSslStatus: function() {
                    return $q.when({
                        data: {
                            globalSslStatus: true
                        }
                    });
                }
            };
        });

        $provide.factory('UserProfileService', function($q) {
            return {
                getCurrentUsersProfile: function() {
                    return $q.when({
                        data: {}
                    });
                }
            };
        });

        $provide.factory('PauseService', function($q) {
            return {
                getPause: function() {
                    return $q.when({
                        data: {}
                    });
                }
            };
        });

        $provide.factory('VpnHomeService', function($q) {
            return {
                loadStatus: function() {
                    return $q.when({
                        data: vpnHomeStatus
                    });
                }
            };
        });

        $provide.factory('WireGuardDashboardService', function($q) {
            return {
                getStatus: function() {
                    if (wireGuardStatusResponse.resolve) {
                        return $q.when({
                            data: wireGuardStatusResponse.data
                        });
                    }

                    return $q.reject({
                        status: wireGuardStatusResponse.status
                    });
                },

                getPeer: function() {
                    if (wireGuardResponse.resolve) {
                        return $q.when({
                            data: wireGuardResponse.data
                        });
                    }

                    return $q.reject({
                        status: wireGuardResponse.status
                    });
                }
            };
        });

        $provide.value('DeviceSelectorService', {
            isLocalDevice: function() {
                return localDevice;
            },
            getSelectedDevice: function() {
                return device;
            }
        });

        /*
         * These are unrelated route/bootstrap dependencies. Keep this
         * service test isolated from dashboard initialization.
         */
        $provide.factory('registration', function($q) {
            return {
                loadProductInfo: function() {
                    return $q.when({
                        data: {
                            productInfo: {
                                productFeatures: []
                            }
                        }
                    });
                },
                getRegistrationInfo: function() {
                    return {
                        productInfo: {
                            productFeatures: []
                        }
                    };
                },
                getProductInfo: function() {
                    return {
                        productFeatures: []
                    };
                }
            };
        });

        $provide.factory('CardService', function($q) {
            return {
                getDashboardData: function() {
                    return $q.when(false);
                },
                getFilterCards: function() {
                    return [];
                }
            };
        });

        $translateProvider.translations('en', {});
    }));

    beforeEach(inject(function(
        _CardAvailabilityService_,
        _$q_,
        _$rootScope_,
        _$httpBackend_) {

        service = _CardAvailabilityService_;
        $q = _$q_;
        $rootScope = _$rootScope_;
        $httpBackend = _$httpBackend_;

        // Requests performed by unrelated dashboard bootstrap services.
        // Keep this CardAvailabilityService test focused on its own
        // OpenVPN/WireGuard availability contract.
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

        device = {
            id: 'device:001122334455',
            mobileState: false,
            operatingUser: 1
        };

        vpnHomeStatus = {
            isRunning: false
        };

        wireGuardResponse = {
            resolve: false,
            status: 404
        };

        wireGuardStatusResponse = {
            resolve: true,
            data: false
        };

        localDevice = true;
    }));

    function update() {
        let resolved = false;
        let rejected = false;

        service.updateData().then(
            function() {
                resolved = true;
            },
            function() {
                rejected = true;
            }
        );

        $rootScope.$digest();

        return {
            resolved: resolved,
            rejected: rejected
        };
    }

    it('preserves existing OpenVPN mobile availability', function() {
        device.mobileState = true;
        vpnHomeStatus.isRunning = true;

        const state = update();

        expect(state.resolved).toBe(true);
        expect(state.rejected).toBe(false);
        expect(
            service.isCardAvailable(mobileCard, {})
        ).toBe(true);
    });

    it('makes mobile card available for enabled WireGuard before the first peer exists', function() {
        wireGuardStatusResponse = {
            resolve: true,
            data: true
        };

        wireGuardResponse = {
            resolve: false,
            status: 404
        };

        const state = update();

        expect(state.resolved).toBe(true);
        expect(state.rejected).toBe(false);
        expect(
            service.isCardAvailable(mobileCard, {})
        ).toBe(true);
    });

    it('makes mobile card available for a bound WireGuard peer', function() {
        wireGuardResponse = {
            resolve: true,
            data: {
                id: 7,
                name: 'Phone',
                allowedIp: '10.13.13.2/32',
                allowLanAccess: false
            }
        };

        const state = update();

        expect(state.resolved).toBe(true);
        expect(state.rejected).toBe(false);
        expect(
            service.isCardAvailable(mobileCard, {})
        ).toBe(true);
    });

    it('treats missing WireGuard peer as normal absence', function() {
        wireGuardResponse = {
            resolve: false,
            status: 404
        };

        const state = update();

        expect(state.resolved).toBe(true);
        expect(state.rejected).toBe(false);
        expect(
            service.isCardAvailable(mobileCard, {})
        ).toBe(false);
    });

    it('keeps bound-peer fallback when WireGuard status read fails', function() {
        wireGuardStatusResponse = {
            resolve: false,
            status: 500
        };

        wireGuardResponse = {
            resolve: true,
            data: {
                id: 7,
                name: 'Phone'
            }
        };

        const state = update();

        expect(state.resolved).toBe(true);
        expect(state.rejected).toBe(false);
        expect(
            service.isCardAvailable(mobileCard, {})
        ).toBe(true);
    });

    it('treats WireGuard status failure without peer as normal absence', function() {
        wireGuardStatusResponse = {
            resolve: false,
            status: 500
        };

        wireGuardResponse = {
            resolve: false,
            status: 404
        };

        const state = update();

        expect(state.resolved).toBe(true);
        expect(state.rejected).toBe(false);
        expect(
            service.isCardAvailable(mobileCard, {})
        ).toBe(false);
    });

    it('does not let a WireGuard metadata failure break OpenVPN availability', function() {
        device.mobileState = true;
        vpnHomeStatus.isRunning = true;

        wireGuardResponse = {
            resolve: false,
            status: 500
        };

        const state = update();

        expect(state.resolved).toBe(true);
        expect(state.rejected).toBe(false);
        expect(
            service.isCardAvailable(mobileCard, {})
        ).toBe(true);
    });

    it('keeps the mobile card local-device only', function() {
        localDevice = false;

        wireGuardResponse = {
            resolve: true,
            data: {
                id: 7,
                name: 'Phone'
            }
        };

        const state = update();

        expect(state.resolved).toBe(true);
        expect(state.rejected).toBe(false);
        expect(
            service.isCardAvailable(mobileCard, {})
        ).toBe(false);
    });
});
