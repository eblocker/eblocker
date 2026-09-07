/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License.
 */

export default {
    templateUrl: 'app/components/wireguard/wireguard-status.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(WireGuardService, DeviceService, DialogService,
                    NotificationService, $q, $window) {
    'ngInject';
    'use strict';

    const vm = this;

    // WireGuard normally refreshes handshakes during active traffic.
    // Treat a handshake observed within three minutes as active.
    // Older non-zero handshakes mean the peer has been seen before.
    const ACTIVE_HANDSHAKE_WINDOW_SECONDS = 180;

    initializeBaseUi();
    initializeRoutingUi();
    initializeProvisioningUi();

    vm.$onInit = vm.reload = function() {
        return load();
    };

    function initializeBaseUi() {
        vm.status = {
            enabled: false,
            runtime: {
                iface: null,
                service: null,
                wg: null,
                peers: 0,
                error: null,
                peerTelemetry: []
            }
        };

        vm.endpoint = {
            type: null,
            host: null
        };
        vm.endpointPersisted = false;

        vm.endpointTypes = [
            'FIXED_IP',
            'DYN_DNS',
            'EBLOCKER_DYN_DNS'
        ];

        vm.peers = [];
        vm.devices = [];
        vm.peerRows = [];

        vm.isLoading = false;
        vm.isToggling = false;
        vm.isSavingEndpoint = false;

        vm.interfaceConfig = {value: '-'};
        vm.serviceConfig = {value: '-'};
        vm.wgConfig = {value: '-'};
        vm.peersConfig = {value: 0};
        vm.portConfig = {value: 'UDP 51820'};

        vm.toggleServer = toggleServer;
        vm.saveEndpoint = saveEndpoint;
        vm.endpointHostRequired = endpointHostRequired;
        vm.isEndpointConfigured = function() {
            return vm.endpointPersisted === true;
        };
        vm.formatBytes = formatBytes;
    }

    function initializeProvisioningUi() {
        vm.provisioning = {
            selectedDeviceId: null,
            selectedPeer: null,
            qrUrl: null,
            isCreating: false,
            isLoadingQr: false,
            isDownloading: false,
            isSavingLanAccess: false,
            isDeleting: false,
            isRegenerating: false
        };

        vm.selectProvisioningDevice = selectProvisioningDevice;
        vm.canManageProvisionedPeer = function(peer) {
            if (!angular.isObject(peer) ||
                    !angular.isString(peer.deviceId) ||
                    peer.deviceId.length === 0) {
                return false;
            }

            return vm.devices.some(function(device) {
                return angular.isObject(device) &&
                    device.id === peer.deviceId;
            });
        };
        vm.manageProvisionedPeer = function(peer) {
            if (!vm.canManageProvisionedPeer(peer) ||
                    provisioningBusy(vm.provisioning)) {
                return;
            }

            revokeProvisioningQrUrl();
            vm.provisioning.selectedDeviceId = peer.deviceId;
            synchronizeProvisioningSelection();

            const panel = $window.document.getElementById(
                'wireguard-client-profiles'
            );

            if (angular.isObject(panel) &&
                    angular.isFunction(panel.scrollIntoView)) {
                panel.scrollIntoView();
            }
        };
        vm.createProvisionedPeer = createProvisionedPeer;
        vm.updateProvisioningLanAccess =
            updateProvisioningLanAccess;
        vm.confirmDeleteProvisionedPeer =
            confirmDeleteProvisionedPeer;
        vm.confirmRegenerateProvisionedPeer =
            confirmRegenerateProvisionedPeer;
        vm.showProvisioningQr = showProvisioningQr;
        vm.downloadProvisioningConfig = downloadProvisioningConfig;
        vm.isProvisioningBusy = function() {
            return provisioningBusy(vm.provisioning);
        };

        vm.$onDestroy = function() {
            revokeProvisioningQrUrl();
        };
    }

    function initializeRoutingUi() {
        vm.isSavingRouting = false;

        vm.tunnelModes = [
            'FULL_TUNNEL',
            'LAN_ONLY',
            'CUSTOM'
        ];

        vm.routingEditor = {
            selectedPeerId: null,
            tunnelMode: 'FULL_TUNNEL',
            customAllowedIpsText: '',
            allowLanAccess: false
        };

        vm.selectRoutingPeer = selectRoutingPeer;
        vm.saveRouting = saveRouting;
        vm.customRoutingInputRequired =
            customRoutingInputRequired;
        vm.showLanOnlyWarning = showLanOnlyWarning;
    }

    function load() {
        vm.isLoading = true;

        const statusPromise = WireGuardService.getStatus()
            .then(function(response) {
                vm.status = response.data;
                updateStatusDisplay();
            })
            .catch(function(response) {
                NotificationService.error(
                    'ADMINCONSOLE.WIREGUARD.NOTIFICATION.STATUS_LOAD_FAILED',
                    response
                );
            });

        const peersPromise = WireGuardService.getPeers()
            .then(function(response) {
                vm.peers = angular.isArray(response.data) ?
                    response.data :
                    [];
            })
            .catch(function(response) {
                vm.peers = [];

                NotificationService.error(
                    'ADMINCONSOLE.WIREGUARD.NOTIFICATION.PEERS_LOAD_FAILED',
                    response
                );
            });

        const devicesPromise = DeviceService.getAll()
            .then(function(response) {
                vm.devices = angular.isArray(response.data) ?
                    response.data :
                    [];
            })
            .catch(function(response) {
                vm.devices = [];

                NotificationService.error(
                    'ADMINCONSOLE.WIREGUARD.NOTIFICATION.DEVICES_LOAD_FAILED',
                    response
                );
            });

        const endpointPromise = WireGuardService.getEndpoint()
            .then(function(response) {
                vm.endpoint = response.data || {
                    type: null,
                    host: null
                };
                vm.endpointPersisted =
                    endpointConfigValid(vm.endpoint);
            })
            .catch(function(response) {
                vm.endpointPersisted = false;
                NotificationService.error(
                    'ADMINCONSOLE.WIREGUARD.NOTIFICATION.ENDPOINT_LOAD_FAILED',
                    response
                );
            });

        return $q.all([
            statusPromise,
            peersPromise,
            devicesPromise,
            endpointPromise
        ]).finally(function() {
            buildPeerRows();
            vm.isLoading = false;
        });
    }

    function buildPeerRows() {
        const devicesById = {};
        const telemetryByPublicKey = {};
        const runtime = vm.status.runtime || {};
        const telemetry = angular.isArray(runtime.peerTelemetry) ?
            runtime.peerTelemetry :
            [];

        telemetry.forEach(function(entry) {
            if (angular.isObject(entry) &&
                    angular.isString(entry.publicKey) &&
                    entry.publicKey.length > 0) {

                telemetryByPublicKey[entry.publicKey] = entry;
            }
        });

        vm.devices.forEach(function(device) {
            if (angular.isObject(device) &&
                    angular.isString(device.id)) {

                devicesById[device.id] = device;
            }
        });

        vm.peerRows = vm.peers.map(function(peer) {
            const row = angular.copy(peer);
            const device = angular.isString(peer.deviceId) ?
                devicesById[peer.deviceId] :
                undefined;

            const peerTelemetry =
                angular.isString(peer.publicKey) ?
                    telemetryByPublicKey[peer.publicKey] :
                    undefined;

            applyPeerTelemetry(row, peerTelemetry);
            normalizePeerRouting(row);

            if (!angular.isString(peer.deviceId) ||
                    peer.deviceId.length === 0) {

                row.deviceDisplayName =
                    'ADMINCONSOLE.WIREGUARD.PEERS.UNBOUND';

            } else if (angular.isObject(device)) {
                row.deviceDisplayName =
                    device.displayName ||
                    device.name ||
                    'ADMINCONSOLE.WIREGUARD.PEERS.UNKNOWN_DEVICE';

            } else {
                row.deviceDisplayName =
                    'ADMINCONSOLE.WIREGUARD.PEERS.UNKNOWN_DEVICE';
            }

            return row;
        });

        synchronizeRoutingSelection();
        synchronizeProvisioningSelection();
    }

    function findPeerRowByDeviceId(deviceId) {
        let found;

        vm.peerRows.some(function(peer) {
            if (peer.deviceId === deviceId) {
                found = peer;
                return true;
            }
            return false;
        });

        return found;
    }

    function synchronizeProvisioningSelection() {
        if (!vm.provisioning.selectedDeviceId &&
                vm.devices.length > 0) {
            vm.provisioning.selectedDeviceId = vm.devices[0].id;
        }

        vm.provisioning.selectedPeer =
            findPeerRowByDeviceId(vm.provisioning.selectedDeviceId) || null;

        if (!vm.provisioning.selectedPeer) {
            revokeProvisioningQrUrl();
        }
    }

    function selectProvisioningDevice() {
        revokeProvisioningQrUrl();
        synchronizeProvisioningSelection();
    }

    function createProvisionedPeer() {
        const deviceId = vm.provisioning.selectedDeviceId;

        if (!angular.isString(deviceId) || deviceId.length === 0) {
            return $q.reject('WireGuard target device is required.');
        }

        vm.provisioning.isCreating = true;
        revokeProvisioningQrUrl();

        return WireGuardService.createPeerForDevice(deviceId)
            .then(function(response) {
                const peer = response.data;

                if (angular.isObject(peer)) {
                    vm.peers = vm.peers.filter(function(candidate) {
                        return candidate.deviceId !== deviceId;
                    });
                    vm.peers.push(peer);
                    buildPeerRows();
                }

                NotificationService.info(
                    'ADMINCONSOLE.WIREGUARD.PROVISIONING.NOTIFICATION.CREATED'
                );
                return peer;
            })
            .catch(function(response) {
                NotificationService.error(
                    'ADMINCONSOLE.WIREGUARD.PROVISIONING.NOTIFICATION.CREATE_FAILED',
                    response
                );
                return $q.reject(response);
            })
            .finally(function() {
                vm.provisioning.isCreating = false;
            });
    }

    function updateProvisioningLanAccess() {
        const peer = vm.provisioning.selectedPeer;

        if (!angular.isObject(peer) ||
                vm.provisioning.isSavingLanAccess ||
                vm.provisioning.isDeleting ||
                vm.provisioning.isRegenerating) {
            return $q.resolve();
        }

        const requested = peer.allowLanAccess === true;
        const previous = !requested;

        vm.provisioning.isSavingLanAccess = true;

        return WireGuardService
            .setLanAccess(peer.id, requested)
            .then(function(response) {
                const updated = response.data;

                if (!angular.isObject(updated) ||
                        typeof updated.allowLanAccess !== 'boolean') {
                    return $q.reject(
                        'WireGuard LAN access response is invalid.'
                    );
                }

                vm.peers = replaceProvisionedPeer(
                    vm.peers,
                    updated
                );
                buildPeerRows();

                NotificationService.info(
                    'ADMINCONSOLE.WIREGUARD.PROVISIONING.NOTIFICATION.LAN_SAVED'
                );

                return updated;
            })
            .catch(function(response) {
                if (angular.isObject(
                    vm.provisioning.selectedPeer
                )) {
                    vm.provisioning.selectedPeer.allowLanAccess =
                        previous;
                }

                NotificationService.error(
                    'ADMINCONSOLE.WIREGUARD.PROVISIONING.NOTIFICATION.LAN_FAILED',
                    response
                );

                return $q.reject(response);
            })
            .finally(function() {
                vm.provisioning.isSavingLanAccess = false;
            });
    }

    function confirmDeleteProvisionedPeer(event) {
        const peer = vm.provisioning.selectedPeer;

        if (!angular.isObject(peer) ||
                provisioningBusy(vm.provisioning)) {
            return;
        }

        return DialogService.confirmationDialog(
            event,
            'ADMINCONSOLE.WIREGUARD.PROVISIONING.DELETE_CONFIRM_TITLE',
            'ADMINCONSOLE.WIREGUARD.PROVISIONING.DELETE_CONFIRM_TEXT',
            'ADMINCONSOLE.WIREGUARD.PROVISIONING.DELETE_CONFIRM_OK',
            'ADMINCONSOLE.WIREGUARD.PROVISIONING.DELETE_CONFIRM_CANCEL',
            peer.name,
            deleteProvisionedPeer,
            function cancel() {}
        );
    }

    function deleteProvisionedPeer() {
        const peer = vm.provisioning.selectedPeer;

        if (!angular.isObject(peer) ||
                vm.provisioning.isDeleting ||
                vm.provisioning.isRegenerating) {
            return $q.resolve();
        }

        vm.provisioning.isDeleting = true;
        revokeProvisioningQrUrl();

        return WireGuardService.deletePeer(peer.id)
            .then(function(response) {
                vm.peers = removeProvisionedPeer(
                    vm.peers,
                    peer
                );
                buildPeerRows();

                NotificationService.info(
                    'ADMINCONSOLE.WIREGUARD.PROVISIONING.NOTIFICATION.DELETED'
                );

                return response;
            })
            .catch(function(response) {
                NotificationService.error(
                    'ADMINCONSOLE.WIREGUARD.PROVISIONING.NOTIFICATION.DELETE_FAILED',
                    response
                );

                return $q.reject(response);
            })
            .finally(function() {
                vm.provisioning.isDeleting = false;
            });
    }

    function confirmRegenerateProvisionedPeer(event) {
        const peer = vm.provisioning.selectedPeer;

        if (!angular.isObject(peer) ||
                provisioningBusy(vm.provisioning)) {
            return;
        }

        return DialogService.confirmationDialog(
            event,
            'ADMINCONSOLE.WIREGUARD.PROVISIONING.REGENERATE_CONFIRM_TITLE',
            'ADMINCONSOLE.WIREGUARD.PROVISIONING.REGENERATE_CONFIRM_TEXT',
            'ADMINCONSOLE.WIREGUARD.PROVISIONING.REGENERATE_CONFIRM_OK',
            'ADMINCONSOLE.WIREGUARD.PROVISIONING.REGENERATE_CONFIRM_CANCEL',
            peer.name,
            regenerateProvisionedPeer,
            function cancel() {}
        );
    }

    function regenerateProvisionedPeer() {
        const peer = vm.provisioning.selectedPeer;
        const deviceId = vm.provisioning.selectedDeviceId;
        let deleted = false;

        if (!angular.isObject(peer) ||
                !angular.isString(deviceId) ||
                deviceId.length === 0 ||
                vm.provisioning.isDeleting ||
                vm.provisioning.isRegenerating) {
            return $q.resolve();
        }

        vm.provisioning.isRegenerating = true;
        revokeProvisioningQrUrl();

        return WireGuardService.deletePeer(peer.id)
            .then(function() {
                deleted = true;
                vm.peers = removeProvisionedPeer(
                    vm.peers,
                    peer
                );
                buildPeerRows();

                return WireGuardService
                    .createPeerForDevice(deviceId);
            })
            .then(function(response) {
                const created = response.data;

                if (!angular.isObject(created)) {
                    return $q.reject(
                        'WireGuard regenerated peer response is invalid.'
                    );
                }

                vm.peers = replaceProvisionedPeer(
                    vm.peers,
                    created
                );
                buildPeerRows();

                NotificationService.info(
                    'ADMINCONSOLE.WIREGUARD.PROVISIONING.NOTIFICATION.REGENERATED'
                );

                return created;
            })
            .catch(function(response) {
                const key = deleted ?
                    'ADMINCONSOLE.WIREGUARD.PROVISIONING.NOTIFICATION.REGENERATE_CREATE_FAILED' :
                    'ADMINCONSOLE.WIREGUARD.PROVISIONING.NOTIFICATION.REGENERATE_FAILED';

                NotificationService.error(
                    key,
                    response
                );

                return $q.reject(response);
            })
            .finally(function() {
                vm.provisioning.isRegenerating = false;
            });
    }

    function showProvisioningQr() {
        const peer = vm.provisioning.selectedPeer;

        if (!angular.isObject(peer)) {
            return $q.reject('WireGuard peer is required.');
        }

        if (!vm.isEndpointConfigured()) {
            NotificationService.error(
                'ADMINCONSOLE.WIREGUARD.PROVISIONING.NOTIFICATION.ENDPOINT_REQUIRED'
            );
            return $q.reject('WireGuard endpoint is required.');
        }

        vm.provisioning.isLoadingQr = true;

        return WireGuardService.getQrCode(peer.id)
            .then(function(response) {
                revokeProvisioningQrUrl();

                const blob = new $window.Blob(
                    [response.data],
                    {type: 'image/png'}
                );

                vm.provisioning.qrUrl =
                    $window.URL.createObjectURL(blob);

                return vm.provisioning.qrUrl;
            })
            .catch(function(response) {
                NotificationService.error(
                    'ADMINCONSOLE.WIREGUARD.PROVISIONING.NOTIFICATION.QR_FAILED',
                    response
                );
                return $q.reject(response);
            })
            .finally(function() {
                vm.provisioning.isLoadingQr = false;
            });
    }

    function downloadProvisioningConfig() {
        const peer = vm.provisioning.selectedPeer;

        if (!angular.isObject(peer)) {
            return $q.reject('WireGuard peer is required.');
        }

        if (!vm.isEndpointConfigured()) {
            NotificationService.error(
                'ADMINCONSOLE.WIREGUARD.PROVISIONING.NOTIFICATION.ENDPOINT_REQUIRED'
            );
            return $q.reject('WireGuard endpoint is required.');
        }

        vm.provisioning.isDownloading = true;

        return WireGuardService.getClientConfig(peer.id)
            .then(function(response) {
                const config = response.data &&
                    response.data.configuration;

                if (!angular.isString(config) || config.length === 0) {
                    return $q.reject(
                        'WireGuard client configuration is empty.'
                    );
                }

                const blob = new $window.Blob(
                    [config],
                    {type: 'text/plain;charset=utf-8'}
                );
                const url = $window.URL.createObjectURL(blob);
                const link = $window.document.createElement('a');

                link.href = url;
                link.download = buildProvisioningFileName(peer);
                link.style.display = 'none';
                $window.document.body.appendChild(link);

                try {
                    link.click();
                } finally {
                    $window.document.body.removeChild(link);
                    $window.URL.revokeObjectURL(url);
                }

                return true;
            })
            .catch(function(response) {
                NotificationService.error(
                    'ADMINCONSOLE.WIREGUARD.PROVISIONING.NOTIFICATION.CONFIG_FAILED',
                    response
                );
                return $q.reject(response);
            })
            .finally(function() {
                vm.provisioning.isDownloading = false;
            });
    }

    function buildProvisioningFileName(peer) {
        const device = vm.devices.filter(function(candidate) {
            return candidate.id === peer.deviceId;
        })[0];

        let name = angular.isObject(device) ?
            (device.displayName || device.name) :
            peer.name;

        if (!angular.isString(name) || name.length === 0) {
            name = 'client';
        }

        name = name
            .replace(/[^A-Za-z0-9._-]+/g, '_')
            .replace(/^_+|_+$/g, '');

        if (name.length === 0) {
            name = 'client';
        }

        return 'WireGuard-' + name + '.conf';
    }

    function revokeProvisioningQrUrl() {
        if (angular.isString(vm.provisioning.qrUrl) &&
                vm.provisioning.qrUrl.length > 0) {
            $window.URL.revokeObjectURL(vm.provisioning.qrUrl);
        }

        vm.provisioning.qrUrl = null;
    }

    function normalizePeerRouting(peer) {
        if (vm.tunnelModes.indexOf(peer.tunnelMode) === -1) {
            peer.tunnelMode = 'FULL_TUNNEL';
        }

        if (!angular.isArray(peer.customAllowedIps)) {
            peer.customAllowedIps = [];
        } else {
            peer.customAllowedIps = peer.customAllowedIps.slice();
        }
    }

    function synchronizeRoutingSelection() {
        if (vm.peerRows.length === 0) {
            vm.routingEditor.selectedPeerId = null;
            vm.routingEditor.tunnelMode = 'FULL_TUNNEL';
            vm.routingEditor.customAllowedIpsText = '';
            vm.routingEditor.allowLanAccess = false;
            return;
        }

        if (!findPeerRowById(vm.routingEditor.selectedPeerId)) {
            vm.routingEditor.selectedPeerId = vm.peerRows[0].id;
        }

        selectRoutingPeer();
    }

    function findPeerRowById(peerId) {
        let found;

        vm.peerRows.some(function(peer) {
            if (peer.id === peerId) {
                found = peer;
                return true;
            }
            return false;
        });

        return found;
    }

    function selectRoutingPeer() {
        const peer = findPeerRowById(vm.routingEditor.selectedPeerId);

        if (!angular.isObject(peer)) {
            return;
        }

        vm.routingEditor.tunnelMode = peer.tunnelMode;
        vm.routingEditor.customAllowedIpsText =
            peer.customAllowedIps.join('\n');
        vm.routingEditor.allowLanAccess =
            peer.allowLanAccess === true;
    }

    function parseCustomAllowedIps(text) {
        if (!angular.isString(text)) {
            return [];
        }

        return text
            .split(/\r?\n/)
            .map(function(value) {
                return value.trim();
            })
            .filter(function(value) {
                return value.length > 0;
            });
    }

    function customRoutingInputRequired() {
        return vm.routingEditor.tunnelMode === 'CUSTOM' &&
            parseCustomAllowedIps(
                vm.routingEditor.customAllowedIpsText
            ).length === 0;
    }

    function showLanOnlyWarning() {
        return vm.routingEditor.tunnelMode === 'LAN_ONLY' &&
            vm.routingEditor.allowLanAccess !== true;
    }

    function saveRouting() {
        const peer = findPeerRowById(
            vm.routingEditor.selectedPeerId
        );

        if (!angular.isObject(peer)) {
            return $q.reject('WireGuard peer is required.');
        }

        if (customRoutingInputRequired()) {
            return $q.reject(
                'At least one CUSTOM route is required.'
            );
        }

        const previous = {
            tunnelMode: peer.tunnelMode,
            customAllowedIps: peer.customAllowedIps.slice()
        };

        const config = {
            tunnelMode: vm.routingEditor.tunnelMode,
            customAllowedIps:
                vm.routingEditor.tunnelMode === 'CUSTOM' ?
                    parseCustomAllowedIps(
                        vm.routingEditor.customAllowedIpsText
                    ) :
                    []
        };

        vm.isSavingRouting = true;

        return WireGuardService
            .setRouting(peer.id, config)
            .then(function(response) {
                const updated = response.data || {};
                const source = vm.peers.filter(function(candidate) {
                    return candidate.id === peer.id;
                })[0];

                if (angular.isObject(source)) {
                    source.tunnelMode =
                        updated.tunnelMode || config.tunnelMode;
                    source.customAllowedIps =
                        angular.isArray(updated.customAllowedIps) ?
                            updated.customAllowedIps.slice() :
                            config.customAllowedIps.slice();
                }

                buildPeerRows();

                NotificationService.info(
                    'ADMINCONSOLE.WIREGUARD.ROUTING.NOTIFICATION.SAVED'
                );

                return updated;
            })
            .catch(function(response) {
                vm.routingEditor.tunnelMode =
                    previous.tunnelMode;
                vm.routingEditor.customAllowedIpsText =
                    previous.customAllowedIps.join('\n');

                NotificationService.error(
                    'ADMINCONSOLE.WIREGUARD.ROUTING.NOTIFICATION.SAVE_FAILED',
                    response
                );

                return $q.reject(response);
            })
            .finally(function() {
                vm.isSavingRouting = false;
            });
    }

    function applyPeerTelemetry(row, telemetry) {
        row.activityState = null;
        row.activityTranslationKey =
            'ADMINCONSOLE.WIREGUARD.PEERS.ACTIVITY.UNAVAILABLE';
        row.latestHandshakeEpochSeconds = null;
        row.latestHandshakeMillis = null;
        row.rxBytes = null;
        row.txBytes = null;

        // No runtime entry means telemetry is unavailable. Do not call
        // this "never connected", because the interface may be down or
        // runtime state may have been recreated.
        if (!angular.isObject(telemetry)) {
            return;
        }

        row.latestHandshakeEpochSeconds =
            normalizeNonNegativeNumber(
                telemetry.latestHandshakeEpochSeconds
            );

        row.rxBytes =
            normalizeNonNegativeNumber(
                telemetry.rxBytes
            );

        row.txBytes =
            normalizeNonNegativeNumber(
                telemetry.txBytes
            );

        if (row.latestHandshakeEpochSeconds === null) {
            return;
        }

        if (row.latestHandshakeEpochSeconds === 0) {
            row.activityState = 'NEVER_CONNECTED';
            row.activityTranslationKey =
                'ADMINCONSOLE.WIREGUARD.PEERS.ACTIVITY.NEVER_CONNECTED';
            return;
        }

        row.latestHandshakeMillis =
            row.latestHandshakeEpochSeconds * 1000;

        const nowEpochSeconds =
            Math.floor(Date.now() / 1000);

        const ageSeconds =
            Math.max(
                0,
                nowEpochSeconds -
                    row.latestHandshakeEpochSeconds
            );

        row.activityState =
            ageSeconds <= ACTIVE_HANDSHAKE_WINDOW_SECONDS ?
                'ACTIVE' :
                'RECENTLY_SEEN';

        row.activityTranslationKey =
            'ADMINCONSOLE.WIREGUARD.PEERS.ACTIVITY.' +
            row.activityState;
    }

    function formatBytes(value) {
        if (!angular.isNumber(value) ||
                !isFinite(value) ||
                value < 0) {

            return '-';
        }

        if (value < 1024) {
            return value + ' B';
        }

        const units = [
            'KiB',
            'MiB',
            'GiB',
            'TiB'
        ];

        let converted = value;
        let unitIndex = -1;

        do {
            converted = converted / 1024;
            unitIndex++;
        } while (
            converted >= 1024 &&
            unitIndex < units.length - 1
        );

        const precision =
            converted >= 10 ?
                0 :
                1;

        return converted
            .toFixed(precision)
            .replace(/\.0$/, '') +
            ' ' +
            units[unitIndex];
    }

    function normalizeNonNegativeNumber(value) {
        if (!angular.isNumber(value) ||
                !isFinite(value) ||
                value < 0) {

            return null;
        }

        return value;
    }

    function toggleServer() {
        vm.isToggling = true;

        const action = vm.status.enabled ?
            WireGuardService.enable :
            WireGuardService.disable;

        action()
            .then(function(response) {
                vm.status = response.data;
                updateStatusDisplay();
            })
            .catch(function(response) {
                vm.status.enabled = !vm.status.enabled;

                NotificationService.error(
                    'ADMINCONSOLE.WIREGUARD.NOTIFICATION.STATUS_CHANGE_FAILED',
                    response
                );
            })
            .finally(function() {
                vm.isToggling = false;
            });
    }

    function updateStatusDisplay() {
        const runtime = vm.status.runtime || {};

        vm.interfaceConfig.value = runtime.iface || '-';
        vm.serviceConfig.value = runtime.service || '-';
        vm.wgConfig.value = runtime.wg || '-';
        vm.peersConfig.value = angular.isNumber(runtime.peers) ?
            runtime.peers :
            0;
    }

    function endpointHostRequired() {
        return vm.endpoint.type === 'FIXED_IP' ||
            vm.endpoint.type === 'DYN_DNS';
    }

    function endpointConfigValid(endpoint) {
        if (!angular.isObject(endpoint) ||
                !angular.isString(endpoint.type)) {
            return false;
        }

        if (endpoint.type === 'EBLOCKER_DYN_DNS') {
            return true;
        }

        if (endpoint.type !== 'FIXED_IP' &&
                endpoint.type !== 'DYN_DNS') {
            return false;
        }

        return angular.isString(endpoint.host) &&
            endpoint.host.trim().length > 0;
    }

    function saveEndpoint() {
        vm.isSavingEndpoint = true;

        const config = {
            type: vm.endpoint.type,
            host: endpointHostRequired() ?
                vm.endpoint.host :
                null
        };

        WireGuardService.setEndpoint(config)
            .then(function(response) {
                vm.endpoint = response.data;
                vm.endpointPersisted =
                    endpointConfigValid(vm.endpoint);

                NotificationService.info(
                    'ADMINCONSOLE.WIREGUARD.NOTIFICATION.ENDPOINT_SAVED'
                );
            })
            .catch(function(response) {
                NotificationService.error(
                    'ADMINCONSOLE.WIREGUARD.NOTIFICATION.ENDPOINT_SAVE_FAILED',
                    response
                );
            })
            .finally(function() {
                vm.isSavingEndpoint = false;
            });
    }
}

function provisioningBusy(provisioning) {
    return provisioning.isCreating ||
        provisioning.isLoadingQr ||
        provisioning.isDownloading ||
        provisioning.isSavingLanAccess ||
        provisioning.isDeleting ||
        provisioning.isRegenerating;
}

function replaceProvisionedPeer(peers, peer) {
    if (!angular.isObject(peer)) {
        return peers;
    }

    const updatedPeers = peers.filter(function(candidate) {
        const sameId = candidate.id === peer.id;
        const sameDevice =
            angular.isString(peer.deviceId) &&
            candidate.deviceId === peer.deviceId;

        return !sameId && !sameDevice;
    });

    updatedPeers.push(peer);
    return updatedPeers;
}

function removeProvisionedPeer(peers, peer) {
    if (!angular.isObject(peer)) {
        return peers;
    }

    return peers.filter(function(candidate) {
        return candidate.id !== peer.id;
    });
}
