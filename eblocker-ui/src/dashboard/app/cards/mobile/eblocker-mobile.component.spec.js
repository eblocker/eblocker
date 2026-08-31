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
/* jshint expr:true */
/* global jasmine */

describe('Component: dashboardMobile', function() { // jshint ignore: line

    beforeEach(angular.mock.module('eblocker.dashboard'));

    let $componentController;
    let $rootScope;
    let $q;
    let $httpBackend;
    let ctrl;

    let vpnHomeStatusResult;
    let vpnHomeCertificatesResult;
    let wireGuardResult;
    let wireGuardCreateResult;
    let wireGuardLanAccessResult;
    let wireGuardDeleteResult;
    let wireGuardConfigResult;
    let wireGuardQrResult;
    let wireGuardPeerRequestCount;
    let wireGuardCreateRequestCount;
    let wireGuardLanAccessRequestCount;
    let wireGuardLanAccessRequestValue;
    let wireGuardDeleteRequestCount;
    let confirmDialogRequestCount;
    let confirmDialogOkCallback;
    let confirmDialogCancelCallback;
    let qrRequestCount;
    let mockWindow;
    let createdLink;
    let createdBlob;
    let revokedObjectUrl;

    const device = {
        id: 'device:001122334455',
        name: 'Phone'
    };

    beforeEach(angular.mock.module(function($provide) {

        $provide.factory('DeviceService', function($q) {
            return {
                getDevice: function() {
                    return $q.when({
                        data: angular.copy(device)
                    });
                }
            };
        });

        $provide.factory('VpnHomeService', function($q) {
            return {
                loadStatus: function() {
                    if (vpnHomeStatusResult.resolve) {
                        return $q.when({
                            data: vpnHomeStatusResult.data
                        });
                    }

                    return $q.reject({
                        status: vpnHomeStatusResult.status
                    });
                },

                loadCertificates: function() {
                    if (vpnHomeCertificatesResult.resolve) {
                        return $q.when({
                            data: vpnHomeCertificatesResult.data
                        });
                    }

                    return $q.reject({
                        status: vpnHomeCertificatesResult.status
                    });
                }
            };
        });

        $provide.factory('WireGuardDashboardService', function($q) {
            return {
                getPeer: function(deviceId) {
                    expect(deviceId).toBe(device.id);
                    wireGuardPeerRequestCount += 1;

                    if (wireGuardResult.resolve) {
                        return $q.when({
                            data: wireGuardResult.data
                        });
                    }

                    return $q.reject({
                        status: wireGuardResult.status
                    });
                },

                createPeer: function(deviceId) {
                    expect(deviceId).toBe(device.id);
                    wireGuardCreateRequestCount += 1;

                    if (wireGuardCreateResult.resolve) {
                        return $q.when({
                            data: wireGuardCreateResult.data,
                            status: 201
                        });
                    }

                    return $q.reject({
                        status: wireGuardCreateResult.status
                    });
                },

                deletePeer: function(deviceId) {
                    expect(deviceId).toBe(device.id);
                    wireGuardDeleteRequestCount += 1;

                    if (wireGuardDeleteResult.resolve) {
                        return $q.when({
                            status: 204
                        });
                    }

                    return $q.reject({
                        status: wireGuardDeleteResult.status
                    });
                },

                setLanAccess: function(deviceId, allowLanAccess) {
                    expect(deviceId).toBe(device.id);

                    wireGuardLanAccessRequestCount += 1;
                    wireGuardLanAccessRequestValue =
                        allowLanAccess;

                    if (wireGuardLanAccessResult.resolve) {
                        return $q.when({
                            data: angular.copy(
                                wireGuardLanAccessResult.data
                            )
                        });
                    }

                    return $q.reject({
                        status: wireGuardLanAccessResult.status
                    });
                },

                getClientConfig: function(deviceId) {
                    expect(deviceId).toBe(device.id);

                    if (wireGuardConfigResult.resolve) {
                        return $q.when({
                            data: wireGuardConfigResult.data
                        });
                    }

                    return $q.reject({
                        status: wireGuardConfigResult.status
                    });
                },

                getQrCode: function(deviceId) {
                    expect(deviceId).toBe(device.id);
                    qrRequestCount += 1;

                    if (wireGuardQrResult.resolve) {
                        return $q.when({
                            data: wireGuardQrResult.data
                        });
                    }

                    return $q.reject({
                        status: wireGuardQrResult.status
                    });
                }
            };
        });

        $provide.factory('CardService', function($q) {
            return {
                scrollToCard: function() {},
                getDashboardData: function() {
                    return $q.when(false);
                },
                getFilterCards: function() {
                    return [];
                }
            };
        });

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

        $provide.factory('DialogService', function($q) {
            return {
                confirmationDialog: function(
                    event,
                    title,
                    text,
                    ok,
                    cancel,
                    subject,
                    okCallback,
                    cancelCallback
                ) {
                    confirmDialogRequestCount += 1;
                    confirmDialogOkCallback = okCallback;
                    confirmDialogCancelCallback = cancelCallback;

                    expect(title).toBe(
                        'MOBILE.CARD.WIREGUARD.DELETE_CONFIRM_TITLE'
                    );
                    expect(text).toBe(
                        'MOBILE.CARD.WIREGUARD.DELETE_CONFIRM_TEXT'
                    );
                    expect(ok).toBe(
                        'MOBILE.CARD.WIREGUARD.DELETE_CONFIRM_OK'
                    );
                    expect(cancel).toBe(
                        'MOBILE.CARD.WIREGUARD.DELETE_CONFIRM_CANCEL'
                    );

                    return $q.when();
                }
            };
        });

        $provide.value('NotificationService', {
            error: function() {}
        });

        $provide.value('deviceDetector', {
            os: 'other'
        });

        $provide.value('logger', {
            error: function() {},
            info: function() {}
        });
    }));

    beforeEach(inject(function(
        _$componentController_,
        _$rootScope_,
        _$q_,
        _$httpBackend_) {

        $componentController = _$componentController_;
        $rootScope = _$rootScope_;
        $q = _$q_;
        $httpBackend = _$httpBackend_;

        // Requests made by unrelated dashboard bootstrap services.
        // This component spec must exercise only the Mobile card's
        // OpenVPN/WireGuard controller behavior.
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

        vpnHomeStatusResult = {
            resolve: true,
            data: {
                isRunning: false
            }
        };

        vpnHomeCertificatesResult = {
            resolve: true,
            data: []
        };

        wireGuardResult = {
            resolve: false,
            status: 404
        };

        wireGuardCreateResult = {
            resolve: true,
            data: {
                id: 7,
                name: 'Phone',
                allowedIp: '10.13.13.2/32',
                allowLanAccess: false
            }
        };

        wireGuardLanAccessResult = {
            resolve: true,
            data: {
                id: 7,
                name: 'Phone',
                allowedIp: '10.13.13.2/32',
                allowLanAccess: true
            }
        };

        wireGuardDeleteResult = {
            resolve: true
        };

        wireGuardConfigResult = {
            resolve: true,
            data: {
                peerId: 7,
                configuration: '[Interface]\nPrivateKey = secret\n'
            }
        };

        wireGuardQrResult = {
            resolve: true,
            data: new ArrayBuffer(8)
        };

        wireGuardPeerRequestCount = 0;
        wireGuardCreateRequestCount = 0;
        wireGuardLanAccessRequestCount = 0;
        wireGuardLanAccessRequestValue = undefined;
        wireGuardDeleteRequestCount = 0;
        confirmDialogRequestCount = 0;
        confirmDialogOkCallback = undefined;
        confirmDialogCancelCallback = undefined;
        qrRequestCount = 0;

        createdLink = {
            href: undefined,
            download: undefined,
            style: {},
            click: jasmine.createSpy('click')
        };

        createdBlob = undefined;
        revokedObjectUrl = undefined;

        mockWindow = {
            location: undefined,

            Blob: function(parts, options) {
                createdBlob = {
                    parts: parts,
                    options: options
                };
                return createdBlob;
            },

            URL: {
                createObjectURL: jasmine.createSpy(
                    'createObjectURL'
                ).and.returnValue(
                    'blob:wireguard-test'
                ),

                revokeObjectURL: jasmine.createSpy(
                    'revokeObjectURL'
                ).and.callFake(function(url) {
                    revokedObjectUrl = url;
                })
            },

            document: {
                createElement: jasmine.createSpy(
                    'createElement'
                ).and.returnValue(createdLink),

                body: {
                    appendChild: jasmine.createSpy(
                        'appendChild'
                    ),
                    removeChild: jasmine.createSpy(
                        'removeChild'
                    )
                }
            }
        };

        ctrl = $componentController(
            'dashboardMobile',
            {
                $window: mockWindow
            },
            {}
        );
    }));

    function init() {
        ctrl.$onInit();
        $rootScope.$digest();
    }

    it('creates a controller instance', function() {
        expect(angular.isDefined(ctrl)).toBe(true);
    });

    it('keeps OpenVPN as the first tab', function() {
        expect(ctrl.mobileModeTabIndex).toBe(0);
    });

    it('loads WireGuard peer even when OpenVPN status fails', function() {
        vpnHomeStatusResult = {
            resolve: false,
            status: 500
        };

        wireGuardResult = {
            resolve: true,
            data: {
                id: 7,
                name: 'Phone',
                allowedIp: '10.13.13.2/32',
                allowLanAccess: false
            }
        };

        init();

        expect(wireGuardPeerRequestCount).toBe(1);
        expect(ctrl.device.id).toBe(device.id);
        expect(ctrl.wireGuardPeer.id).toBe(7);
        expect(ctrl.wireGuardPeer.name).toBe('Phone');
        expect(ctrl.wireGuardPeerLoading).toBe(false);
    });

    it('keeps OpenVPN certificate state correct with independent init branches', function() {
        vpnHomeStatusResult = {
            resolve: true,
            data: {
                isRunning: true
            }
        };

        vpnHomeCertificatesResult = {
            resolve: true,
            data: [
                device.id
            ]
        };

        init();

        expect(ctrl.device.id).toBe(device.id);
        expect(ctrl.device.hasCertificate).toBe(true);
    });

    it('loads device-bound WireGuard peer metadata', function() {
        wireGuardResult = {
            resolve: true,
            data: {
                id: 7,
                name: 'Phone',
                allowedIp: '10.13.13.2/32',
                allowLanAccess: false
            }
        };

        init();

        expect(ctrl.device.id).toBe(device.id);
        expect(ctrl.wireGuardPeer.id).toBe(7);
        expect(ctrl.wireGuardPeer.name).toBe('Phone');
        expect(ctrl.wireGuardPeer.allowLanAccess).toBe(false);
        expect(ctrl.wireGuardPeerLoading).toBe(false);
    });

    it('normalizes missing WireGuard peer to empty state', function() {
        wireGuardResult = {
            resolve: false,
            status: 404
        };

        init();

        expect(ctrl.device.id).toBe(device.id);
        expect(ctrl.wireGuardPeer).toBeUndefined();
        expect(ctrl.wireGuardPeerLoading).toBe(false);
    });

    it('keeps non-404 WireGuard read failure non-fatal', function() {
        wireGuardResult = {
            resolve: false,
            status: 500
        };

        init();

        expect(ctrl.device.id).toBe(device.id);
        expect(ctrl.wireGuardPeer).toBeUndefined();
        expect(ctrl.wireGuardPeerLoading).toBe(false);
    });

    it('creates first WireGuard peer from empty state using only device id', function() {
        init();

        expect(ctrl.wireGuardPeer).toBeUndefined();
        expect(wireGuardCreateRequestCount).toBe(0);

        ctrl.createWireGuardPeer();
        $rootScope.$digest();

        expect(wireGuardCreateRequestCount).toBe(1);
        expect(ctrl.wireGuardPeer.id).toBe(7);
        expect(ctrl.wireGuardPeer.name).toBe('Phone');
        expect(ctrl.wireGuardPeer.allowedIp)
            .toBe('10.13.13.2/32');
        expect(ctrl.wireGuardPeer.allowLanAccess)
            .toBe(false);
        expect(ctrl.wireGuardPeerCreating).toBe(false);

        expect(qrRequestCount).toBe(0);
        expect(createdBlob).toBeUndefined();
        expect(ctrl.wireGuardQrCodeUrl).toBeUndefined();
    });

    it('keeps empty WireGuard state when peer creation fails', function() {
        wireGuardCreateResult = {
            resolve: false,
            status: 500
        };

        init();

        ctrl.createWireGuardPeer();
        $rootScope.$digest();

        expect(wireGuardCreateRequestCount).toBe(1);
        expect(ctrl.wireGuardPeer).toBeUndefined();
        expect(ctrl.wireGuardPeerCreating).toBe(false);
        expect(qrRequestCount).toBe(0);
        expect(createdBlob).toBeUndefined();
    });

    it('does not create another WireGuard peer when one is already bound', function() {
        ctrl.device = angular.copy(device);
        ctrl.wireGuardPeer = {
            id: 7,
            name: 'Phone'
        };

        ctrl.createWireGuardPeer();
        $rootScope.$digest();

        expect(wireGuardCreateRequestCount).toBe(0);
        expect(ctrl.wireGuardPeer.id).toBe(7);
    });

    it('enables WireGuard LAN access using device id and naked boolean', function() {
        wireGuardResult = {
            resolve: true,
            data: {
                id: 7,
                name: 'Phone',
                allowedIp: '10.13.13.2/32',
                allowLanAccess: false
            }
        };

        init();

        expect(ctrl.wireGuardPeer.allowLanAccess)
            .toBe(false);

        // md-switch updates ng-model before ng-change.
        ctrl.wireGuardPeer.allowLanAccess = true;
        ctrl.updateWireGuardLanAccess();
        $rootScope.$digest();

        expect(wireGuardLanAccessRequestCount).toBe(1);
        expect(wireGuardLanAccessRequestValue).toBe(true);
        expect(ctrl.wireGuardPeer.allowLanAccess).toBe(true);
        expect(ctrl.wireGuardLanAccessUpdating).toBe(false);

        expect(qrRequestCount).toBe(0);
        expect(createdBlob).toBeUndefined();
    });

    it('disables WireGuard LAN access using server-returned metadata', function() {
        wireGuardResult = {
            resolve: true,
            data: {
                id: 7,
                name: 'Phone',
                allowedIp: '10.13.13.2/32',
                allowLanAccess: true
            }
        };

        wireGuardLanAccessResult = {
            resolve: true,
            data: {
                id: 7,
                name: 'Phone',
                allowedIp: '10.13.13.2/32',
                allowLanAccess: false
            }
        };

        init();

        ctrl.wireGuardPeer.allowLanAccess = false;
        ctrl.updateWireGuardLanAccess();
        $rootScope.$digest();

        expect(wireGuardLanAccessRequestCount).toBe(1);
        expect(wireGuardLanAccessRequestValue).toBe(false);
        expect(ctrl.wireGuardPeer.allowLanAccess).toBe(false);
        expect(ctrl.wireGuardLanAccessUpdating).toBe(false);
    });

    it('rolls WireGuard LAN switch back when persistence fails', function() {
        wireGuardResult = {
            resolve: true,
            data: {
                id: 7,
                name: 'Phone',
                allowedIp: '10.13.13.2/32',
                allowLanAccess: false
            }
        };

        wireGuardLanAccessResult = {
            resolve: false,
            status: 500
        };

        init();

        ctrl.wireGuardPeer.allowLanAccess = true;
        ctrl.updateWireGuardLanAccess();
        $rootScope.$digest();

        expect(wireGuardLanAccessRequestCount).toBe(1);
        expect(wireGuardLanAccessRequestValue).toBe(true);
        expect(ctrl.wireGuardPeer.allowLanAccess).toBe(false);
        expect(ctrl.wireGuardLanAccessUpdating).toBe(false);

        expect(qrRequestCount).toBe(0);
        expect(createdBlob).toBeUndefined();
    });

    it('does not write WireGuard LAN access without a bound peer', function() {
        ctrl.device = angular.copy(device);
        ctrl.wireGuardPeer = undefined;

        ctrl.updateWireGuardLanAccess();
        $rootScope.$digest();

        expect(wireGuardLanAccessRequestCount).toBe(0);
    });

    it('requires confirmation before deleting WireGuard peer', function() {
        wireGuardResult = {
            resolve: true,
            data: {
                id: 7,
                name: 'Phone',
                allowedIp: '10.13.13.2/32',
                allowLanAccess: false
            }
        };

        init();

        ctrl.confirmDeleteWireGuardPeer({});
        $rootScope.$digest();

        expect(confirmDialogRequestCount).toBe(1);
        expect(wireGuardDeleteRequestCount).toBe(0);
        expect(ctrl.wireGuardPeer.id).toBe(7);
        expect(confirmDialogOkCallback).toEqual(
            jasmine.any(Function)
        );
        expect(confirmDialogCancelCallback).toEqual(
            jasmine.any(Function)
        );
    });

    it('does not delete WireGuard peer when confirmation is cancelled', function() {
        wireGuardResult = {
            resolve: true,
            data: {
                id: 7,
                name: 'Phone',
                allowedIp: '10.13.13.2/32',
                allowLanAccess: false
            }
        };

        init();

        ctrl.confirmDeleteWireGuardPeer({});
        $rootScope.$digest();

        confirmDialogCancelCallback();
        $rootScope.$digest();

        expect(wireGuardDeleteRequestCount).toBe(0);
        expect(ctrl.wireGuardPeer.id).toBe(7);
        expect(ctrl.wireGuardPeerDeleting).toBe(false);
    });

    it('deletes WireGuard peer only after explicit confirmation', function() {
        wireGuardResult = {
            resolve: true,
            data: {
                id: 7,
                name: 'Phone',
                allowedIp: '10.13.13.2/32',
                allowLanAccess: false
            }
        };

        init();

        ctrl.confirmDeleteWireGuardPeer({});
        $rootScope.$digest();

        confirmDialogOkCallback();
        $rootScope.$digest();

        expect(wireGuardDeleteRequestCount).toBe(1);
        expect(ctrl.wireGuardPeer).toBeUndefined();
        expect(ctrl.wireGuardPeerDeleting).toBe(false);
        expect(qrRequestCount).toBe(0);
    });

    it('revokes visible WireGuard QR after confirmed successful delete', function() {
        wireGuardResult = {
            resolve: true,
            data: {
                id: 7,
                name: 'Phone',
                allowedIp: '10.13.13.2/32',
                allowLanAccess: false
            }
        };

        init();

        ctrl.showWireGuardQrCode();
        $rootScope.$digest();

        expect(ctrl.wireGuardQrCodeUrl)
            .toBe('blob:wireguard-test');

        ctrl.confirmDeleteWireGuardPeer({});
        $rootScope.$digest();

        confirmDialogOkCallback();
        $rootScope.$digest();

        expect(wireGuardDeleteRequestCount).toBe(1);
        expect(revokedObjectUrl)
            .toBe('blob:wireguard-test');
        expect(ctrl.wireGuardQrCodeUrl)
            .toBeUndefined();
        expect(ctrl.wireGuardPeer)
            .toBeUndefined();
    });

    it('keeps WireGuard peer when confirmed delete fails', function() {
        wireGuardResult = {
            resolve: true,
            data: {
                id: 7,
                name: 'Phone',
                allowedIp: '10.13.13.2/32',
                allowLanAccess: false
            }
        };

        wireGuardDeleteResult = {
            resolve: false,
            status: 500
        };

        init();

        ctrl.confirmDeleteWireGuardPeer({});
        $rootScope.$digest();

        confirmDialogOkCallback();
        $rootScope.$digest();

        expect(wireGuardDeleteRequestCount).toBe(1);
        expect(ctrl.wireGuardPeer.id).toBe(7);
        expect(ctrl.wireGuardPeer.name).toBe('Phone');
        expect(ctrl.wireGuardPeerDeleting).toBe(false);
    });

    it('does not open WireGuard delete confirmation without a bound peer', function() {
        ctrl.device = angular.copy(device);
        ctrl.wireGuardPeer = undefined;

        ctrl.confirmDeleteWireGuardPeer({});
        $rootScope.$digest();

        expect(confirmDialogRequestCount).toBe(0);
        expect(wireGuardDeleteRequestCount).toBe(0);
    });

    it('downloads WireGuard configuration only through transient local data', function() {
        wireGuardResult = {
            resolve: true,
            data: {
                id: 7,
                name: 'Phone',
                allowedIp: '10.13.13.2/32',
                allowLanAccess: false
            }
        };

        init();

        ctrl.downloadWireGuardConfig();
        $rootScope.$digest();

        expect(createdBlob).toBeDefined();
        expect(createdBlob.parts.length).toBe(1);
        expect(createdBlob.parts[0]).toBe(
            '[Interface]\nPrivateKey = secret\n'
        );
        expect(createdBlob.options.type).toBe(
            'text/plain;charset=utf-8'
        );

        expect(
            mockWindow.URL.createObjectURL
        ).toHaveBeenCalled();

        expect(createdLink.href).toBe(
            'blob:wireguard-test'
        );
        expect(createdLink.download).toBe(
            'wireguard.conf'
        );
        expect(createdLink.click).toHaveBeenCalled();

        expect(
            mockWindow.document.body.appendChild
        ).toHaveBeenCalledWith(createdLink);

        expect(
            mockWindow.document.body.removeChild
        ).toHaveBeenCalledWith(createdLink);

        expect(revokedObjectUrl).toBe(
            'blob:wireguard-test'
        );

        expect(
            ctrl.wireGuardClientConfiguration
        ).toBeUndefined();

        expect(ctrl.isDownloadingWireGuardConfig)
            .toBe(false);
    });

    it('does not request WireGuard configuration without a bound peer', function() {
        ctrl.device = angular.copy(device);
        ctrl.wireGuardPeer = undefined;

        ctrl.downloadWireGuardConfig();
        $rootScope.$digest();

        expect(
            mockWindow.URL.createObjectURL
        ).not.toHaveBeenCalled();

        expect(createdLink.click)
            .not.toHaveBeenCalled();
    });

    it('reveals WireGuard QR through a temporary blob URL only after explicit action', function() {
        wireGuardResult = {
            resolve: true,
            data: {
                id: 7,
                name: 'Phone',
                allowedIp: '10.13.13.2/32',
                allowLanAccess: false
            }
        };

        init();

        expect(qrRequestCount).toBe(0);
        expect(ctrl.wireGuardQrCodeUrl).toBeUndefined();

        ctrl.showWireGuardQrCode();
        $rootScope.$digest();

        expect(qrRequestCount).toBe(1);
        expect(createdBlob).toBeDefined();
        expect(createdBlob.parts.length).toBe(1);
        expect(
            createdBlob.parts[0] instanceof ArrayBuffer
        ).toBe(true);
        expect(createdBlob.options.type).toBe('image/png');

        expect(ctrl.wireGuardQrCodeUrl)
            .toBe('blob:wireguard-test');

        expect(
            ctrl.wireGuardQrCode
        ).toBeUndefined();

        expect(
            ctrl.wireGuardQrCodeBinary
        ).toBeUndefined();

        expect(ctrl.isLoadingWireGuardQrCode)
            .toBe(false);
    });

    it('revokes WireGuard QR blob URL when hidden', function() {
        wireGuardResult = {
            resolve: true,
            data: {
                id: 7,
                name: 'Phone'
            }
        };

        init();

        ctrl.showWireGuardQrCode();
        $rootScope.$digest();

        expect(ctrl.wireGuardQrCodeUrl)
            .toBe('blob:wireguard-test');

        ctrl.hideWireGuardQrCode();

        expect(revokedObjectUrl)
            .toBe('blob:wireguard-test');

        expect(ctrl.wireGuardQrCodeUrl)
            .toBeUndefined();
    });

    it('revokes WireGuard QR blob URL when component is destroyed', function() {
        wireGuardResult = {
            resolve: true,
            data: {
                id: 7,
                name: 'Phone'
            }
        };

        init();

        ctrl.showWireGuardQrCode();
        $rootScope.$digest();

        expect(ctrl.wireGuardQrCodeUrl)
            .toBe('blob:wireguard-test');

        ctrl.$onDestroy();

        expect(revokedObjectUrl)
            .toBe('blob:wireguard-test');

        expect(ctrl.wireGuardQrCodeUrl)
            .toBeUndefined();
    });

    it('does not request WireGuard QR without a bound peer', function() {
        ctrl.device = angular.copy(device);
        ctrl.wireGuardPeer = undefined;

        ctrl.showWireGuardQrCode();
        $rootScope.$digest();

        expect(qrRequestCount).toBe(0);
        expect(ctrl.wireGuardQrCodeUrl)
            .toBeUndefined();
    });
});
