/* global angular */

export default {
    templateUrl: 'app/components/wireguard/wireguard.component.html',
    controllerAs: 'vm',
    controller: WireGuardController
};

function WireGuardController($http, $mdDialog) {
    'ngInject';
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

    vm.$onInit = function () {
        vm.loadStatus();
        vm.loadConfig();
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
        var url = wantEnable ? '/api/adminconsole/wireguard/enable'
                             : '/api/adminconsole/wireguard/disable';

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

    // === Konfigurationsdialog ===
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

    function copyConfig(cfg) {
        if (angular && angular.copy) {
            return angular.copy(cfg);
        }
        return JSON.parse(JSON.stringify(cfg));
    }

    function getConfigDialogTemplate() {
        var tpl = [
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
            '        Dieses Subnetz ist fest vorgegeben',
            '        und kann nicht geändert werden.',
            '      </div>',

            '      <md-checkbox ng-model="dlg.data.allowLanAccess">',
            '        Zugriff auf internes LAN erlauben (Clients können Geräte im lokalen Netzwerk erreichen)',
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
        ];

        return tpl.join('\n');
    }
}
