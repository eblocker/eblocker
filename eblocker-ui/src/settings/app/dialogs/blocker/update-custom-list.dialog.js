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
export default function AddCustomListController(logger, $mdDialog, $translate, blockerName, edit, formatList,
                                                blockerList, isProcessing, okAction) {
    'ngInject';

    const vm = this;

    vm.save = save;
    vm.cancel = cancel;

    vm.nameMaxLength = 32;
    vm.urlMaxLength = 2048;

    vm.blockerName = blockerName; // e.g. 'Domain Blocker' as opposed to listName which is custom
    vm.isEdit = edit;
    vm.isProcessing = isProcessing;
    vm.formatList = formatList;

    if (angular.isObject(blockerList)) {
        // actual values set if 'edit' is true, otherwise we create a new list with no predefined values.
        vm.readOnly = blockerList.providedByEblocker;
        vm.name = angular.isObject(blockerList.name) && angular.isDefined(blockerList.name[$translate.use()]) ?
            blockerList.name[$translate.use()] : '';
        if (angular.isDefined(blockerList.url)) {
            vm.url = blockerList.url;
            vm.isDownload = true;
        } else {
            vm.content = blockerList.content;
            vm.isDownload = false;
        }
        vm.format = blockerList.format || formatList[0];
        vm.updates = blockerList.updateInterval === 'DAILY';
    } else {
        vm.format = formatList[0];
        vm.isDownload = true;
    }

    function cancel() {
        $mdDialog.cancel();
    }

    function save() {
        vm.hasServerError = false;
        if (formValid()) {
            if (angular.isFunction(okAction)) {
                vm.loading = true;

                blockerList.name = {de: vm.name, en: vm.name};
                if (vm.isDownload) {
                    blockerList.url = vm.url;
                    blockerList.updateInterval = vm.updates ? 'DAILY' : 'NEVER';
                    delete blockerList.content;
                } else {
                    blockerList.content = vm.content;
                    delete blockerList.url;
                    delete blockerList.updateInterval;
                }
                blockerList.format = vm.format;

                okAction(blockerList).then(function success(response) {
                    $mdDialog.hide(response);
                }, function error(response) {
                    vm.hasServerError = true;
                    logger.error('Error saving custom list ', response);
                }).finally(function() {
                    vm.loading = false;
                });
            } else {
                $mdDialog.hide('No OK-function defined.');
            }
        }
    }

    function formValid() {
        return vm.customListForm.name.$valid &&
            ((vm.isDownload && vm.customListForm.url.$valid) ||
             (!vm.isDownload && vm.customListForm.content.$valid)) &&
            vm.customListForm.format.$valid;
    }
}
