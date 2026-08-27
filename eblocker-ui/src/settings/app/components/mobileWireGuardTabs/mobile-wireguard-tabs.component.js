export default {
    templateUrl: 'app/components/mobileWireGuardTabs/mobile-wireguard-tabs.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller($state, $scope) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.selectedIndex = stateToIndex($state.current.name);
    vm.go = go;

    // keep tab selection in sync if user navigates by URL/back button
    const unbind = $scope.$on('$stateChangeSuccess', function (event, toState) {
        vm.selectedIndex = stateToIndex(toState.name);
    });

    $scope.$on('$destroy', unbind);

    function go(stateName) {
        $state.go(stateName);
    }

    function stateToIndex(stateName) {
        //  WireGuard TAB
        if ($state.includes('mobilewireguard') || stateName === 'mobilewireguard') {
            return 1;
        }
        // default: OpenVPN / status
        return 0;
    }
}
