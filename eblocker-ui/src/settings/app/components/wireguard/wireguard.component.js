export default {
    templateUrl: 'app/components/wireguard/wireguard.component.html',
    controllerAs: 'vm',
    controller: WireGuardController
};

function WireGuardController($http) {
    'ngInject';
    var vm = this;

    vm.loading = false;
    vm.toggling = false;
    vm.error = null;
    vm.errorText = '';

    vm.status = {
        iface: 'wg0',
        service: 'unknown',
        wg: 'down',
        peers: 0
    };

    vm.enabled = false;

    vm.$onInit = function () {
        vm.loadStatus();
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
            .catch(function (err) {
                vm.error = err;
                vm.errorText = 'Status konnte nicht geladen werden.';
                syncEnabledFromStatus();
            })
            .finally(function () {
                vm.loading = false;
            });
    };

    vm.toggleEnabled = function () {
        // Zielzustand kommt vom Switch
        var wantEnable = !!vm.enabled;

        vm.toggling = true;
        vm.error = null;
        vm.errorText = '';

        // JSHint: ternary in einer Zeile
        var url = wantEnable ? '/api/adminconsole/wireguard/enable' : '/api/adminconsole/wireguard/disable';

        return $http.post(url, {})
            .then(function () {
                return vm.loadStatus();
            })
            .catch(function (err) {
                vm.error = err;
                vm.errorText = 'Konnte WireGuard nicht schalten.';
                // Switch zurück auf echten Zustand
                syncEnabledFromStatus();
            })
            .finally(function () {
                vm.toggling = false;
            });
    };
}
