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
function LabelContainerController($scope) {
    'ngInject';

    const vm = this;
    // allows to pass a value as function
    vm.valueAsFunction = angular.isObject($scope.config) && angular.isFunction($scope.config.value);

    vm.isEditable = function() {
        return angular.isDefined($scope.isEdit) && $scope.isEdit === true &&
            angular.isFunction($scope.editCallback);
    };

    vm.handleEditCallback = function () {
        $scope.editCallback();
    };

    vm.emptyValue = function() {
        return vm.isEditable() &&
            ((vm.valueAsFunction && angular.isObject($scope.config) && $scope.config.value() === '') ||
            (!vm.valueAsFunction && angular.isObject($scope.config) &&
                ($scope.config.value === undefined || $scope.config.value === '')));
    };

    vm.isImage = function() {
        return angular.isObject($scope.config) &&
            angular.isDefined($scope.config.imagePath) &&
            $scope.config.imagePath !== '';
    };
}

export default function LabelContainerDirective() {
    'ngInject';

    return {
        restrict: 'E',
        scope: {
            label: '@',
            config: '<',
            isEdit: '<',
            editCallback: '&?'
        },
        templateUrl: 'directives/eb-label-container.directive.html',
        replace: true,
        transclude: true,
        controller: LabelContainerController,
        controllerAs: 'vm'
    };
}
