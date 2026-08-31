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

function Controller(WireGuardService, DeviceService,
                    NotificationService, $q) {
    'ngInject';
    'use strict';

    const vm = this;

    // WireGuard normally refreshes handshakes during active traffic.
    // Treat a handshake observed within three minutes as active.
    // Older non-zero handshakes mean the peer has been seen before.
    const ACTIVE_HANDSHAKE_WINDOW_SECONDS = 180;

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
    vm.formatBytes = formatBytes;

    vm.$onInit = function() {
        load();
    };

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
            })
            .catch(function(response) {
                NotificationService.error(
                    'ADMINCONSOLE.WIREGUARD.NOTIFICATION.ENDPOINT_LOAD_FAILED',
                    response
                );
            });

        $q.all([
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
