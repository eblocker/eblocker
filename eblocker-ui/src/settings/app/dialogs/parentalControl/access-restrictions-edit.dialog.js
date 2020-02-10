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
export default function Controller($mdDialog, module) {
    'ngInject';

    const vm = this;

    vm.title = module.name;

    vm.module = module;

    vm.isNew = !angular.isDefined(module.id);

    vm.cancel = function() {
        $mdDialog.cancel();
    };

    vm.save = function() {
        if (filtersSelected()){
            if (!vm.hasExceptions || module.accessRestrictionType === 2) {
                removeExceptions();
            }
            $mdDialog.hide(module);
        }else{
            $mdDialog.hide(module);
        }
    };

    vm.hasExceptions = hasExceptions(); // We do not want to export the function, just the first result!

    vm.getFilterLists = getFilterLists;

    vm.getExceptionFilterLists = getExceptionFilterLists;

    function filtersSelected() {
        // Count activated blacklists
        let activeBlacklists = 0;
        for (let i = 0; i < vm.module.blacklisted.length; i++){
            if (vm.module.blacklisted[i].active) {
                activeBlacklists++;
            }
        }
        // Count activated whitelists
        let activeWhitelists = 0;
        for (let i = 0; i < vm.module.whitelisted.length; i++){
            if (vm.module.whitelisted[i].active) {
                activeWhitelists++;
            }
        }
        return ((vm.module.accessRestrictionType === 2 && activeWhitelists > 0 ) ||
            (vm.module.accessRestrictionType === 1 && activeBlacklists > 0));
    }

    function getFilterLists() {
        return module.accessRestrictionType === 1 ? module.blacklisted : module.whitelisted;
    }

    function getExceptionFilterLists() {
        return module.accessRestrictionType === 1 ? module.whitelisted : module.blacklisted;
    }

    function removeExceptions() {
        if (module.accessRestrictionType === 1) {
            module.whitelisted = [];
        } else {
            module.blacklisted = [];
        }
    }

    function hasExceptions() {
        const exceptionFilterLists = getExceptionFilterLists();
        for (let i = 0; i < exceptionFilterLists.length; i++) {
            if (exceptionFilterLists[i].active) {
                return true;
            }
        }
        return false;
    }
}
