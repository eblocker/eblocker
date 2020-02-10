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
    templateUrl: 'app/components/help/help-inline.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        template: '@',
        asDialog: '<',
        iconOnly: '<'
    }
};

function Controller($translate, DialogService) {
    'ngInject';

    const vm = this;

    vm.showHelp = false;
    vm.showClose = false;

    vm.openHelp = openHelp;
    vm.showAsDialog = showAsDialog;
    vm.onMouseLeave = onMouseLeave;

    function showAsDialog() {
        return angular.isDefined(vm.asDialog) && vm.asDialog === true;
    }

    function openHelp(event, open) {
        if (!showAsDialog()) {
            vm.showHelp = true;
        } else if (open) {
            openHelpDialog(event);
        }
    }

    function openHelpDialog(event) {
        DialogService.helpInfoDialog(event, vm.template);
    }

    function onMouseLeave() {
        if (!vm.showClose) {
            vm.showHelp = false;
        }
    }
}
