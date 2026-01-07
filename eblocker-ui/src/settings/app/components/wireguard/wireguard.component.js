export default {
    templateUrl: 'app/components/wireguard/wireguard.component.html',
    controllerAs: 'vm',
    controller: function WireGuardController($http) {
        'ngInject';
        const vm = this;

        vm.loading = false;
        vm.error = null;

        vm.status = {
            iface: 'wg0',
            service: 'unknown',
            wg: 'down',
            peers: 0
        };

        vm.loadStatus = function () {
            vm.loading = true;
            vm.error = null;

            // TODO: final endpoint path once backend route is defined
            return $http.get('/api/wireguard/status')
                .then((res) => {
                    if (res && res.data) {
                        vm.status = res.data;
                    }
                })
                .catch((err) => {
                    vm.error = err;
                })
                .finally(() => {
                    vm.loading = false;
                });
        };

        vm.$onInit = function () {
            vm.loadStatus();
        };
    }
};
