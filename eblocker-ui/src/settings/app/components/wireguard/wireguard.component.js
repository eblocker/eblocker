/* global angular */

export default {
    templateUrl: 'app/components/wireguard/wireguard.component.html',
    controllerAs: 'vm',
    controller: WireGuardController
};

WireGuardController.$inject = ['$http', '$mdDialog'];
function WireGuardController($http, $mdDialog) {
    var vm = this;

    // === WireGuard Subnetz ist FIX (wie OpenVPN) ===
    vm.WG_NETWORK_CIDR = '10.13.13.0/24';
    vm.WG_SERVER_IP_CIDR = '10.13.13.1/24';

    vm.loading = false;
    vm.toggling = false;
    vm.error = null;
    vm.errorText = '';

    vm.status = {
        wg: 'down',
        peers: 0
    };

    vm.enabled = false;

    vm.config = {
        externalHost: '',
        listenPort: 51820,
        allowLanAccess: true,
        portForwardMode: 'manual',
        portForwardConfirmed: false
    };

    vm.peers = [];

    vm.$onInit = function () {
        vm.loadStatus();
        vm.loadConfig();
        vm.loadPeers();
    };

    function syncEnabledFromStatus() {
        vm.enabled = (vm.status && vm.status.wg === 'up');
    }

    vm.loadStatus = function () {
        vm.loading = true;
        vm.error = null;
        vm.errorText = '';

        return $http.get('/api/adminconsole/wireguard/status')
            .then(function (res) {
                if (res && res.data) {
                    vm.status = res.data;
                    syncEnabledFromStatus();
                }
            })
            .catch(function () {
                vm.error = true;
                vm.errorText = 'Status konnte nicht geladen werden.';
                syncEnabledFromStatus();
            })
            .finally(function () {
                vm.loading = false;
            });
    };

    vm.loadConfig = function () {
        return $http.get('/api/adminconsole/wireguard/config')
            .then(function (res) {
                if (res && res.data) {
                    vm.config = res.data;
                }
            })
            .catch(function () {
                // Config ist optional – Statusseite soll trotzdem laufen
            });
    };

    vm.toggleEnabled = function () {
        var wantEnable = !!vm.enabled;
        var url;

        if (wantEnable) {
            url = '/api/adminconsole/wireguard/enable';
        } else {
            url = '/api/adminconsole/wireguard/disable';
        }

        vm.toggling = true;
        vm.error = null;
        vm.errorText = '';

        return $http.post(url, {})
            .then(function () {
                return vm.loadStatus();
            })
            .catch(function () {
                vm.error = true;
                vm.errorText = 'Konnte WireGuard nicht schalten.';
                syncEnabledFromStatus();
            })
            .finally(function () {
                vm.toggling = false;
            });
    };

    // =========================
    // PEERS
    // =========================

    vm.loadPeers = function () {
        return $http.get('/api/adminconsole/wireguard/peers')
            .then(function (res) {
                if (res && res.data && res.data.peers) {
                    vm.peers = res.data.peers;
                } else {
                    vm.peers = [];
                }
            })
            .catch(function () {
                vm.peers = [];
            });
    };

    vm.createPeerDialog = function (ev) {
        return $mdDialog.show({
            targetEvent: ev,
            clickOutsideToClose: true,
            escapeToClose: true,
            template: getCreatePeerDialogTemplate(),
            controllerAs: 'dlg',
            controller: CreatePeerDialogController
        }).then(function (peerName) {
            var name = (peerName || '').trim();

            if (!name) {
                return null;
            }

            return $http.post(
                '/api/adminconsole/wireguard/peers',
                name,
                { headers: { 'Content-Type': 'text/plain' } }
            ).then(function () {
                return vm.loadPeers();
            });
        });
    };

    function CreatePeerDialogController($mdDialog) {
        var dlg = this;
        dlg.name = '';

        dlg.cancel = function () {
            $mdDialog.cancel();
        };

        dlg.save = function () {
            $mdDialog.hide(dlg.name);
        };
    }
    CreatePeerDialogController.$inject = ['$mdDialog'];

    vm.showPeerConfig = function (ev, peer) {
        if (!peer || !peer.id) {
            return;
        }

        var url = '/api/adminconsole/wireguard/peers/' +
            encodeURIComponent(peer.id) + '/config';

        return $http.get(url, { responseType: 'text' })
            .then(function (res) {
                var cfg = (res && res.data) ? res.data : '';
                return showTextDialog(ev, 'Konfiguration: ' + peer.name, cfg);
            })
            .catch(function () {
                return showTextDialog(
                    ev,
                    'Fehler',
                    'Konfiguration konnte nicht geladen werden.'
                );
            });
    };

    vm.deletePeer = function (ev, peer) {
        if (!peer || !peer.id) {
            return;
        }

        var confirm = $mdDialog.confirm()
            .title('Peer löschen?')
            .textContent('Soll "' + peer.name + '" wirklich gelöscht werden?')
            .ariaLabel('Peer löschen')
            .targetEvent(ev)
            .ok('LÖSCHEN')
            .cancel('ABBRECHEN');

        return $mdDialog.show(confirm).then(function () {
            var url = '/api/adminconsole/wireguard/peers/' +
                encodeURIComponent(peer.id);

            return $http.delete(url).then(function () {
                return vm.loadPeers();
            });
        });
    };

    vm.showPeerQr = function (ev, peer) {
        if (!peer || !peer.id) {
            return;
        }

        var url = '/api/adminconsole/wireguard/peers/' +
            encodeURIComponent(peer.id) + '/qrcode';

        return $http.get(url, { responseType: 'arraybuffer' })
            .then(function (res) {
                var bytes = new Uint8Array(res.data);
                var binary = '';
                var i;

                for (i = 0; i < bytes.byteLength; i++) {
                    binary += String.fromCharCode(bytes[i]);
                }

                var b64 = window.btoa(binary);
                var dataUrl = 'data:image/png;base64,' + b64;

                return $mdDialog.show({
                    targetEvent: ev,
                    clickOutsideToClose: true,
                    escapeToClose: true,
                    controllerAs: 'dlg',
                    controller: PeerQrDialogController,
                    template: getPeerQrDialogTemplate(peer, dataUrl)
                });
            })
            .catch(function () {
                return showTextDialog(
                    ev,
                    'Fehler',
                    'QR-Code konnte nicht geladen werden.'
                );
            });
    };

    function PeerQrDialogController($mdDialog) {
        var dlg = this;

        dlg.close = function () {
            $mdDialog.hide();
        };
    }
    PeerQrDialogController.$inject = ['$mdDialog'];

    function getPeerQrDialogTemplate(peer, dataUrl) {
        return [
            '<md-dialog aria-label="QR" style="min-width:420px; max-width:520px;">',

            '  <md-dialog-content style="padding:18px 22px 6px 22px;">',

            '    <h2 class="md-title" style="margin-top:0;">',
            '      QR: ' + escapeHtml(peer && peer.name ? peer.name : ''),
            '    </h2>',

            '    <div layout="row" layout-align="center center"',
            '         style="padding:10px 0 6px 0;">',

            '      <img ng-src="' + dataUrl + '"',
            '           style="width:360px; height:360px;',
            '                  border:1px solid rgba(0,0,0,0.12);',
            '                  border-radius:4px;" />',

            '    </div>',

            '  </md-dialog-content>',

            '  <md-dialog-actions layout="row" layout-align="end center"',
            '                     style="padding:10px 18px 16px 18px;">',
            '    <md-button class="md-raised md-primary" ng-click="dlg.close()">OK</md-button>',
            '  </md-dialog-actions>',

            '</md-dialog>'
        ].join('\n');
    }

    function showTextDialog(ev, title, text) {
        return $mdDialog.show({
            targetEvent: ev,
            clickOutsideToClose: true,
            escapeToClose: true,
            template: getTextDialogTemplate(title, text),
            controllerAs: 'dlg',
            controller: TextDialogController
        });
    }

    function TextDialogController($mdDialog) {
        var dlg = this;
        dlg.close = function () {
            $mdDialog.hide();
        };
    }
    TextDialogController.$inject = ['$mdDialog'];

    // =========================
    // CONFIG DIALOG
    // =========================

    vm.openConfigDialog = function (ev) {
        var tmp = copyConfig(vm.config);

        return $mdDialog.show({
            targetEvent: ev,
            clickOutsideToClose: true,
            escapeToClose: true,
            template: getConfigDialogTemplate(),
            controllerAs: 'dlg',
            controller: ConfigDialogController,
            locals: {
                cfg: tmp,
                wgNet: vm.WG_NETWORK_CIDR,
                wgSrv: vm.WG_SERVER_IP_CIDR
            }
        }).then(function (newConfig) {
            if (!newConfig) {
                return;
            }

            return $http.post('/api/adminconsole/wireguard/config', newConfig)
                .then(function (res) {
                    if (res && res.data && res.data.ok === true) {
                        vm.config = newConfig;
                    } else {
                        vm.error = true;
                        vm.errorText = 'Konfiguration konnte nicht gespeichert werden.';
                    }
                })
                .catch(function () {
                    vm.error = true;
                    vm.errorText = 'Konfiguration konnte nicht gespeichert werden.';
                });
        });
    };

    function ConfigDialogController($mdDialog, cfg, wgNet, wgSrv) {
        var dlg = this;

        dlg.data = cfg;
        dlg.wgNetwork = wgNet;
        dlg.wgServer = wgSrv;

        dlg.cancel = function () {
            $mdDialog.cancel();
        };

        dlg.save = function () {
            $mdDialog.hide(dlg.data);
        };
    }
    ConfigDialogController.$inject = ['$mdDialog', 'cfg', 'wgNet', 'wgSrv'];

    function copyConfig(cfg) {
        if (angular && angular.copy) {
            return angular.copy(cfg);
        }
        return JSON.parse(JSON.stringify(cfg));
    }

    function getCreatePeerDialogTemplate() {
        return [
            '<md-dialog aria-label="Neuer Peer" style="min-width:420px; max-width:620px;">',
            '  <md-dialog-content style="padding:18px 22px 6px 22px;">',
            '    <h2 class="md-title" style="margin-top:0;">Neuer Peer</h2>',
            '    <md-input-container class="md-block">',
            '      <label>Name</label>',
            '      <input ng-model="dlg.name" placeholder="z.B. Holger-Handy">',
            '    </md-input-container>',
            '  </md-dialog-content>',
            '  <md-dialog-actions layout="row" layout-align="end center"',
            '                     style="padding:10px 18px 16px 18px;">',
            '    <md-button ng-click="dlg.cancel()">ABBRECHEN</md-button>',
            '    <md-button class="md-raised md-primary"',
            '               ng-click="dlg.save()"',
            '               ng-disabled="!dlg.name">',
            '      ERSTELLEN',
            '    </md-button>',
            '  </md-dialog-actions>',
            '</md-dialog>'
        ].join('\n');
    }

    function getTextDialogTemplate(title, text) {
        var safeTitle = title || '';
        var safeText = text || '';

        return [
            '<md-dialog aria-label="Text" style="min-width:720px; max-width:900px;">',
            '  <md-dialog-content style="padding:18px 22px 6px 22px;">',
            '    <h2 class="md-title" style="margin-top:0;">' + escapeHtml(safeTitle) + '</h2>',
            '    <md-input-container class="md-block">',
            '      <textarea rows="12" style="font-family: monospace;" readonly>',
            escapeHtml(safeText),
            '      </textarea>',
            '    </md-input-container>',
            '  </md-dialog-content>',
            '  <md-dialog-actions layout="row" layout-align="end center"',
            '                     style="padding:10px 18px 16px 18px;">',
            '    <md-button class="md-raised md-primary" ng-click="dlg.close()">OK</md-button>',
            '  </md-dialog-actions>',
            '</md-dialog>'
        ].join('\n');
    }

    function escapeHtml(s) {
        return String(s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
    }

    function getConfigDialogTemplate() {
        return [
            '<md-dialog aria-label="WireGuard Einstellungen" style="min-width:520px; max-width:720px;">',
            '  <md-dialog-content style="padding:18px 22px 6px 22px;">',
            '    <h2 class="md-title" style="margin-top:0;">WireGuard Einstellungen</h2>',

            '    <div layout="column" layout-gap="14">',

            '      <md-input-container class="md-block">',
            '        <label>Externe IP-Adresse oder DNS-Name</label>',
            '        <input ng-model="dlg.data.externalHost" placeholder="z.B. example.dynv6.net">',
            '      </md-input-container>',

            '      <md-input-container class="md-block">',
            '        <label>UDP Port</label>',
            '        <input type="number" min="1" max="65535" ng-model="dlg.data.listenPort">',
            '      </md-input-container>',

            '      <div style="opacity:0.8; font-size:13px;">WireGuard Subnetz (fest)</div>',
            '      <div style="padding:8px 10px; border:1px solid rgba(0,0,0,0.12); border-radius:4px;">',
            '        <div><strong>Netz:</strong> {{dlg.wgNetwork}}</div>',
            '        <div style="opacity:0.8;"><strong>Server:</strong> {{dlg.wgServer}}</div>',
            '      </div>',
            '      <div style="opacity:0.65; font-size:12px;">',
            '        Dieses Subnetz ist fest vorgegeben und kann nicht geändert werden.',
            '      </div>',

            '      <md-checkbox ng-model="dlg.data.allowLanAccess">',
            '        Zugriff auf internes LAN erlauben',
            '        (Clients können Geräte im lokalen Netzwerk erreichen)',
            '      </md-checkbox>',

            '      <md-divider></md-divider>',

            '      <div style="opacity:0.8; font-size:13px;">Portweiterleitung</div>',
            '      <md-radio-group ng-model="dlg.data.portForwardMode">',
            '        <md-radio-button value="manual">',
            '          Ich werde die Ports selbst zuweisen.',
            '        </md-radio-button>',
            '        <md-radio-button value="upnp" ng-disabled="true">',
            '          Ports automatisch per UPnP zuweisen (später)',
            '        </md-radio-button>',
            '      </md-radio-group>',

            '      <md-checkbox ng-model="dlg.data.portForwardConfirmed">',
            '        Ich habe den UDP Port in meinem Router weitergeleitet.',
            '      </md-checkbox>',

            '    </div>',
            '  </md-dialog-content>',

            '  <md-dialog-actions layout="row" layout-align="space-between center"',
            '                     style="padding:10px 18px 16px 18px;">',

            '    <span ng-if="!dlg.data.portForwardConfirmed"',
            '          style="opacity:0.65; font-size:12px;">',
            '      Bitte bestätigen Sie die Portweiterleitung im Router.',
            '    </span>',

            '    <div>',
            '      <md-button ng-click="dlg.cancel()">ABBRECHEN</md-button>',
            '      <md-button class="md-raised md-primary"',
            '                 ng-click="dlg.save()"',
            '                 ng-disabled="!dlg.data.portForwardConfirmed">',
            '        SPEICHERN',
            '      </md-button>',
            '    </div>',

            '  </md-dialog-actions>',
            '</md-dialog>'
        ].join('\n');
    }
}
