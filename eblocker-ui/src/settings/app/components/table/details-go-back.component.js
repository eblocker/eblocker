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
    templateUrl: 'app/components/table/details-go-back.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        state: '@',
        params: '<',
        label: '@'
    }
};

function Controller(StateService) {
    'ngInject';

    const vm = this;

    vm.$onInit = function() {
        if (angular.isObject(vm.params) && angular.isObject(vm.params.param)) {
            vm.pageConfig = vm.params.param.pageConfig;
        }
    };

    vm.goBack = function() {
        const param = {pageConfig: vm.pageConfig};
        StateService.goToState(vm.state, param);
    };
}
