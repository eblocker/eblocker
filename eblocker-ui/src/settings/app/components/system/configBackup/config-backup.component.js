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
    templateUrl: 'app/components/system/configBackup/config-backup.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(logger, $mdDialog) {
    'ngInject';
    'use strict';

    const vm = this;

    function configBackupExportDialog() {
        return $mdDialog.show({
            controller: 'ConfigBackupExportController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/system/config-backup-export.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false
        });
    }

    function configBackupImportDialog() {
        return $mdDialog.show({
            controller: 'ConfigBackupImportController',
            controllerAs: 'vm',
            templateUrl: 'app/dialogs/system/config-backup-import.dialog.tmpl.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false
        });
    }

    function showExportDialog() {
        configBackupExportDialog().then(function(result) {
            logger.info('Export complete');
        }, function(reason) {
            logger.error('Export dialog failed/cancelled');
        });
    }

    function showImportDialog() {
        configBackupImportDialog().then(function(password) {
            logger.info('Import complete');
        }, function(response) {
            logger.error('Import dialog failed/cancelled');
        });
    }

    vm.startExportDialog = function() {
        showExportDialog();
    };

    vm.startImportDialog = function() {
        showImportDialog();
    };
}
