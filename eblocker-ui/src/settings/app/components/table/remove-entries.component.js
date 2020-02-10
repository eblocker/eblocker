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
    templateUrl: 'app/components/table/remove-entries.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        tableData: '=',
        buttonLabel: '@',
        buttonTooltip: '@',
        buttonDisabled: '&',
        dialogTitle: '@',
        dialogText: '@',
        dialogTextUndeletable: '@',
        isEntryDeletable: '&',
        deleteSingleEntry: '&?',    // make optional to avoid binding to angular.noop
        onSingleDeleteDone: '&?',   // make optional to avoid binding to angular.noop
        onBulkDelete: '&?',         // make optional to avoid binding to angular.noop
        onBulkDeleteDone: '&?'      // make optional to avoid binding to angular.noop
    }
};

function Controller(logger, DialogService, NotificationService, $q) {
    'ngInject';

    const vm = this;

    vm.getNumberOfSelectedEntries = getNumberOfSelectedEntries;
    vm.handleButtonClick = handleButtonClick;

    function getNumberOfSelectedEntries() {
        let count = 0;
        if (angular.isArray(vm.tableData)) {
            vm.tableData.forEach((entry) => {
                if (entry._checked) {
                    count++;
                }
            });
        }
        // ** we don't need a warning, if tableData is not initialized.
        // In that case the user won't see any data anyhow.
        return count;
    }

    function getSelectedEntries() {
        const selected = [];
        if (angular.isArray(vm.tableData)) {
            vm.tableData.forEach((entry) => {
                if (entry._checked) {
                    selected.push(entry);
                }
            });
        }
        // ** we don't need a warning, if tableData is not initialized.
        // In that case the user won't see any data anyhow.
        return selected;
    }

    function getSelectedDeletableEntries() {
        const selected = [];
        if (angular.isArray(vm.tableData)) {
            vm.tableData.forEach((entry) => {
                if (entry._checked && vm.isEntryDeletable({value: entry})) {
                    selected.push(entry);
                }
            });
        }
        return selected;
    }


    function getNumberOfUndeletable() {
        let count = 0;
        getSelectedEntries().forEach((item) => {
            if (!vm.isEntryDeletable({value: item})){
                count++;
            }
        });
        return count;
    }

    function handleButtonClick(event) {
        const numUndel = getNumberOfUndeletable();

        DialogService.deleteEntries(
            vm.dialogTitle,
            vm.dialogText,
            vm.dialogTextUndeletable,
            numUndel,
            ok,
            null,
            event
        );
    }

    function ok() {
        const selected = getSelectedDeletableEntries();
        return deleteSelected(selected).then(function success() {
            NotificationService.info('ADMINCONSOLE.NOTIFICATION.DELETE_SUCCESS_PARAM',
                {num: vm.deleted, total: selected.length});
        }, function error(response) {
            NotificationService.error('ADMINCONSOLE.NOTIFICATION.DELETE_ERROR', response);
        });
    }

    function deleteSelected(selected) {
        vm.deleted = 0;
        if (angular.isDefined(vm.onBulkDelete) && angular.isFunction(vm.onBulkDelete)) {
            return $q.all(deleteAll(selected));
        } else {
            return $q.all(deleteSingleEntries(selected)).then(function success() {
                if (angular.isDefined(vm.onSingleDeleteDone) && angular.isFunction(vm.onSingleDeleteDone)) {
                    vm.onSingleDeleteDone();
                }
            });
        }
    }

    function deleteAll(selected) {
        const promises = [];
        promises.push(vm.onBulkDelete({values: selected}).then(function success(response) {
            vm.deleted = response.data;
            if (angular.isDefined(vm.onBulkDeleteDone) && angular.isFunction(vm.onBulkDeleteDone)) {
                vm.onBulkDeleteDone();
            }
        }));
        return promises;
    }

    function deleteSingleEntries(selected) {
        const promises = [];
        vm.deleted = 0;
        selected.forEach((entry) => {
            if ( vm.isEntryDeletable({value: entry}) ) {
                const promise = vm.deleteSingleEntry({value: entry});
                // Delete
                if (angular.isDefined(promise) && angular.isDefined(promise.then)) {
                    promises.push(promise.then(function success(deleted) {
                        if (deleted) {
                            vm.deleted++;
                        }
                    }));
                } else {
                    // We deleted local data, so we assume that delete was successful.
                    vm.deleted++;
                }
            } else {
                logger.warning('Unable to delete entry ', entry);
            }
        });
        return promises;
    }

    function getButtonDisabled() {
        return false;
    }

    vm.$onInit = function() {
        vm.isButtonDisabled = angular.isFunction(vm.buttonDisabled) ? vm.buttonDisabled : getButtonDisabled;
    };

}
