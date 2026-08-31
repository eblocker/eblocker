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
export default {
    templateUrl: 'app/cards/mobile/eblocker-mobile.component.html',
    controllerAs: 'vm',
    controller: Controller,
    bindings: {
        cardId: '@'
    }
};

function Controller(logger, $timeout, $window, $q, CardService, VpnHomeService, DialogService, DeviceService, // jshint ignore: line
                    WireGuardDashboardService, NotificationService, deviceDetector) {
    'ngInject';
    'use strict';

    const vm = this;

    const CARD_NAME = 'MOBILE'; //'card-11';

    vm.downloadClientConf =  downloadClientConf;
    vm.createWireGuardPeer = createWireGuardPeer;
    vm.updateWireGuardLanAccess = updateWireGuardLanAccess;
    vm.confirmDeleteWireGuardPeer = confirmDeleteWireGuardPeer;
    vm.downloadWireGuardConfig = downloadWireGuardConfig;
    vm.showWireGuardQrCode = showWireGuardQrCode;
    vm.hideWireGuardQrCode = hideWireGuardQrCode;
    vm.goToRecommendedApps =  goToRecommendedApps;

    vm.mobileModeTabIndex = 0;
    vm.wireGuardPeer = undefined;
    vm.wireGuardPeerLoading = false;
    vm.wireGuardPeerCreating = false;
    vm.wireGuardLanAccessUpdating = false;
    vm.wireGuardPeerDeleting = false;

    // type equals enum on server
    // name equals string from deviceDetector (except "other")
    // value is the String visible in UI
    vm.osTypes = [
        {type: 'WINDOWS', name: 'windows', value: 'Windows'},
        {type: 'MAC', name: 'mac', value: 'MacOS'},
        {type: 'IOS', name: 'ios', value: 'iOS'},
        {type: 'ANDROID', name: 'android', value: 'Android'},
        {type: 'OTHER', name: 'other', value: 'SHARED.MOBILE.DEVICE_TYPE.OTHER'}
    ];

    vm.$onInit = function() {
        /*
         * OpenVPN and WireGuard are independent mobile VPN modes.
         *
         * A failure while loading OpenVPN state must not prevent the
         * local device and its WireGuard peer metadata from loading.
         * Likewise, device loading must not delay OpenVPN status.
         */
        loadStatus().then(function success() {
            return loadCertificates();
        }).then(
            function success() {
                updateDeviceCertificateState();
            },
            function error(response) {
                logger.error(
                    'Unable to initialize OpenVPN mobile state',
                    response
                );
                vm.vpnHomeCertificates = [];
                updateDeviceCertificateState();
                return $q.resolve();
            }
        );

        loadDevice().then(function success() {
            return loadWireGuardPeer();
        });

        vm.operatingSystemType = getDeviceTypeObject(vm.osTypes, deviceDetector.os);
    };

    function getDeviceTypeObject(types, type) {
        let ret = vm.osTypes[4];
        types.forEach((item) => {
            if (item.name === type) {
                ret = item;
            }
        });
        return ret;
    }

    vm.$postLink = function() {
        $timeout(function() {
            CardService.scrollToCard(CARD_NAME);
        }, 300);
    };

    vm.$onDestroy = function() {
        hideWireGuardQrCode();
    };

    function loadStatus() {
        return VpnHomeService.loadStatus().then(function success(response) {
            vm.vpnHomeStatus = response.data;
            return response.data;
        });
    }

    function loadDevice() {
        return DeviceService.getDevice().then(function success(response) {
            if (angular.isObject(response.data)) {
                vm.device = response.data;
                updateDeviceCertificateState();
            }
            return response;
        });
    }

    function updateDeviceCertificateState() {
        if (!angular.isObject(vm.device)) {
            return;
        }

        vm.device.hasCertificate =
            angular.isArray(vm.vpnHomeCertificates) &&
            vm.vpnHomeCertificates.indexOf(vm.device.id) > -1;
    }

    function loadWireGuardPeer() {
        vm.wireGuardPeer = undefined;

        if (!angular.isObject(vm.device) ||
                !angular.isString(vm.device.id) ||
                vm.device.id.length === 0) {
            return $q.resolve();
        }

        vm.wireGuardPeerLoading = true;

        return WireGuardDashboardService.getPeer(vm.device.id).then(
            function success(response) {
                vm.wireGuardPeer = response.data;
                return response;
            },
            function error(response) {
                if (response.status !== 404) {
                    logger.error(
                        'Unable to load WireGuard peer metadata',
                        response
                    );
                }
                vm.wireGuardPeer = undefined;
                return $q.resolve();
            }
        ).finally(function done() {
            vm.wireGuardPeerLoading = false;
        });
    }

    function goToRecommendedApps() {

    }

    /*
     * Creating a peer is an explicit user action.
     *
     * The client supplies only the authorized local device id. Peer
     * name, id, keys, address and LAN policy are resolved server-side.
     * The create response contains metadata only; client configuration
     * and QR code remain separate explicit secret-bearing actions.
     */
    function createWireGuardPeer() {
        if (!angular.isObject(vm.device) ||
                !angular.isString(vm.device.id) ||
                vm.device.id.length === 0 ||
                angular.isObject(vm.wireGuardPeer) ||
                vm.wireGuardPeerCreating ||
                vm.wireGuardPeerLoading) {
            return;
        }

        vm.wireGuardPeerCreating = true;

        return WireGuardDashboardService.createPeer(
            vm.device.id
        ).then(
            function success(response) {
                if (!response ||
                        !angular.isObject(response.data)) {
                    return $q.reject(
                        new Error(
                            'WireGuard peer create response is invalid.'
                        )
                    );
                }

                vm.wireGuardPeer = response.data;
                return response;
            },
            function error(response) {
                logger.error(
                    'Unable to create WireGuard peer',
                    response
                );
                vm.wireGuardPeer = undefined;
                return $q.resolve();
            }
        ).finally(function done() {
            vm.wireGuardPeerCreating = false;
        });
    }

    /*
     * Angular updates the switch model before invoking ng-change.
     * Therefore the previous persisted UI value is the inverse of the
     * requested boolean. The server response remains authoritative.
     *
     * On failure, restore the previous value so the dashboard never
     * displays a LAN policy that was not successfully persisted.
     */
    function updateWireGuardLanAccess() {
        if (!angular.isObject(vm.device) ||
                !angular.isString(vm.device.id) ||
                vm.device.id.length === 0 ||
                !angular.isObject(vm.wireGuardPeer) ||
                !angular.isDefined(vm.wireGuardPeer.allowLanAccess) ||
                vm.wireGuardLanAccessUpdating ||
                vm.wireGuardPeerDeleting) {
            return;
        }

        const requested =
            vm.wireGuardPeer.allowLanAccess === true;

        const previous =
            !requested;

        vm.wireGuardLanAccessUpdating = true;

        return WireGuardDashboardService.setLanAccess(
            vm.device.id,
            requested
        ).then(
            function success(response) {
                if (!response ||
                        !angular.isObject(response.data) ||
                        typeof response.data.allowLanAccess !== 'boolean') {
                    vm.wireGuardPeer.allowLanAccess = previous;

                    return $q.reject(
                        new Error(
                            'WireGuard LAN access response is invalid.'
                        )
                    );
                }

                vm.wireGuardPeer = response.data;
                return response;
            },
            function error(response) {
                vm.wireGuardPeer.allowLanAccess = previous;

                logger.error(
                    'Unable to update WireGuard LAN access',
                    response
                );

                return $q.resolve();
            }
        ).finally(function done() {
            vm.wireGuardLanAccessUpdating = false;
        });
    }

    /*
     * Peer deletion is destructive and therefore always requires the
     * existing dashboard confirmation dialog. The delete request itself
     * receives only the authorized local device id.
     */
    function confirmDeleteWireGuardPeer(event) {
        if (!angular.isObject(vm.device) ||
                !angular.isString(vm.device.id) ||
                vm.device.id.length === 0 ||
                !angular.isObject(vm.wireGuardPeer) ||
                vm.wireGuardPeerDeleting) {
            return;
        }

        return DialogService.confirmationDialog(
            event,
            'MOBILE.CARD.WIREGUARD.DELETE_CONFIRM_TITLE',
            'MOBILE.CARD.WIREGUARD.DELETE_CONFIRM_TEXT',
            'MOBILE.CARD.WIREGUARD.DELETE_CONFIRM_OK',
            'MOBILE.CARD.WIREGUARD.DELETE_CONFIRM_CANCEL',
            vm.wireGuardPeer.name,
            deleteWireGuardPeer,
            function cancel() {}
        );
    }

    function deleteWireGuardPeer() {
        if (!angular.isObject(vm.device) ||
                !angular.isString(vm.device.id) ||
                vm.device.id.length === 0 ||
                !angular.isObject(vm.wireGuardPeer) ||
                vm.wireGuardPeerDeleting) {
            return $q.resolve();
        }

        vm.wireGuardPeerDeleting = true;

        return WireGuardDashboardService.deletePeer(
            vm.device.id
        ).then(
            function success(response) {
                hideWireGuardQrCode();
                vm.wireGuardPeer = undefined;
                return response;
            },
            function error(response) {
                logger.error(
                    'Unable to delete WireGuard peer',
                    response
                );
                return $q.resolve();
            }
        ).finally(function done() {
            vm.wireGuardPeerDeleting = false;
        });
    }

    function loadCertificates() {
        if (vm.vpnHomeStatus.isRunning) {
            return VpnHomeService.loadCertificates().then(function success(response) {
                vm.vpnHomeCertificates = response.data;
                updateDeviceCertificateState();
                return response;
            });
        } else {
            vm.vpnHomeCertificates = [];
            updateDeviceCertificateState();
            return $q.resolve({data: []});
        }
    }

    function downloadClientConf(device) {
        if (!angular.isString(vm.vpnHomeStatus.host) || vm.vpnHomeStatus.host === '') {
            NotificationService.error('MOBILE.CARD.NOTIFICATION.HOST_MISSING');
        } else {
            vm.isDownloadingConf = true;
            VpnHomeService.generateDownloadUrl(device.id, vm.operatingSystemType.type).then(function success(response) {
                // Sort certificates into dic
                $window.location = response.data;
            }, function error(response) {
                // fail
            }).finally(function done() {
                vm.isDownloadingConf = false;
            });
        }
    }

    /*
     * The rendered WireGuard configuration contains client secrets.
     * Fetch it only after an explicit user action and keep it out of
     * controller state. The temporary object URL is revoked immediately
     * after the browser has received the download click.
     */
    function downloadWireGuardConfig() {
        if (!angular.isObject(vm.device) ||
                !angular.isString(vm.device.id) ||
                vm.device.id.length === 0 ||
                !angular.isObject(vm.wireGuardPeer) ||
                vm.wireGuardPeerDeleting) {
            return;
        }

        vm.isDownloadingWireGuardConfig = true;

        return WireGuardDashboardService.getClientConfig(
            vm.device.id
        ).then(function success(response) {
            const configuration =
                response &&
                response.data &&
                response.data.configuration;

            if (!angular.isString(configuration) ||
                    configuration.length === 0) {
                return $q.reject(
                    new Error('WireGuard client configuration is empty.')
                );
            }

            const blob = new $window.Blob(
                [configuration],
                {
                    type: 'text/plain;charset=utf-8'
                }
            );

            const objectUrl =
                $window.URL.createObjectURL(blob);

            const link =
                $window.document.createElement('a');

            try {
                link.href = objectUrl;
                link.download = 'wireguard.conf';
                link.style.display = 'none';

                $window.document.body.appendChild(link);
                link.click();
                $window.document.body.removeChild(link);
            } finally {
                $window.URL.revokeObjectURL(objectUrl);
            }
        }, function error(response) {
            logger.error(
                'Unable to download WireGuard client configuration',
                response
            );
        }).finally(function done() {
            vm.isDownloadingWireGuardConfig = false;
        });
    }


    /*
     * A WireGuard QR code contains the same client secrets as the
     * downloadable configuration. Load it only after explicit user
     * interaction. Controller state contains only the temporary blob
     * URL, never the binary response itself.
     */
    function showWireGuardQrCode() {
        if (!angular.isObject(vm.device) ||
                !angular.isString(vm.device.id) ||
                vm.device.id.length === 0 ||
                !angular.isObject(vm.wireGuardPeer) ||
                vm.wireGuardPeerDeleting) {
            return;
        }

        hideWireGuardQrCode();
        vm.isLoadingWireGuardQrCode = true;

        return WireGuardDashboardService.getQrCode(
            vm.device.id
        ).then(function success(response) {
            const png = response && response.data;

            if (!(png instanceof ArrayBuffer)) {
                return $q.reject(
                    new Error('WireGuard QR code response is invalid.')
                );
            }

            const blob = new $window.Blob(
                [png],
                {
                    type: 'image/png'
                }
            );

            vm.wireGuardQrCodeUrl =
                $window.URL.createObjectURL(blob);
        }, function error(response) {
            logger.error(
                'Unable to load WireGuard QR code',
                response
            );
            hideWireGuardQrCode();
        }).finally(function done() {
            vm.isLoadingWireGuardQrCode = false;
        });
    }

    function hideWireGuardQrCode() {
        if (angular.isString(vm.wireGuardQrCodeUrl) &&
                vm.wireGuardQrCodeUrl.length > 0) {
            $window.URL.revokeObjectURL(
                vm.wireGuardQrCodeUrl
            );
        }

        vm.wireGuardQrCodeUrl = undefined;
    }
}
