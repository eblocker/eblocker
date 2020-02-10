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
    templateUrl: 'components/dropdown/drop-down.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        content: '<'
    }
};

function Controller($scope, $window, $timeout) {
    'ngInject';

    const vm = this;

    vm.$onDestroy = function() {
        removeEventListeners();
    };

    vm.toggleDropdown = function() {
        vm.openDropdown = !vm.openDropdown;
        if (vm.openDropdown) {
            addEventListeners();
        } else {
            removeEventListeners();
        }
    };

    vm.clickHandler = function(item, param) {
        $timeout(function () {
            handleClick(item, param);
        }, 0);
    };

    function handleClick(item, param) {
        if (item.closeOnClick) {
            vm.toggleDropdown();
        }
        item.action(param);
    }

    function addEventListeners() {
        if ('ontouchstart' in $window) {
            // iOS may not fire a click event, so react on 'touchstart' instead
            $window.addEventListener('touchstart', eventHandler);
        } else {
            $window.addEventListener('click', eventHandler);
        }
    }

    function removeEventListeners() {
        if ('ontouchstart' in $window) {
            $window.removeEventListener('touchstart', eventHandler);
        } else {
            $window.removeEventListener('click', eventHandler);
        }
    }

    /**
     * Here we bind this click handler on $window, so this handler is also called on any click made in the UI.
     * Window --(Capturing phase)--> target/target phase --(bubbling phase)--> Window.
     * So the code here is called in the bubbling phase, most likely after the event handler of the actual click
     * outside the dropdown has been called. Unless the target is the dropdown, then there should not be any
     * more click events on the way up.
     * So we check if our event target (element clicked) is part of the dropdown. In other words: we check
     * if the click was made on the dropdown or outside the dropdown. In case of click outside, we close the dropdown.
     * In case of inside, we leave the dropdown open -- unless the dropdown action requires to close the dropdown
     * (see handleClick()).
     *
     */
    function eventHandler(event){
        const element = angular.element(document.getElementsByClassName('eblocker-dropdown-component'));
        const isClickedElementChildOfPopup = element
            .find(event.target)
            .length > 0;

        if (isClickedElementChildOfPopup) {
            return;
        }
        vm.openDropdown = false;
        removeEventListeners();
        $scope.$apply();
    }
}
