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
    templateUrl: 'app/components/filters/analysis/analysis-details.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller($stateParams, STATES, StateService) { // jshint ignore: line
    'ngInject';
    'use strict';

    const vm = this;


    vm.backState = STATES.FILTER_ANALYSIS;
    vm.stateParams = $stateParams;

    vm.$onInit = function() {
        if (angular.isObject($stateParams.param) &&
            angular.isObject($stateParams.param.entry)) {
            vm.entry = $stateParams.param.entry;
        }

        if (!angular.isObject(vm.entry)) {
            StateService.goToState(vm.backState);
            return;
        }
    };

}
